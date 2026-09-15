require('dotenv').config();
const express = require('express');
const axios = require('axios');
const OpenAI = require('openai');
const ExaModule = require('exa-js');
const Exa = ExaModule.default || ExaModule;

const app = express();
const port = process.env.PORT || 3000;
app.use(express.json());

const fallbackDecision = { shouldNotify: false, message: '', microAction: '', error: true };
const parseJson = (value) => {
  if (typeof value === 'object' && value !== null) return value;
  const match = String(value).match(/\{[\s\S]*\}/);
  return JSON.parse(match ? match[0] : value);
};
const activitySummary = (activity) => `${activity.app} for ${activity.durationMin ?? 'an unknown number of'} minutes at ${activity.timeOfDay ?? 'an unknown time'}`;
const sessionSummary = (session) => `${session.apps.map(({ app, durationMin }) => `${app} for ${durationMin} minutes`).join(', ')} in the last ${session.windowMin ?? 30} minutes (total: ${session.totalDurationMin} minutes)`;

async function classify(goals, activity, session, habits = '') {
  const fallback = { classification: 'ambiguous', reasoning: 'Classification was unavailable, so the activity needs cautious review.' };
  const activityContext = activity ? [
    `App: ${activity.app}`,
    `Duration: ${activity.durationMin ?? 'unknown'} minutes`,
    `Time of day: ${activity.timeOfDay ?? 'unknown'}`,
    typeof activity.category === 'string' && activity.category.trim() ? `Category: ${activity.category.trim()}` : null
  ].filter(Boolean).join('\n') : `Session: ${sessionSummary(session)}`;
  try {
    const response = await axios.post('https://openrouter.ai/api/v1/chat/completions', {
      model: process.env.OPENROUTER_MODEL || 'google/gemini-2.5-flash',
      messages: [
        { role: 'system', content: 'Classify an activity against stated goals. Reply ONLY with valid JSON: {"classification":"aligned|misaligned|ambiguous","reasoning":"one sentence"}. Use ambiguous when its purpose could reasonably support a goal.' },
        { role: 'user', content: `Goals: ${goals.join(', ')}\n${activityContext}${habits ? `\nHabits: ${habits}` : ''}` }
      ], temperature: 0
    }, { headers: { Authorization: `Bearer ${process.env.OPENROUTER_API_KEY}`, 'Content-Type': 'application/json', 'HTTP-Referer': 'http://localhost:3000', 'X-Title': 'nudge-engine' }, timeout: 15000 });
    const parsed = parseJson(response.data.choices?.[0]?.message?.content);
    const classification = ['aligned', 'misaligned', 'ambiguous'].includes(parsed.classification) ? parsed.classification : 'ambiguous';
    return { classification, reasoning: String(parsed.reasoning || fallback.reasoning) };
  } catch (error) {
    console.error('[classify] OpenRouter failed:', error.message);
    return fallback;
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
        exa.search(query, {
          type: 'auto',
          numResults: 3,
          contents: { highlights: true }
        }),
        new Promise((_, reject) => {
          timeoutId = setTimeout(() => reject(new Error('Exa request timed out after 12 seconds')), 12000);
        })
      ]);

      return (response.results || []).slice(0, 3).map((result) => ({
        title: result.title || 'Untitled source',
        snippet: Array.isArray(result.highlights) ? result.highlights.join(' ') : ''
      })).filter((result) => result.snippet || result.title !== 'Untitled source');
    } finally {
      clearTimeout(timeoutId);
    }
  } catch (error) {
    console.error('[ground] Exa failed:', error.message);
    return [];
  }
}

async function decide(goals, activity, session, habits, classification, grounding) {
  try {
    const openai = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });
    const response = await openai.responses.create({
      model: process.env.OPENAI_MODEL || 'gpt-5.6', reasoning: { effort: 'low' }, max_output_tokens: 300,
      instructions: 'You are a thoughtful mobile-app nudge engine. Decide whether to notify the person. Do not nag when behavior is aligned or reasonably justified. If notifying, be warm, brief, and never guilt-trip. The message is 1-2 sentences. microAction is one concrete, immediately doable action. When a session is supplied, assess the combined app list and total duration rather than treating it as one app. Personalize recommendations using the supplied habits only when relevant; never invent habits. For commuting, include a specific leave-by time when the input time makes that possible. When an active commute can support movement, suggest walking to the station or part of the route. When a reading goal fits transit time, suggest taking a book or audiobook along.',
      input: JSON.stringify({ goals, activity, session, habits, classification, grounding }),
      text: { format: { type: 'json_schema', name: 'nudge_decision', strict: true, schema: { type: 'object', additionalProperties: false, properties: { shouldNotify: { type: 'boolean' }, message: { type: 'string' }, microAction: { type: 'string' } }, required: ['shouldNotify', 'message', 'microAction'] } } }
    });
    return { ...parseJson(response.output_text), error: false };
  } catch (error) {
    console.error('[decide] OpenAI failed:', error.message);
    return fallbackDecision;
  }
}

app.post('/nudge', async (req, res) => {
  const { goals, activity, session, habits = '' } = req.body || {};
  const validActivity = activity && typeof activity.app === 'string';
  const validSession = session && Array.isArray(session.apps) && session.apps.length && typeof session.totalDurationMin === 'number';
  if (!Array.isArray(goals) || !goals.length || (!validActivity && !validSession)) return res.status(400).json({ error: 'Provide non-empty goals and either an activity with an app or a session with apps.' });
  const stageOne = await classify(goals, activity, session, habits);
  const grounding = stageOne.classification === 'ambiguous' ? await ground(goals, activity, session) : [];
  const decision = await decide(goals, activity, session, habits, stageOne, grounding);
  res.json({ classification: stageOne.classification, shouldNotify: decision.shouldNotify, message: decision.message, microAction: decision.microAction, groundingUsed: grounding.length > 0, ...(decision.error ? { error: true } : {}) });
});

app.post('/parse-goals', async (req, res) => {
  const { transcript, existingGoals = [] } = req.body || {};
  if (typeof transcript !== 'string' || !transcript.trim() || !Array.isArray(existingGoals)) return res.status(400).json({ error: 'Provide a spoken transcript and existingGoals array.' });
  try {
    const currentGoals = existingGoals.map(String).map((goal) => goal.trim()).filter(Boolean).slice(0, 3);
    const currentGoalsText = currentGoals.length ? currentGoals.map((goal, index) => `${index + 1}. ${goal}`).join('\n') : 'none';
    const openai = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });
    const response = await openai.responses.create({
      model: process.env.OPENAI_MODEL || 'gpt-5.6', reasoning: { effort: 'low' }, max_output_tokens: 200,
      instructions: 'Return the FULL updated goal list as concise, plain-language self-improvement goals. Start with the current goals exactly as given. Add the new goal from the user statement alongside them unless it clearly and explicitly replaces a specific existing goal. Never drop an existing goal unless the statement explicitly replaces it. Maximum three goals: if the list is already full and there is no explicit replacement, return the current goals unchanged. Return only the requested JSON.',
      input: `Current goals:\n${currentGoalsText}\n\nNew statement from user: ${JSON.stringify(transcript.trim())}`,
      text: { format: { type: 'json_schema', name: 'parsed_goals', strict: true, schema: { type: 'object', additionalProperties: false, properties: { goals: { type: 'array', items: { type: 'string' }, minItems: 1, maxItems: 3 } }, required: ['goals'] } } }
    });
    const goals = parseJson(response.output_text).goals.map(String).map((goal) => goal.trim()).filter(Boolean).slice(0, 3);
    res.json({ goals });
  } catch (error) {
    console.error('[parse-goals] failed:', error.message);
    res.status(502).json({ error: 'Could not parse spoken goals. Please try again or type them manually.' });
  }
});
app.get('/health', (_req, res) => res.json({ ok: true }));
app.listen(port, () => console.log(`nudge-engine listening on http://localhost:${port}`));
