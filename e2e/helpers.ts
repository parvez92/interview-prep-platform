import { type Page, expect } from '@playwright/test';

export const TEST_USER = {
  email:    process.env['E2E_EMAIL']    ?? 'e2e@preploop.test',
  password: process.env['E2E_PASSWORD'] ?? 'E2ETestPass123!',
  name:     'E2E Tester',
};

/** Log in via the UI and wait for the home page. */
export async function login(page: Page) {
  await page.goto('/login');
  await page.getByPlaceholder('you@example.com').fill(TEST_USER.email);
  await page.locator('input[type="password"]').fill(TEST_USER.password);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL('/', { timeout: 10_000 });
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
}

/** Create a minimal study plan (phase → week → topic) via API. */
export async function seedStudyPlan(page: Page, token: string) {
  const base = process.env['API_URL'] ?? 'http://localhost:8080';
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };

  // This helper is for direct API seeding outside the browser — use request context
  const ctx = await page.context().request;
  const phaseRes = await ctx.post(`${base}/api/phases`, {
    headers,
    data: { code: 'DSA-E2E', name: 'E2E DSA', icon: '🧪', blurb: '', displayOrder: 99 },
  });
  const phase = await phaseRes.json() as { id: number };

  const weekRes = await ctx.post(`${base}/api/phases/${phase.id}/weeks`, {
    headers,
    data: { code: 'W1-E2E', title: 'E2E Week 1', displayOrder: 1 },
  });
  const week = await weekRes.json() as { id: number };

  const topicRes = await ctx.post(`${base}/api/phases/${phase.id}/weeks/${week.id}/topics`, {
    headers,
    data: { code: 'E2E-ARRAYS', slug: 'e2e-arrays', title: 'E2E Arrays', tag: 'dsa', source: 'standard', displayOrder: 1 },
  });
  return { phase, week, topic: await topicRes.json() as { id: number; slug: string } };
}

/** Get the access token stored in-memory — exposed via window.__accessToken in dev mode. */
export async function getToken(page: Page): Promise<string> {
  return page.evaluate(() => (window as unknown as { __accessToken?: string }).__accessToken ?? '');
}
