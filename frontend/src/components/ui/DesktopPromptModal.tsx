import { useState } from 'react';
import { IconCopy, IconCheck, IconX } from '@tabler/icons-react';
import { Spinner } from './Spinner';
import styles from './DesktopPromptModal.module.css';

export interface DesktopPromptModalProps {
  prompt: string;
  /** label shown above the paste-back area, e.g. "Paste the plan JSON" */
  pasteLabel?: string;
  /** if true, user must paste structured JSON; if false, any text is accepted */
  expectJson?: boolean;
  onSubmit: (value: string) => Promise<void>;
  onClose: () => void;
}

export function DesktopPromptModal({
  prompt,
  pasteLabel = 'Paste the response from your desktop AI',
  expectJson = false,
  onSubmit,
  onClose,
}: DesktopPromptModalProps) {
  const [copied, setCopied]   = useState(false);
  const [pasted, setPasted]   = useState('');
  const [error, setError]     = useState('');
  const [saving, setSaving]   = useState(false);

  const copy = async () => {
    await navigator.clipboard.writeText(prompt);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const submit = async () => {
    setError('');
    if (!pasted.trim()) { setError('Paste the response first.'); return; }
    if (expectJson) {
      try { JSON.parse(pasted.trim()); }
      catch { setError('Not valid JSON — check the response and try again.'); return; }
    }
    setSaving(true);
    try { await onSubmit(pasted.trim()); }
    catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Something went wrong.');
    } finally { setSaving(false); }
  };

  return (
    <div className={styles.overlay} onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className={styles.modal}>
        <div className={styles.header}>
          <span className={styles.title}>Desktop AI — copy & paste</span>
          <button className={styles.close} onClick={onClose}><IconX size={16} /></button>
        </div>

        <p className={styles.hint}>
          Copy the prompt below, run it in Claude Desktop, ChatGPT, or any AI, then paste the response back.
        </p>

        <div className={styles.promptBox}>
          <pre className={styles.promptText}>{prompt}</pre>
          <button className={styles.copyBtn} onClick={copy}>
            {copied ? <IconCheck size={14} /> : <IconCopy size={14} />}
            {copied ? 'Copied!' : 'Copy prompt'}
          </button>
        </div>

        <label className={styles.field}>
          <span className={styles.label}>{pasteLabel}</span>
          <textarea
            className={styles.pasteArea}
            value={pasted}
            onChange={(e) => setPasted(e.target.value)}
            placeholder={expectJson ? '{ "phases": [...] }' : 'Paste the AI response here…'}
            rows={8}
          />
        </label>

        {error && <p className={styles.error}>{error}</p>}

        <div className={styles.footer}>
          <button className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button className="btn btn-primary" onClick={submit} disabled={saving || !pasted.trim()}>
            {saving && <Spinner size={14} />} Submit
          </button>
        </div>
      </div>
    </div>
  );
}
