import { useState } from 'react';
import { IconBuilding, IconMapPin, IconStar, IconCheck, IconX, IconBolt, IconExternalLink } from '@tabler/icons-react';
import { useJobs, usePatchJob } from '@/hooks/useJobs';
import { PageSpinner } from '@/components/ui/Spinner';
import { EmptyState } from '@/components/ui/EmptyState';
import type { Job } from '@/types';
import styles from './Jobs.module.css';

type SortMode = 'fit' | 'recent';

export function Jobs() {
  const [sort, setSort]     = useState<SortMode>('fit');
  const [filter, setFilter] = useState<string>('new');

  const { data: jobs, isLoading } = useJobs(sort, filter === 'all' ? undefined : filter);
  const patch = usePatchJob();

  if (isLoading) return <PageSpinner />;

  const active   = (jobs ?? []).filter((j) => j.status !== 'dismissed');
  const dismissed = (jobs ?? []).filter((j) => j.status === 'dismissed');
  const shown    = filter === 'dismissed' ? dismissed : active;

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <div>
          <h1 className="h1" style={{ fontFamily: 'var(--font-head)' }}>Jobs feed</h1>
          <p className="caption" style={{ marginTop: 4 }}>Scored against your résumé by AI. Parsed from Gmail job alerts.</p>
        </div>
      </div>

      <div className={styles.toolbar}>
        <div className={styles.filters}>
          {['new', 'interested', 'dismissed', 'all'].map((f) => (
            <button
              key={f}
              className={`${styles.filterBtn} ${filter === f ? styles.filterActive : ''}`}
              onClick={() => setFilter(f)}
            >
              {f}
            </button>
          ))}
        </div>
        <div className={styles.sortToggle}>
          <span className="caption">Sort:</span>
          <button className={`${styles.sortBtn} ${sort === 'fit' ? styles.sortActive : ''}`} onClick={() => setSort('fit')}>fit</button>
          <button className={`${styles.sortBtn} ${sort === 'recent' ? styles.sortActive : ''}`} onClick={() => setSort('recent')}>recent</button>
        </div>
      </div>

      {shown.length === 0 ? (
        <EmptyState
          icon={<IconBolt />}
          title="No jobs here"
          body="Jobs are parsed from your Gmail job alerts automatically."
        />
      ) : (
        <div className={styles.grid}>
          {shown.map((job) => (
            <JobCard key={job.id} job={job} onPatch={(id, body) => patch.mutate({ id, ...body })} />
          ))}
        </div>
      )}
    </div>
  );
}

function JobCard({ job, onPatch }: { job: Job; onPatch: (id: number, body: Partial<Job>) => void }) {
  const fitColor = job.fit >= 7 ? 'var(--green)' : job.fit >= 5 ? 'var(--amber)' : 'var(--ink-4)';

  return (
    <div className={`card ${styles.jobCard} ${job.status === 'dismissed' ? styles.dismissed : ''}`}>
      <div className={styles.top}>
        <div className={styles.fitBadge} style={{ background: job.fit >= 7 ? 'var(--green-bg)' : job.fit >= 5 ? 'var(--amber-bg)' : 'var(--line-2)', color: fitColor }}>
          {job.fit}/10
        </div>
        {job.status === 'interested' && <span className="badge badge-green">interested</span>}
        {job.status === 'new'        && <span className="badge badge-neutral">new</span>}
      </div>

      <div className={styles.company}>
        <IconBuilding size={15} style={{ color: 'var(--ink-3)', flexShrink: 0 }} />
        <span className={styles.companyName}>{job.company}</span>
      </div>

      <p className={styles.role}>{job.role}</p>

      <div className={styles.meta}>
        {job.location && <span><IconMapPin size={12} /> {job.location}</span>}
        {job.comp     && <span>💰 {job.comp}</span>}
      </div>

      <p className={styles.reason}>{job.reason}</p>

      {job.tags.length > 0 && (
        <div className={styles.tags}>
          {job.tags.map((t) => <span key={t} className="badge badge-neutral">{t}</span>)}
        </div>
      )}

      <div className={styles.actions}>
        {job.status !== 'interested' && (
          <button className="btn btn-sm" style={{ background: 'var(--green-bg)', color: 'var(--green)' }}
            onClick={() => onPatch(job.id, { status: 'interested' })}>
            <IconStar size={13} /> Interested
          </button>
        )}
        {job.status !== 'dismissed' && (
          <button className="btn btn-ghost btn-sm" onClick={() => onPatch(job.id, { status: 'dismissed' })}>
            <IconX size={13} /> Dismiss
          </button>
        )}
        <a href={job.via} target="_blank" rel="noopener noreferrer" className="btn btn-ghost btn-sm" style={{ marginLeft: 'auto' }}>
          <IconExternalLink size={13} /> Apply
        </a>
      </div>
    </div>
  );
}
