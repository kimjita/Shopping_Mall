import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { server } from '../test/server'
import { renderWithProviders } from '../test/render'
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
})
