import { act, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { server } from '../test/server'
import { Header } from '../components/Header'
import { renderWithProviders, testMe } from '../test/render'
import { authChannel, type AuthMessage } from './authChannel'
import { useAuth } from './AuthProvider'

function Probe() {
  const { status, me, logout } = useAuth()
  return (
    <>
      <p>status:{status}</p>
      <p>name:{me?.name ?? '-'}</p>
      <button onClick={() => void logout()}>로그아웃</button>
    </>
  )
}

afterEach(() => vi.restoreAllMocks())

describe('AuthProvider', () => {
  it('refresh 쿠키가 유효하면 member가 되고 me를 읽는다', async () => {
    renderWithProviders(<Probe />, { member: { name: '홍길동' } })
    expect(await screen.findByText('status:member')).toBeInTheDocument()
    expect(screen.getByText('name:홍길동')).toBeInTheDocument()
  })

  it('refresh 401이면 guest', async () => {
    renderWithProviders(<Probe />)
    expect(await screen.findByText('status:guest')).toBeInTheDocument()
  })

  it('logout 뒤 guest가 되고 logout API를 부른다', async () => {
    const logoutCalled = vi.fn()
    renderWithProviders(<Probe />, { member: {} })
    server.use(
      http.post('*/api/auth/logout', () => {
        logoutCalled()
        return new HttpResponse(null, { status: 204 })
      }),
    )
    await screen.findByText('status:member')

    await userEvent.click(screen.getByRole('button', { name: '로그아웃' }))

    expect(await screen.findByText('status:guest')).toBeInTheDocument()
    expect(logoutCalled).toHaveBeenCalledTimes(1)
  })

  it('다른 탭의 login 메시지를 받으면 캐시를 비우고 새 계정의 me를 읽는다', async () => {
    let onMessage: (msg: AuthMessage) => void = () => {}
    vi.spyOn(authChannel, 'onMessage').mockImplementation((fn) => {
      onMessage = fn
      return () => {}
    })
    renderWithProviders(<Probe />, { member: { name: '홍길동' } })
    await screen.findByText('name:홍길동')
    server.use(
      http.post('*/api/auth/refresh', () =>
        HttpResponse.json({ accessToken: 'tab-b-token', tokenType: 'Bearer', expiresIn: 1800 }),
      ),
      http.get('*/api/members/me', () => HttpResponse.json({ ...testMe, name: '김철수' })),
    )

    await act(async () => onMessage({ type: 'login' }))

    expect(await screen.findByText('name:김철수')).toBeInTheDocument()
  })

  it('요청 중 refresh가 401이면 이전 회원의 데이터를 화면에서 지운다', async () => {
    const cartLine = {
      skuId: 1,
      skuName: '기본',
      productId: 1,
      productName: '상품',
      brandName: '브랜드',
      thumbnailUrl: null,
      quantity: 1,
      unitPrice: 1000,
      unitSalePrice: 1000,
      lineTotal: 1000,
      status: 'AVAILABLE',
    }
    server.use(http.get('*/api/members/me/cart', () => HttpResponse.json({ items: [1, 2].map((id) => ({ ...cartLine, id })) })))
    const { queryClient } = renderWithProviders(
      <>
        <Header />
        <Probe />
      </>,
      { member: { name: '홍길동' } },
    )
    expect(await screen.findByRole('link', { name: '장바구니 2개' })).toBeInTheDocument()
    const expired = { code: 'INVALID_REFRESH_TOKEN', message: '다시 로그인해 주세요.', errors: [] }
    server.use(
      http.get('*/api/members/me/cart', () => HttpResponse.json({ code: 'UNAUTHORIZED', message: '로그인이 필요합니다.', errors: [] }, { status: 401 })),
      http.post('*/api/auth/refresh', () => HttpResponse.json(expired, { status: 401 })),
    )

    await act(() => queryClient.invalidateQueries({ queryKey: ['cart'] }))

    expect(await screen.findByText('status:guest')).toBeInTheDocument()
    expect(screen.getByText('name:-')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '장바구니' })).toBeInTheDocument()
  })
})
