import { Router } from 'express';
import { getEntitlement, isInstallId } from '../services/entitlements';

const router = Router();

router.get('/', async (req, res) => {
  const installId = req.header('x-install-id');
  if (!isInstallId(installId)) return res.status(400).json({ error: 'Invalid request.' });
  return res.json(await getEntitlement(installId));
});

export default router;
