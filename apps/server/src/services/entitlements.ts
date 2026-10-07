import { z } from 'zod';
import { upstashRedis } from './redis';

export const FREE_MAX_GOALS = 3;
export const PRO_MAX_GOALS = 10;

const storedEntitlementSchema = z
  .object({
    tier: z.enum(['free', 'pro']),
    source: z.enum(['admin', 'play']),
    expiresAt: z.string().datetime({ offset: true }).optional(),
  })
  .strict();

export type Entitlement = z.infer<typeof storedEntitlementSchema>;
export type PublicEntitlement = Pick<Entitlement, 'tier' | 'expiresAt'> & { maxGoals: number };

const freeEntitlement: PublicEntitlement = { tier: 'free', maxGoals: FREE_MAX_GOALS };
const keyFor = (installId: string) => `entitlement:${installId}`;

export const isInstallId = (value: string | undefined) =>
  z.string().uuid().safeParse(value).success;
export const maxGoalsForTier = (tier: Entitlement['tier']) =>
  tier === 'pro' ? PRO_MAX_GOALS : FREE_MAX_GOALS;

export const publicEntitlement = (entitlement?: Entitlement): PublicEntitlement => {
  if (!entitlement || (entitlement.expiresAt && Date.parse(entitlement.expiresAt) <= Date.now()))
    return freeEntitlement;
  return {
    tier: entitlement.tier,
    maxGoals: maxGoalsForTier(entitlement.tier),
    ...(entitlement.expiresAt ? { expiresAt: entitlement.expiresAt } : {}),
  };
};

export async function getEntitlement(installId: string | undefined): Promise<PublicEntitlement> {
  if (!installId || !isInstallId(installId) || !upstashRedis) return freeEntitlement;
  try {
    const raw = await upstashRedis.get<string>(keyFor(installId));
    if (!raw) return freeEntitlement;
    const entitlement = storedEntitlementSchema.parse(JSON.parse(raw));
    return publicEntitlement(entitlement);
  } catch {
    return freeEntitlement;
  }
}

export async function setAdminEntitlement(
  installId: string,
  tier: Entitlement['tier'],
  expiresAt?: string,
) {
  if (!upstashRedis) throw new Error('Entitlement store unavailable');
  await upstashRedis.set(
    keyFor(installId),
    JSON.stringify({ tier, source: 'admin', ...(expiresAt ? { expiresAt } : {}) }),
  );
}
