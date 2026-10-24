import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, expect, it, vi } from 'vitest'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { AuthProvider } from './auth/AuthProvider'
import { routes } from './routes'
import { server } from './test/server'

function Boom(): never {
  throw new Error('boom')
}

afterEach(() => vi.restoreAllMocks())

it('화면이 렌더 중에 터지면 안내와 홈 링크를 보여 준다', async () => {
  vi.spyOn(console, 'error').mockImplementation(() => {}) // React·라우터가 잡은 오류를 찍는 로그를 숨긴다.
  server.use(http.post('*/api/auth/refresh', () => HttpResponse.json({ code: 'INVALID_REFRESH_TOKEN', message: '다시 로그인해 주세요.', errors: [] }, { status: 401 })))
  const router = createMemoryRouter(
    [{ element: routes[0].element, errorElement: routes[0].errorElement, children: [{ path: 'boom', element: <Boom /> }] }],
    { initialEntries: ['/boom'] },
  )
  render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>,
  )
  expect(await screen.findByText('문제가 발생했습니다. 잠시 후 다시 시도해 주세요.')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '홈으로' })).toHaveAttribute('href', '/')
})
