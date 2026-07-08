import '@testing-library/jest-dom';
import { server } from './server';
import { beforeAll, afterAll, afterEach } from 'vitest';

// Establish API mocking before all tests.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));

// Reset any request handlers that were added during tests (so they don't affect other tests).
afterEach(() => server.resetHandlers());

// Clean up after all tests are done.
afterAll(() => server.close());
