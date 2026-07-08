import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  IconPlus, IconBuilding, IconCalendar, IconSparkles,
  IconAlertTriangle, IconChevronRight,
} from '@tabler/icons-react';
import {
  useInterviews, useCreateInterview, usePatchInterview,
} from '@/hooks/useInterviews';
import { PageSpinner, Spinner } from '@/components/ui/Spinner';
import { EmptyState } from '@/components/ui/EmptyState';
import { RatingDots } from '@/components/ui/RatingDots';
import type { Interview, InterviewStage } from '@/types';
import styles from './Interviews.module.css';

const STAGES: { id: InterviewStage; label: string }[] = [
  { id: 'applied',   label: 'Applied'    },
  { id: 'screening', label: 'Screening'  },
  { id: 'onsite',    label: 'On-site'    },
  { id: 'offer',     label: 'Offer'      },
  { id: 'rejected',  label: 'Rejected'   },
];

const STAGE_COLOR: Record<InterviewStage, string> = {
  applied:   'var(--ink-4)',
  screening: 'var(--accent)',
  onsite:    'var(--amber)',
  offer:     'var(--green)',
  rejected:  'var(--red)',
};

export function Interviews() {
  const { data: interviews, isLoading } = useInterviews();
  const create    = useCreateInterview();
  const patch     = usePatchInterview();
  const [modal, setModal] = useState(false);

  if (isLoading) return <PageSpinner />;

  const byStage = (stage: InterviewStage) =>
    (interviews ?? []).filter((i) => i.stage === stage);

  const handleCreate = async (data: Partial<Interview>) => {
    await create.mutateAsync(data);
    setModal(false);
  };

  const handleMove = async (id: number, stage: InterviewStage) => {
    await patch.mutateAsync({ id, stage });
  };

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <div>
          <h1 className="h1" style={{ fontFamily: 'var(--font-head)' }}>Interviews</h1>
          <p className="caption" style={{ marginTop: 4 }}>Track your pipeline. Low self-ratings feed back to your study plan.</p>
        </div>
        <button className="btn btn-primary" onClick={() => setModal(true)}>
          <IconPlus size={15} /> Log interview
        </button>
      </div>

      {/* Kanban */}
      {(interviews?.length ?? 0) === 0 ? (
        <EmptyState
          icon={<IconBuilding />}
          title="No interviews yet"
          body="Log your first application and track it through the pipeline."
          action={
            <button className="btn btn-primary" onClick={() => setModal(true)}>
              <IconPlus size={14} /> Log interview
            </button>
          }
        />
      ) : (
        <div className={styles.kanban}>
          {STAGES.map(({ id, label }) => (
            <KanbanColumn
              key={id}
              stage={id}
              label={label}
              color={STAGE_COLOR[id]}
              cards={byStage(id)}
              onMove={handleMove}
            />
          ))}
        </div>
      )}

      {modal && (
        <NewInterviewModal
          onClose={() => setModal(false)}
          onCreate={handleCreate}
          loading={create.isPending}
        />
      )}
    </div>
  );
}

/* ── Kanban column ───────────────────────────────────────────────────────── */
function KanbanColumn({
  stage, label, color, cards, onMove,
}: {
  stage:  InterviewStage;
  label:  string;
  color:  string;
  cards:  Interview[];
  onMove: (id: number, stage: InterviewStage) => void;
}) {
  return (
    <div className={styles.column}>
      <div className={styles.colHeader}>
        <span className={styles.colDot} style={{ background: color }} />
        <span className={styles.colLabel}>{label}</span>
        <span className={styles.colCount}>{cards.length}</span>
      </div>
      <div className={styles.cardStack}>
        {cards.map((c) => (
          <InterviewCard key={c.id} interview={c} onMove={onMove} />
        ))}
        {cards.length === 0 && (
          <div className={styles.colEmpty}>Drop here</div>
        )}
      </div>
    </div>
  );
}

/* ── Interview card ──────────────────────────────────────────────────────── */
function InterviewCard({ interview, onMove }: { interview: Interview; onMove: (id: number, s: InterviewStage) => void }) {
  const [moving, setMoving] = useState(false);

  const nextStage: Partial<Record<InterviewStage, InterviewStage>> = {
    applied: 'screening', screening: 'onsite', onsite: 'offer',
  };
  const next = nextStage[interview.stage];

  return (
    <Link to={`/interviews/${interview.id}`} className={styles.card}>
      <div className={styles.cardTop}>
        <span className={styles.cardCompany}>{interview.company}</span>
        {interview.weak.length > 0 && (
          <span title={`${interview.weak.length} weak topics`}>
            <IconAlertTriangle size={14} style={{ color: 'var(--amber)' }} />
          </span>
        )}
      </div>
      <span className={styles.cardRole}>{interview.role}</span>
      {interview.round && <span className="caption">{interview.round}</span>}
      {interview.scheduledAt && (
        <div className={styles.cardDate}>
          <IconCalendar size={12} />
          {new Date(interview.scheduledAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
        </div>
      )}
      {next && (
        <button
          className={styles.moveBtn}
          onClick={(e) => { e.preventDefault(); setMoving(true); void Promise.resolve(onMove(interview.id, next)).finally(() => setMoving(false)); }}
          title={`Move to ${next}`}
        >
          {moving ? <Spinner size={12} /> : <><IconChevronRight size={12} /> {next}</>}
        </button>
      )}
    </Link>
  );
}

/* ── New interview modal ─────────────────────────────────────────────────── */
function NewInterviewModal({
  onClose, onCreate, loading,
}: {
  onClose: () => void;
  onCreate: (d: Partial<Interview>) => void;
  loading:  boolean;
}) {
  const [form, setForm] = useState({ company: '', role: '', stage: 'applied' as InterviewStage, round: '', scheduledAt: '', jdText: '' });

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <h2 className="h2" style={{ fontFamily: 'var(--font-head)', marginBottom: 20 }}>Log interview</h2>

        <div className={styles.formGrid}>
          <label className={styles.field}>
            <span className="label">Company</span>
            <input className="input-field" value={form.company} onChange={(e) => setForm({ ...form, company: e.target.value })} placeholder="Acme Corp" />
          </label>
          <label className={styles.field}>
            <span className="label">Role</span>
            <input className="input-field" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })} placeholder="Senior SWE" />
          </label>
          <label className={styles.field}>
            <span className="label">Stage</span>
            <select className="input-field" value={form.stage} onChange={(e) => setForm({ ...form, stage: e.target.value as InterviewStage })}>
              {STAGES.map((s) => <option key={s.id} value={s.id}>{s.label}</option>)}
            </select>
          </label>
          <label className={styles.field}>
            <span className="label">Round</span>
            <input className="input-field" value={form.round} onChange={(e) => setForm({ ...form, round: e.target.value })} placeholder="Technical, Phone, …" />
          </label>
          <label className={styles.field}>
            <span className="label">Scheduled</span>
            <input className="input-field" type="date" value={form.scheduledAt} onChange={(e) => setForm({ ...form, scheduledAt: e.target.value })} />
          </label>
        </div>

        <label className={styles.field} style={{ marginTop: 8 }}>
          <span className="label">Job description <span className="muted">(paste for prep pack)</span></span>
          <textarea
            className="input-field"
            style={{ minHeight: 100, resize: 'vertical', fontFamily: 'var(--font-mono)', fontSize: '.8rem' }}
            value={form.jdText}
            onChange={(e) => setForm({ ...form, jdText: e.target.value })}
            placeholder="Paste JD text…"
          />
        </label>

        <div className={styles.modalActions}>
          <button className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" onClick={() => onCreate(form)} disabled={loading || !form.company || !form.role}>
            {loading ? <Spinner size={14} /> : <IconPlus size={14} />}
            Log interview
          </button>
        </div>
      </div>
    </div>
  );
}
