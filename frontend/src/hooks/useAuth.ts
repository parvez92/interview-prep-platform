import { useMutation } from '@tanstack/react-query';
import api, { clearTokens, setTokens } from '@/lib/api';
import { queryClient } from '@/lib/queryClient';
import { ONBOARDING_KEY } from '@/pages/Onboarding';
import type { AuthTokens } from '@/types';

export function useLogin() {
  return useMutation({
    mutationFn: (body: { email: string; password: string }) =>
      api.post<AuthTokens>('/auth/login', body).then((r) => r.data),
    onSuccess: (data) => {
      setTokens(data.accessToken, data.refreshToken);
      sessionStorage.removeItem(ONBOARDING_KEY);
      queryClient.removeQueries({ queryKey: ['me'] });
    },
  });
}

export function useRegister() {
  return useMutation({
    mutationFn: (body: { email: string; password: string; displayName?: string }) =>
      api.post<AuthTokens>('/auth/register', body).then((r) => r.data),
    onSuccess: (data) => {
      setTokens(data.accessToken, data.refreshToken);
      sessionStorage.removeItem(ONBOARDING_KEY);
      queryClient.removeQueries({ queryKey: ['me'] });
    },
  });
}

export function useLogout() {
  return () => {
    clearTokens();
    queryClient.clear();
    sessionStorage.removeItem(ONBOARDING_KEY);
    window.location.href = '/login';
  };
}
