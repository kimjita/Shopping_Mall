import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { Spinner } from '../components/Spinner'
import { useAuth } from './AuthProvider'

// eslint-disable-next-line react-refresh/only-export-components
export const loginPath = (currentPathWithSearch: string) =>
  '/login?redirect=' + encodeURIComponent(currentPathWithSearch)

export function RequireAuth({ children }: { children: ReactNode }) {
  const { status } = useAuth()
  const { pathname, search } = useLocation()
  if (status === 'loading') return <Spinner />
  if (status === 'guest') return <Navigate to={loginPath(pathname + search)} replace />
  return children
}
