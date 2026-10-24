import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { useLocation } from 'react-router'
import { expect, it } from 'vitest'
import { useAuth } from '../auth/AuthProvider'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { WishButton } from './WishButton'

function Loc() {
  const l = useLocation()
  return <div data-testid="loc">{l.pathname + l.search}</div>
}

function Status() {
  return <div data-testid="status">{useAuth().status}</div>
}

it('비회원이 누르면 로그인으로 보낸다', async () => {
  renderWithProviders(
    <>
      <WishButton productId={3} wished={false} />
      <Loc />
    </>,
    { route: '/products/3' },
  )
  await userEvent.click(await screen.findByRole('button', { name: '찜하기' }))
  expect(screen.getByTestId('loc')).toHaveTextContent('/login?redirect=%2Fproducts%2F3')
})

it('회원이 누르면 PUT을 보내고 눌림 상태가 된다', async () => {
  let called = 0
  server.use(
    http.put('*/api/members/me/wishlist/3', () => {
      called++
      return new HttpResponse(null, { status: 204 })
    }),
  )
  renderWithProviders(
    <>
      <WishButton productId={3} wished={false} />
      <Status />
    </>,
    { member: {} },
  )
  await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('member'))
  await userEvent.click(screen.getByRole('button', { name: '찜하기' }))
  const pressed = await screen.findByRole('button', { name: '찜 취소' })
  expect(pressed).toHaveAttribute('aria-pressed', 'true')
  await waitFor(() => expect(called).toBe(1))
})

it('실패하면 눌림 상태를 되돌린다', async () => {
  server.use(
    http.put('*/api/members/me/wishlist/3', () =>
      HttpResponse.json({ code: 'X', message: '실패', errors: [] }, { status: 500 }),
    ),
  )
  renderWithProviders(
    <>
      <WishButton productId={3} wished={false} />
      <Status />
    </>,
    { member: {} },
  )
  await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('member'))
  await userEvent.click(screen.getByRole('button', { name: '찜하기' }))
  await waitFor(() => expect(screen.getByRole('button', { name: '찜하기' })).toHaveAttribute('aria-pressed', 'false'))
})
