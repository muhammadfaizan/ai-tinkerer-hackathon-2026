import 'dotenv/config';
import express from 'express';
import nudgeRouter from './routes/nudge';

const app = express();
app.use(express.json());
app.use(nudgeRouter);
app.get('/health', (_req, res) => res.json({ ok: true }));

export default app;
