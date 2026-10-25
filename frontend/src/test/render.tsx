import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import type { ReactElement } from 'react'
import { MemoryRouter, type InitialEntry } from 'react-router'
import type { Me } from '../api/types'
import { AuthProvider } from '../auth/AuthProvider'
import { server } from './server'

export const testMe: Me = {
  id: 1,
  email: 'user@example.com',
  name: '홍길동',
  phone: '010-1234-5678',
  role: 'USER',
  grade: 'BABY',
  skinType: null,
  skinTone: null,
  skinConcerns: [],
}

// member가 있으면 로그인한 회원, 없으면 refresh 401인 비회원으로 시작한다.
export function renderWithProviders(ui: ReactElement, opts: { route?: InitialEntry; member?: Partial<Me> } = {}) {
  if (opts.member) {
    server.use(
      http.post('*/api/auth/refresh', () =>
        HttpResponse.json({
          accessToken: 'test-token',
          tokenType: 'Bearer',
          expiresIn: 1800,
        }),
      ),
      http.get('*/api/members/me', () => HttpResponse.json({ ...testMe, ...opts.member })),
    )
  } else {
    server.use(
      http.post('*/api/auth/refresh', () =>
        HttpResponse.json(
          {
            code: 'INVALID_REFRESH_TOKEN',
            message: '다시 로그인해 주세요.',
            errors: [],
          },
          { status: 401 },
        ),
      ),
    )
  }
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const result = render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <MemoryRouter initialEntries={[opts.route ?? '/']}>{ui}</MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  )
  return { ...result, queryClient }
}
