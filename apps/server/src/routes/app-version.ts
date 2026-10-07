import { Router } from 'express';
import { appVersionFromEnv } from '../services/app-version';

const router = Router();

router.get('/', (_req, res) => {
  const version = appVersionFromEnv();
  if (!version) return res.status(404).json({ error: 'Not found.' });
  return res.json(version);
});

export default router;
