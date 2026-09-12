require('dotenv').config();
const express = require('express');
const axios = require('axios');
const OpenAI = require('openai');

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

async function classify(goals, activity) {
  const fallback = { classification: 'ambiguous', reasoning: 'Classification was unavailable, so the activity needs cautious review.' };
  try {
    const response = await axios.post('https://openrouter.ai/api/v1/chat/completions', {
      model: process.env.OPENROUTER_MODEL || 'google/gemini-2.5-flash',
      messages: [
        { role: 'system', content: 'Classify an activity against stated goals. Reply ONLY with valid JSON: {"classification":"aligned|misaligned|ambiguous","reasoning":"one sentence"}. Use ambiguous when its purpose could reasonably support a goal.' },
        { role: 'user', content: JSON.stringify({ goals, activity }) }
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

async function ground(goals, activity) {
  try {
    const query = `Is ${activitySummary(activity)} useful for someone whose goals are: ${goals.join(', ')}?`;
    const response = await axios.post('https://api.exa.ai/search', { query, type: 'auto', numResults: 3, contents: { text: { maxCharacters: 500 } } }, { headers: { 'x-api-key': process.env.EXA_API_KEY, 'Content-Type': 'application/json' }, timeout: 12000 });
    return (response.data.results || []).slice(0, 3).map((result) => ({ title: result.title || 'Untitled source', snippet: result.text || result.highlights?.join(' ') || '' })).filter((result) => result.snippet || result.title !== 'Untitled source');
  } catch (error) {
    console.error('[ground] Exa failed:', error.message);
    return [];
  }
}

async function decide(goals, activity, classification, grounding) {
  try {
    const openai = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });
    const response = await openai.responses.create({
      model: process.env.OPENAI_MODEL || 'gpt-5.6', reasoning: { effort: 'low' }, max_output_tokens: 300,
      instructions: 'You are a thoughtful mobile-app nudge engine. Decide whether to notify the person. Do not nag when behavior is aligned or reasonably justified. If notifying, be warm, brief, and never guilt-trip. The message is 1-2 sentences. microAction is one concrete, immediately doable action. For commuting, include a specific leave-by time when the input time makes that possible.',
      input: JSON.stringify({ goals, activity, classification, grounding }),
      text: { format: { type: 'json_schema', name: 'nudge_decision', strict: true, schema: { type: 'object', additionalProperties: false, properties: { shouldNotify: { type: 'boolean' }, message: { type: 'string' }, microAction: { type: 'string' } }, required: ['shouldNotify', 'message', 'microAction'] } } }
    });
    return { ...parseJson(response.output_text), error: false };
  } catch (error) {
    console.error('[decide] OpenAI failed:', error.message);
    return fallbackDecision;
  }
}

app.post('/nudge', async (req, res) => {
  const { goals, activity } = req.body || {};
  if (!Array.isArray(goals) || !goals.length || !activity || typeof activity.app !== 'string') return res.status(400).json({ error: 'Provide non-empty goals and an activity with an app.' });
  const stageOne = await classify(goals, activity);
  const grounding = stageOne.classification === 'ambiguous' ? await ground(goals, activity) : [];
  const decision = await decide(goals, activity, stageOne, grounding);
  res.json({ classification: stageOne.classification, shouldNotify: decision.shouldNotify, message: decision.message, microAction: decision.microAction, groundingUsed: grounding.length > 0, ...(decision.error ? { error: true } : {}) });
});
app.get('/health', (_req, res) => res.json({ ok: true }));
app.listen(port, () => console.log(`nudge-engine listening on http://localhost:${port}`));
