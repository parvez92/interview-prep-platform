import { describe, it, expect } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Home } from '@/pages/Home';
import { renderWithProviders } from '@/test/render';
import { server } from '@/test/server';
import { PROGRESS_RESPONSE, TODAY_RESPONSE } from '@/test/handlers';

describe('Home dashboard', () => {
  it('renders readiness percentage after data loads', async () => {
    renderWithProviders(<Home />);

    await waitFor(() => {
      expect(screen.getByText(/42/)).toBeInTheDocument();
    });
  });

  it('shows streak when greater than zero', async () => {
    renderWithProviders(<Home />);

    await waitFor(() => {
      expect(screen.getByText(/3-day streak/i)).toBeInTheDocument();
    });
  });

  it('shows flaggedCount badge', async () => {
    renderWithProviders(<Home />);

    await waitFor(() => {
      expect(screen.getByText(/1 flagged/i)).toBeInTheDocument();
    });
  });

  it("renders today's focus items with topic labels", async () => {
    renderWithProviders(<Home />);

    await waitFor(() => {
      expect(screen.getByText('Arrays & Two-Pointer')).toBeInTheDocument();
      expect(screen.getByText('Dynamic Programming')).toBeInTheDocument();
    });
  });

  it("shows 'All caught up' when today list is empty", async () => {
    server.use(http.get('/api/review/today', () => HttpResponse.json([])));
    renderWithProviders(<Home />);

    await waitFor(() => {
      expect(screen.getByText(/all caught up/i)).toBeInTheDocument();
    });
  });

  it('shows per-track readiness bars', async () => {
    renderWithProviders(<Home />);

    await waitFor(() => {
      expect(screen.getByText('DSA')).toBeInTheDocument();
      expect(screen.getByText('Behavioral')).toBeInTheDocument();
    });
  });

  it('shows greeting based on time of day', async () => {
    renderWithProviders(<Home />);

    await waitFor(() => {
      const greeting = screen.getByRole('heading', { level: 1 });
      expect(greeting.textContent).toMatch(/Good (morning|afternoon|evening)/i);
    });
  });

  it('renders without crashing while data is loading', () => {
    // Slow responses — component should mount without throwing
    server.use(
      http.get('/api/progress', async () => {
        await new Promise((r) => setTimeout(r, 300));
        return HttpResponse.json(PROGRESS_RESPONSE);
      })
    );
    // Should not throw during initial render
    expect(() => renderWithProviders(<Home />)).not.toThrow();
  });

  it('budget warning banner shown when usage.warning is true', async () => {
    server.use(
      http.get('/api/usage', () =>
        HttpResponse.json({ monthUsd: 4.9, budgetUsd: 5.0, remainingUsd: 0.1, warning: true })
      )
    );
    renderWithProviders(<Home />);

    await waitFor(() => {
      expect(screen.getByText(/budget/i)).toBeInTheDocument();
    });
  });

  it('does not show streak text when streak is 0', async () => {
    server.use(
      http.get('/api/progress', () =>
        HttpResponse.json({ ...PROGRESS_RESPONSE, streak: 0 })
      )
    );
    renderWithProviders(<Home />);

    await waitFor(() => {
      expect(screen.queryByText(/day streak/i)).not.toBeInTheDocument();
      expect(screen.getByText(/ready to prep/i)).toBeInTheDocument();
    });
  });

  it('today items link to topic study page', async () => {
    renderWithProviders(<Home />);

    await waitFor(() => {
      const link = screen.getByRole('link', { name: /arrays & two-pointer/i });
      expect(link).toHaveAttribute('href', '/study/arrays');
    });
  });

  it("shows user's first name in greeting from /api/me", async () => {
    renderWithProviders(<Home />);

    await waitFor(() => {
      // Home renders: "Good morning, Test." — name comes after the comma
      const heading = screen.getByRole('heading', { level: 1 });
      expect(heading.textContent).toMatch(/Test/);
    });
  });
});
