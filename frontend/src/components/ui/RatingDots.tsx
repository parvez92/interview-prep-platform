interface Props {
  value:    number; /* 1–5 */
  onChange?: (n: number) => void;
  size?:    number;
}

export function RatingDots({ value, onChange, size = 10 }: Props) {
  return (
    <span style={{ display: 'inline-flex', gap: 4, alignItems: 'center' }}>
      {[1, 2, 3, 4, 5].map((n) => {
        const filled = n <= value;
        const color  = value <= 2 ? 'var(--amber)' : 'var(--green)';
        return (
          <span
            key={n}
            onClick={() => onChange?.(n)}
            title={`Rating ${n}`}
            style={{
              width:  size, height: size,
              borderRadius: '50%',
              background: filled ? color : 'var(--line)',
              cursor: onChange ? 'pointer' : 'default',
              transition: 'background var(--t-fast)',
              flexShrink: 0,
            }}
          />
        );
      })}
    </span>
  );
}
