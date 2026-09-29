require('dotenv').config();
const express = require('express');
const axios = require('axios');
const ExaModule = require('exa-js');
const Exa = ExaModule.default || ExaModule;
const { activeRoutineContext, routineSummary } = require('./routine-context');

const app = express();
const port = process.env.PORT || 3000;
const OPENROUTER_CHAT_URL = 'https://openrouter.ai/api/v1/chat/completions';
const OPENROUTER_DECISIONS_URL = 'https://openrouter.ai/api/alpha/decisions';
const JEV_MODEL = 'typesafe/jev-1.13';
const CHAT_MODEL = process.env.OPENROUTER_CLASSIFIER_MODEL || 'google/gemini-2.5-flash-lite';
const WRITER_MODEL = process.env.OPENROUTER_WRITER_MODEL || 'google/gemini-2.5-flash-lite';
const PARSER_MODEL = process.env.OPENROUTER_PARSER_MODEL || 'google/gemini-2.5-flash-lite';
const GROUNDING_CONFIDENCE_MIN = Number(process.env.GROUNDING_CONFIDENCE_MIN || 0.6);
app.use(express.json());

const fallbackDecision = { shouldNotify: false, message: '', microAction: '', error: true };
const parseJson = (value) => {
  if (typeof value === 'object' && value !== null) return value;
  const match = String(value).match(/\{[\s\S]*\}/);
  return JSON.parse(match ? match[0] : value);
};
const activitySummary = (activity) =>
  `${activity.app} for ${activity.durationMin ?? 'an unknown number of'} minutes at ${activity.timeOfDay ?? 'an unknown time'}`;
const sessionSummary = (session) =>
  `${session.apps.map(({ app, durationMin }) => `${app} for ${durationMin} minutes`).join(', ')} in the last ${session.windowMin ?? 30} minutes (total: ${session.totalDurationMin} minutes)`;
const openRouterHeaders = {
  Authorization: `Bearer ${process.env.OPENROUTER_API_KEY}`,
  'Content-Type': 'application/json',
  'HTTP-Referer': 'http://localhost:3000',
  'X-Title': 'nudge-engine',
};

async function chatJson({ model, messages, schema, name, maxTokens }) {
  const request = {
    model,
    messages,
    temperature: 0,
    max_tokens: maxTokens,
    response_format: { type: 'json_schema', json_schema: { name, strict: true, schema } },
  };
  try {
    const response = await axios.post(OPENROUTER_CHAT_URL, request, {
      headers: openRouterHeaders,
      timeout: 15000,
    });
    return parseJson(response.data.choices?.[0]?.message?.content);
  } catch (firstError) {
    const retry = {
      ...request,
      response_format: undefined,
      messages: [
        ...messages,
        {
          role: 'user',
          content: 'Reply with only one valid JSON object matching the requested schema.',
        },
      ],
    };
    try {
      const response = await axios.post(OPENROUTER_CHAT_URL, retry, {
        headers: openRouterHeaders,
        timeout: 15000,
      });
      return parseJson(response.data.choices?.[0]?.message?.content);
    } catch (retryError) {
      retryError.message = `${retryError.message} (structured-output attempt: ${firstError.message})`;
      throw retryError;
    }
  }
}

const classificationSchema = {
  type: 'object',
  additionalProperties: false,
  properties: { classification: { type: 'string', enum: ['aligned', 'misaligned', 'ambiguous'] } },
  required: ['classification'],
};
const decisionSchema = {
  type: 'object',
  additionalProperties: false,
  properties: {
    shouldNotify: { type: 'boolean' },
    message: { type: 'string' },
    microAction: { type: 'string' },
  },
  required: ['shouldNotify', 'message', 'microAction'],
};
const goalsSchema = {
  type: 'object',
  additionalProperties: false,
  properties: { goals: { type: 'array', items: { type: 'string' }, minItems: 1, maxItems: 3 } },
  required: ['goals'],
};

function classificationState(goals, activity, session, habits, routineContext) {
  return {
    goals,
    activity: activity
      ? {
          app: activity.app,
          durationMin: activity.durationMin ?? null,
          timeOfDay: activity.timeOfDay ?? null,
          category: activity.category?.trim?.() || null,
        }
      : null,
    session: session
      ? {
          apps: session.apps.map(({ app, durationMin }) => ({ app, durationMin })),
          totalDurationMin: session.totalDurationMin,
          windowMin: session.windowMin ?? 30,
        }
      : null,
    timeOfDay: activity?.timeOfDay ?? null,
    category: activity?.category?.trim?.() || null,
    routineContext,
    habits: habits || null,
  };
}

async function classifyWithJev(goals, activity, session, habits, routineContext) {
  const response = await axios.post(
    OPENROUTER_DECISIONS_URL,
    {
      model: JEV_MODEL,
      state: classificationState(goals, activity, session, habits, routineContext),
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
    { headers: openRouterHeaders, timeout: 15000 },
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

async function classifyWithChatFallback(goals, activity, session, habits, routineContext) {
  const routineContextText = routineSummary(routineContext);
  const activityContext = activity
    ? [
        `App: ${activity.app}`,
        `Duration: ${activity.durationMin ?? 'unknown'} minutes`,
        `Time of day: ${activity.timeOfDay ?? 'unknown'}`,
        activity.category?.trim?.() ? `Category: ${activity.category.trim()}` : null,
      ]
        .filter(Boolean)
        .join('\n')
    : `Session: ${sessionSummary(session)}`;
  const parsed = await chatJson({
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
        content: `Goals: ${goals.join(', ')}\n${activityContext}${routineContextText ? `\n${routineContextText}` : ''}${habits ? `\nHabits: ${habits}` : ''}`,
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

async function classify(goals, activity, session, habits = '', routineContext = []) {
  try {
    const result = await classifyWithJev(goals, activity, session, habits, routineContext);
    console.log(
      `[classify] path=jev classification=${result.classification} confidence=${result.confidence}`,
    );
    return result;
  } catch (error) {
    console.error('[classify] Jev failed; using chat fallback:', error.message);
    try {
      const result = await classifyWithChatFallback(
        goals,
        activity,
        session,
        habits,
        routineContext,
      );
      console.log(`[classify] path=chat-fallback classification=${result.classification}`);
      return result;
    } catch (fallbackError) {
      console.error('[classify] chat fallback failed:', fallbackError.message);
      return { classification: 'ambiguous', path: 'fallback-unavailable' };
    }
  }
}

async function ground(goals, activity, session) {
  try {
    if (!process.env.EXA_API_KEY) {
      console.warn('[ground] Exa skipped: EXA_API_KEY is not configured.');
      return [];
    }
    const exa = new Exa(process.env.EXA_API_KEY);
    const query = `Is ${activity ? activitySummary(activity) : sessionSummary(session)} plausibly useful toward these goals: ${goals.join(', ')}?`;
    let timeoutId;
    try {
      const response = await Promise.race([
        exa.search(query, { type: 'auto', numResults: 3, contents: { highlights: true } }),
        new Promise((_, reject) => {
          timeoutId = setTimeout(
            () => reject(new Error('Exa request timed out after 12 seconds')),
            12000,
          );
        }),
      ]);
      return (response.results || [])
        .slice(0, 3)
        .map((result) => ({
          title: result.title || 'Untitled source',
          snippet: Array.isArray(result.highlights) ? result.highlights.join(' ') : '',
        }))
        .filter((result) => result.snippet || result.title !== 'Untitled source');
    } finally {
      clearTimeout(timeoutId);
    }
  } catch (error) {
    console.error('[ground] Exa failed:', error.message);
    return [];
  }
}

async function decide(
  goals,
  activity,
  session,
  habits,
  classification,
  grounding,
  routineContext = [],
) {
  try {
    const result = await chatJson({
      model: WRITER_MODEL,
      name: 'nudge_decision',
      schema: decisionSchema,
      maxTokens: 300,
      messages: [
        {
          role: 'system',
          content:
            'You are a thoughtful mobile-app nudge engine. Decide whether to notify the person. Do not nag when behavior is aligned or reasonably justified. If notifying, be warm, brief, and never guilt-trip. The message is 1-2 sentences. microAction is one concrete, immediately doable action. When a session is supplied, assess the combined app list and total duration rather than treating it as one app. Personalize recommendations using supplied habits only when relevant; never invent habits. Treat each routine as optional context only for its current window; do not blanket-suppress nudges outside it. For commuting, include a specific leave-by time when the input time makes that possible. When an active commute can support movement, suggest walking to the station or part of the route. When a reading goal fits transit time, suggest taking a book or audiobook along.',
        },
        {
          role: 'user',
          content: JSON.stringify({
            goals,
            activity,
            session,
            habits,
            classification,
            grounding,
            routineContext,
          }),
        },
      ],
    });
    return { ...result, error: false };
  } catch (error) {
    console.error('[decide] OpenRouter failed:', error.message);
    return fallbackDecision;
  }
}

app.post('/nudge', async (req, res) => {
  const { goals, activity, session, habits = '', routineContext = [] } = req.body || {};
  const validActivity = activity && typeof activity.app === 'string';
  const validSession =
    session &&
    Array.isArray(session.apps) &&
    session.apps.length &&
    typeof session.totalDurationMin === 'number';
  if (!Array.isArray(goals) || !goals.length || (!validActivity && !validSession))
    return res.status(400).json({
      error: 'Provide non-empty goals and either an activity with an app or a session with apps.',
    });
  const activeRoutines = activeRoutineContext(activity, routineContext);
  if (activeRoutines.length) console.log(`[nudge] ${routineSummary(activeRoutines)}`);
  const stageOne = await classify(goals, activity, session, habits, activeRoutines);
  const groundingRequired =
    stageOne.classification === 'ambiguous' ||
    (Number.isFinite(stageOne.confidence) && stageOne.confidence < GROUNDING_CONFIDENCE_MIN);
  const grounding = groundingRequired ? await ground(goals, activity, session) : [];
  const decision = await decide(
    goals,
    activity,
    session,
    habits,
    stageOne.classification,
    grounding,
    activeRoutines,
  );
  res.json({
    classification: stageOne.classification,
    ...(Number.isFinite(stageOne.confidence)
      ? { confidence: stageOne.confidence, probabilities: stageOne.probabilities }
      : {}),
    shouldNotify: decision.shouldNotify,
    message: decision.message,
    microAction: decision.microAction,
    groundingUsed: grounding.length > 0,
    ...(decision.error ? { error: true } : {}),
  });
});

app.post('/parse-goals', async (req, res) => {
  const { transcript, existingGoals = [] } = req.body || {};
  if (typeof transcript !== 'string' || !transcript.trim() || !Array.isArray(existingGoals))
    return res.status(400).json({ error: 'Provide a spoken transcript and existingGoals array.' });
  try {
    const currentGoals = existingGoals
      .map(String)
      .map((goal) => goal.trim())
      .filter(Boolean)
      .slice(0, 3);
    const currentGoalsText = currentGoals.length
      ? currentGoals.map((goal, index) => `${index + 1}. ${goal}`).join('\n')
      : 'none';
    const parsed = await chatJson({
      model: PARSER_MODEL,
      name: 'parsed_goals',
      schema: goalsSchema,
      maxTokens: 200,
      messages: [
        {
          role: 'system',
          content:
            'Return the FULL updated goal list as concise, plain-language self-improvement goals. Start with the current goals exactly as given. Add the new goal from the user statement alongside them unless it clearly and explicitly replaces a specific existing goal. Never drop an existing goal unless the statement explicitly replaces it. Maximum three goals: if the list is already full and there is no explicit replacement, return the current goals unchanged.',
        },
        {
          role: 'user',
          content: `Current goals:\n${currentGoalsText}\n\nNew statement from user: ${JSON.stringify(transcript.trim())}`,
        },
      ],
    });
    const goals = (Array.isArray(parsed.goals) ? parsed.goals : [])
      .map(String)
      .map((goal) => goal.trim())
      .filter(Boolean)
      .slice(0, 3);
    if (!goals.length) throw new Error('Model returned no goals');
    res.json({ goals });
  } catch (error) {
    console.error('[parse-goals] failed:', error.message);
    res
      .status(502)
      .json({ error: 'Could not parse spoken goals. Please try again or type them manually.' });
  }
});
app.get('/health', (_req, res) => res.json({ ok: true }));
if (require.main === module)
  app.listen(port, () => console.log(`nudge-engine listening on http://localhost:${port}`));

module.exports = app;
