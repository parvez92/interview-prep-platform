interface Props {
  pct:  number;   /* 0–100 */
  size?: number;
  strokeWidth?: number;
  label?: string;
}

export function ReadinessRing({ pct, size = 120, strokeWidth = 10, label }: Props) {
  const r     = (size - strokeWidth) / 2;
  const circ  = 2 * Math.PI * r;
  const dash  = (pct / 100) * circ;
  const color = pct >= 70 ? 'var(--green)' : pct >= 40 ? 'var(--accent)' : 'var(--amber)';

  return (
    <div style={{ position: 'relative', width: size, height: size, flexShrink: 0 }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} style={{ transform: 'rotate(-90deg)' }}>
        <circle
          cx={size / 2} cy={size / 2} r={r}
          fill="none"
          stroke="var(--line)"
          strokeWidth={strokeWidth}
        />
        <circle
          cx={size / 2} cy={size / 2} r={r}
          fill="none"
          stroke={color}
          strokeWidth={strokeWidth}
          strokeLinecap="round"
          strokeDasharray={`${dash} ${circ - dash}`}
          style={{ transition: 'stroke-dasharray .6s cubic-bezier(.25,.1,.25,1)' }}
        />
      </svg>
      <div style={{
        position: 'absolute', inset: 0,
        display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'center',
      }}>
        <span style={{
          fontFamily: 'var(--font-head)',
          fontSize: size * 0.22 + 'px',
          fontWeight: 700,
          color,
          lineHeight: 1,
        }}>
          {Math.round(pct)}
        </span>
        {label && (
          <span style={{
            fontSize: size * 0.1 + 'px',
            color: 'var(--ink-3)',
            fontFamily: 'var(--font-mono)',
            marginTop: 2,
          }}>
            {label}
          </span>
        )}
      </div>
    </div>
  );
}
