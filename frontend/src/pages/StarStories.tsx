import { useState } from 'react';
import { IconStar, IconPlus, IconTrash, IconPencil, IconChevronDown } from '@tabler/icons-react';
import { useStarStories, useCreateStarStory, useDeleteStarStory } from '@/hooks/useStarStories';
import { PageSpinner, Spinner } from '@/components/ui/Spinner';
import { EmptyState } from '@/components/ui/EmptyState';
import type { StarStory } from '@/types';
import styles from './StarStories.module.css';

export function StarStories() {
  const { data: stories, isLoading } = useStarStories();
  const create = useCreateStarStory();
  const del    = useDeleteStarStory();
  const [modal, setModal] = useState(false);

  if (isLoading) return <PageSpinner />;

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <div>
          <h1 className="h1" style={{ fontFamily: 'var(--font-head)' }}>STAR bank</h1>
          <p className="caption" style={{ marginTop: 4 }}>Behavioral story bank — reuse across interviews.</p>
        </div>
        <button className="btn btn-primary" onClick={() => setModal(true)}>
          <IconPlus size={15} /> Add story
        </button>
      </div>

      {(stories?.length ?? 0) === 0 ? (
        <EmptyState
          icon={<IconStar />}
          title="No STAR stories yet"
          body="Add your first story — each maps to behavioral interview prompts."
          action={<button className="btn btn-primary" onClick={() => setModal(true)}><IconPlus size={14} /> Add story</button>}
        />
      ) : (
        <div className={styles.grid}>
          {stories?.map((s) => (
            <StoryCard key={s.id} story={s} onDelete={() => del.mutate(s.id)} />
          ))}
        </div>
      )}

      {modal && (
        <NewStoryModal
          onClose={() => setModal(false)}
          onCreate={(data) => { create.mutate(data); setModal(false); }}
          loading={create.isPending}
        />
      )}
    </div>
  );
}

function StoryCard({ story, onDelete }: { story: StarStory; onDelete: () => void }) {
  const [open, setOpen] = useState(false);

  return (
    <div className={`card ${styles.card}`}>
      <div className={styles.cardTop}>
        <div className={styles.cardTitle}>{story.title}</div>
        <div className={styles.cardActions}>
          <button className="btn btn-icon btn-ghost btn-sm" onClick={() => setOpen(!open)}>
            <IconChevronDown size={15} style={{ transform: open ? 'rotate(180deg)' : '', transition: 'transform .2s' }} />
          </button>
          <button className="btn btn-icon btn-danger btn-sm" onClick={onDelete}><IconTrash size={14} /></button>
        </div>
      </div>

      {story.tags.length > 0 && (
        <div className={styles.tags}>
          {story.tags.map((t) => <span key={t} className="badge badge-neutral">{t}</span>)}
        </div>
      )}

      {open && (
        <div className={styles.starGrid}>
          {(['situation','task','action','result'] as (keyof StarStory)[]).map((key) => (
            <div key={key} className={styles.starRow}>
              <span className={styles.starKey}>{key.charAt(0).toUpperCase()}</span>
              <p className={styles.starText}>{String(story[key])}</p>
            </div>
          ))}
          {story.mappedPrompts.length > 0 && (
            <div className={styles.prompts}>
              <span className="label" style={{ color: 'var(--ink-3)' }}>Prompts</span>
              <ul>
                {story.mappedPrompts.map((p, i) => <li key={i}>{p}</li>)}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function NewStoryModal({
  onClose, onCreate, loading,
}: {
  onClose: () => void;
  onCreate: (d: Omit<StarStory, 'id'>) => void;
  loading:  boolean;
}) {
  const blank: Omit<StarStory, 'id'> = { title: '', situation: '', task: '', action: '', result: '', tags: [], mappedPrompts: [] };
  const [form, setForm] = useState(blank);
  const [tagInput, setTagInput] = useState('');

  const set = (k: keyof typeof blank, v: string) => setForm((p) => ({ ...p, [k]: v }));
  const addTag = () => {
    if (tagInput.trim()) { setForm((p) => ({ ...p, tags: [...p.tags, tagInput.trim()] })); setTagInput(''); }
  };

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <h2 className="h2" style={{ fontFamily: 'var(--font-head)', marginBottom: 20 }}>Add STAR story</h2>

        <label className={styles.field}>
          <span className="label">Title</span>
          <input className="input-field" value={form.title} onChange={(e) => set('title', e.target.value)} placeholder="Led system migration…" />
        </label>

        {(['situation','task','action','result'] as const).map((k) => (
          <label key={k} className={styles.field}>
            <span className="label">{k.charAt(0).toUpperCase() + k.slice(1)}</span>
            <textarea
              className="input-field"
              style={{ minHeight: 72, resize: 'vertical' }}
              value={String(form[k])}
              onChange={(e) => set(k, e.target.value)}
              placeholder={`Describe the ${k}…`}
            />
          </label>
        ))}

        <div className={styles.tagRow}>
          <input className="input-field" value={tagInput} onChange={(e) => setTagInput(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && addTag()} placeholder="Add tag (press Enter)" style={{ flex: 1 }} />
        </div>
        {form.tags.length > 0 && (
          <div className={styles.tags} style={{ marginTop: 6 }}>
            {form.tags.map((t) => (
              <span key={t} className="badge badge-neutral" style={{ cursor: 'pointer' }}
                onClick={() => setForm((p) => ({ ...p, tags: p.tags.filter((x) => x !== t) }))}>
                {t} ×
              </span>
            ))}
          </div>
        )}

        <div className={styles.modalActions}>
          <button className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" onClick={() => onCreate(form)} disabled={loading || !form.title}>
            {loading ? <Spinner size={14} /> : <IconPlus size={14} />} Save story
          </button>
        </div>
      </div>
    </div>
  );
}
