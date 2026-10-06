import Exa from 'exa-js';
import type { Activity, Session } from '../routes/types';

const summary = (activity?: Activity, session?: Session) =>
  activity
    ? `${activity.app} for ${activity.durationMin ?? 'an unknown number of'} minutes at ${activity.timeOfDay ?? 'an unknown time'}`
    : `${session?.apps.map(({ app, durationMin }) => `${app} for ${durationMin} minutes`).join(', ')} in the last ${session?.windowMin ?? 30} minutes (total: ${session?.totalDurationMin} minutes)`;

export async function ground(goals: string[], activity?: Activity, session?: Session) {
  if (!process.env.EXA_API_KEY) return [];
  try {
    const exa = new Exa(process.env.EXA_API_KEY);
    let timeoutId: NodeJS.Timeout | undefined;
    try {
      const response = await Promise.race([
        exa.search(
          `Is ${summary(activity, session)} plausibly useful toward these goals: ${goals.join(', ')}?`,
          { type: 'auto', numResults: 3, contents: { highlights: true } },
        ),
        new Promise<never>((_, reject) => {
          timeoutId = setTimeout(
            () => reject(new Error('Exa request timed out after 12 seconds')),
            12_000,
          );
        }),
      ]);
      return (response.results || [])
        .slice(0, 3)
        .map((result: { title?: string | null; highlights?: string[] }) => ({
          title: result.title || 'Untitled source',
          snippet: Array.isArray(result.highlights) ? result.highlights.join(' ') : '',
        }))
        .filter(
          (result: { title: string; snippet: string }) =>
            result.snippet || result.title !== 'Untitled source',
        );
    } finally {
      clearTimeout(timeoutId);
    }
  } catch (error) {
    console.error('[ground] Exa failed:', error instanceof Error ? error.message : error);
    return [];
  }
}
