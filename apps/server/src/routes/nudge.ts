import { Router } from 'express';
import { activeRoutineContext, routineSummary } from '../routine-context';
import { ground } from '../services/exa';
import {
  chatJson,
  classifyWithChatFallback,
  classifyWithJev,
  decisionSchema,
  goalsSchema,
  parserModel,
  writerModel,
} from '../services/openrouter';
import type { Activity, Routine, Session } from '../types';

const router = Router();
const GROUNDING_CONFIDENCE_MIN = Number(process.env.GROUNDING_CONFIDENCE_MIN || 0.6);
const fallbackDecision = { shouldNotify: false, message: '', microAction: '', error: true };
type NudgeBody = {
  goals?: unknown;
  activity?: Activity;
  session?: Session;
  habits?: unknown;
  routineContext?: unknown;
};
type Decision = { shouldNotify: boolean; message: string; microAction: string };

async function classify(
  goals: string[],
  activity: Activity | undefined,
  session: Session | undefined,
  habits: string,
  routines: Routine[],
) {
  try {
    const result = await classifyWithJev(goals, activity, session, habits, routines);
    console.log(
      `[classify] path=jev classification=${result.classification} confidence=${result.confidence}`,
    );
    return result;
  } catch (error) {
    console.error(
      '[classify] Jev failed; using chat fallback:',
      error instanceof Error ? error.message : error,
    );
    try {
      const result = await classifyWithChatFallback(
        goals,
        activity,
        session,
        habits,
        routineSummary(routines),
      );
      console.log(`[classify] path=chat-fallback classification=${result.classification}`);
      return result;
    } catch (fallbackError) {
      console.error(
        '[classify] chat fallback failed:',
        fallbackError instanceof Error ? fallbackError.message : fallbackError,
      );
      return { classification: 'ambiguous' as const, path: 'fallback-unavailable' };
    }
  }
}

async function decide(
  goals: string[],
  activity: Activity | undefined,
  session: Session | undefined,
  habits: string,
  classification: string,
  grounding: unknown[],
  routines: Routine[],
) {
  try {
    return {
      ...(await chatJson<Decision>({
        model: writerModel,
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
              routineContext: routines,
            }),
          },
        ],
      })),
      error: false,
    };
  } catch (error) {
    console.error('[decide] OpenRouter failed:', error instanceof Error ? error.message : error);
    return fallbackDecision;
  }
}

router.post('/nudge', async (req, res) => {
  const {
    goals,
    activity,
    session,
    habits = '',
    routineContext = [],
  } = (req.body || {}) as NudgeBody;
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

  const typedGoals = goals.filter((goal): goal is string => typeof goal === 'string');
  const typedActivity = validActivity ? activity : undefined;
  const typedSession = validSession ? session : undefined;
  const activeRoutines = activeRoutineContext(typedActivity, routineContext);
  if (activeRoutines.length) console.log(`[nudge] ${routineSummary(activeRoutines)}`);
  const stageOne = await classify(
    typedGoals,
    typedActivity,
    typedSession,
    typeof habits === 'string' ? habits : '',
    activeRoutines,
  );
  const groundingRequired =
    stageOne.classification === 'ambiguous' ||
    (Number.isFinite(stageOne.confidence) &&
      (stageOne.confidence as number) < GROUNDING_CONFIDENCE_MIN);
  const grounding = groundingRequired ? await ground(typedGoals, typedActivity, typedSession) : [];
  const decision = await decide(
    typedGoals,
    typedActivity,
    typedSession,
    typeof habits === 'string' ? habits : '',
    stageOne.classification,
    grounding,
    activeRoutines,
  );
  return res.json({
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

router.post('/parse-goals', async (req, res) => {
  const { transcript, existingGoals = [] } = (req.body || {}) as {
    transcript?: unknown;
    existingGoals?: unknown;
  };
  if (typeof transcript !== 'string' || !transcript.trim() || !Array.isArray(existingGoals))
    return res.status(400).json({ error: 'Provide a spoken transcript and existingGoals array.' });
  try {
    const currentGoals = existingGoals
      .map(String)
      .map((goal) => goal.trim())
      .filter(Boolean)
      .slice(0, 3);
    const parsed = await chatJson<{ goals: unknown[] }>({
      model: parserModel,
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
          content: `Current goals:\n${currentGoals.length ? currentGoals.map((goal, index) => `${index + 1}. ${goal}`).join('\n') : 'none'}\n\nNew statement from user: ${JSON.stringify(transcript.trim())}`,
        },
      ],
    });
    const parsedGoals = (Array.isArray(parsed.goals) ? parsed.goals : [])
      .map(String)
      .map((goal) => goal.trim())
      .filter(Boolean)
      .slice(0, 3);
    if (!parsedGoals.length) throw new Error('Model returned no goals');
    return res.json({ goals: parsedGoals });
  } catch (error) {
    console.error('[parse-goals] failed:', error instanceof Error ? error.message : error);
    return res
      .status(502)
      .json({ error: 'Could not parse spoken goals. Please try again or type them manually.' });
  }
});

export default router;
