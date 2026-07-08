import { IconX, IconAlertTriangle, IconInfoCircle } from '@tabler/icons-react';
import { useState } from 'react';

interface Props {
  variant?: 'amber' | 'red' | 'accent';
  message:  string;
  dismissible?: boolean;
}

export function Banner({ variant = 'amber', message, dismissible = true }: Props) {
  const [gone, setGone] = useState(false);
  if (gone) return null;

  const colors = {
    amber:  { bg: 'var(--amber-bg)',  color: 'var(--amber)',  border: '#f0c97080' },
    red:    { bg: 'var(--red-bg)',    color: 'var(--red)',    border: '#e9967a80' },
    accent: { bg: 'var(--accent-bg)', color: 'var(--accent)', border: '#8fa3e880' },
  }[variant];

  const Icon = variant === 'accent' ? IconInfoCircle : IconAlertTriangle;

  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: '10px',
      padding: '10px 16px',
      background: colors.bg, color: colors.color,
      border: `1px solid ${colors.border}`,
      borderRadius: 'var(--radius-sm)',
      fontSize: '.875rem', fontWeight: 500,
    }}>
      <Icon size={16} style={{ flexShrink: 0 }} />
      <span style={{ flex: 1 }}>{message}</span>
      {dismissible && (
        <button
          onClick={() => setGone(true)}
          style={{ color: 'inherit', opacity: .6, padding: '2px' }}
          aria-label="Dismiss"
        >
          <IconX size={14} />
        </button>
      )}
    </div>
  );
}
