import { useState, useRef, useEffect } from 'react';
import { IconMicrophone2, IconSend, IconSparkles } from '@tabler/icons-react';
import api from '@/lib/api';
import { Spinner } from '@/components/ui/Spinner';
import { RatingDots } from '@/components/ui/RatingDots';
import { DesktopPromptModal } from '@/components/ui/DesktopPromptModal';
import styles from './MockInterviewer.module.css';

type SessionType = 'technical' | 'system-design' | 'behavioral';

interface Turn { role: 'ai' | 'user'; text: string; }

export function MockInterviewer() {
  const [sessionId,  setSessionId]  = useState<number | null>(null);
  const [turns,      setTurns]      = useState<Turn[]>([]);
  const [input,      setInput]      = useState('');
  const [loading,    setLoading]    = useState(false);
  const [done,       setDone]       = useState(false);
  const [score,      setScore]      = useState<number | null>(null);
  const [feedback,   setFeedback]   = useState<string>('');
  const [sessionType, setType]      = useState<SessionType>('technical');
  const [desktopPrompt, setDesktopPrompt] = useState<string | null>(null);
  const [desktopContext, setDesktopContext] = useState<'start' | 'turn'>('start');
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' });
  }, [turns]);

  const startSession = async () => {
    setLoading(true);
    try {
      const res = await api.post('/mocks/start', { type: sessionType });
      const { id, transcript } = res.data;
      setSessionId(id);
      setTurns(transcript ?? [{ role: 'ai', text: res.data.firstQuestion ?? '' }]);
      setDone(false); setScore(null); setFeedback('');
    } finally { setLoading(false); }
  };

  const sendTurn = async () => {
    if (!input.trim() || !sessionId || loading) return;
    const text = input;
    setInput('');
    setTurns((prev) => [...prev, { role: 'user', text }]);
    setLoading(true);
    try {
      const res = await api.post<{ desktop_mode?: boolean; desktop_prompt?: string; result?: { reply: string; done?: boolean; score?: number; feedback?: string } }>(`/mocks/${sessionId}/turn`, { answer: text });
      if (res.data.desktop_mode && res.data.desktop_prompt) {
        setDesktopContext('turn');
        setDesktopPrompt(res.data.desktop_prompt);
      } else {
        const { reply, done: isDone, score: s, feedback: fb } = res.data.result ?? (res.data as { reply: string; done?: boolean; score?: number; feedback?: string });
        setTurns((prev) => [...prev, { role: 'ai', text: reply }]);
        if (isDone) { setDone(true); setScore(s ?? null); setFeedback(fb ?? ''); }
      }
    } finally { setLoading(false); }
  };

  const desktopModal = desktopPrompt ? (
    <DesktopPromptModal
      prompt={desktopPrompt}
      pasteLabel={desktopContext === 'start' ? 'Paste the interviewer opening question' : 'Paste the interviewer reply'}
      expectJson={false}
      onSubmit={async (text) => {
        setTurns((prev) => [...prev, { role: 'ai', text }]);
        setDesktopPrompt(null);
      }}
      onClose={() => setDesktopPrompt(null)}
    />
  ) : null;

  if (!sessionId) {
    return (
      <>
        {desktopModal}
        <div className={styles.startScreen}>
        <div className={styles.startCard}>
          <IconMicrophone2 size={40} style={{ color: 'var(--accent)', marginBottom: 8 }} />
          <h1 className={styles.startTitle}>Mock interviewer</h1>
          <p className={styles.startSub}>
            An AI interviewer adapts to your weak topics. Low scores flag topics back to your study plan.
          </p>

          <div className={styles.typeGrid}>
            {(['technical', 'system-design', 'behavioral'] as SessionType[]).map((t) => (
              <button
                key={t}
                className={`${styles.typeBtn} ${sessionType === t ? styles.typeBtnActive : ''}`}
                onClick={() => setType(t)}
              >
                {t}
              </button>
            ))}
          </div>

          <button className="btn btn-primary" onClick={startSession} disabled={loading} style={{ width: '100%', justifyContent: 'center' }}>
            {loading ? <Spinner size={16} /> : <IconSparkles size={16} />}
            Start {sessionType} interview
          </button>
        </div>
      </div>
      </>
    );
  }

  return (
    <>
      {desktopModal}
    <div className={styles.chat}>
      <div className={styles.chatHeader}>
        <div>
          <span className={styles.chatTitle}>Mock {sessionType} interview</span>
          <span className="caption" style={{ marginLeft: 10 }}>Answer honestly — low self-rating flags topics for review</span>
        </div>
        <span className="badge badge-accent" style={{ gap: 5 }}>
          <IconSparkles size={12} /> AI
        </span>
      </div>

      <div className={styles.messages} ref={scrollRef}>
        {turns.map((t, i) => (
          <div key={i} className={`${styles.bubble} ${t.role === 'user' ? styles.bubbleUser : styles.bubbleAi}`}>
            {t.role === 'ai' && <span className={styles.aiBadge}>AI</span>}
            <p>{t.text}</p>
          </div>
        ))}
        {loading && (
          <div className={`${styles.bubble} ${styles.bubbleAi}`}>
            <span className={styles.aiBadge}>AI</span>
            <Spinner size={16} />
          </div>
        )}
      </div>

      {done ? (
        <ScoreCard score={score!} feedback={feedback} onRestart={() => { setSessionId(null); setTurns([]); }} />
      ) : (
        <div className={styles.inputRow}>
          <textarea
            className={styles.input}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Type your answer…"
            rows={3}
            onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendTurn(); } }}
          />
          <button className={`btn btn-primary ${styles.sendBtn}`} onClick={sendTurn} disabled={loading || !input.trim()}>
            {loading ? <Spinner size={18} /> : <IconSend size={18} />}
          </button>
        </div>
      )}
    </div>
    </>
  );
}

function ScoreCard({ score, feedback, onRestart }: { score: number; feedback: string; onRestart: () => void }) {
  const color = score >= 4 ? 'var(--green)' : score >= 3 ? 'var(--amber)' : 'var(--red)';
  return (
    <div className={styles.scoreCard}>
      <div className={styles.scoreNum} style={{ color }}>{score}/5</div>
      <RatingDots value={score} size={14} />
      <p className={styles.scoreFeedback}>{feedback}</p>
      <button className="btn btn-primary" onClick={onRestart}>Start new session</button>
    </div>
  );
}
