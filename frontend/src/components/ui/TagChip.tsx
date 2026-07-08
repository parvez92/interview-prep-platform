import type { Tag, Source } from '@/types';

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

export function KindChip({ kind }: { kind: 'new' | 'flagged' | 'drill' }) {
  const cls = { new: 'badge-accent', flagged: 'badge-amber', drill: 'badge-neutral' }[kind];
  return <span className={`badge ${cls}`}>{kind}</span>;
}
