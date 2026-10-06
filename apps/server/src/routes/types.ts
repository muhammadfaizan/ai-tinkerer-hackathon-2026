export type Activity = {
  app: string;
  durationMin?: number;
  timeOfDay?: string;
  category?: string;
};

export type Session = {
  apps: { app: string; durationMin: number }[];
  totalDurationMin: number;
  windowMin?: number;
};

export type Routine = {
  label: string;
  dayPattern: string;
  approxStartHour: number;
  approxEndHour: number;
};

export type NudgeBody = {
  goals?: unknown;
  activity?: Activity;
  session?: Session;
  habits?: unknown;
  routineContext?: unknown;
};
