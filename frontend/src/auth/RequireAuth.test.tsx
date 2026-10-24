import { screen } from '@testing-library/react'
import { Route, Routes, useLocation } from 'react-router'
import { describe, expect, it } from 'vitest'
import { renderWithProviders } from '../test/render'
import { RequireAuth } from './RequireAuth'

function Where() {
  const { pathname, search } = useLocation()
  return <p>at:{pathname + search}</p>
}

function routes() {
  return (
    <Routes>
      <Route
        path="/cart"
        element={
          <RequireAuth>
            <p>cart page</p>
          </RequireAuth>
        }
      />
      <Route path="/login" element={<Where />} />
    </Routes>
  )
}

describe('RequireAuth', () => {
  it('비회원은 현재 주소를 달아 로그인으로 보낸다', async () => {
    renderWithProviders(routes(), { route: '/cart?x=1' })
    expect(await screen.findByText('at:/login?redirect=%2Fcart%3Fx%3D1')).toBeInTheDocument()
  })

  it('회원은 자식을 본다', async () => {
    renderWithProviders(routes(), { route: '/cart', member: {} })
    expect(await screen.findByText('cart page')).toBeInTheDocument()
  })
})
