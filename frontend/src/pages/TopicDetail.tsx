import { useState, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import {
  IconCircleCheck, IconCircle, IconSparkles, IconBook,
  IconLink, IconCode, IconQuestionMark, IconExternalLink,
  IconTrash, IconPlus, IconBulb,
} from '@tabler/icons-react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { useTopic, useToggleTopicDone, usePatchTopic } from '@/hooks/usePhases';
import api from '@/lib/api';
import { queryClient } from '@/lib/queryClient';
import { TagChip, SourceChip } from '@/components/ui/TagChip';
import { Spinner, PageSpinner } from '@/components/ui/Spinner';
import { RatingDots } from '@/components/ui/RatingDots';
import { Banner } from '@/components/ui/Banner';
import { DesktopPromptModal } from '@/components/ui/DesktopPromptModal';
import styles from './TopicDetail.module.css';

type Tab = 'notes' | 'resources' | 'exercises' | 'questions';

type GuideResponse = { desktop_mode?: boolean; desktop_prompt?: string; status?: string; budgetWarning?: boolean };

export function TopicDetail() {
  const { slug } = useParams<{ slug: string }>();
  const { data: topic, isLoading } = useTopic(slug!);
  const toggleDone = useToggleTopicDone(slug!);
  const patchTopic = usePatchTopic(slug!);

  const [tab, setTab] = useState<Tab>('notes');
  const [overviewLoading, setOverviewLoading] = useState(false);
  const [overviewDesktop, setOverviewDesktop] = useState<string | null>(null);
  const [budgetWarn, setBudgetWarn] = useState(false);

  const handleGenerateOverview = async () => {
    setOverviewLoading(true);
    try {
      const res = await api.post<GuideResponse>('/ai/guide', { slug, tab: 'overview' });
      if (res.data.desktop_mode && res.data.desktop_prompt) {
        setOverviewDesktop(res.data.desktop_prompt);
      } else {
        setBudgetWarn(!!res.data.budgetWarning);
        queryClient.invalidateQueries({ queryKey: ['topic', slug] });
      }
    } finally { setOverviewLoading(false); }
  };

  if (isLoading) return <PageSpinner />;
  if (!topic)   return <p className="caption" style={{ padding: 32 }}>Topic not found.</p>;

  const done = topic.status === 'done';

  return (
    <div className={styles.page} key={slug}>
      {overviewDesktop && (
        <DesktopPromptModal
          prompt={overviewDesktop}
          pasteLabel="Paste the JSON overview from your desktop AI"
          expectJson
          onSubmit={async (text) => {
            const data = JSON.parse(text);
            await api.patch(`/topics/${slug}`, {
              concept: data.concept,
              points: data.points,
              angle: data.angle,
            });
            setOverviewDesktop(null);
            queryClient.invalidateQueries({ queryKey: ['topic', slug] });
          }}
          onClose={() => setOverviewDesktop(null)}
        />
      )}
      {budgetWarn && (
        <Banner variant="amber" message="Generated in economy mode — monthly budget reached." />
      )}

      {/* Header */}
      <div className={styles.header}>
        <div className={styles.headerLeft}>
          <button
            className={styles.doneToggle}
            onClick={() => toggleDone.mutate(done ? 'todo' : 'done')}
            title={done ? 'Mark todo' : 'Mark done'}
          >
            {done
              ? <IconCircleCheck size={22} style={{ color: 'var(--green)' }} />
              : <IconCircle      size={22} style={{ color: 'var(--ink-4)' }} />}
          </button>
          <div>
            <h1 className={styles.title}>{topic.title}</h1>
            <div className={styles.chips}>
              <SourceChip source={topic.source} />
              <TagChip tag={topic.tag} />
              {topic.confidence !== null && (
                <RatingDots value={topic.confidence ?? 0} size={8} onChange={(n) => patchTopic.mutate({ confidence: n })} />
              )}
            </div>
          </div>
        </div>
        <button
          className="btn btn-primary btn-sm"
          onClick={handleGenerateOverview}
          disabled={overviewLoading}
          style={{ gap: 6 }}
        >
          {overviewLoading ? <Spinner size={14} /> : <IconSparkles size={14} />}
          Generate overview
        </button>
      </div>

      {/* Deep dive panel */}
      {topic.deepDive?.concept && (
        <div className={styles.deepDive}>
          <div className={styles.deepDiveIcon}><IconBulb size={16} style={{ color: 'var(--accent)' }} /></div>
          <div>
            <div className={styles.deepDiveConcept}>
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{topic.deepDive.concept}</ReactMarkdown>
            </div>
            {topic.deepDive.points?.length > 0 && (
              <ul className={styles.deepDivePoints}>
                {topic.deepDive.points.map((p, i) => <li key={i}>{p}</li>)}
              </ul>
            )}
            {topic.deepDive.angle && (
              <p className={styles.deepDiveAngle}><em>Interview angle:</em> {topic.deepDive.angle}</p>
            )}
          </div>
        </div>
      )}

      {/* Tabs */}
      <div className={styles.tabs}>
        {(['notes','resources','exercises','questions'] as Tab[]).map((t) => (
          <button
            key={t}
            className={`${styles.tab} ${tab === t ? styles.tabActive : ''}`}
            onClick={() => setTab(t)}
          >
            {t === 'notes'     && <IconBook size={14} />}
            {t === 'resources' && <IconLink size={14} />}
            {t === 'exercises' && <IconCode size={14} />}
            {t === 'questions' && <IconQuestionMark size={14} />}
            {t.charAt(0).toUpperCase() + t.slice(1)}
          </button>
        ))}
      </div>

      {/* Tab content */}
      <div className={styles.tabContent}>
        {tab === 'notes'     && <NotesTab slug={slug!} note={topic.note} />}
        {tab === 'resources' && <ResourcesTab slug={slug!} resources={topic.resources} />}
        {tab === 'exercises' && <ExercisesTab slug={slug!} exercises={topic.exercises} />}
        {tab === 'questions' && <QuestionsTab slug={slug!} questions={topic.questions} />}
      </div>
    </div>
  );
}

/* ── Notes tab ───────────────────────────────────────────────────────────── */
function NotesTab({ slug, note }: { slug: string; note: NonNullable<ReturnType<typeof useTopic>['data']>['note'] }) {
  const [md, setMd]           = useState(note?.contentMd ?? '');
  const [preview, setPreview] = useState(false);
  const [saving, setSaving]   = useState(false);

  const save = useCallback(async (content: string) => {
    setSaving(true);
    try { await api.put(`/topics/${slug}/note`, { contentMd: content }); }
    finally { setSaving(false); }
  }, [slug]);

  return (
    <div className={styles.notesWrap}>
      <div className={styles.notesToolbar}>
        <span className="caption">Markdown · autosaves</span>
        {saving && <Spinner size={12} />}
        <button
          className={`btn btn-sm btn-ghost ${preview ? 'btn-primary' : ''}`}
          onClick={() => setPreview(!preview)}
        >
          {preview ? 'Edit' : 'Preview'}
        </button>
      </div>
      {preview ? (
        <div className={styles.markdownPreview}>
          <ReactMarkdown remarkPlugins={[remarkGfm]}>{md}</ReactMarkdown>
        </div>
      ) : (
        <textarea
          className={styles.noteEditor}
          value={md}
          onChange={(e) => setMd(e.target.value)}
          onBlur={() => save(md)}
          placeholder="Write your study notes in Markdown…"
          spellCheck={false}
        />
      )}
    </div>
  );
}

/* ── Resources tab ───────────────────────────────────────────────────────── */
function ResourcesTab({ slug, resources }: { slug: string; resources: NonNullable<ReturnType<typeof useTopic>['data']>['resources'] }) {
  const [label,   setLabel]   = useState('');
  const [url,     setUrl]     = useState('');
  const [adding,  setAdding]  = useState(false);
  const [genLoad, setGenLoad] = useState(false);
  const [desktop, setDesktop] = useState<string | null>(null);

  const add = async () => {
    if (!label || !url) return;
    setAdding(true);
    await api.post(`/topics/${slug}/resources`, { label, url });
    queryClient.invalidateQueries({ queryKey: ['topic', slug] });
    setLabel(''); setUrl(''); setAdding(false);
  };

  const del = async (id: number) => {
    await api.delete(`/resources/${id}`);
    queryClient.invalidateQueries({ queryKey: ['topic', slug] });
  };

  const generate = async () => {
    setGenLoad(true);
    try {
      const res = await api.post<GuideResponse>('/ai/guide', { slug, tab: 'resources' });
      if (res.data.desktop_mode && res.data.desktop_prompt) {
        setDesktop(res.data.desktop_prompt);
      } else {
        queryClient.invalidateQueries({ queryKey: ['topic', slug] });
      }
    } finally { setGenLoad(false); }
  };

  return (
    <div className={styles.listWrap}>
      {desktop && (
        <DesktopPromptModal
          prompt={desktop}
          pasteLabel='Paste the JSON from your AI — e.g. {"resources":[{"label":"...","url":"..."}]}'
          expectJson
          onSubmit={async (text) => {
            const data = JSON.parse(text);
            for (const r of (data.resources ?? [])) {
              if (r.label && r.url) await api.post(`/topics/${slug}/resources`, { label: r.label, url: r.url });
            }
            setDesktop(null);
            queryClient.invalidateQueries({ queryKey: ['topic', slug] });
          }}
          onClose={() => setDesktop(null)}
        />
      )}
      {resources.map((r) => (
        <div key={r.id} className={styles.listRow}>
          <IconLink size={14} style={{ color: 'var(--accent)', flexShrink: 0 }} />
          <a href={r.url} target="_blank" rel="noopener noreferrer" className={styles.resourceLabel}>
            {r.label} <IconExternalLink size={11} />
          </a>
          <button className="btn btn-icon btn-danger btn-sm" onClick={() => del(r.id)}><IconTrash size={13}/></button>
        </div>
      ))}
      <div className={styles.addRow}>
        <input className={styles.input} placeholder="Label" value={label} onChange={(e) => setLabel(e.target.value)} />
        <input className={styles.input} placeholder="https://…" value={url} onChange={(e) => setUrl(e.target.value)} style={{ flex: 2 }} />
        <button className="btn btn-primary btn-sm" onClick={add} disabled={adding}>
          {adding ? <Spinner size={13} /> : <IconPlus size={13} />} Add
        </button>
      </div>
      <div className={styles.aiRow}>
        <button className="btn btn-sm btn-ghost" onClick={generate} disabled={genLoad}>
          {genLoad ? <Spinner size={13} /> : <IconSparkles size={13} />}
          Generate with AI
        </button>
      </div>
    </div>
  );
}

/* ── Exercises tab ───────────────────────────────────────────────────────── */
function ExercisesTab({ slug, exercises }: { slug: string; exercises: NonNullable<ReturnType<typeof useTopic>['data']>['exercises'] }) {
  const [genLoad, setGenLoad] = useState(false);
  const [desktop, setDesktop] = useState<string | null>(null);

  const toggle = async (id: number, done: boolean) => {
    await api.patch(`/exercises/${id}`, { done: !done });
    queryClient.invalidateQueries({ queryKey: ['topic', slug] });
  };

  const generate = async () => {
    setGenLoad(true);
    try {
      const res = await api.post<GuideResponse>('/ai/guide', { slug, tab: 'exercises' });
      if (res.data.desktop_mode && res.data.desktop_prompt) {
        setDesktop(res.data.desktop_prompt);
      } else {
        queryClient.invalidateQueries({ queryKey: ['topic', slug] });
      }
    } finally { setGenLoad(false); }
  };

  return (
    <div className={styles.listWrap}>
      {desktop && (
        <DesktopPromptModal
          prompt={desktop}
          pasteLabel='Paste the JSON from your AI — e.g. {"exercises":[{"title":"..."}]}'
          expectJson
          onSubmit={async (text) => {
            const data = JSON.parse(text);
            for (const e of (data.exercises ?? [])) {
              if (e.title) await api.post(`/topics/${slug}/exercises`, { title: e.title, repoUrl: e.repoUrl ?? '', done: false, displayOrder: 0 });
            }
            setDesktop(null);
            queryClient.invalidateQueries({ queryKey: ['topic', slug] });
          }}
          onClose={() => setDesktop(null)}
        />
      )}
      {exercises.map((ex) => (
        <div key={ex.id} className={styles.listRow}>
          <button onClick={() => toggle(ex.id, ex.done)} style={{ flexShrink: 0 }}>
            {ex.done
              ? <IconCircleCheck size={16} style={{ color: 'var(--green)' }} />
              : <IconCircle      size={16} style={{ color: 'var(--ink-4)' }} />}
          </button>
          <span className={ex.done ? styles.doneText : ''}>{ex.title}</span>
          {ex.repoUrl && (
            <a href={ex.repoUrl} target="_blank" rel="noopener noreferrer" className="btn btn-ghost btn-sm btn-icon" title="Open repo">
              <IconCode size={13} />
            </a>
          )}
        </div>
      ))}
      {exercises.length === 0 && (
        <p className="caption" style={{ padding: '16px 0' }}>No exercises yet — add one below or generate with AI.</p>
      )}
      <div className={styles.aiRow}>
        <button className="btn btn-sm btn-ghost" onClick={generate} disabled={genLoad}>
          {genLoad ? <Spinner size={13} /> : <IconSparkles size={13} />}
          Generate with AI
        </button>
      </div>
    </div>
  );
}

/* ── Questions tab ───────────────────────────────────────────────────────── */
function QuestionsTab({ slug, questions }: { slug: string; questions: NonNullable<ReturnType<typeof useTopic>['data']>['questions'] }) {
  const [text,    setText]    = useState('');
  const [genLoad, setGenLoad] = useState(false);
  const [desktop, setDesktop] = useState<string | null>(null);

  const add = async () => {
    if (!text.trim()) return;
    await api.post(`/topics/${slug}/questions`, { text });
    queryClient.invalidateQueries({ queryKey: ['topic', slug] });
    setText('');
  };

  const del = async (id: number) => {
    await api.delete(`/questions/${id}`);
    queryClient.invalidateQueries({ queryKey: ['topic', slug] });
  };

  const generate = async () => {
    setGenLoad(true);
    try {
      const res = await api.post<GuideResponse>('/ai/guide', { slug, tab: 'questions' });
      if (res.data.desktop_mode && res.data.desktop_prompt) {
        setDesktop(res.data.desktop_prompt);
      } else {
        queryClient.invalidateQueries({ queryKey: ['topic', slug] });
      }
    } finally { setGenLoad(false); }
  };

  return (
    <div className={styles.listWrap}>
      {desktop && (
        <DesktopPromptModal
          prompt={desktop}
          pasteLabel='Paste the JSON from your AI — e.g. {"questions":[{"text":"..."}]}'
          expectJson
          onSubmit={async (text) => {
            const data = JSON.parse(text);
            for (const q of (data.questions ?? [])) {
              if (q.text) await api.post(`/topics/${slug}/questions`, { text: q.text });
            }
            setDesktop(null);
            queryClient.invalidateQueries({ queryKey: ['topic', slug] });
          }}
          onClose={() => setDesktop(null)}
        />
      )}
      {questions.map((q) => (
        <div key={q.id} className={styles.listRow}>
          <IconQuestionMark size={14} style={{ color: 'var(--accent)', flexShrink: 0 }} />
          <span style={{ flex: 1 }}>{q.text}</span>
          <button className="btn btn-icon btn-danger btn-sm" onClick={() => del(q.id)}><IconTrash size={13}/></button>
        </div>
      ))}
      <div className={styles.addRow}>
        <input
          className={styles.input}
          placeholder="Add a study question…"
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && add()}
          style={{ flex: 1 }}
        />
        <button className="btn btn-primary btn-sm" onClick={add}>
          <IconPlus size={13} /> Add
        </button>
      </div>
      <div className={styles.aiRow}>
        <button className="btn btn-sm btn-ghost" onClick={generate} disabled={genLoad}>
          {genLoad ? <Spinner size={13} /> : <IconSparkles size={13} />}
          Generate with AI
        </button>
      </div>
    </div>
  );
}
