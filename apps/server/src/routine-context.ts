import type { Activity, Routine } from './routes/types';

const timeHour = (value?: string) => {
  const match = value?.match(/^(\d{1,2}):\d{2}$/);
  const hour = match ? Number(match[1]) : Number.NaN;
  return Number.isInteger(hour) && hour >= 0 && hour < 24 ? hour : null;
};

export const activeRoutineContext = (
  activity: Activity | undefined,
  routineContext: unknown,
): Routine[] => {
  const routines = Array.isArray(routineContext)
    ? routineContext.filter(
        (routine): routine is Routine =>
          typeof routine?.label === 'string' &&
          typeof routine.dayPattern === 'string' &&
          Number.isInteger(routine.approxStartHour) &&
          Number.isInteger(routine.approxEndHour),
      )
    : [];
  const hour = timeHour(activity?.timeOfDay);
  return hour === null
    ? []
    : routines.filter(
        ({ approxStartHour, approxEndHour }) =>
          hour >= approxStartHour && hour < Math.max(approxEndHour, approxStartHour + 1),
      );
};

export const routineSummary = (routines: Routine[]) =>
  routines.length
    ? `Known routine context:\n${routines
        .map(
          ({ label, dayPattern, approxStartHour, approxEndHour }) =>
            `- ${dayPattern} ${String(approxStartHour).padStart(2, '0')}:00–${String(approxEndHour).padStart(2, '0')}:00 — ${label}`,
        )
        .join('\n')}`
    : '';
