import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { IconRobot } from '@tabler/icons-react';
import { useLogin, useRegister } from '@/hooks/useAuth';
import { Spinner } from '@/components/ui/Spinner';
import styles from './Login.module.css';

type Mode = 'signin' | 'signup';

export function Login() {
  const [mode,        setMode]        = useState<Mode>('signin');
  const [email,       setEmail]       = useState('');
  const [password,    setPassword]    = useState('');
  const [displayName, setDisplayName] = useState('');
  const [error,       setError]       = useState('');
  const nav      = useNavigate();
  const login    = useLogin();
  const register = useRegister();

  const isPending = login.isPending || register.isPending;

  const switchMode = (m: Mode) => {
    setMode(m);
    setError('');
    setEmail('');
    setPassword('');
    setDisplayName('');
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    try {
      if (mode === 'signin') {
        await login.mutateAsync({ email, password });
      } else {
        if (password.length < 8) { setError('Password must be at least 8 characters.'); return; }
        await register.mutateAsync({ email, password, displayName: displayName || undefined });
      }
      nav('/');
    } catch (err: unknown) {
      type ErrShape = { response?: { data?: { message?: string; error?: { message?: string } } } };
      const apiMsg = (err as ErrShape)?.response?.data?.message
        ?? (err as ErrShape)?.response?.data?.error?.message;
      if (mode === 'signin') {
        setError('Invalid email or password.');
      } else if (apiMsg?.toLowerCase().includes('already')) {
        setError('An account with this email already exists.');
        switchMode('signin');
      } else {
        setError(apiMsg ?? 'Registration failed. Please try again.');
      }
    }
  };

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <div className={styles.logo}>
          <IconRobot size={28} style={{ color: 'var(--accent)' }} />
          <span>PrepLoop</span>
        </div>
        <p className={styles.sub}>Your AI-powered interview prep engine.</p>

        <div className={styles.modeTabs}>
          <button
            type="button"
            className={`${styles.modeTab} ${mode === 'signin' ? styles.modeTabActive : ''}`}
            onClick={() => switchMode('signin')}
          >
            Sign in
          </button>
          <button
            type="button"
            className={`${styles.modeTab} ${mode === 'signup' ? styles.modeTabActive : ''}`}
            onClick={() => switchMode('signup')}
          >
            Create account
          </button>
        </div>

        <form onSubmit={submit} className={styles.form}>
          {mode === 'signup' && (
            <label className={styles.field}>
              <span className="label">Name <span style={{ color: 'var(--ink-3)', fontWeight: 400 }}>(optional)</span></span>
              <input
                className="input-field"
                type="text"
                value={displayName}
                autoComplete="name"
                onChange={(e) => setDisplayName(e.target.value)}
                placeholder="Your name"
              />
            </label>
          )}
          <label className={styles.field}>
            <span className="label">Email</span>
            <input
              className="input-field"
              type="email"
              value={email}
              autoComplete="email"
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              required
            />
          </label>
          <label className={styles.field}>
            <span className="label">Password {mode === 'signup' && <span style={{ color: 'var(--ink-3)', fontWeight: 400 }}>(min 8 chars)</span>}</span>
            <input
              className="input-field"
              type="password"
              value={password}
              autoComplete={mode === 'signup' ? 'new-password' : 'current-password'}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </label>
          {error && <p className={styles.error}>{error}</p>}
          <button className={`btn btn-primary ${styles.submitBtn}`} type="submit" disabled={isPending}>
            {isPending ? <Spinner size={16} /> : null}
            {mode === 'signin' ? 'Sign in' : 'Create account'}
          </button>
        </form>

        <p className={styles.switchRow}>
          {mode === 'signin'
            ? <>New here? <button type="button" onClick={() => switchMode('signup')}>Create an account</button></>
            : <>Already have an account? <button type="button" onClick={() => switchMode('signin')}>Sign in</button></>}
        </p>
      </div>
    </div>
  );
}
