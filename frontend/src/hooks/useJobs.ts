import { useMutation, useQuery } from '@tanstack/react-query';
import api from '@/lib/api';
import { queryClient } from '@/lib/queryClient';
import type { Job } from '@/types';

export function useJobs(sort: 'fit' | 'recent' = 'fit', status?: string) {
  return useQuery({
    queryKey: ['jobs', sort, status],
    queryFn: () => {
      const params = new URLSearchParams({ sort });
      if (status) params.set('status', status);
      return api.get<Job[]>(`/jobs?${params}`).then((r) => r.data);
    },
  });
}

export function useSyncJobs() {
  return useMutation({
    mutationFn: () =>
      api.post<{ fetched: number; matched: number; ingested: number }>('/jobs/sync').then((r) => r.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['jobs'] }),
  });
}

export function usePatchJob() {
  return useMutation({
    mutationFn: ({ id, ...body }: { id: number } & Partial<Job>) =>
      api.patch<Job>(`/jobs/${id}`, body).then((r) => r.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['jobs'] }),
  });
}
