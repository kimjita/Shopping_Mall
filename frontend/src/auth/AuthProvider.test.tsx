import { act, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { server } from '../test/server'
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
})
