# nudge-engine

A minimal Express backend that turns goals and current activity into a considerate mobile-app nudge. It uses Jev through OpenRouter's Decisions API for classification, OpenRouter chat completions for structured writing and goal parsing, and optionally Exa for grounding.

## Run

Requires Node.js 18+.

```bash
npm install
cp .env.example .env
# Add OPENROUTER_API_KEY and EXA_API_KEY to .env
npm start
```

In another terminal, run the three demo scenarios:

```bash
node test.js
```

The server runs on `http://localhost:3000` by default. Set `PORT` to change it. `OPENROUTER_WRITER_MODEL` and `OPENROUTER_PARSER_MODEL` optionally override the default `google/gemini-2.5-flash-lite` model. `OPENROUTER_CLASSIFIER_MODEL` only applies if Jev's alpha Decisions API is unavailable.

The `/nudge` request also accepts an optional `habits` string. The mobile app uses it for a user-provided routine note so the final recommendation can tailor a commute or reading suggestion without inventing personal context.

## Exa grounding

Set `EXA_API_KEY` in `apps/server/.env` (using the existing `.env.example` as the template). The service uses Exa only when Jev returns `ambiguous` or confidence below `GROUNDING_CONFIDENCE_MIN` (default `0.6`), then sends up to three title/highlight snippets to the nudge writer. Missing keys, errors, and 12-second timeouts are logged and safely skip grounding.

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

If Jev fails or times out, classification falls back to an OpenRouter chat model and logs the path. Exa failures simply omit grounding. If the OpenRouter writer fails, the API returns `shouldNotify: false`, empty text fields, and `error: true`; each failure is logged to the server console.
