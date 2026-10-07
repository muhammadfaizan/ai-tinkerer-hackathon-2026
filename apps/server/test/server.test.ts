process.env.APP_API_KEY = 'test-app-key';
process.env.NODE_ENV = 'test';

const { strict: assert } = require('node:assert');
const { once } = require('node:events');
const { expect } = require('chai');
const nock = require('nock');
const app = require('../src/app').default;
const { publicEntitlement } = require('../src/services/entitlements');

const openRouter = 'https://openrouter.ai';
let server;
let baseUrl;
let savedExaKey;
let savedServiceDisabled;
let savedNodeEnv;
let savedAdminKey;
const appVersionEnv = {
  APP_LATEST_VERSION_CODE: '2',
  APP_MIN_VERSION_CODE: '1',
  APP_LATEST_VERSION_NAME: '2.0.0',
  APP_DOWNLOAD_URL: 'https://downloads.example.com/in-tune.apk',
  APP_RELEASE_NOTES: 'A calmer check-in experience.',
};
const savedAppVersionEnv = {};

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

const appKeyHeaders = { 'X-App-Key': process.env.APP_API_KEY };
async function post(path, body, headers = appKeyHeaders) {
  const response = await fetch(`${baseUrl}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body),
  });
  return { status: response.status, body: await response.json() };
}

async function get(path, headers = appKeyHeaders) {
  const response = await fetch(`${baseUrl}${path}`, { headers });
  return { status: response.status, body: await response.json() };
}

before(async () => {
  savedExaKey = process.env.EXA_API_KEY;
  savedServiceDisabled = process.env.SERVICE_DISABLED;
  savedNodeEnv = process.env.NODE_ENV;
  savedAdminKey = process.env.ADMIN_API_KEY;
  for (const key of Object.keys(appVersionEnv)) {
    savedAppVersionEnv[key] = process.env[key];
    delete process.env[key];
  }
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
  if (savedServiceDisabled) process.env.SERVICE_DISABLED = savedServiceDisabled;
  else delete process.env.SERVICE_DISABLED;
  if (savedNodeEnv) process.env.NODE_ENV = savedNodeEnv;
  if (savedAdminKey) process.env.ADMIN_API_KEY = savedAdminKey;
  else delete process.env.ADMIN_API_KEY;
  for (const key of Object.keys(appVersionEnv)) {
    if (savedAppVersionEnv[key]) process.env[key] = savedAppVersionEnv[key];
    else delete process.env[key];
  }
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
    mockChat(
      { shouldNotify: true, message: 'Read a page.', microAction: 'Open your book.' },
      ({ messages }) => {
        expect(messages.at(-1).content).to.match(/^<untrusted-input>/);
        return true;
      },
    );

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
      const content = messages.at(-1).content;
      const input = JSON.parse(
        content.slice('<untrusted-input>'.length, -'</untrusted-input>'.length),
      );
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
    expect(result.body).to.deep.equal({ error: 'Invalid request.' });
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

  it('rejects malformed writer output before responding', async () => {
    mockJev('misaligned', 0.9);
    mockChat({ shouldNotify: false, message: '', microAction: '', extra: true });

    const result = await post('/nudge', {
      goals: ['read more books'],
      activity: { app: 'Instagram', durationMin: 30, timeOfDay: '21:00' },
    });

    expect(result.body).to.include({ shouldNotify: false, error: true });
  });
});

describe('POST /parse-goals', () => {
  it('returns a merged full goal list', async () => {
    mockChat({ goals: ['read more books', 'walk more', 'save more money'] }, ({ messages }) => {
      expect(messages.at(-1).content).to.include('<untrusted-data>');
      expect(messages.at(-1).content).to.include('read more books');
      expect(messages.at(-1).content).to.include('walk more');
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

  it('rejects malformed parser output before responding', async () => {
    mockChat({ goals: ['read more books'], extra: true });
    const result = await post('/parse-goals', {
      transcript: 'I want to read more books',
      existingGoals: [],
    });

    expect(result).to.deep.equal({
      status: 502,
      body: { error: 'Could not parse spoken goals. Please try again or type them manually.' },
    });
  });
});

describe('entitlements', () => {
  const installId = '0f8fad5b-d9cb-469f-a165-70867728950e';
  const fourGoals = ['one', 'two', 'three', 'four'];
  const installHeaders = { ...appKeyHeaders, 'X-Install-Id': installId };

  it('returns the default free entitlement and requires a UUID install id', async () => {
    const entitlement = await get('/entitlement', installHeaders);
    const invalid = await get('/entitlement', { ...appKeyHeaders, 'X-Install-Id': 'not-a-uuid' });

    expect(entitlement).to.deep.equal({ status: 200, body: { tier: 'free', maxGoals: 3 } });
    expect(invalid).to.deep.equal({ status: 400, body: { error: 'Invalid request.' } });
  });

  it('rejects more than the free goal limit before calling providers', async () => {
    const nudge = await post(
      '/nudge',
      { goals: fourGoals, activity: { app: 'Instagram' } },
      installHeaders,
    );
    const parser = await post(
      '/parse-goals',
      { transcript: 'another goal', existingGoals: fourGoals },
      installHeaders,
    );

    expect(nudge).to.deep.equal({ status: 403, body: { error: 'goal_limit' } });
    expect(parser).to.deep.equal({ status: 403, body: { error: 'goal_limit' } });
  });

  it('maps pro and expired stored entitlements to their effective public limits', () => {
    expect(publicEntitlement({ tier: 'pro', source: 'admin' })).to.deep.equal({
      tier: 'pro',
      maxGoals: 10,
    });
    expect(
      publicEntitlement({ tier: 'pro', source: 'admin', expiresAt: '2020-01-01T00:00:00.000Z' }),
    ).to.deep.equal({ tier: 'free', maxGoals: 3 });
  });

  it('keeps admin authorization separate from the app key and hides an unconfigured route', async () => {
    delete process.env.ADMIN_API_KEY;
    const disabled = await post('/admin/entitlement', { installId, tier: 'pro' }, {});
    process.env.ADMIN_API_KEY = 'test-admin-key';
    const wrong = await post(
      '/admin/entitlement',
      { installId, tier: 'pro' },
      {
        Authorization: 'Bearer wrong-key',
        'X-App-Key': process.env.APP_API_KEY,
      },
    );
    delete process.env.ADMIN_API_KEY;

    expect(disabled).to.deep.equal({ status: 404, body: { error: 'Not found.' } });
    expect(wrong).to.deep.equal({ status: 401, body: { error: 'Unauthorized.' } });
  });
});

describe('GET /app-version', () => {
  it('requires an app key and is hidden until its complete HTTPS config exists', async () => {
    const unavailable = await get('/app-version');
    const unauthorized = await get('/app-version', {});
    Object.assign(process.env, appVersionEnv);
    const available = await get('/app-version');
    delete process.env.APP_DOWNLOAD_URL;
    const invalid = await get('/app-version');
    Object.assign(process.env, appVersionEnv);

    expect(unavailable).to.deep.equal({ status: 404, body: { error: 'Not found.' } });
    expect(unauthorized).to.deep.equal({ status: 401, body: { error: 'Unauthorized.' } });
    expect(available).to.deep.equal({
      status: 200,
      body: {
        latestVersionCode: 2,
        minVersionCode: 1,
        latestVersionName: '2.0.0',
        downloadUrl: 'https://downloads.example.com/in-tune.apk',
        notes: 'A calmer check-in experience.',
      },
    });
    expect(invalid).to.deep.equal({ status: 404, body: { error: 'Not found.' } });
  });
});

describe('security middleware', () => {
  it('allows health without an app key and rejects missing or wrong keys elsewhere', async () => {
    const health = await fetch(`${baseUrl}/health`);
    const missing = await post(
      '/nudge',
      { goals: ['read more books'], activity: { app: 'Instagram' } },
      {},
    );
    const wrong = await post(
      '/nudge',
      { goals: ['read more books'], activity: { app: 'Instagram' } },
      { 'X-App-Key': 'wrong-key' },
    );

    expect(health.status).to.equal(200);
    expect(await health.json()).to.deep.equal({ ok: true });
    expect(missing).to.deep.equal({ status: 401, body: { error: 'Unauthorized.' } });
    expect(wrong).to.deep.equal({ status: 401, body: { error: 'Unauthorized.' } });
  });

  it('returns generic failures for oversized JSON and an enabled kill switch', async () => {
    const oversized = await post('/parse-goals', {
      transcript: 'x'.repeat(21_000),
      existingGoals: [],
    });
    process.env.SERVICE_DISABLED = 'true';
    const disabled = await post('/nudge', {
      goals: ['read more books'],
      activity: { app: 'Instagram' },
    });
    delete process.env.SERVICE_DISABLED;

    expect(oversized).to.deep.equal({ status: 400, body: { error: 'Invalid request.' } });
    expect(disabled).to.deep.equal({ status: 503, body: { error: 'Service unavailable.' } });
  });

  it('fails closed in production when Upstash is not configured', async () => {
    process.env.NODE_ENV = 'production';
    const result = await post('/nudge', {
      goals: ['read more books'],
      activity: { app: 'Instagram' },
    });
    process.env.NODE_ENV = 'test';

    expect(result).to.deep.equal({ status: 503, body: { error: 'Service unavailable.' } });
  });
});
