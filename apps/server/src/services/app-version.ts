import { z } from 'zod';

const appVersionSchema = z
  .object({
    APP_LATEST_VERSION_CODE: z.coerce.number().int().positive(),
    APP_MIN_VERSION_CODE: z.coerce.number().int().positive(),
    APP_LATEST_VERSION_NAME: z.string().trim().min(1).max(80),
    APP_DOWNLOAD_URL: z
      .string()
      .url()
      .refine((url) => new URL(url).protocol === 'https:'),
    APP_RELEASE_NOTES: z.string().trim().max(2_000),
  })
  .refine((value) => value.APP_MIN_VERSION_CODE <= value.APP_LATEST_VERSION_CODE);

export type AppVersion = {
  latestVersionCode: number;
  minVersionCode: number;
  latestVersionName: string;
  downloadUrl: string;
  notes: string;
};

export const appVersionFromEnv = (env = process.env): AppVersion | undefined => {
  const parsed = appVersionSchema.safeParse(env);
  if (!parsed.success) return undefined;
  const value = parsed.data;
  return {
    latestVersionCode: value.APP_LATEST_VERSION_CODE,
    minVersionCode: value.APP_MIN_VERSION_CODE,
    latestVersionName: value.APP_LATEST_VERSION_NAME,
    downloadUrl: value.APP_DOWNLOAD_URL,
    notes: value.APP_RELEASE_NOTES,
  };
};
