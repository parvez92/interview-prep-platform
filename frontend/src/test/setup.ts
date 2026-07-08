import '@testing-library/jest-dom';
import { server } from './server';
import { beforeAll, afterAll, afterEach } from 'vitest';

// Node >= 22 defines an experimental global localStorage that is undefined without
// --localstorage-file, and vitest's jsdom env won't overwrite an existing global —
// so neither Node nor jsdom provides a working one. Polyfill in-memory storage.
class MemoryStorage implements Storage {
  private store = new Map<string, string>();
  get length() { return this.store.size; }
  clear() { this.store.clear(); }
  getItem(key: string) { return this.store.get(key) ?? null; }
  key(index: number) { return [...this.store.keys()][index] ?? null; }
  removeItem(key: string) { this.store.delete(key); }
  setItem(key: string, value: string) { this.store.set(key, String(value)); }
}
Object.defineProperty(globalThis, 'localStorage', { value: new MemoryStorage(), writable: true });
Object.defineProperty(globalThis, 'sessionStorage', { value: new MemoryStorage(), writable: true });

// Establish API mocking before all tests.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));

// Reset any request handlers that were added during tests (so they don't affect other tests).
afterEach(() => server.resetHandlers());

// Clean up after all tests are done.
afterAll(() => server.close());
