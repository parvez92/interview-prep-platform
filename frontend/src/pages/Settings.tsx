import { useState } from 'react';
import {
  IconUser, IconRobot, IconDownload, IconDeviceLaptop,
  IconCloudUpload, IconCheck, IconServer,
} from '@tabler/icons-react';
import { useMe } from '@/hooks/useMe';
import api from '@/lib/api';
import { BudgetMeter } from '@/components/ui/BudgetMeter';
import { useUsage } from '@/hooks/useDashboard';
import { Spinner } from '@/components/ui/Spinner';
import { Banner } from '@/components/ui/Banner';
import styles from './Settings.module.css';

type Provider = 'anthropic' | 'openai' | 'gemini' | 'ollama' | 'desktop';

const PROVIDERS: { id: Provider; label: string; hint: string }[] = [
  { id: 'anthropic', label: 'Anthropic', hint: 'Claude models via API key' },
  { id: 'openai',    label: 'OpenAI',    hint: 'GPT models via API key' },
  { id: 'gemini',    label: 'Gemini',    hint: 'Google Gemini via API key' },
  { id: 'ollama',    label: 'Ollama',    hint: 'Local model, free, no internet' },
  { id: 'desktop',   label: 'Desktop',   hint: 'Copy-paste flow, no API key' },
];

const DEFAULT_MODELS: Record<Provider, { strong: string; cheap: string }> = {
  anthropic: { strong: 'claude-opus-4-8',           cheap: 'claude-haiku-4-5-20251001' },
  openai:    { strong: 'gpt-4o',                     cheap: 'gpt-4o-mini' },
  gemini:    { strong: 'gemini-2.0-flash',           cheap: 'gemini-2.0-flash-lite' },
  ollama:    { strong: 'llama3.2',                   cheap: 'llama3.2' },
  desktop:   { strong: '',                           cheap: '' },
};

export function Settings() {
  const { data: me, refetch } = useMe();
  const { data: usage }       = useUsage();

  const s = me?.settings;
  const [provider,    setProvider]    = useState<Provider>((s?.llmProvider as Provider) ?? 'anthropic');
  const [modelStrong, setModelStrong] = useState(s?.modelStrong ?? DEFAULT_MODELS.anthropic.strong);
  const [modelCheap,  setModelCheap]  = useState(s?.modelCheap  ?? DEFAULT_MODELS.anthropic.cheap);
  const [ollamaUrl,   setOllamaUrl]   = useState(s?.ollamaUrl   ?? 'http://localhost:11434');
  const [budget,      setBudget]      = useState(Number(s?.monthlyBudgetUsd ?? 20));
  const [displayName, setDisplayName] = useState(me?.user.displayName ?? '');
  const [saving,   setSaving]   = useState(false);
  const [saved,    setSaved]    = useState(false);
  const [exporting, setExporting] = useState(false);

  const switchProvider = (p: Provider) => {
    if (modelStrong === DEFAULT_MODELS[provider].strong) setModelStrong(DEFAULT_MODELS[p].strong);
    if (modelCheap  === DEFAULT_MODELS[provider].cheap)  setModelCheap(DEFAULT_MODELS[p].cheap);
    setProvider(p);
  };

  const save = async () => {
    setSaving(true); setSaved(false);
    try {
      await api.patch('/me', {
        displayName: displayName || undefined,
        settings: {
          llmProvider:      provider,
          modelStrong:      modelStrong || null,
          modelCheap:       modelCheap  || null,
          ollamaUrl:        provider === 'ollama' ? ollamaUrl : null,
          monthlyBudgetUsd: budget,
        },
      });
      await refetch();
      setSaved(true);
      setTimeout(() => setSaved(false), 2500);
    } finally { setSaving(false); }
  };

  const exportData = async () => {
    setExporting(true);
    try {
      const res = await api.get('/me/export');
      const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json' });
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = 'preploop-export.json';
      a.click();
    } finally { setExporting(false); }
  };

  return (
    <div className={styles.page}>
      <h1 className="h1" style={{ fontFamily: 'var(--font-head)', marginBottom: 28 }}>Settings</h1>

      {provider === 'desktop' && (
        <Banner
          variant="accent"
          message="Desktop mode: résumé parsing and AI features show a structured prompt — paste the result back. No API key needed."
          dismissible={false}
        />
      )}

      {/* Profile */}
      <Section icon={<IconUser size={16} />} title="Profile">
        <label className={styles.field}>
          <span className="label">Display name</span>
          <input className="input-field" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
        </label>
        <p className={styles.fieldHint}>{me?.user.email}</p>
      </Section>

      {/* AI Provider */}
      <Section icon={<IconRobot size={16} />} title="AI provider">
        <div className={styles.providerGrid}>
          {PROVIDERS.map((p) => (
            <button
              key={p.id}
              className={`${styles.providerBtn} ${provider === p.id ? styles.providerActive : ''}`}
              onClick={() => switchProvider(p.id)}
              title={p.hint}
            >
              {p.id === 'desktop' && <IconDeviceLaptop size={14} />}
              {p.id === 'ollama'  && <IconServer       size={14} />}
              {p.label}
            </button>
          ))}
        </div>

        {provider === 'ollama' && (
          <div className={styles.ollamaRow}>
            <span className={styles.label}>URL</span>
            <input
              className="input-field"
              style={{ flex: 1 }}
              value={ollamaUrl}
              onChange={(e) => setOllamaUrl(e.target.value)}
              placeholder="http://localhost:11434"
            />
          </div>
        )}

        {provider === 'desktop' && (
          <div className={styles.desktopNote}>
            <IconDeviceLaptop size={14} />
            Every AI feature shows a ready-made prompt. Run it in Claude Desktop, ChatGPT, or any LLM, then paste the response back.
          </div>
        )}
      </Section>

      {/* Models — hidden for desktop */}
      {provider !== 'desktop' && (
        <Section icon={<IconRobot size={16} />} title="Models">
          <div className={styles.modelGrid}>
            <label className={styles.field}>
              <span className="label">Strong model</span>
              <input
                className="input-field"
                value={modelStrong}
                onChange={(e) => setModelStrong(e.target.value)}
                placeholder={DEFAULT_MODELS[provider].strong}
              />
            </label>
            <label className={styles.field}>
              <span className="label">Cheap model (budget fallback)</span>
              <input
                className="input-field"
                value={modelCheap}
                onChange={(e) => setModelCheap(e.target.value)}
                placeholder={DEFAULT_MODELS[provider].cheap}
              />
            </label>
          </div>
          <p className={styles.fieldHint}>
            The cheap model is used automatically once your monthly budget is exceeded.
          </p>
        </Section>
      )}

      {/* Budget */}
      <Section icon={<span>💰</span>} title="Monthly AI budget">
        <label className={styles.field}>
          <span className="label">Limit (USD/month)</span>
          <input
            className="input-field" type="number" min={0} step={1}
            value={budget} onChange={(e) => setBudget(Number(e.target.value))}
            style={{ maxWidth: 160 }}
          />
        </label>
        {usage && <BudgetMeter {...usage} />}
      </Section>

      {/* Export */}
      <Section icon={<IconDownload size={16} />} title="Data export">
        <p style={{ fontSize: '.875rem', color: 'var(--ink-2)' }}>
          Download all your data as JSON — plan, interviews, notes, STAR stories.
        </p>
        <button className="btn btn-ghost" onClick={exportData} disabled={exporting}>
          {exporting ? <Spinner size={14} /> : <IconDownload size={14} />} Export JSON
        </button>
      </Section>

      {/* Resume */}
      <Section icon={<IconCloudUpload size={16} />} title="Resume">
        <p style={{ fontSize: '.875rem', color: 'var(--ink-2)', marginBottom: 8 }}>
          Re-upload to refresh your RAG embeddings and parsed profile.
        </p>
        <button className="btn btn-ghost" onClick={() => window.location.href = '/onboarding'}>
          Re-upload résumé
        </button>
      </Section>

      <div className={styles.saveRow}>
        <button className="btn btn-primary" onClick={save} disabled={saving}>
          {saving ? <Spinner size={14} /> : saved ? <IconCheck size={14} /> : null}
          {saved ? 'Saved!' : 'Save settings'}
        </button>
      </div>
    </div>
  );
}

function Section({ icon, title, children }: { icon: React.ReactNode; title: string; children: React.ReactNode }) {
  return (
    <div className={`card ${styles.section}`}>
      <div className={styles.sectionHeader}>
        <span style={{ color: 'var(--accent)' }}>{icon}</span>
        <h2 className="h3">{title}</h2>
      </div>
      <div className={styles.sectionBody}>{children}</div>
    </div>
  );
}
