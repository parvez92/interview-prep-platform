/* ── Shared primitives ──────────────────────────────────────────────────── */
export type Tag      = 'new' | 'refresh' | 'dsa' | 'exp';
export type Source   = 'resume' | 'standard' | 'interest' | 'custom';
export type Priority = 'high' | 'medium' | 'low';

/* ── Study plan ─────────────────────────────────────────────────────────── */
export interface Phase {
  code:     string;
  name:     string;
  icon:     string;
  blurb:    string;
  progress: { done: number; total: number };
  weeks:    Week[];
}

export interface Week {
  code:   string;
  title:  string;
  /** narrative pass: one sentence bridging from the previous week */
  bridge?: string | null;
  /** narrative pass: tie-in to the candidate's real experience */
  anchor?: string | null;
  topics: TopicLite[];
}

export interface TopicLite {
  slug:       string;
  code:       string;
  title:      string;
  tag:        Tag;
  source:     Source;
  priority:   Priority;
  status:     'todo' | 'done';
  confidence: number | null;
  /** snake_case: must match the AI plan JSON key that commitPlan maps to topic.angle */
  resources_hint?: string;
  /** plan-assigned category (dsa, system_design, …) — persisted by commitPlan */
  category?: string;
  /** v2 coarse plan: what this unit covers — commitPlan maps it to topic.angle */
  scope?: string;
  /** v2 coarse plan: none|maybe|likely — depth pass may split this unit */
  split_hint?: string;
}

export interface TopicDetail extends TopicLite {
  deepDive:  { concept: string; points: string[]; angle: string };
  note:      Note;
  resources: Resource[];
  exercises: Exercise[];
  questions: Question[];
}

export interface Note {
  contentMd:   string;
  updatedAt:   string;
  attachments: { id: number; url: string; originalName: string; mimeType: string }[];
}

export interface Resource { id: number; label: string; url: string; }
export interface Exercise  { id: number; title: string; repoUrl: string; done: boolean; }
export interface Question  { id: number; text: string; }

/* ── Interviews ─────────────────────────────────────────────────────────── */
export type InterviewStage   = 'applied' | 'screening' | 'onsite' | 'offer' | 'rejected';
export type InterviewOutcome = 'pending' | 'debrief' | 'passed' | 'offer' | 'rejected';

export interface Interview {
  id:          number;
  company:     string;
  role:        string;
  stage:       InterviewStage;
  round:       string;
  scheduledAt: string;
  outcome:     InterviewOutcome;
  jdText?:     string;
  weak:        string[];
}

export interface InterviewQuestion {
  id:          number;
  text:        string;
  topicSlug?:  string;
  selfRating:  number;
}

export interface PrepPack {
  interviewId:  number;
  summary:      string;
  topics:       { topicSlug: string; why: string }[];
  generatedAt:  string;
}

/* ── STAR ────────────────────────────────────────────────────────────────── */
export interface StarStory {
  id:             number;
  title:          string;
  situation:      string;
  task:           string;
  action:         string;
  result:         string;
  tags:           string[];
  mappedPrompts:  string[];
}

/* ── Mock interviewer ────────────────────────────────────────────────────── */
export interface MockSession {
  id:          number;
  type:        'technical' | 'system-design' | 'behavioral';
  topicScope:  string[];
  transcript:  { role: 'ai' | 'user'; text: string }[];
  score:       number;
  feedback:    string;
  createdAt:   string;
}

/* ── Jobs ────────────────────────────────────────────────────────────────── */
export interface Job {
  id:       number;
  company:  string;
  role:     string;
  location: string;
  comp?:    string;
  via:      string;
  date:     string;
  fit:      number;
  reason:   string;
  tags:     string[];
  status:   'new' | 'interested' | 'dismissed';
}

/* ── Progress / dashboard ────────────────────────────────────────────────── */
export interface Progress {
  overallPct:   number;
  streak:       number;
  tracks:       { name: string; readiness: number }[];
  flaggedCount: number;
}

export interface TodayItem {
  kind:       'new' | 'flagged' | 'spaced' | 'drill';
  topicSlug:  string;
  label:      string;
  note:       string;
  estMinutes: number;
}

/** computed today queue — flags first, then current week, then spaced repetition */
export interface TodayQueue {
  items:      TodayItem[];
  budgetMin:  number;
  plannedMin: number;
  velocity:   string;
}

export interface Usage {
  monthUsd:     number;
  budgetUsd:    number;
  remainingUsd: number;
  warning:      boolean;
}

/* ── Onboarding ──────────────────────────────────────────────────────────── */
export interface ResumeProfile {
  skills:      { name: string; level: string; years: number }[];
  domains:     string[];
  seniority:   string;
  totalYears:  number;
  experiences: { company: string; role: string; dates: string; highlights: string[] }[];
  gaps:        string[];
  confirmed:   boolean;
}

export interface OnboardingTargets {
  targetRole:        string;
  targetLevel:       string;
  weeks:             number;
  hoursPerWeek:      number;
  interests:         string[];
  monthlyBudgetUsd:  number;
  llmProvider:       string;
  ollamaUrl:         string;
  ollamaModel:       string;
  additionalContext: string;
}

/* ── Auth / me ───────────────────────────────────────────────────────────── */
export interface UserSettings {
  llmProvider:      string;
  modelStrong:      string | null;
  modelCheap:       string | null;
  ollamaUrl:        string | null;
  monthlyBudgetUsd: number;
}

export interface MeResponse {
  user:      { email: string; displayName: string };
  settings:  UserSettings;
  onboarded: boolean;
}

export interface AuthTokens {
  accessToken:  string;
  refreshToken: string;
  expiresIn:    number;
}
