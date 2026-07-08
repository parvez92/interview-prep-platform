import type { ReactNode } from 'react';

interface Props {
  icon:    ReactNode;
  title:   string;
  body?:   string;
  action?: ReactNode;
}

export function EmptyState({ icon, title, body, action }: Props) {
  return (
    <div style={{
      display: 'flex', flexDirection: 'column', alignItems: 'center',
      gap: '12px', padding: '48px 24px', textAlign: 'center',
      color: 'var(--ink-3)',
    }}>
      <div style={{ fontSize: '2rem', opacity: .6 }}>{icon}</div>
      <p style={{ fontFamily: 'var(--font-head)', fontWeight: 600, color: 'var(--ink-2)', margin: 0 }}>
        {title}
      </p>
      {body && <p style={{ fontSize: '.875rem', maxWidth: 320, margin: 0 }}>{body}</p>}
      {action}
    </div>
  );
}
