/** Module-level event bus for tracking in-flight AI requests across the whole app. */

export interface AiLoadingState {
  active:  boolean;
  message: string;
  isLocal: boolean; // true when using a local model (Ollama)
}

type Listener = (state: AiLoadingState) => void;

let _count    = 0;
let _message  = '';
let _isLocal  = false;
const _listeners = new Set<Listener>();

function notify() {
  const state: AiLoadingState = { active: _count > 0, message: _message, isLocal: _isLocal };
  _listeners.forEach((l) => l(state));
}

export function aiStart(message: string, isLocal = false) {
  _count++;
  _message = message;
  _isLocal = isLocal;
  notify();
}

export function aiEnd() {
  _count = Math.max(0, _count - 1);
  if (_count === 0) { _message = ''; _isLocal = false; }
  notify();
}

export function subscribeAiLoading(fn: Listener): () => void {
  _listeners.add(fn);
  return () => _listeners.delete(fn);
}

export function getAiLoadingState(): AiLoadingState {
  return { active: _count > 0, message: _message, isLocal: _isLocal };
}
