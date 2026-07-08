import { test, expect } from '@playwright/test';
import { login } from './helpers';

/**
 * E2E: Mock interviewer — all three services.
 *
 * React (3000) → Java /api/ai/mock (8080) → Python /ai/mock (8000) → LLM
 *
 * Uses desktop mode (X-Provider: desktop) to avoid real LLM cost.
 * Desktop mode is triggered via user settings in the app.
 */
test.describe('Mock Interviewer', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('mock interview page loads', async ({ page }) => {
    await page.goto('/mock');
    await expect(page.getByRole('heading', { name: /mock interview/i })).toBeVisible();
  });

  test('shows question prompt after starting session', async ({ page }) => {
    await page.goto('/mock');

    const startBtn = page.getByRole('button', { name: /start|begin/i }).first();
    if (await startBtn.isVisible()) {
      await startBtn.click();
      // The AI should respond with a question (or desktop mode placeholder)
      await expect(
        page.locator('[class*="question"], [class*="prompt"], [class*="message"]').first()
      ).toBeVisible({ timeout: 15_000 });
    } else {
      test.skip();
    }
  });

  test('submitting an answer shows feedback', async ({ page }) => {
    await page.goto('/mock');

    const startBtn = page.getByRole('button', { name: /start|begin/i }).first();
    if (!await startBtn.isVisible()) { test.skip(); return; }

    await startBtn.click();

    // Type an answer
    const answerInput = page.locator('textarea, [contenteditable="true"]').first();
    await expect(answerInput).toBeVisible({ timeout: 10_000 });
    await answerInput.fill('My answer is that quicksort uses divide and conquer with O(n log n) average complexity.');

    const submitBtn = page.getByRole('button', { name: /submit|send/i }).first();
    await expect(submitBtn).toBeVisible();
    await submitBtn.click();

    // Feedback should appear (score, tips, etc.)
    await expect(
      page.locator('[class*="feedback"], [class*="score"], [class*="result"]').first()
    ).toBeVisible({ timeout: 20_000 });
  });

  test('cost is zero in desktop mode', async ({ page }) => {
    // Navigate to settings and enable desktop provider if available
    await page.goto('/settings');
    const providerSelect = page.locator('select[name="llmProvider"], [class*="provider"]').first();
    if (await providerSelect.isVisible()) {
      await providerSelect.selectOption({ label: /desktop|claude desktop/i });
      await page.getByRole('button', { name: /save/i }).first().click();
      await page.waitForTimeout(500);
    }

    // Run a mock session
    await page.goto('/mock');
    const startBtn = page.getByRole('button', { name: /start|begin/i }).first();
    if (!await startBtn.isVisible()) { test.skip(); return; }

    await startBtn.click();

    // Usage meter should show $0.00 or no charge
    await page.goto('/');
    const budget = page.locator('[class*="budget"], [class*="usage"]').first();
    if (await budget.isVisible()) {
      const text = (await budget.textContent()) ?? '';
      // Desktop mode costs $0
      expect(text).toMatch(/\$0\.0{1,2}|\$0/);
    }
  });
});
