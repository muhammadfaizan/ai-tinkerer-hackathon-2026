# nudge-engine

A minimal Express backend that turns goals and current activity into a considerate mobile-app nudge. It uses OpenRouter for classification, optionally Exa for grounding ambiguous cases, and the official OpenAI SDK / Responses API for a structured final decision.

## Run

Requires Node.js 18+.

```bash
npm install
cp .env.example .env
# Add your three API keys to .env
npm start
```

In another terminal, run the three demo scenarios:

```bash
node test.js
```

The server runs on `http://localhost:3000` by default. Set `PORT` to change it. Optional `OPENROUTER_MODEL` and `OPENAI_MODEL` environment variables override the defaults (`google/gemini-2.5-flash` and `gpt-5.6`).

## Interactive API documentation

The reusable API definition is [openapi.yaml](./openapi.yaml). To present and test it in Swagger UI, run this in a separate terminal:

```bash
npm run swagger
```

Swagger UI starts a local documentation server and watches `openapi.yaml` for changes. Start `npm run start` separately before using **Try it out** against the local API.

## API

```bash
curl -X POST http://localhost:3000/nudge \
  -H 'Content-Type: application/json' \
  -d '{
    "goals": ["read more books", "lose weight"],
    "activity": {"app": "Instagram", "durationMin": 22, "timeOfDay": "21:30"}
  }'
```

Example response:

```json
{
  "classification": "misaligned",
  "shouldNotify": true,
  "message": "A short break from the feed could make room for the reading you wanted tonight.",
  "microAction": "Put Instagram away and read 10 pages before bed.",
  "groundingUsed": false
}
```

If OpenRouter fails, classification safely becomes `ambiguous`. Exa failures simply omit grounding. If OpenAI fails, the API returns `shouldNotify: false`, empty text fields, and `error: true`; each failure is logged to the server console.

OpenAI’s official docs list `gpt-5.6` as a flagship-model alias, support it on the Responses API, and support structured outputs: [model documentation](https://developers.openai.com/api/docs/models/gpt-5.6-sol).
