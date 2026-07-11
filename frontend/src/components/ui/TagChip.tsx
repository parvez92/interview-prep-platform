import type { Tag, Source, Priority } from '@/types';

const TAG_LABEL: Record<Tag, string>   = { new: 'new', refresh: 'refresh', dsa: 'DSA', exp: 'exp' };
const SRC_LABEL: Record<Source, string> = { resume: 'résumé', standard: 'standard', interest: 'interest', custom: 'custom' };

export function TagChip({ tag }: { tag: Tag }) {
  return (
    <span className="badge badge-accent" style={{ gap: 3 }}>
      {TAG_LABEL[tag]}
    </span>
  );
}

const SRC_COLOR: Record<Source, string> = {
  resume:   'badge-green',
  standard: 'badge-neutral',
  interest: 'badge-accent',
  custom:   'badge-amber',
};

export function SourceChip({ source }: { source: Source }) {
  return <span className={`badge ${SRC_COLOR[source]}`}>{SRC_LABEL[source]}</span>;
}

/**
 * Plan-amendment-01 §1.4: `low` shows a muted "optional" chip; `medium` shows no chip but a
 * compress-if-behind tooltip on the row; `high` (the interview core) is unadorned.
 */
const MEDIUM_HINT = 'Compress if behind: concept + angle only, skip exercises (~30 min)';
const LOW_HINT = 'Optional — skip freely when time-pressed; dropped from behind-pace queues';

export function PriorityChip({ priority }: { priority: Priority }) {
  if (priority === 'low') {
    return <span className="badge badge-neutral" style={{ opacity: 0.6 }} title={LOW_HINT}>optional</span>;
  }
  return null;
}

/** The row-level tooltip a `medium` topic carries. `high`/`low` add nothing here. */
export function priorityTooltip(priority: Priority): string | undefined {
  return priority === 'medium' ? MEDIUM_HINT : undefined;
}

export function KindChip({ kind }: { kind: 'new' | 'flagged' | 'spaced' | 'drill' }) {
  const cls = { new: 'badge-accent', flagged: 'badge-amber', spaced: 'badge-neutral', drill: 'badge-neutral' }[kind];
  return <span className={`badge ${cls}`}>{kind}</span>;
}
