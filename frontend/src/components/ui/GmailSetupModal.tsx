import { useState } from 'react';
import { IconX, IconCopy, IconCheck, IconExternalLink, IconShieldLock } from '@tabler/icons-react';
import styles from './GmailSetupModal.module.css';

const PLAYGROUND_URL = 'https://developers.google.com/oauthplayground';
const CONSOLE_URL = 'https://console.cloud.google.com/apis/library/gmail.googleapis.com';
const GMAIL_SCOPE = 'https://www.googleapis.com/auth/gmail.readonly';

const VAULT_CMD = `vault kv patch secret/backend-core \\
  gmail.client.id=YOUR_CLIENT_ID \\
  gmail.client.secret=YOUR_CLIENT_SECRET \\
  gmail.refresh.token=YOUR_REFRESH_TOKEN`;

const ENV_SNIPPET = `GMAIL_ENABLED=true
# optional, strictest mode: only mail you route to this Gmail label is ever read
GMAIL_LABEL=job-alerts`;

function CopyBlock({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    await navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <div className={styles.codeBox}>
      <pre className={styles.code}>{text}</pre>
      <button className={styles.copyBtn} onClick={copy}>
        {copied ? <IconCheck size={13} /> : <IconCopy size={13} />} {copied ? 'Copied' : 'Copy'}
      </button>
    </div>
  );
}

export function GmailSetupModal({ onClose }: { onClose: () => void }) {
  return (
    <div className={styles.overlay} onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className={styles.modal}>
        <div className={styles.header}>
          <span className={styles.title}>Connect Gmail — one-time setup</span>
          <button className={styles.close} onClick={onClose}><IconX size={16} /></button>
        </div>

        <p className={styles.hint}>
          The app reads job alerts from your Gmail with a <strong>read-only</strong> token stored in Vault.
          It never lists your whole mailbox — only known job-board senders, or a Gmail label you control.
        </p>

        <ol className={styles.steps}>
          <li>
            <strong>Create an OAuth client.</strong> In{' '}
            <a href={CONSOLE_URL} target="_blank" rel="noopener noreferrer">
              Google Cloud Console <IconExternalLink size={11} />
            </a>
            : enable the <em>Gmail API</em>, configure the OAuth consent screen (add yourself as a test
            user), then create an <em>OAuth client ID</em> of type <em>Web application</em> with redirect
            URI <code>{PLAYGROUND_URL}</code>.
          </li>
          <li>
            <strong>Get a refresh token.</strong> In the{' '}
            <a href={PLAYGROUND_URL} target="_blank" rel="noopener noreferrer">
              OAuth 2.0 Playground <IconExternalLink size={11} />
            </a>
            : open the gear icon → <em>Use your own OAuth credentials</em>, authorize the scope below,
            exchange the code, and copy the <em>refresh token</em>.
            <CopyBlock text={GMAIL_SCOPE} />
          </li>
          <li>
            <strong>Store the credentials in Vault</strong> (<code>patch</code>, not <code>put</code> —
            {' '}<code>put</code> would wipe your other secrets):
            <CopyBlock text={VAULT_CMD} />
          </li>
          <li>
            <strong>Enable and restart backend-core</strong> with:
            <CopyBlock text={ENV_SNIPPET} />
            For the label mode, add a Gmail filter that applies the <code>job-alerts</code> label to the
            senders you want synced.
          </li>
        </ol>

        <p className={styles.privacy}>
          <IconShieldLock size={14} />
          Emails that don&apos;t match a job-board sender or job subject are dropped immediately — never
          stored, never sent to any AI model. Only company, role, and the job description text are kept.
        </p>

        <div className={styles.footer}>
          <button className="btn btn-primary btn-sm" onClick={onClose}>Done — try Sync again</button>
        </div>
      </div>
    </div>
  );
}
