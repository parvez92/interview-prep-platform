import { useState, useCallback, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  IconUpload, IconCheck, IconTarget, IconClipboardList,
  IconChevronRight, IconChevronLeft, IconSparkles, IconX,
  IconBolt, IconDeviceDesktop, IconServer, IconPlus, IconRefresh,
} from '@tabler/icons-react';
import api from '@/lib/api';
import { queryClient } from '@/lib/queryClient';
import { Spinner } from '@/components/ui/Spinner';
import { AnimeThinking } from '@/components/ui/AnimeThinking';
import { DesktopPromptModal } from '@/components/ui/DesktopPromptModal';
import type { ResumeProfile, OnboardingTargets, Phase, Week, TopicLite, Source, Priority } from '@/types';
import styles from './Onboarding.module.css';

type ParseMode = 'api' | 'ollama' | 'desktop';

/** Convert AI plan JSON (weeks_detail / integer weeks) → frontend Phase[] */
function normalizePlan(raw: unknown): Phase[] {
  if (!Array.isArray(raw)) return [];
  return raw.map((p, pi) => {
    const phase = p as Record<string, unknown>;
    // AI JSON uses weeks_detail; frontend Phase type uses weeks (same array role)
    const rawWeeks = phase.weeks_detail ?? (Array.isArray(phase.weeks) ? phase.weeks : []);
    const weeks: Week[] = (rawWeeks as Record<string, unknown>[]).map((w, wi) => {
      // v2 plans emit coarse_topics; older shapes used topics
      const rawTopics = (w.coarse_topics ?? w.topics ?? []) as Record<string, unknown>[];
      const topics: TopicLite[] = rawTopics.map((t, ti) => ({
        slug:       slugify(String(t.title ?? 'topic')) + `-${pi}-${wi}-${ti}`,
        code:       `t-${pi}-${wi}-${ti}`,
        title:      String(t.title ?? 'Topic'),
        // tag is assigned server-side against the résumé; source comes from the plan pass
        tag:        'new'  as const,
        source:     isSource(t.source) ? t.source : 'standard',
        priority:   isPriority(t.priority) ? t.priority : 'high',
        status:     'todo' as const,
        confidence: null,
        resources_hint: typeof t.resources_hint === 'string' && t.resources_hint ? t.resources_hint : undefined,
        category: typeof t.category === 'string' && t.category ? t.category : undefined,
        scope: typeof t.scope === 'string' && t.scope ? t.scope : undefined,
        split_hint: typeof t.split_hint === 'string' && t.split_hint ? t.split_hint : undefined,
      }));
      return {
        code:   `w-${pi}-${wi}`,
        title:  String(w.title ?? `Week ${w.week_number ?? wi + 1}`),
        topics,
      };
    });
    const total = weeks.reduce((s, w) => s + w.topics.length, 0);
    return {
      code:     `phase-${pi + 1}`,
      name:     String(phase.name ?? `Phase ${pi + 1}`),
      icon:     String(phase.icon ?? ''),
      blurb:    String(phase.goal ?? phase.blurb ?? ''),
      progress: { done: 0, total },
      weeks,
    };
  });
}

function slugify(s: string): string {
  return s.toLowerCase().replace(/[^a-z0-9\s-]/g, '').trim().replace(/\s+/g, '-');
}

const SOURCES: Source[] = ['resume', 'standard', 'interest', 'custom'];
function isSource(v: unknown): v is Source {
  return typeof v === 'string' && (SOURCES as string[]).includes(v);
}

const PRIORITIES: Priority[] = ['high', 'medium', 'low'];
function isPriority(v: unknown): v is Priority {
  return typeof v === 'string' && (PRIORITIES as string[]).includes(v);
}

const PARSE_MESSAGES = [
  'Reading your résumé…',
  'Identifying skills and experience…',
  'Spotting strengths and gaps…',
  'Building your profile…',
  'Almost there…',
];
const PLAN_MESSAGES = [
  'Analysing your profile…',
  'Mapping skill gaps to topics…',
  'Designing your study phases…',
  'Laying out week-by-week tasks…',
  'Finalising your plan…',
];

function useAnimatedMessage(messages: string[], active: boolean, intervalMs = 3500): string {
  const [idx, setIdx] = useState(0);
  useEffect(() => {
    if (!active) { setIdx(0); return; }
    const t = setInterval(() => setIdx(i => (i + 1) % messages.length), intervalMs);
    return () => clearInterval(t);
  }, [active, messages, intervalMs]);
  return messages[idx];
}

function friendlyError(err: unknown): string {
  if (!err) return 'Something went wrong. Please try again.';
  // axios error with our error envelope
  const data = (err as { response?: { data?: { error?: { message?: string; code?: string } } } }).response?.data;
  if (data?.error?.message) return data.error.message;
  // plain message
  const msg = (err as { message?: string }).message ?? String(err);
  if (msg.includes('502') || msg.includes('Bad Gateway')) return 'The AI service is not responding. Check that all containers are running.';
  if (msg.includes('401')) return 'AI provider authentication failed. Check your API key in Settings.';
  if (msg.includes('Network') || msg.includes('network')) return 'Network error — check your connection and try again.';
  return 'Something went wrong. Please try again.';
}

const STEPS = [
  { icon: IconUpload,       label: 'Upload résumé'    },
  { icon: IconCheck,        label: 'Confirm profile'  },
  { icon: IconTarget,       label: 'Targets & budget' },
  { icon: IconClipboardList,label: 'Review plan'      },
];

export const ONBOARDING_KEY = 'prep_onboarding';

function readSaved(): {
  step: number;
  profile: ResumeProfile | null;
  targets: Partial<OnboardingTargets>;
  plan: Phase[] | null;
} | null {
  try {
    const raw = sessionStorage.getItem(ONBOARDING_KEY);
    if (!raw) return null;
    const s = JSON.parse(raw);
    if (typeof s?.step !== 'number' || s.step < 0 || s.step > 3) return null;
    // Validate consistency: step 1+ requires a profile, step 3 requires a plan
    if (s.step >= 1 && !s.profile) return null;
    if (s.step === 3 && !s.plan) return { ...s, step: 2 };
    return s;
  } catch { return null; }
}

export function Onboarding() {
  const [step,    setStep]    = useState<number>(() => {
    // Settings → "Regenerate plan" deep-links past upload/confirm; the server
    // already holds the confirmed profile, which is all plan generation needs.
    if (new URLSearchParams(window.location.search).get('start') === 'targets') return 2;
    return readSaved()?.step ?? 0;
  });
  const [profile, setProfile] = useState<ResumeProfile | null>(() => readSaved()?.profile ?? null);
  const [targets, setTargets] = useState<Partial<OnboardingTargets>>(() => readSaved()?.targets ?? {});
  const [plan,    setPlan]    = useState<Phase[] | null>(() => readSaved()?.plan ?? null);
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState<string | null>(null);
  const [desktopPrompt, setDesktopPrompt] = useState<string | null>(null);
  const [seedPrompt, setSeedPrompt] = useState<string | null>(null);
  // bumped per generation so PlanStep remounts with the fresh plan (it holds local edit state)
  const [planVersion, setPlanVersion] = useState(0);
  const nav = useNavigate();

  const finishOnboarding = () => {
    sessionStorage.removeItem(ONBOARDING_KEY);
    queryClient.removeQueries({ queryKey: ['me'] });
    nav('/');
  };

  useEffect(() => {
    try {
      sessionStorage.setItem(ONBOARDING_KEY, JSON.stringify({ step, profile, targets, plan }));
    } catch { /* storage full or disabled — silently skip */ }
  }, [step, profile, targets, plan]);

  const generatePlan = async (regenerate: boolean) => {
    setLoading(true); setError(null);
    try {
      const res = await api.post<{ phases?: Phase[]; desktop_mode?: boolean; desktop_prompt?: string }>(
        '/onboarding/plan', { ...targets, regenerate });
      if (res.data.desktop_mode && res.data.desktop_prompt) {
        setDesktopPrompt(res.data.desktop_prompt);
      } else {
        const phases = normalizePlan(res.data.phases ?? []);
        if (phases.length === 0) { setError('Plan came back empty — try again.'); return; }
        setPlan(phases);
        setPlanVersion(v => v + 1);
        setStep(3);
      }
    } catch (e: unknown) {
      setError(friendlyError(e));
    } finally { setLoading(false); }
  };

  return (
    <div className={styles.page}>
      {/* Stepper */}
      <div className={styles.stepper}>
        {STEPS.map(({ icon: Icon, label }, i) => (
          <div key={i} className={styles.stepItem}>
            <div className={`${styles.stepDot} ${i < step ? styles.stepDone : i === step ? styles.stepActive : ''}`}>
              {i < step ? <IconCheck size={14} /> : <Icon size={14} />}
            </div>
            <span className={`${styles.stepLabel} ${i === step ? styles.stepLabelActive : ''}`}>{label}</span>
            {i < STEPS.length - 1 && <div className={`${styles.stepLine} ${i < step ? styles.stepLineDone : ''}`} />}
          </div>
        ))}
      </div>

      {error && (
        <div style={{ maxWidth: 680, width: '100%', padding: '10px 16px', background: 'var(--red-bg, #fef2f2)', border: '1px solid var(--red, #ef4444)', borderRadius: 'var(--radius)', color: 'var(--red, #ef4444)', fontSize: '.875rem' }}>
          {error}
        </div>
      )}

      {/* Step content */}
      <div className={styles.card}>
        {step === 0 && (
          <UploadStep
            onNext={async (file, mode, ollamaUrl, ollamaModel) => {
              setLoading(true); setError(null);
              try {
                const fd = new FormData();
                fd.append('file', file);
                const headers: Record<string, string> = {};
                if (mode === 'ollama') {
                  headers['X-Override-Provider'] = 'ollama';
                  if (ollamaUrl)   headers['X-Ollama-Url']   = ollamaUrl;
                  if (ollamaModel) headers['X-Ollama-Model'] = ollamaModel;
                  setTargets(t => ({ ...t, llmProvider: 'ollama', ollamaUrl: ollamaUrl ?? '', ollamaModel: ollamaModel ?? '' }));
                }
                const res = await api.post<{ result: ResumeProfile }>('/onboarding/resume', fd, { headers });
                setProfile(res.data.result ?? res.data as unknown as ResumeProfile);
                setStep(1);
              } catch (e: unknown) {
                setError(friendlyError(e));
              } finally { setLoading(false); }
            }}
            onNextManual={async (file, parsedJson) => {
              setLoading(true); setError(null);
              try {
                const fd = new FormData();
                fd.append('file', file);
                fd.append('parsedJson', parsedJson);
                const res = await api.post<{ result: ResumeProfile }>('/onboarding/resume/manual', fd);
                setProfile(res.data.result);
                setStep(1);
              } catch (e: unknown) {
                setError(friendlyError(e));
              } finally { setLoading(false); }
            }}
            loading={loading}
          />
        )}
        {step === 1 && profile && (
          <ProfileStep
            profile={profile}
            onChange={setProfile}
            onBack={() => setStep(0)}
            onNext={async () => {
              setLoading(true);
              try {
                await api.put('/onboarding/profile', { profile });
                setStep(2);
              } finally { setLoading(false); }
            }}
            loading={loading}
          />
        )}
        {step === 2 && (
          <TargetsStep
            targets={targets}
            onChange={setTargets}
            onBack={() => setStep(profile ? 1 : 0)}
            onNext={() => generatePlan(false)}
            loading={loading}
          />
        )}
        {seedPrompt && (
          <DesktopPromptModal
            prompt={seedPrompt}
            pasteLabel='Paste the JSON — {"topics":[{"slug":"...","resources":[...],"exercises":[...],"questions":[...]}]}. Or close to skip; each topic has its own Generate button.'
            expectJson
            onSubmit={async (json) => {
              await api.post('/ai/seed-plan/manual', JSON.parse(json));
              setSeedPrompt(null);
              finishOnboarding();
            }}
            onClose={() => { setSeedPrompt(null); finishOnboarding(); }}
          />
        )}
        {desktopPrompt && (
          <DesktopPromptModal
            prompt={desktopPrompt}
            pasteLabel='Paste the plan JSON from your desktop AI'
            expectJson
            onSubmit={async (json) => {
              const parsed = JSON.parse(json);
              const res = await api.post<{ phases: unknown[] }>('/onboarding/plan/manual', parsed);
              const phases = normalizePlan(res.data.phases ?? parsed.phases ?? []);
              if (phases.length === 0) throw new Error('No phases found in the pasted JSON. Make sure it contains a "phases" key.');
              setPlan(phases);
              setDesktopPrompt(null);
              setStep(3);
            }}
            onClose={() => setDesktopPrompt(null)}
          />
        )}
        {step === 3 && plan && (
          <PlanStep
            key={planVersion}
            plan={plan}
            onRegenerate={() => generatePlan(true)}
            onBack={() => setStep(2)}
            onCommit={async (editedPlan, seedViaDesktop) => {
              setLoading(true);
              try {
                await api.put('/onboarding/plan/commit', { phases: editedPlan });
                // Seed resources/exercises/questions for all topics. Non-fatal —
                // per-tab "Generate with AI" works as fallback.
                const seedPromise = api.post<{ desktop_mode?: boolean; desktop_prompt?: string }>(
                  `/ai/seed-plan${seedViaDesktop ? '?provider=desktop' : ''}`);
                // Desktop payloads return instantly; a real LLM seed takes many minutes —
                // don't block the commit on it, let it finish in the background.
                const fast = await Promise.race([
                  seedPromise,
                  new Promise<null>((r) => setTimeout(() => r(null), 3000)),
                ]);
                if (fast && fast.data.desktop_mode && fast.data.desktop_prompt) {
                  setSeedPrompt(fast.data.desktop_prompt);
                  return;
                }
                seedPromise.catch(() => { /* background seeding is best-effort */ });
                finishOnboarding();
              } finally { setLoading(false); }
            }}
            loading={loading}
          />
        )}
      </div>
    </div>
  );
}

/* ── Step 0: Upload ───────────────────────────────────────────────────────── */

const DESKTOP_PROMPT_TEMPLATE = (resumeText: string) => `Parse the following resume and return ONLY valid JSON — no explanation, no markdown fences.

Resume:
${resumeText}

Required JSON format (fill every field):
{
  "result": {
    "seniority": "Senior",
    "totalYears": 5,
    "skills": [
      {"name": "Python", "level": "advanced", "years": 4},
      {"name": "React", "level": "intermediate", "years": 2}
    ],
    "domains": ["backend", "cloud"],
    "gaps": ["Kubernetes", "Go"],
    "experiences": [
      {"company": "Acme Corp", "role": "Software Engineer", "dates": "2020–2023", "highlights": ["Built X", "Led Y"]}
    ],
    "confirmed": false
  }
}`;

function UploadStep({
  onNext,
  onNextManual,
  loading,
}: {
  onNext: (f: File, mode: ParseMode, ollamaUrl?: string, ollamaModel?: string) => void;
  onNextManual: (f: File, parsedJson: string) => void;
  loading: boolean;
}) {
  const [file,        setFile]        = useState<File | null>(null);
  const [drag,        setDrag]        = useState(false);
  const [mode,        setMode]        = useState<ParseMode>('api');
  const [ollamaUrl,   setOllamaUrl]   = useState('http://host.docker.internal:11434');
  const [ollamaModel, setOllamaModel] = useState('');
  const [rawText,     setRawText]     = useState('');
  const [pasteJson,   setPasteJson]   = useState('');
  const [pasteError,  setPasteError]  = useState('');
  const [showPrompt,  setShowPrompt]  = useState(false);
  const isOllama = mode === 'ollama';
  const thinkingMsg = useAnimatedMessage(PARSE_MESSAGES, loading);

  const onDrop = useCallback((e: React.DragEvent) => {
    e.preventDefault(); setDrag(false);
    const f = e.dataTransfer.files[0];
    if (f) { setFile(f); setShowPrompt(false); setPasteJson(''); setPasteError(''); }
  }, []);

  const handleFileChange = (f: File) => {
    setFile(f); setShowPrompt(false); setPasteJson(''); setPasteError('');
  };

  // Read the file as text for the desktop prompt preview
  const readFileText = useCallback(async (f: File): Promise<string> => {
    return new Promise((res) => {
      const reader = new FileReader();
      reader.onload = () => res((reader.result as string).slice(0, 8000)); // truncate for prompt display
      reader.readAsText(f);
    });
  }, []);

  const handleDesktopPrepare = async () => {
    if (!file) return;
    const text = await readFileText(file);
    setRawText(text);
    setShowPrompt(true);
  };

  const handlePasteSubmit = () => {
    if (!file) return;
    setPasteError('');
    let parsed: unknown;
    try {
      parsed = JSON.parse(pasteJson);
    } catch {
      setPasteError('Invalid JSON — paste the raw JSON exactly as the AI returned it.');
      return;
    }
    // Allow the AI to return {"result": {...}} or just {...}
    const data = (parsed as Record<string, unknown>).result ?? parsed;
    onNextManual(file, JSON.stringify(data));
  };

  return (
    <div className={styles.stepContent}>
      <h2 className={styles.stepTitle}>Upload your résumé</h2>
      <p className={styles.stepSub}>We'll parse it with AI to tailor your study plan. PDF or plain text.</p>

      {/* Provider mode toggle */}
      <div className={styles.modeRow}>
        {([
          { key: 'api',     icon: IconBolt,          label: 'API',     desc: 'Anthropic / OpenAI / Gemini' },
          { key: 'ollama',  icon: IconServer,         label: 'Ollama',  desc: 'Local model (no API cost)' },
          { key: 'desktop', icon: IconDeviceDesktop,  label: 'Desktop', desc: 'Claude / ChatGPT / Gemini app' },
        ] as const).map(({ key, icon: Icon, label, desc }) => (
          <button
            key={key}
            className={`${styles.modeBtn} ${mode === key ? styles.modeBtnActive : ''}`}
            onClick={() => { setMode(key); setShowPrompt(false); }}
          >
            <Icon size={16} />
            <span className={styles.modeBtnLabel}>{label}</span>
            <span className={styles.modeBtnDesc}>{desc}</span>
          </button>
        ))}
      </div>

      {mode === 'ollama' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <div className={styles.ollamaRow}>
            <label className="label" style={{ minWidth: 80 }}>Ollama URL</label>
            <input
              className="input-field"
              style={{ flex: 1 }}
              value={ollamaUrl}
              onChange={(e) => setOllamaUrl(e.target.value)}
              placeholder="http://host.docker.internal:11434"
            />
          </div>
          <div className={styles.ollamaRow}>
            <label className="label" style={{ minWidth: 80 }}>Model name</label>
            <input
              className="input-field"
              style={{ flex: 1 }}
              value={ollamaModel}
              onChange={(e) => setOllamaModel(e.target.value)}
              placeholder="e.g. qwen3.6:27b, llama3.2, mistral"
            />
          </div>
        </div>
      )}

      {mode === 'desktop' && !showPrompt && (
        <div className={styles.desktopNote}>
          <p>Your resume text will be shown as a ready-made prompt. Paste it into Claude Desktop, ChatGPT, or Gemini,
            then paste the JSON response back here.</p>
        </div>
      )}

      {/* Drop zone */}
      <div
        className={`${styles.dropZone} ${drag ? styles.dropZoneActive : ''} ${file ? styles.dropZoneHasFile : ''}`}
        onDragOver={(e) => { e.preventDefault(); setDrag(true); }}
        onDragLeave={() => setDrag(false)}
        onDrop={onDrop}
        onClick={() => document.getElementById('file-input')?.click()}
      >
        <input
          id="file-input"
          type="file"
          accept=".pdf,.txt,.docx"
          hidden
          onChange={(e) => { const f = e.target.files?.[0]; if (f) handleFileChange(f); }}
        />
        {file ? (
          <>
            <IconCheck size={28} style={{ color: 'var(--green)' }} />
            <span className={styles.fileName}>{file.name}</span>
            <button className="btn btn-ghost btn-sm" onClick={(e) => { e.stopPropagation(); setFile(null); setShowPrompt(false); }}>
              <IconX size={12} /> Remove
            </button>
          </>
        ) : (
          <>
            <IconUpload size={28} style={{ color: 'var(--accent)', opacity: .7 }} />
            <span>Drop PDF / TXT here, or click to browse</span>
          </>
        )}
      </div>

      {/* Desktop paste-back flow */}
      {mode === 'desktop' && file && showPrompt && (
        <div className={styles.desktopFlow}>
          <p className="label" style={{ marginBottom: 6 }}>
            1. Copy this prompt and run it in your desktop AI app:
          </p>
          <textarea
            className={styles.promptBox}
            readOnly
            value={DESKTOP_PROMPT_TEMPLATE(rawText)}
            rows={8}
            onClick={(e) => (e.target as HTMLTextAreaElement).select()}
          />
          <button
            className="btn btn-ghost btn-sm"
            style={{ alignSelf: 'flex-start' }}
            onClick={() => navigator.clipboard.writeText(DESKTOP_PROMPT_TEMPLATE(rawText))}
          >
            Copy prompt
          </button>

          <p className="label" style={{ marginTop: 16, marginBottom: 6 }}>
            2. Paste the JSON the AI returned:
          </p>
          <textarea
            className={styles.promptBox}
            value={pasteJson}
            onChange={(e) => { setPasteJson(e.target.value); setPasteError(''); }}
            placeholder='{"result": {"seniority": "Senior", ...}}'
            rows={6}
          />
          {pasteError && <p style={{ color: 'var(--red)', fontSize: 13 }}>{pasteError}</p>}
        </div>
      )}

      {loading && (
        <div style={{
          display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8,
          padding: '20px 20px 16px', borderRadius: 'var(--radius)',
          background: 'var(--surface-2, #1e2030)', border: '1px solid var(--border, #2e3248)',
        }}>
          <AnimeThinking />
          <p style={{ margin: 0, fontWeight: 500, fontSize: '1rem' }}>{thinkingMsg}</p>
          {isOllama && (
            <p style={{ margin: 0, fontSize: '.8rem', opacity: .55, textAlign: 'center' }}>
              Running on your local model — this can take a few minutes depending on model size.
            </p>
          )}
        </div>
      )}

      {!loading && (
        <div className={styles.nav}>
          <span />
          {mode === 'desktop' && file && !showPrompt && (
            <button className="btn btn-primary" onClick={handleDesktopPrepare}>
              <IconDeviceDesktop size={15} /> Generate prompt
            </button>
          )}
          {mode === 'desktop' && file && showPrompt && (
            <button className="btn btn-primary" onClick={handlePasteSubmit} disabled={!pasteJson.trim()}>
              <IconCheck size={15} /> Use this result
              <IconChevronRight size={15} />
            </button>
          )}
          {mode !== 'desktop' && (
            <button
              className="btn btn-primary"
              onClick={() => file && onNext(file, mode, isOllama ? ollamaUrl : undefined, isOllama ? ollamaModel : undefined)}
              disabled={!file || (isOllama && !ollamaModel.trim())}
            >
              <IconSparkles size={15} /> Parse with {isOllama ? 'Ollama' : 'API'}
              <IconChevronRight size={15} />
            </button>
          )}
        </div>
      )}
    </div>
  );
}

/* ── Step 1: Profile ──────────────────────────────────────────────────────── */
function ProfileStep({ profile, onChange, onBack, onNext, loading }: {
  profile:  ResumeProfile;
  onChange: (p: ResumeProfile) => void;
  onBack:   () => void;
  onNext:   () => void;
  loading:  boolean;
}) {
  return (
    <div className={styles.stepContent}>
      <h2 className={styles.stepTitle}>Confirm your profile</h2>
      <p className={styles.stepSub}>Parsing is lossy — correct anything that's wrong.</p>

      <div className={styles.profileGrid}>
        <div className={styles.profileMeta}>
          <label className={styles.field}>
            <span className="label">Seniority</span>
            <input className="input-field" value={profile.seniority} onChange={(e) => onChange({ ...profile, seniority: e.target.value })} />
          </label>
          <label className={styles.field}>
            <span className="label">Total years</span>
            <input className="input-field" type="number" value={profile.totalYears} onChange={(e) => onChange({ ...profile, totalYears: Number(e.target.value) })} />
          </label>
        </div>

        <div>
          <p className="label" style={{ color: 'var(--ink-3)', marginBottom: 8 }}>Skills detected</p>
          <div className={styles.chipList}>
            {profile.skills.map((s, i) => (
              <span key={i} className="badge badge-neutral">{s.name} · {s.level}</span>
            ))}
          </div>
        </div>

        {profile.gaps.length > 0 && (
          <div>
            <p className="label" style={{ color: 'var(--amber)', marginBottom: 8 }}>Gaps identified</p>
            <div className={styles.chipList}>
              {profile.gaps.map((g, i) => <span key={i} className="badge badge-amber">{g}</span>)}
            </div>
          </div>
        )}

        <div>
          <p className="label" style={{ color: 'var(--ink-3)', marginBottom: 8 }}>Experience</p>
          {profile.experiences.map((ex, i) => (
            <div key={i} className={styles.expRow}>
              <strong>{ex.company}</strong> — {ex.role} <span className="caption">{ex.dates}</span>
            </div>
          ))}
        </div>
      </div>

      <div className={styles.nav}>
        <button className="btn btn-ghost" onClick={onBack}><IconChevronLeft size={15} /> Back</button>
        <button className="btn btn-primary" onClick={onNext} disabled={loading}>
          {loading ? <Spinner size={15} /> : null} Confirm <IconChevronRight size={15} />
        </button>
      </div>
    </div>
  );
}

/* ── Step 2: Targets ──────────────────────────────────────────────────────── */
const INTEREST_OPTIONS = ['Agentic AI', 'System design', 'ML infra', 'Security', 'Frontend', 'Data eng', 'Cloud'];

function TargetsStep({ targets, onChange, onBack, onNext, loading }: {
  targets:  Partial<OnboardingTargets>;
  onChange: (t: Partial<OnboardingTargets>) => void;
  onBack:   () => void;
  onNext:   () => void;
  loading:  boolean;
}) {
  const set = (k: keyof OnboardingTargets, v: unknown) => onChange({ ...targets, [k]: v });
  const toggleInterest = (i: string) => {
    const cur = targets.interests ?? [];
    set('interests', cur.includes(i) ? cur.filter((x) => x !== i) : [...cur, i]);
  };
  const isOllama = targets.llmProvider === 'ollama';
  const thinkingMsg = useAnimatedMessage(PLAN_MESSAGES, loading);

  return (
    <div className={styles.stepContent}>
      <h2 className={styles.stepTitle}>Targets & budget</h2>
      <p className={styles.stepSub}>We'll generate a custom plan based on these goals.</p>

      <div className={styles.formGrid}>
        <label className={styles.field}>
          <span className="label">Target role</span>
          <input className="input-field" value={targets.targetRole ?? ''} onChange={(e) => set('targetRole', e.target.value)} placeholder="Software Engineer" />
        </label>
        <label className={styles.field}>
          <span className="label">Target level</span>
          <input className="input-field" value={targets.targetLevel ?? ''} onChange={(e) => set('targetLevel', e.target.value)} placeholder="Senior / L5 / Staff" />
        </label>
        <label className={styles.field}>
          <span className="label">Prep weeks</span>
          <input className="input-field" type="number" min={1} max={52} value={targets.weeks ?? 12} onChange={(e) => set('weeks', Number(e.target.value))} />
        </label>
        <label className={styles.field}>
          <span className="label">Hours / week</span>
          <input className="input-field" type="number" min={1} max={40} value={targets.hoursPerWeek ?? 10} onChange={(e) => set('hoursPerWeek', Number(e.target.value))} />
        </label>
        <label className={styles.field}>
          <span className="label">Monthly AI budget (USD)</span>
          <input className="input-field" type="number" min={0} step={5} value={targets.monthlyBudgetUsd ?? 20} onChange={(e) => set('monthlyBudgetUsd', Number(e.target.value))} />
        </label>
        <label className={styles.field}>
          <span className="label">AI provider</span>
          <select className="input-field" value={targets.llmProvider ?? 'anthropic'} onChange={(e) => set('llmProvider', e.target.value)}>
            {['anthropic', 'openai', 'ollama', 'bedrock', 'desktop'].map((p) => <option key={p} value={p}>{p}</option>)}
          </select>
        </label>
        {(targets.llmProvider === 'anthropic' || targets.llmProvider === 'openai') && (
          <div className={styles.field} style={{ gridColumn: '1 / -1' }}>
            <span className="label" style={{ color: 'var(--ink-3)' }}>
              API key is read from Vault — set it with: <code>docker exec vault vault kv put secret/ai-service anthropic.api_key=sk-ant-…</code>
            </span>
          </div>
        )}
        {targets.llmProvider === 'ollama' && (
          <>
            <label className={styles.field}>
              <span className="label">Ollama URL</span>
              <input
                className="input-field"
                value={targets.ollamaUrl ?? 'http://host.docker.internal:11434'}
                onChange={(e) => set('ollamaUrl', e.target.value)}
                placeholder="http://host.docker.internal:11434"
              />
            </label>
            <label className={styles.field}>
              <span className="label">Ollama model</span>
              <input
                className="input-field"
                value={targets.ollamaModel ?? ''}
                onChange={(e) => set('ollamaModel', e.target.value)}
                placeholder="e.g. qwen3.6:27b, llama3.2, mistral"
              />
            </label>
          </>
        )}
      </div>

      <div>
        <p className="label" style={{ color: 'var(--ink-3)', marginBottom: 8 }}>Interest add-ons</p>
        <div className={styles.chipList}>
          {INTEREST_OPTIONS.map((i) => (
            <button
              key={i}
              className={`badge ${(targets.interests ?? []).includes(i) ? 'badge-accent' : 'badge-neutral'}`}
              style={{ cursor: 'pointer', padding: '5px 10px' }}
              onClick={() => toggleInterest(i)}
            >
              {i}
            </button>
          ))}
        </div>
      </div>

      <label className={styles.field}>
        <span className="label">
          Additional context <span style={{ color: 'var(--ink-3)', fontWeight: 400 }}>(optional)</span>
        </span>
        <textarea
          className="input-field"
          rows={3}
          placeholder="e.g. Focus heavily on dynamic programming, I'm weak at system design, targeting FAANG L5…"
          value={targets.additionalContext ?? ''}
          onChange={(e) => set('additionalContext', e.target.value)}
          style={{ resize: 'vertical', fontFamily: 'inherit', lineHeight: 1.5 }}
        />
      </label>

      {loading && (
        <div style={{
          display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8,
          padding: '20px 20px 16px', borderRadius: 'var(--radius)',
          background: 'var(--surface-2, #1e2030)', border: '1px solid var(--border, #2e3248)',
        }}>
          <AnimeThinking />
          <p style={{ margin: 0, fontWeight: 500, fontSize: '1rem' }}>{thinkingMsg}</p>
          {isOllama && (
            <p style={{ margin: 0, fontSize: '.8rem', opacity: .55, textAlign: 'center' }}>
              Running on your local model — this can take a few minutes.
            </p>
          )}
        </div>
      )}

      {!loading && (
        <div className={styles.nav}>
          <button className="btn btn-ghost" onClick={onBack}><IconChevronLeft size={15} /> Back</button>
          <button className="btn btn-primary" onClick={onNext} disabled={!targets.targetRole}>
            <IconSparkles size={15} /> Generate plan
            <IconChevronRight size={15} />
          </button>
        </div>
      )}
    </div>
  );
}

/* ── Step 3: Plan preview ─────────────────────────────────────────────────── */

interface Suggestion {
  id: string; label: string; skill: string; category: string;
  phaseIndex: number; weekIndex: number; weekTitle: string;
}
interface SkillCoverage { skill: string; category: string; coveragePct: number; ready: boolean }
interface WeekLoad {
  phaseIndex: number; weekIndex: number; weekTitle: string;
  plannedMinutes: number; budgetMinutes: number; estimated: boolean; over: boolean;
}
interface PlanAudit {
  coverage: SkillCoverage[]; suggestions: Suggestion[];
  weeks: WeekLoad[]; budgetMinutes: number; prepWeeks: number;
}

function PlanStep({ plan: initialPlan, onBack, onCommit, onRegenerate, loading }: {
  plan:         Phase[];
  onBack:       () => void;
  onCommit:     (plan: Phase[], seedViaDesktop: boolean) => void;
  onRegenerate: () => void;
  loading:      boolean;
}) {
  const [plan, setPlan] = useState<Phase[]>(initialPlan);
  const [inputs, setInputs] = useState<Record<string, string>>({});
  const [seedViaDesktop, setSeedViaDesktop] = useState(false);
  const [audit, setAudit] = useState<PlanAudit | null>(null);
  const [dismissed, setDismissed] = useState<Set<string>>(new Set());

  // Coverage + feasibility are deterministic and free — re-audit whenever the plan changes.
  useEffect(() => {
    let stale = false;
    api.post<PlanAudit>('/plan/audit', { phases: plan })
      .then(res => { if (!stale) setAudit(res.data); })
      .catch(() => { if (!stale) setAudit(null); });
    return () => { stale = true; };
  }, [plan]);

  /** Accepting a suggestion inserts the coarse topic; the seed pipeline deepens it on commit. */
  const acceptSuggestion = (s: Suggestion) => {
    const newTopic: TopicLite = {
      slug:       slugify(s.label) + `-c${Date.now()}`,
      code:       `t-c${Date.now()}`,
      title:      s.label,
      tag:        'new',
      source:     'standard',
      priority:   'high',
      status:     'todo',
      confidence: null,
      category:   s.category,
      scope:      `Checklist gap for ${s.skill}: ${s.label}. Cover at senior interview depth.`,
      split_hint: 'maybe',
    };
    setPlan(prev => prev.map((phase, p) => {
      if (p !== s.phaseIndex) return phase;
      const weeks = phase.weeks.map((week, w) =>
        w === s.weekIndex ? { ...week, topics: [...week.topics, newTopic] } : week);
      const total = weeks.reduce((acc, wk) => acc + wk.topics.length, 0);
      return { ...phase, weeks, progress: { ...phase.progress, total } };
    }));
    setDismissed(prev => new Set(prev).add(s.id));
  };

  const removeTopic = (pi: number, wi: number, ti: number) => {
    setPlan(prev => prev.map((phase, p) => {
      if (p !== pi) return phase;
      const weeks = phase.weeks.map((week, w) => {
        if (w !== wi) return week;
        const topics = week.topics.filter((_, t) => t !== ti);
        return { ...week, topics };
      });
      const total = weeks.reduce((s, wk) => s + wk.topics.length, 0);
      return { ...phase, weeks, progress: { ...phase.progress, total } };
    }));
  };

  const addTopic = (pi: number, wi: number) => {
    const key = `${pi}-${wi}`;
    const title = (inputs[key] ?? '').trim();
    if (!title) return;
    const newTopic: TopicLite = {
      slug:       slugify(title) + `-c${Date.now()}`,
      code:       `t-c${Date.now()}`,
      title,
      tag:        'new',
      source:     'custom',
      priority:   'high',
      status:     'todo',
      confidence: null,
    };
    setPlan(prev => prev.map((phase, p) => {
      if (p !== pi) return phase;
      const weeks = phase.weeks.map((week, w) => {
        if (w !== wi) return week;
        return { ...week, topics: [...week.topics, newTopic] };
      });
      const total = weeks.reduce((s, wk) => s + wk.topics.length, 0);
      return { ...phase, weeks, progress: { ...phase.progress, total } };
    }));
    setInputs(prev => ({ ...prev, [key]: '' }));
  };

  const totalTopics = plan.reduce((s, p) => s + p.progress.total, 0);
  const totalWeeks  = plan.reduce((s, p) => s + p.weeks.length, 0);

  return (
    <div className={styles.stepContent}>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
        <div>
          <h2 className={styles.stepTitle}>Review your plan</h2>
          <p className={styles.stepSub}>
            {plan.length} phases · {totalWeeks} weeks · {totalTopics} topics —
            remove unwanted topics or add your own before committing.
          </p>
        </div>
        <button
          className="btn btn-sm btn-ghost"
          onClick={onRegenerate}
          disabled={loading}
          title="Discard this plan and generate a fresh one from your profile"
          style={{ flexShrink: 0, marginTop: 4 }}
        >
          {loading ? <Spinner size={13} /> : <IconRefresh size={13} />} Regenerate
        </button>
      </div>

      <CoveragePanel
        audit={audit}
        dismissed={dismissed}
        onAccept={acceptSuggestion}
        onDismiss={id => setDismissed(prev => new Set(prev).add(id))}
      />

      <div className={styles.planList}>
        {plan.map((phase, pi) => (
          <div key={phase.code} className={styles.planPhase}>
            <div className={styles.planPhaseHeader}>
              <span>{phase.icon}</span>
              <span className={styles.planPhaseName}>{phase.name}</span>
              <span className="badge badge-neutral">{phase.progress.total} topics</span>
            </div>
            <div className={styles.planWeeks}>
              {phase.weeks.map((week, wi) => {
                const key = `${pi}-${wi}`;
                return (
                  <div key={week.code} className={styles.planWeek}>
                    <span className={styles.planWeekTitle}>{week.title}</span>
                    <div className={styles.chipList}>
                      {week.topics.map((t, ti) => (
                        <span key={t.slug + ti} className="badge badge-neutral" style={{ display: 'inline-flex', alignItems: 'center', gap: 3 }}>
                          {t.title}
                          <button
                            onClick={() => removeTopic(pi, wi, ti)}
                            style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '0 1px', lineHeight: 1, color: 'inherit', opacity: .55, display: 'flex' }}
                            title="Remove"
                          >
                            <IconX size={10} />
                          </button>
                        </span>
                      ))}
                    </div>
                    <div className={styles.addTopicRow}>
                      <input
                        className={styles.addTopicInput}
                        placeholder="Add a topic…"
                        value={inputs[key] ?? ''}
                        onChange={e => setInputs(prev => ({ ...prev, [key]: e.target.value }))}
                        onKeyDown={e => e.key === 'Enter' && addTopic(pi, wi)}
                      />
                      <button
                        className="btn btn-sm btn-ghost"
                        onClick={() => addTopic(pi, wi)}
                        disabled={!(inputs[key] ?? '').trim()}
                      >
                        <IconPlus size={13} /> Add
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        ))}
      </div>

      <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: '.8125rem', color: 'var(--ink-2)', cursor: 'pointer' }}>
        <input
          type="checkbox"
          checked={seedViaDesktop}
          onChange={(e) => setSeedViaDesktop(e.target.checked)}
        />
        Seed study materials via desktop copy-paste (no model call) — otherwise your configured AI
        fills topics in the background after commit.
      </label>

      <div className={styles.nav}>
        <button className="btn btn-ghost" onClick={onBack}><IconChevronLeft size={15} /> Back</button>
        <button className="btn btn-primary" onClick={() => onCommit(plan, seedViaDesktop)} disabled={loading}>
          {loading ? <Spinner size={15} /> : <IconCheck size={15} />}
          Commit plan — start prep!
        </button>
      </div>
    </div>
  );
}

/* ── Plan-review coverage audit ───────────────────────────────────────────── */
/**
 * Deterministic audit of the draft plan: which checklist items a claimed-expert skill
 * is missing, and which weeks exceed the study budget. Suggestions are opt-in — nothing
 * is inserted unless the user clicks Add.
 */
function CoveragePanel({ audit, dismissed, onAccept, onDismiss }: {
  audit:     PlanAudit | null;
  dismissed: Set<string>;
  onAccept:  (s: Suggestion) => void;
  onDismiss: (id: string) => void;
}) {
  if (!audit) return null;

  const open = audit.suggestions.filter(s => !dismissed.has(s.id));
  const overloaded = audit.weeks.filter(w => w.over);
  const weak = audit.coverage.filter(c => !c.ready);
  if (!audit.coverage.length) return null;

  return (
    <div className={styles.auditPanel}>
      <div className={styles.auditRow}>
        {audit.coverage.map(c => (
          <span key={c.skill} className={`badge ${c.ready ? 'badge-green' : 'badge-amber'}`}>
            {c.skill} coverage {c.coveragePct}%
          </span>
        ))}
      </div>

      {weak.length > 0 && (
        <p className={styles.auditNote}>
          At {audit.prepWeeks} weeks, {weak.map(c => `${c.skill} is ${c.coveragePct}%`).join(' and ')} covered
          against the senior checklist. Add the gaps below, or extend your prep window.
        </p>
      )}

      {open.length > 0 && (
        <div className={styles.chipList}>
          {open.map(s => (
            <span key={s.id} className="badge badge-neutral" style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
              {s.label}
              <button
                onClick={() => onAccept(s)}
                title={`Add to ${s.weekTitle}`}
                style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '0 1px', lineHeight: 1, color: 'inherit', display: 'flex' }}
              >
                <IconPlus size={11} />
              </button>
              <button
                onClick={() => onDismiss(s.id)}
                title="Not needed"
                style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '0 1px', lineHeight: 1, color: 'inherit', opacity: .55, display: 'flex' }}
              >
                <IconX size={10} />
              </button>
            </span>
          ))}
        </div>
      )}

      {overloaded.length > 0 && (
        <p className={styles.auditNote}>
          {overloaded.length} week{overloaded.length > 1 ? 's exceed' : ' exceeds'} your{' '}
          {Math.round(audit.budgetMinutes / 60)}h/week budget
          {overloaded[0].estimated ? ' (estimated — real effort is known after seeding)' : ''}:{' '}
          {overloaded.slice(0, 3).map(w => w.weekTitle).join(', ')}
          {overloaded.length > 3 ? '…' : ''}
        </p>
      )}
    </div>
  );
}
