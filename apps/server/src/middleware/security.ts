import { Ratelimit } from '@upstash/ratelimit';
import { createHash, timingSafeEqual } from 'node:crypto';
import type { NextFunction, Request, Response } from 'express';
import { upstashRedis } from '../services/redis';

const RATE_LIMIT_REQUESTS = 30;
const ADMIN_RATE_LIMIT_REQUESTS = 10;
const RATE_LIMIT_WINDOW = '1 m';

const hash = (value: string) => createHash('sha256').update(value).digest();
const appKeyMatches = (provided: string | undefined) => {
  const expected = process.env.APP_API_KEY;
  return Boolean(expected && provided && timingSafeEqual(hash(expected), hash(provided)));
};

const limiterFor = (requests: number, prefix: string) =>
  upstashRedis && process.env.NODE_ENV !== 'test'
    ? new Ratelimit({
        redis: upstashRedis,
        limiter: Ratelimit.slidingWindow(requests, RATE_LIMIT_WINDOW),
        prefix,
        analytics: false,
      })
    : undefined;
const rateLimiter = limiterFor(RATE_LIMIT_REQUESTS, 'intune-api');
const adminRateLimiter = limiterFor(ADMIN_RATE_LIMIT_REQUESTS, 'intune-admin');

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

export const requireAdminKey = (request: Request, response: Response, next: NextFunction) => {
  const expected = process.env.ADMIN_API_KEY;
  if (!expected) return response.status(404).json({ error: 'Not found.' });
  const provided = request.header('authorization')?.match(/^Bearer (.+)$/)?.[1];
  if (!provided || !timingSafeEqual(hash(expected), hash(provided)))
    return response.status(401).json({ error: 'Unauthorized.' });
  return next();
};

const applyRateLimit = async (
  limiter: Ratelimit | undefined,
  identifiers: string[],
  response: Response,
  next: NextFunction,
) => {
  if (!limiter) {
    if (process.env.NODE_ENV === 'production')
      return response.status(503).json({ error: 'Service unavailable.' });
    return next();
  }
  try {
    const activeLimiter = limiter;
    const results = await Promise.all(
      identifiers.map((identifier) => activeLimiter.limit(identifier)),
    );
    if (results.some((result) => !result.success))
      return response.status(429).json({ error: 'Too many requests. Please try again later.' });
    return next();
  } catch {
    return response.status(503).json({ error: 'Service unavailable.' });
  }
};

export const rateLimit = (request: Request, response: Response, next: NextFunction) => {
  const installId = request.header('x-install-id');
  const identifiers = [`ip:${clientIp(request)}`];
  if (installId) identifiers.push(`install:${installId}`);
  return applyRateLimit(rateLimiter, identifiers, response, next);
};

export const strictAdminRateLimit = (request: Request, response: Response, next: NextFunction) =>
  !process.env.ADMIN_API_KEY
    ? next()
    : applyRateLimit(adminRateLimiter, [`ip:${clientIp(request)}`], response, next);
