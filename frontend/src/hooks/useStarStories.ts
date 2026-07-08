import { useMutation, useQuery } from '@tanstack/react-query';
import api from '@/lib/api';
import { queryClient } from '@/lib/queryClient';
import type { StarStory } from '@/types';

export function useStarStories() {
  return useQuery({
    queryKey: ['star-stories'],
    queryFn: () => api.get<StarStory[]>('/star-stories').then((r) => r.data),
  });
}

export function useCreateStarStory() {
  return useMutation({
    mutationFn: (body: Omit<StarStory, 'id'>) =>
      api.post<StarStory>('/star-stories', body).then((r) => r.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['star-stories'] }),
  });
}

export function usePatchStarStory() {
  return useMutation({
    mutationFn: ({ id, ...body }: Partial<StarStory> & { id: number }) =>
      api.patch<StarStory>(`/star-stories/${id}`, body).then((r) => r.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['star-stories'] }),
  });
}

export function useDeleteStarStory() {
  return useMutation({
    mutationFn: (id: number) => api.delete(`/star-stories/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['star-stories'] }),
  });
}
