import axios, { type AxiosInstance } from 'axios';
import { aiEnd, aiStart } from './aiLoading';

const BASE = '/api';
const LS_ACCESS  = 'prep_access';
const LS_REFRESH = 'prep_refresh';

let _accessToken: string | null  = localStorage.getItem(LS_ACCESS);
let _refreshToken: string | null = localStorage.getItem(LS_REFRESH);
let _refreshPromise: Promise<string> | null = null;

export function setTokens(access: string, refresh: string) {
  _accessToken  = access;
  _refreshToken = refresh;
  localStorage.setItem(LS_ACCESS,  access);
  localStorage.setItem(LS_REFRESH, refresh);
}

export function clearTokens() {
  _accessToken  = null;
  _refreshToken = null;
  localStorage.removeItem(LS_ACCESS);
  localStorage.removeItem(LS_REFRESH);
}

export function getAccessToken(): string | null {
  return _accessToken;
}

const api: AxiosInstance = axios.create({ baseURL: BASE });

api.interceptors.request.use((config) => {
  if (_accessToken) {
    config.headers.Authorization = `Bearer ${_accessToken}`;
  }
  return config;
});

api.interceptors.response.use(
  (r) => r,
  async (err) => {
    const original = err.config;
    if (err.response?.status === 401 && !original._retry && _refreshToken) {
      if (_refreshPromise) {
        const token = await _refreshPromise;
        original.headers.Authorization = `Bearer ${token}`;
        return api(original);
      }
      original._retry = true;
      _refreshPromise = axios
        .post(`${BASE}/auth/refresh`, { refreshToken: _refreshToken })
        .then((r) => {
          const { accessToken, refreshToken } = r.data;
          setTokens(accessToken, refreshToken);
          return accessToken as string;
        })
        .finally(() => { _refreshPromise = null; });
      try {
        const token = await _refreshPromise;
        original.headers.Authorization = `Bearer ${token}`;
        return api(original);
      } catch {
        clearTokens();
        window.location.href = '/login';
      }
    }
    return Promise.reject(err);
  }
);

// AI endpoint patterns that can be slow — show a global loading banner
const AI_PATHS = ['/api/ai/', '/api/onboarding/resume', '/api/onboarding/plan'];

function isAiPath(url: string | undefined): boolean {
  return !!url && AI_PATHS.some((p) => url.includes(p));
}

function aiMessage(url: string | undefined, isLocal: boolean): string {
  if (url?.includes('/onboarding/resume')) {
    return isLocal
      ? 'Parsing resume with local model — this can take a few minutes…'
      : 'Parsing resume…';
  }
  if (url?.includes('/onboarding/plan')) {
    return isLocal
      ? 'Generating study plan with local model — hang tight…'
      : 'Generating study plan…';
  }
  return isLocal ? 'Running AI call with local model…' : 'Running AI call…';
}

api.interceptors.request.use((config) => {
  if (isAiPath(config.url)) {
    const isLocal = config.headers?.['X-Override-Provider'] === 'ollama';
    aiStart(aiMessage(config.url, isLocal), isLocal);
  }
  return config;
});

api.interceptors.response.use(
  (r) => { if (isAiPath(r.config.url)) aiEnd(); return r; },
  (err) => { if (isAiPath(err.config?.url)) aiEnd(); return Promise.reject(err); }
);

export default api;
