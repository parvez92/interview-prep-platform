import { Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { queryClient } from '@/lib/queryClient';
import { useMe } from '@/hooks/useMe';
import { getAccessToken } from '@/lib/api';

import { AppShell } from '@/components/layout/AppShell';
import { PageSpinner } from '@/components/ui/Spinner';
import { AiLoadingBanner } from '@/components/ui/AiLoadingBanner';

import { Login }           from '@/pages/Login';
import { Onboarding }      from '@/pages/Onboarding';
import { Home }            from '@/pages/Home';
import { Study }           from '@/pages/Study';
import { TopicDetail }     from '@/pages/TopicDetail';
import { Interviews }      from '@/pages/Interviews';
import { InterviewDetail } from '@/pages/InterviewDetail';
import { MockInterviewer } from '@/pages/MockInterviewer';
import { Jobs }            from '@/pages/Jobs';
import { StarStories }     from '@/pages/StarStories';
import { Settings }        from '@/pages/Settings';

/** Requires a valid token; redirects to /login if not authenticated. */
function LoginRequired({ children }: { children: React.ReactNode }) {
  const { isPending, isError } = useMe();
  if (isPending) return <PageSpinner />;
  if (isError || !getAccessToken()) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

/** Requires auth AND a completed onboarding. */
function AuthGate({ children }: { children: React.ReactNode }) {
  const { data: me, isPending, isError } = useMe();

  if (isPending) return <PageSpinner />;
  if (isError || !getAccessToken()) return <Navigate to="/login" replace />;
  if (me && !me.onboarded)          return <Navigate to="/onboarding" replace />;

  return <>{children}</>;
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/login"      element={<Login />} />
      <Route path="/onboarding" element={<LoginRequired><Onboarding /></LoginRequired>} />

      <Route element={<AuthGate><AppShell /></AuthGate>}>
        <Route index element={<Home />} />
        <Route path="/study" element={<Study />}>
          <Route path=":slug" element={<TopicDetail />} />
        </Route>
        <Route path="/interviews"    element={<Interviews />} />
        <Route path="/interviews/:id" element={<InterviewDetail />} />
        <Route path="/mock"          element={<MockInterviewer />} />
        <Route path="/jobs"          element={<Jobs />} />
        <Route path="/star"          element={<StarStories />} />
        <Route path="/settings"      element={<Settings />} />
        <Route path="*"              element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Suspense fallback={<PageSpinner />}>
          <AppRoutes />
        </Suspense>
        <AiLoadingBanner />
      </BrowserRouter>
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  );
}
