import { describe, it, expect } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Login } from '@/pages/Login';
import { renderWithProviders } from '@/test/render';
import { server } from '@/test/server';

// the page also has a "Sign in" mode tab — target the actual submit button
const submitButton = () =>
  screen.getAllByRole('button', { name: /sign in/i })
    .find((b) => b.getAttribute('type') === 'submit') as HTMLButtonElement;

describe('Login page', () => {
  it('renders the login form', () => {
    renderWithProviders(<Login />);
    expect(screen.getByText('PrepLoop')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('you@example.com')).toBeInTheDocument();
    expect(submitButton()).toBeInTheDocument();
  });

  it('fills in credentials and submits successfully', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Login />);

    await user.type(screen.getByPlaceholderText('you@example.com'), 'test@prep.test');
    await user.type(screen.getByDisplayValue(''), 'password');
    await user.click(submitButton());

    // No error shown after successful login
    await waitFor(() => {
      expect(screen.queryByText(/invalid email or password/i)).not.toBeInTheDocument();
    });
  });

  it('shows error message on invalid credentials', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Login />);

    await user.type(screen.getByPlaceholderText('you@example.com'), 'wrong@test.com');
    const passwordInput = screen.getAllByRole('textbox').find(() => false) ??
      document.querySelector('input[type="password"]')!;
    await user.type(passwordInput, 'wrongpassword');
    await user.click(submitButton());

    await waitFor(() => {
      expect(screen.getByText(/invalid email or password/i)).toBeInTheDocument();
    });
  });

  it('disables submit button while request is in flight', async () => {
    // Simulate slow response
    server.use(
      http.post('/api/auth/login', async () => {
        await new Promise((r) => setTimeout(r, 100));
        return HttpResponse.json({ accessToken: 'tok', refreshToken: 'r', expiresIn: 900 });
      })
    );

    const user = userEvent.setup();
    renderWithProviders(<Login />);

    await user.type(screen.getByPlaceholderText('you@example.com'), 'test@prep.test');
    const pw = document.querySelector('input[type="password"]') as HTMLInputElement;
    await user.type(pw, 'password');

    const btn = submitButton();
    await user.click(btn);
    expect(btn).toBeDisabled();
  });

  it('shows error state when API returns 401', async () => {
    const user = userEvent.setup();
    renderWithProviders(<Login />);

    // Fill with wrong creds so the mock handler returns 401
    await user.type(screen.getByPlaceholderText('you@example.com'), 'bad@test.com');
    const pw = document.querySelector('input[type="password"]') as HTMLInputElement;
    await user.type(pw, 'wrongpass');
    await user.click(submitButton());

    await waitFor(() => {
      expect(screen.getByText(/invalid email or password/i)).toBeInTheDocument();
    });
    // After error, the form is still visible (not navigated away)
    expect(screen.getByPlaceholderText('you@example.com')).toBeInTheDocument();
  });
});
