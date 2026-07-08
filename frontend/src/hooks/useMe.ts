import { useQuery } from '@tanstack/react-query';
import api from '@/lib/api';
import type { MeResponse } from '@/types';

export function useMe() {
  return useQuery({
    queryKey: ['me'],
    queryFn: () => api.get<MeResponse>('/me').then((r) => r.data),
    staleTime: 5 * 60_000,
    retry: false,
  });
}
