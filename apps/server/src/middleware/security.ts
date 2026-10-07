import { Ratelimit } from '@upstash/ratelimit';
import { Redis } from '@upstash/redis';
import { createHash, timingSafeEqual } from 'node:crypto';
import type { NextFunction, Request, Response } from 'express';

const RATE_LIMIT_REQUESTS = 30;
const RATE_LIMIT_WINDOW = '1 m';

const hash = (value: string) => createHash('sha256').update(value).digest();
const appKeyMatches = (provided: string | undefined) => {
  const expected = process.env.APP_API_KEY;
  return Boolean(expected && provided && timingSafeEqual(hash(expected), hash(provided)));
};

const redisUrl = process.env.UPSTASH_REDIS_REST_URL;
const redisToken = process.env.UPSTASH_REDIS_REST_TOKEN;
const rateLimiter =
  redisUrl && redisToken
    ? new Ratelimit({
        redis: new Redis({ url: redisUrl, token: redisToken }),
        limiter: Ratelimit.slidingWindow(RATE_LIMIT_REQUESTS, RATE_LIMIT_WINDOW),
        prefix: 'intune-api',
        analytics: false,
      })
    : undefined;

const clientIp = (request: Request) =>
  request.header('x-forwarded-for')?.split(',')[0]?.trim() || request.ip || 'unknown';

export const serviceDisabled = (_request: Request, response: Response, next: NextFunction) => {
  if (process.env.SERVICE_DISABLED === 'true')
    return response.status(503).json({ error: 'Service unavailable.' });
  return next();
};

export const requireAppKey = (request: Request, response: Response, next: NextFunction) => {
  if (!appKeyMatches(request.header('x-app-key') || undefined))
    return response.status(401).json({ error: 'Unauthorized.' });
  return next();
};

export const rateLimit = async (request: Request, response: Response, next: NextFunction) => {
  if (!rateLimiter) {
    if (process.env.NODE_ENV === 'production')
      return response.status(503).json({ error: 'Service unavailable.' });
    return next();
  }

  const installId = request.header('x-install-id');
  const identifiers = [`ip:${clientIp(request)}`];
  if (installId) identifiers.push(`install:${installId}`);
  try {
    const results = await Promise.all(
      identifiers.map((identifier) => rateLimiter.limit(identifier)),
    );
    if (results.some((result) => !result.success))
      return response.status(429).json({ error: 'Too many requests. Please try again later.' });
    return next();
  } catch {
    return response.status(503).json({ error: 'Service unavailable.' });
  }
};
