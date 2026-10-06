export type JsonSchema = Record<string, unknown>;

export type ChatJsonRequest = {
  model: string;
  messages: { role: string; content: string }[];
  schema: JsonSchema;
  name: string;
  maxTokens: number;
};

export type Classification = 'aligned' | 'misaligned' | 'ambiguous';

export type StageOne = {
  classification: Classification;
  confidence?: number;
  probabilities?: Record<string, number>;
  path: string;
};

export type Decision = {
  shouldNotify: boolean;
  message: string;
  microAction: string;
};
