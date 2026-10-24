import { screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import { AppRoutes } from './routes'
import { renderWithProviders } from './test/render'

it('첫 화면에 dewy 로고 링크가 있다', async () => {
  renderWithProviders(<AppRoutes />, { route: '/' })
  expect(await screen.findByRole('link', { name: 'dewy 홈' })).toHaveAttribute('href', '/')
})

it('없는 주소는 안내 화면을 보여 준다', async () => {
  renderWithProviders(<AppRoutes />, { route: '/nope' })
  expect(await screen.findByText('페이지를 찾을 수 없습니다.')).toBeInTheDocument()
})
