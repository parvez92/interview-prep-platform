import { Link } from 'react-router-dom';
import { IconFlame, IconFlag3, IconCircleCheck, IconArrowRight } from '@tabler/icons-react';
import { useProgress, useTodayReview, useUsage } from '@/hooks/useDashboard';
import { useMe } from '@/hooks/useMe';
import { ReadinessRing } from '@/components/ui/ReadinessRing';
import { KindChip } from '@/components/ui/TagChip';
import { BudgetMeter } from '@/components/ui/BudgetMeter';
import { PageSpinner } from '@/components/ui/Spinner';
import styles from './Home.module.css';

export function Home() {
  const { data: me }       = useMe();
  const { data: progress, isLoading: pLoad } = useProgress();
  const { data: today,    isLoading: tLoad } = useTodayReview();
  const { data: usage }    = useUsage();

  if (pLoad || tLoad) return <PageSpinner />;

  const hour    = new Date().getHours();
  const greeting = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  const name    = me?.user.displayName?.split(' ')[0] ?? 'there';

  return (
    <div className={styles.page}>
      {/* Header row */}
      <div className={styles.header}>
        <div>
          <h1 className={styles.greeting}>{greeting}, {name}.</h1>
          <p className={styles.sub}>
            {progress?.streak
              ? <><IconFlame size={15} style={{ color: 'var(--amber)', verticalAlign: 'middle' }} /> {progress.streak}-day streak — keep it going.</>
              : 'Ready to prep?'}
          </p>
        </div>
        {progress?.flaggedCount ? (
          <span className="badge badge-amber" style={{ gap: 5 }}>
            <IconFlag3 size={13} />
            {progress.flaggedCount} flagged
          </span>
        ) : null}
      </div>

      <div className={styles.grid}>
        {/* Readiness card */}
        <div className={`card ${styles.readinessCard}`}>
          <p className={styles.cardLabel}>Overall readiness</p>
          <div className={styles.readinessBody}>
            <ReadinessRing pct={progress?.overallPct ?? 0} size={110} label="%" />
            <div className={styles.trackBars}>
              {progress?.tracks.map((t) => (
                <TrackBar key={t.name} name={t.name} pct={t.readiness} />
              ))}
            </div>
          </div>
        </div>

        {/* Today's focus */}
        <div className={`card ${styles.todayCard}`}>
          <div className={styles.cardHeader}>
            <p className={styles.cardLabel}>Today's focus</p>
            <Link to="/study" className="caption" style={{ color: 'var(--accent)', display: 'flex', alignItems: 'center', gap: 3 }}>
              Plan <IconArrowRight size={13} />
            </Link>
          </div>
          {today && today.items.length > 0 ? (
            <>
              <div className={styles.todayBudget}>
                <div className={styles.todayBudgetBar}>
                  <div
                    className={styles.todayBudgetFill}
                    style={{ width: Math.min(100, (today.plannedMin / Math.max(1, today.budgetMin)) * 100) + '%' }}
                  />
                </div>
                <span className="caption">{today.plannedMin} of {today.budgetMin} min · {today.velocity}</span>
              </div>
              <ul className={styles.todayList}>
                {today.items.map((item, i) => (
                  <li key={i} className={styles.todayItem} title={item.note ?? ''}>
                    <KindChip kind={item.kind} />
                    <Link to={`/study/${item.topicSlug}`} className={styles.todayLabel}>
                      {item.label}
                    </Link>
                    <span className="caption" style={{ flexShrink: 0 }}>{item.estMinutes}m</span>
                  </li>
                ))}
              </ul>
            </>
          ) : (
            <div className={styles.todayEmpty}>
              <IconCircleCheck size={28} style={{ color: 'var(--green)', opacity: .7 }} />
              <p>All caught up for today!</p>
            </div>
          )}
        </div>

        {/* Pillar cards */}
        <PillarCard
          title="Study"
          icon="📚"
          done={progress?.tracks.find(t => t.name === 'study')?.readiness ?? 0}
          href="/study"
          color="var(--accent)"
          bg="var(--accent-bg)"
        />
        <PillarCard
          title="Practice"
          icon="💻"
          done={progress?.tracks.find(t => t.name === 'practice')?.readiness ?? 0}
          href="/study"
          color="var(--green)"
          bg="var(--green-bg)"
        />
        <PillarCard
          title="Interviews"
          icon="🏢"
          done={progress?.tracks.find(t => t.name === 'interviews')?.readiness ?? 0}
          href="/interviews"
          color="var(--amber)"
          bg="var(--amber-bg)"
        />
        <PillarCard
          title="Mock sessions"
          icon="🎤"
          done={progress?.tracks.find(t => t.name === 'mock')?.readiness ?? 0}
          href="/mock"
          color="var(--ink-2)"
          bg="var(--line-2)"
        />

        {/* Budget */}
        {usage && (
          <div className={`card ${styles.budgetCard}`}>
            <p className={styles.cardLabel}>AI budget</p>
            <BudgetMeter {...usage} />
          </div>
        )}
      </div>
    </div>
  );
}

/* ── Sub-components ───────────────────────────────────────────────────────── */

function TrackBar({ name, pct }: { name: string; pct: number }) {
  const color = pct >= 70 ? 'var(--green)' : pct >= 40 ? 'var(--accent)' : 'var(--amber)';
  return (
    <div className={styles.trackItem}>
      <div className={styles.trackMeta}>
        <span className={styles.trackName}>{name}</span>
        <span className={styles.trackPct} style={{ color }}>{Math.round(pct)}%</span>
      </div>
      <div className={styles.trackBg}>
        <div className={styles.trackFill} style={{ width: pct + '%', background: color }} />
      </div>
    </div>
  );
}

function PillarCard({
  title, icon, done, href, color, bg,
}: {
  title: string; icon: string; done: number; href: string; color: string; bg: string;
}) {
  return (
    <Link to={href} className={`card ${styles.pillarCard}`} style={{ '--c': color, '--bg': bg } as React.CSSProperties}>
      <div className={styles.pillarIcon}>{icon}</div>
      <div>
        <div className={styles.pillarTitle}>{title}</div>
        <div className={styles.pillarPct} style={{ color }}>{Math.round(done)}%</div>
      </div>
      <div className={styles.pillarBar}>
        <div className={styles.pillarFill} style={{ width: done + '%', background: color }} />
      </div>
    </Link>
  );
}
