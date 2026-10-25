import { screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import { renderWithProviders } from '../test/render'
import { MyPage } from './MyPage'

it('회원 이름과 등급, 바로가기 링크를 보여 준다', async () => {
  renderWithProviders(<MyPage />, { route: '/mypage', member: { name: '김듀이', grade: 'PINK' } })
  expect(await screen.findByText('김듀이님')).toBeInTheDocument()
  expect(screen.getByText('핑크 등급')).toBeInTheDocument()
  const hrefs = {
    '주문 내역': '/mypage/orders',
    '배송지 관리': '/mypage/addresses',
    '쿠폰 받기': '/coupons',
    찜: '/wishlist',
    '최근 본 상품': '/recent',
  }
  for (const [name, href] of Object.entries(hrefs)) {
    expect(screen.getByRole('link', { name })).toHaveAttribute('href', href)
  }
})
