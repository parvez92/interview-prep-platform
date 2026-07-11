import { useState } from 'react';
import { Link, Outlet, useParams } from 'react-router-dom';
import {
  IconChevronDown, IconChevronRight, IconCircleCheck,
  IconCircle, IconPlus,
} from '@tabler/icons-react';
import { usePhases } from '@/hooks/usePhases';
import { TagChip, SourceChip, PriorityChip, priorityTooltip } from '@/components/ui/TagChip';
import { PageSpinner } from '@/components/ui/Spinner';
import type { Phase, Week, TopicLite } from '@/types';
import styles from './Study.module.css';

export function Study() {
  const { data: phases, isLoading } = usePhases();
  const params = useParams<{ slug?: string }>();
  const hasDetail = !!params.slug;

  if (isLoading) return <PageSpinner />;
  if (!phases)   return null;

  return (
    <div className={styles.layout}>
      <PhaseSidebar phases={phases} />
      <div className={styles.workspace}>
        {hasDetail ? <Outlet /> : (
          <div className={styles.placeholder}>
            <p>← Select a topic to open the workspace.</p>
          </div>
        )}
      </div>
    </div>
  );
}

/* ── Phase sidebar ──────────────────────────────────────────────────────── */
function PhaseSidebar({ phases }: { phases: Phase[] }) {
  const [openPhase, setOpenPhase] = useState<string>(phases[0]?.code ?? '');

  return (
    <aside className={styles.sidebar}>
      <div className={styles.sidebarHead}>
        <span className="h3">Study plan</span>
        <button className="btn btn-sm btn-ghost" title="Add custom topic">
          <IconPlus size={14} />
        </button>
      </div>
      <div className={styles.phaseList}>
        {phases.map((phase) => (
          <PhaseSection
            key={phase.code}
            phase={phase}
            open={openPhase === phase.code}
            onToggle={() => setOpenPhase(openPhase === phase.code ? '' : phase.code)}
          />
        ))}
      </div>
    </aside>
  );
}

function PhaseSection({ phase, open, onToggle }: { phase: Phase; open: boolean; onToggle: () => void }) {
  const pct   = phase.progress.total ? (phase.progress.done / phase.progress.total) * 100 : 0;
  const color = pct >= 70 ? 'var(--green)' : pct >= 30 ? 'var(--accent)' : 'var(--ink-4)';

  return (
    <div className={styles.phaseSection}>
      <button className={styles.phaseHeader} onClick={onToggle}>
        <span className={styles.phaseIcon}>{phase.icon}</span>
        <div className={styles.phaseMeta}>
          <span className={styles.phaseName}>{phase.name}</span>
          <div className={styles.phaseProgress}>
            <div className={styles.phaseBar}>
              <div style={{ width: pct + '%', background: color }} className={styles.phaseBarFill} />
            </div>
            <span className="label" style={{ color }}>{phase.progress.done}/{phase.progress.total}</span>
          </div>
        </div>
        {open ? <IconChevronDown size={15} /> : <IconChevronRight size={15} />}
      </button>

      {open && (
        <div className={styles.weekList}>
          {phase.blurb && <p className={styles.phaseBlurb}>{phase.blurb}</p>}
          {phase.weeks.map((week) => <WeekGroup key={week.code} week={week} />)}
        </div>
      )}
    </div>
  );
}

function WeekGroup({ week }: { week: Week }) {
  const [open, setOpen] = useState(true);
  return (
    <div className={styles.weekGroup}>
      {week.bridge && <p className={styles.weekBridge}>{week.bridge}</p>}
      <button className={styles.weekHeader} onClick={() => setOpen(!open)}>
        {open ? <IconChevronDown size={13} /> : <IconChevronRight size={13} />}
        <span className={styles.weekTitle}>{week.title}</span>
        {week.anchor && <span className={styles.weekAnchor} title={week.anchor}>your experience</span>}
      </button>
      {open && (
        <ul className={styles.topicList}>
          {week.topics.map((topic) => <TopicRow key={topic.slug} topic={topic} />)}
        </ul>
      )}
    </div>
  );
}

function TopicRow({ topic }: { topic: TopicLite }) {
  const done = topic.status === 'done';
  return (
    <li>
      <Link to={`/study/${topic.slug}`} className={styles.topicRow}>
        {done
          ? <IconCircleCheck size={15} style={{ color: 'var(--green)', flexShrink: 0 }} />
          : <IconCircle      size={15} style={{ color: 'var(--ink-4)', flexShrink: 0 }} />}
        <span className={`${styles.topicName} ${done ? styles.topicDone : ''}`}
              title={priorityTooltip(topic.priority) ?? topic.title}>
          {topic.title}
        </span>
        <div className={styles.topicChips}>
          <PriorityChip priority={topic.priority} />
          <SourceChip source={topic.source} />
          <TagChip tag={topic.tag} />
        </div>
      </Link>
    </li>
  );
}
