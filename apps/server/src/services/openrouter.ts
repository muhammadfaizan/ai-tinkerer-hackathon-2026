import axios from 'axios';
import type { Activity, Routine, Session } from '../routes/types';
import type { ChatJsonRequest, Classification, JsonSchema, StageOne } from './types';

const CHAT_URL = 'https://openrouter.ai/api/v1/chat/completions';
const DECISIONS_URL = 'https://openrouter.ai/api/alpha/decisions';
const JEV_MODEL = 'typesafe/jev-1.13';
const CHAT_MODEL = process.env.OPENROUTER_CLASSIFIER_MODEL || 'google/gemini-2.5-flash-lite';
const WRITER_MODEL = process.env.OPENROUTER_WRITER_MODEL || 'google/gemini-2.5-flash-lite';
const PARSER_MODEL = process.env.OPENROUTER_PARSER_MODEL || 'google/gemini-2.5-flash-lite';

const headers = () => ({
  Authorization: `Bearer ${process.env.OPENROUTER_API_KEY}`,
  'Content-Type': 'application/json',
  'HTTP-Referer': 'http://localhost:3000',
  'X-Title': 'nudge-engine',
});

const parseJson = <T>(value: unknown): T => {
  if (typeof value === 'object' && value !== null) return value as T;
  const match = String(value).match(/\{[\s\S]*\}/);
  return JSON.parse(match ? match[0] : String(value)) as T;
};

export async function chatJson<T>({
  model,
  messages,
  schema,
  name,
  maxTokens,
}: ChatJsonRequest): Promise<T> {
  const request = {
    model,
    messages,
    temperature: 0,
    max_tokens: maxTokens,
    response_format: { type: 'json_schema', json_schema: { name, strict: true, schema } },
  };
  try {
    const response = await axios.post(CHAT_URL, request, { headers: headers(), timeout: 15_000 });
    return parseJson<T>(response.data.choices?.[0]?.message?.content);
  } catch (firstError) {
    try {
      const response = await axios.post(
        CHAT_URL,
        {
          ...request,
          response_format: undefined,
          messages: [
            ...messages,
            {
              role: 'user',
              content: 'Reply with only one valid JSON object matching the requested schema.',
            },
          ],
        },
        { headers: headers(), timeout: 15_000 },
      );
      return parseJson<T>(response.data.choices?.[0]?.message?.content);
    } catch (retryError) {
      const firstMessage = firstError instanceof Error ? firstError.message : String(firstError);
      if (retryError instanceof Error)
        retryError.message += ` (structured-output attempt: ${firstMessage})`;
      throw retryError;
    }
  }
}

const classificationSchema: JsonSchema = {
  type: 'object',
  additionalProperties: false,
  properties: { classification: { type: 'string', enum: ['aligned', 'misaligned', 'ambiguous'] } },
  required: ['classification'],
};

export const decisionSchema: JsonSchema = {
  type: 'object',
  additionalProperties: false,
  properties: {
    shouldNotify: { type: 'boolean' },
    message: { type: 'string' },
    microAction: { type: 'string' },
  },
  required: ['shouldNotify', 'message', 'microAction'],
};

export const goalsSchema: JsonSchema = {
  type: 'object',
  additionalProperties: false,
  properties: { goals: { type: 'array', items: { type: 'string' }, minItems: 1, maxItems: 3 } },
  required: ['goals'],
};

const state = (
  goals: string[],
  activity?: Activity,
  session?: Session,
  habits = '',
  routineContext: Routine[] = [],
) => ({
  goals,
  activity: activity
    ? {
        app: activity.app,
        durationMin: activity.durationMin ?? null,
        timeOfDay: activity.timeOfDay ?? null,
        category: activity.category?.trim() || null,
      }
    : null,
  session: session
    ? {
        apps: session.apps,
        totalDurationMin: session.totalDurationMin,
        windowMin: session.windowMin ?? 30,
      }
    : null,
  timeOfDay: activity?.timeOfDay ?? null,
  category: activity?.category?.trim() || null,
  routineContext,
  habits: habits || null,
});

export async function classifyWithJev(
  goals: string[],
  activity?: Activity,
  session?: Session,
  habits = '',
  routineContext: Routine[] = [],
): Promise<StageOne> {
  const response = await axios.post(
    DECISIONS_URL,
    {
      model: JEV_MODEL,
      state: state(goals, activity, session, habits, routineContext),
      questions: {
        alignment: {
          type: 'choice',
          instructions:
            'Choose the best label for whether this observed app activity or combined session aligns with the stated goals. Use routine context only when it applies to the current time. Choose ambiguous when the available information cannot reliably establish alignment.',
          criteria: {
            aligned:
              'The activity or session clearly advances, or reasonably supports, at least one stated goal.',
            misaligned:
              'The activity or session likely detracts from the stated goals without a reasonable goal-supporting purpose.',
            ambiguous:
              'The purpose is uncertain, a plausible goal-supporting purpose exists, or the evidence is insufficient.',
          },
        },
      },
    },
    { headers: headers(), timeout: 15_000 },
  );
  const answer = response.data?.answers?.alignment;
  if (!answer || !['aligned', 'misaligned', 'ambiguous'].includes(answer.choice))
    throw new Error('Jev returned no valid alignment choice');
  return {
    classification: answer.choice,
    confidence: Number(answer.confidence),
    probabilities: answer.probabilities || {},
    path: 'jev',
  };
}

export async function classifyWithChatFallback(
  goals: string[],
  activity?: Activity,
  session?: Session,
  habits = '',
  routineText = '',
): Promise<StageOne> {
  const activityText = activity
    ? [
        `App: ${activity.app}`,
        `Duration: ${activity.durationMin ?? 'unknown'} minutes`,
        `Time of day: ${activity.timeOfDay ?? 'unknown'}`,
        activity.category?.trim() ? `Category: ${activity.category.trim()}` : null,
      ]
        .filter(Boolean)
        .join('\n')
    : `Session: ${session?.apps.map(({ app, durationMin }) => `${app} for ${durationMin} minutes`).join(', ')} in the last ${session?.windowMin ?? 30} minutes (total: ${session?.totalDurationMin} minutes)`;
  const parsed = await chatJson<{ classification: Classification }>({
    model: CHAT_MODEL,
    name: 'activity_classification',
    schema: classificationSchema,
    maxTokens: 80,
    messages: [
      {
        role: 'system',
        content:
          'Classify an activity against stated goals. Use ambiguous when its purpose could reasonably support a goal. Treat supplied routines as optional context only when their current window applies.',
      },
      {
        role: 'user',
        content: `Goals: ${goals.join(', ')}\n${activityText}${routineText ? `\n${routineText}` : ''}${habits ? `\nHabits: ${habits}` : ''}`,
      },
    ],
  });
  return {
    classification: ['aligned', 'misaligned', 'ambiguous'].includes(parsed.classification)
      ? parsed.classification
      : 'ambiguous',
    path: 'chat-fallback',
  };
}

export const writerModel = WRITER_MODEL;
export const parserModel = PARSER_MODEL;
