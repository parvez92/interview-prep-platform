interface Props {
  monthUsd:     number;
  budgetUsd:    number;
  remainingUsd: number;
  warning:      boolean;
}

export function BudgetMeter({ monthUsd, budgetUsd, warning }: Props) {
  const pct   = budgetUsd > 0 ? Math.min((monthUsd / budgetUsd) * 100, 100) : 0;
  const color = warning ? 'var(--amber)' : 'var(--green)';
  const bg    = warning ? 'var(--amber-bg)' : 'var(--green-bg)';

  return (
    <div style={{
      display: 'flex', flexDirection: 'column', gap: 6,
      padding: '12px 16px',
      background: bg,
      borderRadius: 'var(--radius-sm)',
      border: `1px solid ${warning ? '#f0c97060' : '#6bbf9560'}`,
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span style={{ fontFamily: 'var(--font-mono)', fontSize: '.7rem', color }}>AI spend</span>
        <span style={{ fontFamily: 'var(--font-mono)', fontSize: '.7rem', color }}>
          ${monthUsd.toFixed(2)} / ${budgetUsd.toFixed(2)}
        </span>
      </div>
      <div style={{
        height: 5, background: warning ? '#f0c97040' : '#6bbf9530',
        borderRadius: 99, overflow: 'hidden',
      }}>
        <div style={{
          height: '100%', width: pct + '%',
          background: color,
          borderRadius: 99,
          transition: 'width .5s var(--ease)',
        }} />
      </div>
    </div>
  );
}
