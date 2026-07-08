import { defineConfig, devices } from '@playwright/test';

/**
 * E2E tests require the full stack running:
 *   docker-compose up -d postgres vault
 *   cd backend-core && ./gradlew bootRun &
 *   cd ai-service && uvicorn app.main:app --port 8000 &
 *   cd frontend && npm run dev &
 *
 * Or: docker-compose --profile e2e up -d
 * Then: npx playwright test
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 45_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,     // share a single test DB; run serially
  retries: process.env['CI'] ? 2 : 0,
  reporter: [['html', { outputFolder: 'playwright-report', open: 'never' }], ['line']],

  use: {
    baseURL: process.env['BASE_URL'] ?? 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
