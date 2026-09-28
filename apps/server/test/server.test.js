const { strict: assert } = require('node:assert');
const { once } = require('node:events');
const { expect } = require('chai');
const nock = require('nock');
const { app } = require('../server');

const openRouter = 'https://openrouter.ai';
let server;
let baseUrl;
let savedExaKey;

const completion = (value) => ({ choices: [{ message: { content: JSON.stringify(value) } }] });
const jevAnswer = (choice, confidence = 0.9) => ({
  answers: { alignment: { choice, confidence, probabilities: { [choice]: confidence } } },
});
const mockJev = (choice, confidence, check = () => true) =>
  nock(openRouter)
    .post('/api/alpha/decisions', (body) => {
      expect(body.model).to.equal('typesafe/jev-1.13');
      expect(body.questions.alignment.type).to.equal('choice');
      return check(body);
    })
    .reply(200, jevAnswer(choice, confidence));
const mockChat = (value, check = () => true) =>
  nock(openRouter)
    .post('/api/v1/chat/completions', (body) => check(body))
    .reply(200, completion(value));

async function post(path, body) {
  const response = await fetch(`${baseUrl}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return { status: response.status, body: await response.json() };
}

before(async () => {
  savedExaKey = process.env.EXA_API_KEY;
  delete process.env.EXA_API_KEY;
  nock.disableNetConnect();
  nock.enableNetConnect('127.0.0.1');
  server = app.listen(0, '127.0.0.1');
  await once(server, 'listening');
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

beforeEach(() => nock.cleanAll());
afterEach(() => assert.equal(nock.isDone(), true, `Pending mocks: ${nock.pendingMocks()}`));
after(async () => {
  nock.cleanAll();
  nock.enableNetConnect();
  if (savedExaKey) process.env.EXA_API_KEY = savedExaKey;
  await new Promise((resolve) => server.close(resolve));
});

describe('POST /nudge', () => {
  it('uses Jev state and returns its normalized confidence and probabilities', async () => {
    mockJev('misaligned', 0.92, ({ state }) => {
      expect(state.goals).to.deep.equal(['read more books']);
      expect(state.activity).to.deep.equal({
        app: 'Instagram',
        durationMin: 22,
        timeOfDay: '21:30',
        category: 'social',
      });
      expect(state.session).to.equal(null);
      return true;
    });
    mockChat({ shouldNotify: true, message: 'Read a page.', microAction: 'Open your book.' });

    const result = await post('/nudge', {
      goals: ['read more books'],
      activity: { app: 'Instagram', durationMin: 22, timeOfDay: '21:30', category: 'social' },
    });

    expect(result.status).to.equal(200);
    expect(result.body).to.include({ classification: 'misaligned', confidence: 0.92 });
    expect(result.body.probabilities).to.deep.equal({ misaligned: 0.92 });
    expect(result.body.shouldNotify).to.equal(true);
  });

  it('grounds ambiguous or low-confidence Jev results before writing', async () => {
    mockJev('aligned', 0.4);
    mockChat({ shouldNotify: false, message: '', microAction: '' }, ({ messages }) => {
      const input = JSON.parse(messages.at(-1).content);
      expect(input.classification).to.equal('aligned');
      expect(input.grounding).to.deep.equal([]);
      return true;
    });

    const result = await post('/nudge', {
      goals: ['walk more'],
      activity: { app: 'Maps', durationMin: 10, timeOfDay: '12:00' },
    });

    expect(result.body).to.include({ classification: 'aligned', groundingUsed: false });
  });

  it('falls back to chat classification when Jev fails', async () => {
    nock(openRouter).post('/api/alpha/decisions').reply(500, { error: 'alpha unavailable' });
    mockChat({ classification: 'ambiguous' }, ({ response_format }) => {
      expect(response_format.json_schema.name).to.equal('activity_classification');
      return true;
    });
    mockChat({ shouldNotify: false, message: '', microAction: '' });

    const result = await post('/nudge', {
      goals: ['read more books'],
      activity: { app: 'Instagram', durationMin: 15, timeOfDay: '14:00' },
    });

    expect(result.status).to.equal(200);
    expect(result.body).to.include({ classification: 'ambiguous', shouldNotify: false });
    expect(result.body).to.not.have.property('confidence');
  });

  it('sends a session and only the active routine context to Jev', async () => {
    const routine = {
      label: 'School drop-off',
      dayPattern: 'weekday',
      approxStartHour: 7,
      approxEndHour: 8,
    };
    mockJev('ambiguous', 0.8, ({ state }) => {
      expect(state.session).to.deep.equal({
        apps: [
          { app: 'Instagram', durationMin: 12 },
          { app: 'YouTube', durationMin: 18 },
        ],
        totalDurationMin: 30,
        windowMin: 30,
      });
      expect(state.routineContext).to.deep.equal([]);
      return true;
    });
    mockChat({ shouldNotify: false, message: '', microAction: '' });

    const result = await post('/nudge', {
      goals: ['read more books'],
      session: {
        apps: [
          { app: 'Instagram', durationMin: 12 },
          { app: 'YouTube', durationMin: 18 },
        ],
        totalDurationMin: 30,
      },
      routineContext: [routine],
    });

    expect(result.status).to.equal(200);
  });

  it('rejects malformed requests without calling providers', async () => {
    const result = await post('/nudge', { goals: [], activity: { app: 'Instagram' } });
    expect(result.status).to.equal(400);
    expect(result.body.error).to.match(/non-empty goals/);
  });

  it('keeps the safe no-notification fallback when the writer fails twice', async () => {
    mockJev('misaligned', 0.9);
    nock(openRouter).post('/api/v1/chat/completions').reply(500, { error: 'writer failed' });
    nock(openRouter).post('/api/v1/chat/completions').reply(500, { error: 'writer failed' });

    const result = await post('/nudge', {
      goals: ['read more books'],
      activity: { app: 'Instagram', durationMin: 30, timeOfDay: '21:00' },
    });

    expect(result.body).to.include({
      shouldNotify: false,
      message: '',
      microAction: '',
      error: true,
    });
  });
});

describe('POST /parse-goals', () => {
  it('returns a merged full goal list', async () => {
    mockChat({ goals: ['read more books', 'walk more', 'save more money'] }, ({ messages }) => {
      expect(messages.at(-1).content).to.include('1. read more books');
      expect(messages.at(-1).content).to.include('2. walk more');
      return true;
    });

    const result = await post('/parse-goals', {
      transcript: 'I also want to save more money',
      existingGoals: ['read more books', 'walk more'],
    });

    expect(result).to.deep.equal({
      status: 200,
      body: { goals: ['read more books', 'walk more', 'save more money'] },
    });
  });

  it('returns a replacement goal list and rejects invalid parser input', async () => {
    mockChat({ goals: ['save more money'] });
    const replacement = await post('/parse-goals', {
      transcript: 'Actually forget books, focus on saving money instead',
      existingGoals: ['read more books'],
    });
    const invalid = await post('/parse-goals', { transcript: '', existingGoals: [] });

    expect(replacement.body).to.deep.equal({ goals: ['save more money'] });
    expect(invalid.status).to.equal(400);
  });
});
