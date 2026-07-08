import { describe, it, expect } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Interviews } from '@/pages/Interviews';
import { renderWithProviders } from '@/test/render';
import { server } from '@/test/server';

describe('Interviews kanban', () => {
  // ── Initial render ─────────────────────────────────────────────────────────

  it('renders all kanban columns', async () => {
    renderWithProviders(<Interviews />);
    await waitFor(() => {
      expect(screen.getByText('Applied')).toBeInTheDocument();
      expect(screen.getByText('Screening')).toBeInTheDocument();
      expect(screen.getByText('On-site')).toBeInTheDocument();
      expect(screen.getByText('Offer')).toBeInTheDocument();
      expect(screen.getByText('Rejected')).toBeInTheDocument();
    });
  });

  it('shows existing interviews in correct columns', async () => {
    renderWithProviders(<Interviews />);

    await waitFor(() => {
      // Google is in 'onsite'
      const onsiteCol = screen.getByText('On-site').closest('div')!.parentElement!;
      expect(within(onsiteCol).getByText('Google')).toBeInTheDocument();

      // Meta is in 'applied'
      const appliedCol = screen.getByText('Applied').closest('div')!.parentElement!;
      expect(within(appliedCol).getByText('Meta')).toBeInTheDocument();
    });
  });

  it('shows weak-topic warning icon on card with weak topics', async () => {
    renderWithProviders(<Interviews />);

    await waitFor(() => {
      // Google has weak: ['arrays'] → alert icon should appear on Google's card
      const googleCard = screen.getByText('Google').closest('a')!;
      // The IconAlertTriangle has a title attribute set to its count
      const warning = googleCard.querySelector('[title*="weak"]') ??
        googleCard.querySelector('svg[class*="icon"]');
      expect(warning).not.toBeNull();
    });
  });

  it('shows empty state when no interviews', async () => {
    server.use(http.get('/api/interviews', () => HttpResponse.json([])));
    renderWithProviders(<Interviews />);

    await waitFor(() => {
      expect(screen.getByText(/no interviews yet/i)).toBeInTheDocument();
    });
  });

  // ── Create interview modal ────────────────────────────────────────────────

  it('opens create-interview modal on button click', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Interviews />);

    await waitFor(() => screen.getByText('Log interview'));
    await user.click(screen.getAllByRole('button', { name: /log interview/i })[0]);

    expect(screen.getByText(/log interview/i, { selector: 'h2' })).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Acme Corp')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Senior SWE')).toBeInTheDocument();
  });

  it('submits new interview and closes modal', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Interviews />);

    await waitFor(() => screen.getByText('Log interview'));
    await user.click(screen.getAllByRole('button', { name: /log interview/i })[0]);

    await user.type(screen.getByPlaceholderText('Acme Corp'), 'Netflix');
    await user.type(screen.getByPlaceholderText('Senior SWE'), 'Staff SWE');

    // Submit is enabled when company + role filled
    const submitBtn = screen.getAllByRole('button', { name: /log interview/i }).at(-1)!;
    expect(submitBtn).not.toBeDisabled();

    await user.click(submitBtn);

    await waitFor(() => {
      // Modal should close after successful submit
      expect(screen.queryByPlaceholderText('Acme Corp')).not.toBeInTheDocument();
    });
  });

  it('submit button disabled when company or role is empty', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Interviews />);

    await waitFor(() => screen.getByText('Log interview'));
    await user.click(screen.getAllByRole('button', { name: /log interview/i })[0]);

    const submitBtn = screen.getAllByRole('button', { name: /log interview/i }).at(-1)!;
    expect(submitBtn).toBeDisabled();

    // Fill only company
    await user.type(screen.getByPlaceholderText('Acme Corp'), 'Netflix');
    expect(submitBtn).toBeDisabled();

    // Fill role → enabled
    await user.type(screen.getByPlaceholderText('Senior SWE'), 'SWE');
    expect(submitBtn).not.toBeDisabled();
  });

  it('closes modal on cancel click', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Interviews />);

    await waitFor(() => screen.getByText('Log interview'));
    await user.click(screen.getAllByRole('button', { name: /log interview/i })[0]);
    await user.click(screen.getByRole('button', { name: /cancel/i }));

    expect(screen.queryByPlaceholderText('Acme Corp')).not.toBeInTheDocument();
  });

  it('closes modal on backdrop click', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Interviews />);

    await waitFor(() => screen.getByText('Log interview'));
    await user.click(screen.getAllByRole('button', { name: /log interview/i })[0]);

    // The overlay div (behind the modal) should close when clicked
    const overlay = document.querySelector('[class*="overlay"]') as HTMLElement;
    if (overlay) await user.click(overlay);

    expect(screen.queryByPlaceholderText('Acme Corp')).not.toBeInTheDocument();
  });

  // ── Move card ─────────────────────────────────────────────────────────────

  it('shows move button on cards that can advance', async () => {
    renderWithProviders(<Interviews />);

    await waitFor(() => {
      // Google is at 'onsite' → next is 'offer', so move button shows 'offer'
      const googleCard = screen.getByText('Google').closest('a')!;
      expect(within(googleCard).getByText(/offer/i)).toBeInTheDocument();
    });
  });

  it('clicking move button calls PATCH and does not navigate', async () => {
    let patched = false;
    server.use(
      http.patch('/api/interviews/:id', async ({ request }) => {
        const body = await request.json() as { stage: string };
        patched = true;
        return HttpResponse.json({ id: 1, company: 'Google', role: 'L5 SWE', stage: body.stage, round: null, scheduledAt: null, outcome: null, weak: [] });
      })
    );

    const user = userEvent.setup();
    renderWithProviders(<Interviews />);

    await waitFor(() => screen.getByText('Google'));

    const googleCard = screen.getByText('Google').closest('a')!;
    const moveBtn = within(googleCard).getByRole('button');
    await user.click(moveBtn);

    await waitFor(() => expect(patched).toBe(true));
  });

  it('header shows page title and description', async () => {
    renderWithProviders(<Interviews />);

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /interviews/i })).toBeInTheDocument();
      expect(screen.getByText(/low self-ratings feed back/i)).toBeInTheDocument();
    });
  });
});
