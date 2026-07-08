import { useEffect, useState } from 'react';
import { type AiLoadingState, getAiLoadingState, subscribeAiLoading } from '@/lib/aiLoading';

export function useAiLoading(): AiLoadingState {
  const [state, setState] = useState<AiLoadingState>(getAiLoadingState);
  useEffect(() => subscribeAiLoading(setState), []);
  return state;
}
