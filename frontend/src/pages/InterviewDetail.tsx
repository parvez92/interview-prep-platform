import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  IconArrowLeft, IconSparkles, IconPlus, IconAlertTriangle,
} from '@tabler/icons-react';
import { useInterview, useAddInterviewQuestion, useGeneratePrepPack, usePrepPack } from '@/hooks/useInterviews';
import { RatingDots } from '@/components/ui/RatingDots';
import { Spinner, PageSpinner } from '@/components/ui/Spinner';
import { Banner } from '@/components/ui/Banner';
import styles from './InterviewDetail.module.css';

export function InterviewDetail() {
  const { id }  = useParams<{ id: string }>();
  const numId   = Number(id);
  const { data, isLoading } = useInterview(numId);
  const { data: pack }      = usePrepPack(numId);
  const addQ    = useAddInterviewQuestion(numId);
  const genPack = useGeneratePrepPack(numId);

  const [qText,   setQText]   = useState('');
  const [qSlug,   setQSlug]   = useState('');
  const [qRating, setQRating] = useState(3);

  if (isLoading) return <PageSpinner />;
  if (!data)     return null;

  const handleAddQ = async () => {
    if (!qText.trim()) return;
    await addQ.mutateAsync({ text: qText, topicSlug: qSlug || undefined, selfRating: qRating });
    setQText(''); setQSlug(''); setQRating(3);
  };

  const weakTopics = data.weak ?? [];

  return (
    <div className={styles.page}>
      <Link to="/interviews" className={styles.back}>
        <IconArrowLeft size={15} /> Interviews
      </Link>

      {/* Header */}
      <div className={styles.header}>
        <div>
          <h1 className={styles.company}>{data.company}</h1>
          <p className={styles.role}>{data.role} · {data.round}</p>
        </div>
        <div className={styles.headerActions}>
          {data.jdText && (
            <button
              className="btn btn-primary"
              onClick={() => genPack.mutate()}
              disabled={genPack.isPending}
            >
              {genPack.isPending ? <Spinner size={14} /> : <IconSparkles size={14} />}
              Generate prep pack
            </button>
          )}
        </div>
      </div>

      {weakTopics.length > 0 && (
        <Banner
          variant="amber"
          message={`Weak topics sent back to plan: ${weakTopics.join(', ')}`}
          dismissible={false}
        />
      )}

      <div className={styles.body}>
        {/* Q&A log */}
        <div className={styles.section}>
          <h2 className="h3" style={{ marginBottom: 14 }}>Interview Q&A</h2>
          <p className="caption" style={{ marginBottom: 12 }}>Rate each answer honestly — ratings ≤ 2 will flag the topic for review.</p>

          {data.questions?.map((q) => (
            <div key={q.id} className={styles.qRow}>
              <div className={styles.qText}>{q.text}</div>
              <div className={styles.qMeta}>
                {q.topicSlug && <span className="badge badge-neutral">{q.topicSlug}</span>}
                <RatingDots value={q.selfRating} size={10} />
                {q.selfRating <= 2 && <IconAlertTriangle size={13} style={{ color: 'var(--amber)' }} title="Flagged for review" />}
              </div>
            </div>
          ))}

          {/* Add Q form */}
          <div className={styles.addQ}>
            <textarea
              className={styles.qInput}
              placeholder="Question that was asked…"
              value={qText}
              onChange={(e) => setQText(e.target.value)}
              rows={2}
            />
            <div className={styles.addQMeta}>
              <input
                className={styles.slugInput}
                placeholder="Topic slug (optional)"
                value={qSlug}
                onChange={(e) => setQSlug(e.target.value)}
              />
              <div className={styles.ratingRow}>
                <span className="caption">Self-rating</span>
                <RatingDots value={qRating} onChange={setQRating} />
              </div>
              <button className="btn btn-primary btn-sm" onClick={handleAddQ} disabled={addQ.isPending || !qText.trim()}>
                {addQ.isPending ? <Spinner size={13} /> : <IconPlus size={13} />} Log
              </button>
            </div>
          </div>
        </div>

        {/* Prep pack */}
        {pack && (
          <div className={styles.section}>
            <h2 className="h3" style={{ marginBottom: 14 }}>
              <IconSparkles size={15} style={{ verticalAlign: 'middle', color: 'var(--accent)' }} /> Prep pack
              <span className="caption" style={{ marginLeft: 8 }}>Generated {new Date(pack.generatedAt).toLocaleDateString()}</span>
            </h2>
            <p style={{ fontSize: '.875rem', color: 'var(--ink-2)', marginBottom: 12 }}>{pack.summary}</p>
            <div className={styles.packTopics}>
              {pack.topics.map((t) => (
                <Link key={t.topicSlug} to={`/study/${t.topicSlug}`} className={styles.packTopic}>
                  <strong>{t.topicSlug}</strong>
                  <span className="caption">{t.why}</span>
                </Link>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
