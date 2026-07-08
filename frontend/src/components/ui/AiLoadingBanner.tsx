import { useAiLoading } from '@/hooks/useAiLoading';
import { Spinner } from './Spinner';

export function AiLoadingBanner() {
  const { active, message, isLocal } = useAiLoading();
  if (!active) return null;

  return (
    <div style={{
      position: 'fixed',
      bottom: 24,
      left: '50%',
      transform: 'translateX(-50%)',
      zIndex: 9999,
      display: 'flex',
      alignItems: 'center',
      gap: 12,
      padding: '12px 20px',
      background: 'var(--surface-2, #1e2030)',
      border: '1px solid var(--border, #2e3248)',
      borderRadius: 'var(--radius)',
      boxShadow: '0 8px 32px rgba(0,0,0,.35)',
      color: 'var(--ink-1)',
      fontSize: '.875rem',
      fontWeight: 500,
      maxWidth: '90vw',
      whiteSpace: 'nowrap',
    }}>
      <Spinner size={16} />
      <span>{message || 'Running AI…'}</span>
      {isLocal && (
        <span style={{ opacity: .55, fontSize: '.8rem', fontWeight: 400 }}>
          · local model, may take a few minutes
        </span>
      )}
    </div>
  );
}
