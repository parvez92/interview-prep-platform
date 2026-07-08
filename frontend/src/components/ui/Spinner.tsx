export function Spinner({ size = 20 }: { size?: number }) {
  return (
    <svg
      width={size} height={size}
      viewBox="0 0 24 24" fill="none"
      style={{ animation: 'spin .8s linear infinite', flexShrink: 0 }}
    >
      <circle cx="12" cy="12" r="10" stroke="var(--line)" strokeWidth="3" />
      <path d="M12 2a10 10 0 0 1 10 10" stroke="var(--accent)" strokeWidth="3" strokeLinecap="round" />
    </svg>
  );
}

export function PageSpinner() {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60vh' }}>
      <Spinner size={32} />
    </div>
  );
}
