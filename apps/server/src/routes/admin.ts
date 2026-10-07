import { Router } from 'express';
import { requireAdminKey, strictAdminRateLimit } from '../middleware/security';
import { setAdminEntitlement } from '../services/entitlements';
import { adminEntitlementRequestSchema } from './schemas';

const router = Router();

router.post('/entitlement', strictAdminRateLimit, requireAdminKey, async (req, res) => {
  const request = adminEntitlementRequestSchema.safeParse(req.body);
  if (!request.success) return res.status(400).json({ error: 'Invalid request.' });
  try {
    const { installId, tier, expiresAt } = request.data;
    await setAdminEntitlement(installId, tier, expiresAt);
    return res.json({ tier, ...(expiresAt ? { expiresAt } : {}) });
  } catch {
    return res.status(503).json({ error: 'Service unavailable.' });
  }
});

export default router;
