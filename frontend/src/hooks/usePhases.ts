import { useMutation, useQuery } from '@tanstack/react-query';
import api from '@/lib/api';
import { queryClient } from '@/lib/queryClient';
import type { Phase, TopicDetail } from '@/types';

export function usePhases() {
  return useQuery({
    queryKey: ['phases'],
    queryFn: () => api.get<Phase[]>('/phases').then((r) => r.data),
  });
}

export function useTopic(slug: string) {
  return useQuery({
    queryKey: ['topic', slug],
    queryFn: () => api.get<TopicDetail>(`/topics/${slug}`).then((r) => r.data),
    enabled: !!slug,
  });
}

export function useToggleTopicDone(slug: string) {
  return useMutation({
    mutationFn: (status: 'done' | 'todo') =>
      api.patch<TopicDetail>(`/topics/${slug}`, { status }).then((r) => r.data),
    onMutate: async (status) => {
      await queryClient.cancelQueries({ queryKey: ['topic', slug] });
      const prev = queryClient.getQueryData<TopicDetail>(['topic', slug]);
      queryClient.setQueryData<TopicDetail>(['topic', slug], (old) =>
        old ? { ...old, status } : old
      );
      return { prev };
    },
    onError: (_e, _v, ctx) => {
      if (ctx?.prev) queryClient.setQueryData(['topic', slug], ctx.prev);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ['phases'] });
      queryClient.invalidateQueries({ queryKey: ['topic', slug] });
    },
  });
}

export function usePatchTopic(slug: string) {
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      api.patch<TopicDetail>(`/topics/${slug}`, body).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['topic', slug] });
      queryClient.invalidateQueries({ queryKey: ['phases'] });
    },
  });
}
