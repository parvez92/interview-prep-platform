import { useMutation, useQuery } from '@tanstack/react-query';
import api from '@/lib/api';
import { queryClient } from '@/lib/queryClient';
import type { Interview, InterviewQuestion, PrepPack } from '@/types';

export function useInterviews() {
  return useQuery({
    queryKey: ['interviews'],
    queryFn: () => api.get<Interview[]>('/interviews').then((r) => r.data),
  });
}

export function useInterview(id: number) {
  return useQuery({
    queryKey: ['interview', id],
    queryFn: () =>
      api.get<Interview & { questions: InterviewQuestion[] }>(`/interviews/${id}`).then((r) => r.data),
    enabled: !!id,
  });
}

export function useCreateInterview() {
  return useMutation({
    mutationFn: (body: Partial<Interview>) =>
      api.post<Interview>('/interviews', body).then((r) => r.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['interviews'] }),
  });
}

export function usePatchInterview() {
  return useMutation({
    mutationFn: ({ id, ...body }: { id: number } & Partial<Interview>) =>
      api.patch<Interview>(`/interviews/${id}`, body).then((r) => r.data),
    onSuccess: (_, vars) => {
      queryClient.invalidateQueries({ queryKey: ['interviews'] });
      queryClient.invalidateQueries({ queryKey: ['interview', vars.id] });
    },
  });
}

export function useAddInterviewQuestion(interviewId: number) {
  return useMutation({
    mutationFn: (body: { text: string; topicSlug?: string; selfRating: number }) =>
      api.post(`/interviews/${interviewId}/questions`, body).then((r) => r.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['interview', interviewId] }),
  });
}

export function usePrepPack(interviewId: number) {
  return useQuery({
    queryKey: ['prep-pack', interviewId],
    queryFn: () => api.get<PrepPack>(`/interviews/${interviewId}/prep-pack`).then((r) => r.data),
    enabled: !!interviewId,
  });
}

export function useGeneratePrepPack(interviewId: number) {
  return useMutation({
    mutationFn: () =>
      api.post<PrepPack>(`/interviews/${interviewId}/prep-pack`).then((r) => r.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['prep-pack', interviewId] }),
  });
}
