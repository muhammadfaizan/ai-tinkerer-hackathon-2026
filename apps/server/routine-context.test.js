const assert = require('node:assert/strict');
const { activeRoutineContext, routineSummary } = require('./routine-context');

const routines = [
  { label: 'School drop-off', dayPattern: 'weekday', approxStartHour: 7, approxEndHour: 8 },
  { label: 'Commute home', dayPattern: 'weekday', approxStartHour: 17, approxEndHour: 18 },
];

assert.deepEqual(activeRoutineContext({ timeOfDay: '07:30' }, routines), [routines[0]]);
assert.deepEqual(activeRoutineContext({ timeOfDay: '21:30' }, routines), []);
assert.match(
  routineSummary(routines),
  /- weekday 07:00–08:00 — School drop-off\n- weekday 17:00–18:00 — Commute home/,
);
console.log('routine-context tests passed');
