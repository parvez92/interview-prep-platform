import { http, HttpResponse } from 'msw';

export const ME_RESPONSE = {
  user: { id: 1, email: 'test@prep.test', displayName: 'Test User', onboarded: true },
  onboarded: true,
};

export const PROGRESS_RESPONSE = {
  overallPct: 42,
  streak: 3,
  flaggedCount: 1,
  tracks: [
    { name: 'DSA',        readiness: 60 },
    { name: 'Behavioral', readiness: 30 },
  ],
};

export const TODAY_RESPONSE = {
  items: [
    { kind: 'new',     topicSlug: 'arrays', label: 'Arrays & Two-Pointer', note: 'week 1 of your plan', estMinutes: 60 },
    { kind: 'flagged', topicSlug: 'dp',     label: 'Dynamic Programming',  note: 'Flagged 2 days ago',  estMinutes: 45 },
  ],
  budgetMin: 100,
  plannedMin: 105,
  velocity: 'on pace',
};

export const INTERVIEWS_RESPONSE = [
  {
    id: 1, company: 'Google', role: 'L5 SWE', stage: 'onsite', round: 'Technical',
    scheduledAt: '2026-07-01T10:00:00Z', outcome: null, weak: ['arrays'],
  },
  {
    id: 2, company: 'Meta', role: 'E5', stage: 'applied', round: null,
    scheduledAt: null, outcome: null, weak: [],
  },
];

export const PHASES_RESPONSE = [
  {
    id: 1, code: 'DSA', name: 'Data Structures', icon: '🧩', blurb: 'Core DS',
    displayOrder: 1, progress: { done: 1, total: 3 },
    weeks: [
      {
        id: 1, code: 'W1', title: 'Week 1 — Arrays',
        topics: [
          { id: 1, slug: 'arrays', title: 'Arrays & Two-Pointer', tag: 'dsa', status: 'done', confidence: 80 },
          { id: 2, slug: 'hashing', title: 'Hash Maps', tag: 'dsa', status: 'todo', confidence: 50 },
        ],
      },
    ],
  },
];

export const handlers = [
  http.post('/api/auth/login', async ({ request }) => {
    const body = await request.json() as { email: string; password: string };
    if (body.email === 'test@prep.test' && body.password === 'password') {
      return HttpResponse.json({
        accessToken: 'mock-access-token',
        refreshToken: 'mock-refresh-token',
        expiresIn: 900,
      });
    }
    return HttpResponse.json({ error: 'Unauthorized' }, { status: 401 });
  }),

  http.post('/api/auth/refresh', () =>
    HttpResponse.json({ accessToken: 'mock-access-token-refreshed', refreshToken: 'mock-refresh-token', expiresIn: 900 })
  ),

  http.get('/api/me', () => HttpResponse.json(ME_RESPONSE)),

  http.get('/api/progress', () => HttpResponse.json(PROGRESS_RESPONSE)),

  http.get('/api/review/today', () => HttpResponse.json(TODAY_RESPONSE)),

  http.get('/api/usage', () =>
    HttpResponse.json({ monthUsd: 0.12, budgetUsd: 5.0, remainingUsd: 4.88, warning: false })
  ),

  http.get('/api/interviews', () => HttpResponse.json(INTERVIEWS_RESPONSE)),

  http.post('/api/interviews', async ({ request }) => {
    const body = await request.json() as Record<string, unknown>;
    return HttpResponse.json(
      { id: 99, company: body['company'], role: body['role'], stage: body['stage'], round: body['round'] ?? null, scheduledAt: null, outcome: null, weak: [] },
      { status: 201 }
    );
  }),

  http.patch('/api/interviews/:id', async ({ request }) => {
    const body = await request.json() as Record<string, unknown>;
    return HttpResponse.json({ id: 1, company: 'Google', role: 'L5 SWE', stage: body['stage'] ?? 'onsite', round: null, scheduledAt: null, outcome: null, weak: [] });
  }),

  http.get('/api/phases', () => HttpResponse.json(PHASES_RESPONSE)),

  http.get('/api/topics/:slug', ({ params }) =>
    HttpResponse.json({
      id: 1, slug: params['slug'], title: 'Arrays & Two-Pointer', tag: 'dsa',
      status: 'todo', confidence: 70,
      deepDive: { concept: 'Contiguous memory.', points: ['O(1) access'], angle: 'vs linked list' },
      note: { id: null, content: '' },
      resources: [], exercises: [], questions: [],
    })
  ),

  http.patch('/api/topics/:slug', async ({ params, request }) => {
    const body = await request.json() as Record<string, unknown>;
    return HttpResponse.json({ id: 1, slug: params['slug'], status: body['status'] ?? 'todo', confidence: 70 });
  }),
];
