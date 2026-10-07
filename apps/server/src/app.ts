import 'dotenv/config';
import express from 'express';
import helmet from 'helmet';
import { rateLimit, requireAppKey, serviceDisabled } from './middleware/security';
import nudgeRouter from './routes/nudge';
import { healthRequestSchema } from './routes/schemas';

const app = express();
app.set('trust proxy', 1);
app.use(serviceDisabled);
app.use(helmet());
app.get('/health', (req, res) => {
  if (!healthRequestSchema.safeParse(req.query).success)
    return res.status(400).json({ error: 'Invalid request.' });
  return res.json({ ok: true });
});
app.use(requireAppKey);
app.use(rateLimit);
app.use(express.json({ limit: '20kb' }));
app.use(nudgeRouter);
app.use(
  (_error: unknown, _req: express.Request, res: express.Response, _next: express.NextFunction) =>
    res.status(400).json({ error: 'Invalid request.' }),
);

export default app;
