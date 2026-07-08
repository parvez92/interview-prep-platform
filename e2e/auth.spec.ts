import { test, expect } from '@playwright/test';
import { TEST_USER, login } from './helpers';

/**
 * E2E: Authentication flow.
 * Requires: Java backend-core on :8080, frontend on :3000.
 */
test.describe('Authentication', () => {
  test('login page renders correctly', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByText('PrepLoop')).toBeVisible();
    await expect(page.getByPlaceholder('you@example.com')).toBeVisible();
    await expect(page.getByRole('button', { name: /sign in/i })).toBeVisible();
  });

  test('login with valid credentials navigates to home', async ({ page }) => {
    await login(page);
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });

  test('login with wrong password shows error', async ({ page }) => {
    await page.goto('/login');
    await page.getByPlaceholder('you@example.com').fill(TEST_USER.email);
    await page.locator('input[type="password"]').fill('wrong-password');
    await page.getByRole('button', { name: /sign in/i }).click();

    await expect(page.getByText(/invalid email or password/i)).toBeVisible();
    await expect(page).toHaveURL('/login');
  });

  test('unauthenticated access to / redirects to /login', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL('/login');
  });

  test('unauthenticated access to /study redirects to /login', async ({ page }) => {
    await page.goto('/study');
    await expect(page).toHaveURL('/login');
  });

  test('token refresh: app re-authenticates silently after access token expires', async ({ page }) => {
    // Log in normally
    await login(page);

    // Simulate token expiry by clearing the in-memory token via JS
    // The refresh interceptor should auto-fetch a new one when the next API call returns 401
    await page.evaluate(() => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (window as any).__forceTokenExpiry?.();
    });

    // Navigate to a protected page — the refresh interceptor should kick in
    await page.goto('/study');
    await expect(page).toHaveURL('/study');
    await expect(page.getByRole('heading', { name: /study plan/i })).toBeVisible({ timeout: 10_000 });
  });
});
