const baseUrl = process.env.NUDGE_ENGINE_URL || 'http://localhost:3000';
const scenarios = [
  { name: 'Instagram vs reading', body: { goals: ['read more books'], activity: { app: 'Instagram', durationMin: 22, timeOfDay: '21:30' } } },
  { name: 'Instagram content research', body: { goals: ['grow my content channel'], activity: { app: 'Instagram', durationMin: 15, timeOfDay: '14:00' } } },
  { name: 'Cab vs active commute', body: { goals: ['lose weight'], activity: { app: 'Ordering a cab to the office', durationMin: 0, timeOfDay: '08:15' } } }
];
async function run() {
  for (const scenario of scenarios) {
    try {
      const response = await fetch(`${baseUrl}/nudge`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(scenario.body) });
      console.log(`\n${scenario.name} (${response.status})`);
      console.log(JSON.stringify(await response.json(), null, 2));
    } catch (error) { console.error(`\n${scenario.name} failed:`, error.message); }
  }
}
run();
