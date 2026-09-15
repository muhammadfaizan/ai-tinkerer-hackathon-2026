const timeHour = (value) => {
  const match = typeof value === 'string' && value.match(/^(\d{1,2}):\d{2}$/);
  const hour = match && Number(match[1]);
  return Number.isInteger(hour) && hour >= 0 && hour < 24 ? hour : null;
};

const activeRoutineContext = (activity, routineContext) => {
  const routines = Array.isArray(routineContext)
    ? routineContext.filter(
        (routine) =>
          typeof routine?.label === 'string' &&
          Number.isInteger(routine.approxStartHour) &&
          Number.isInteger(routine.approxEndHour),
      )
    : [];
  const hour = timeHour(activity?.timeOfDay);
  return hour === null
    ? routines
    : routines.filter(
        ({ approxStartHour, approxEndHour }) =>
          hour >= approxStartHour && hour < Math.max(approxEndHour, approxStartHour + 1),
      );
};

const routineSummary = (routines) =>
  routines.length
    ? `Known routine context:\n${routines.map(({ label, dayPattern, approxStartHour, approxEndHour }) => `- ${dayPattern} ${String(approxStartHour).padStart(2, '0')}:00–${String(approxEndHour).padStart(2, '0')}:00 — ${label}`).join('\n')}`
    : '';

module.exports = { activeRoutineContext, routineSummary };
