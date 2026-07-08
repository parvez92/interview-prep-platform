import { useQuery } from '@tanstack/react-query';
import api from '@/lib/api';
import type { Progress, TodayQueue, Usage } from '@/types';

export function useProgress() {
  return useQuery({
    queryKey: ['progress'],
    queryFn: () => api.get<Progress>('/progress').then((r) => r.data),
    refetchInterval: 5 * 60_000,
  });
}

export function useTodayReview() {
  return useQuery({
    queryKey: ['review', 'today'],
    queryFn: () => api.get<TodayQueue>('/review/today').then((r) => r.data),
    refetchInterval: 5 * 60_000,
  });
}

export function useUsage() {
  return useQuery({
    queryKey: ['usage'],
    queryFn: () => api.get<Usage>('/usage').then((r) => r.data),
    refetchInterval: 60_000,
  });
}
