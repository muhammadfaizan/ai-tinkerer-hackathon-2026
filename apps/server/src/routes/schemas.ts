import { z } from 'zod';

export const MAX_GOALS = 3;
export const healthRequestSchema = z.object({}).strict();
const shortText = (maxLength: number) => z.string().trim().min(1).max(maxLength);

const activitySchema = z
  .object({
    app: shortText(120),
    durationMin: z.number().finite().int().min(0).max(1_440).optional(),
    timeOfDay: z.string().trim().max(16).optional(),
    category: z.string().trim().max(60).optional(),
  })
  .strict();

const sessionSchema = z
  .object({
    apps: z
      .array(
        z
          .object({ app: shortText(120), durationMin: z.number().finite().int().min(0).max(1_440) })
          .strict(),
      )
      .min(1)
      .max(10),
    totalDurationMin: z.number().finite().int().min(0).max(1_440),
    windowMin: z.number().finite().int().min(1).max(1_440).optional(),
  })
  .strict();

const routineSchema = z
  .object({
    label: shortText(80),
    dayPattern: shortText(32),
    approxStartHour: z.number().int().min(0).max(23),
    approxEndHour: z.number().int().min(0).max(23),
  })
  .strict();

export const nudgeRequestSchema = z
  .object({
    goals: z.array(shortText(120)).min(1).max(MAX_GOALS),
    activity: activitySchema.optional(),
    session: sessionSchema.optional(),
    habits: z.string().trim().max(1_000).optional().default(''),
    routineContext: z.array(routineSchema).max(10).optional().default([]),
  })
  .strict()
  .refine(({ activity, session }) => Boolean(activity || session));

export const parseGoalsRequestSchema = z
  .object({
    transcript: shortText(1_000),
    existingGoals: z.array(shortText(120)).max(MAX_GOALS).optional().default([]),
  })
  .strict();
