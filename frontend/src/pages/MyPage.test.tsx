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
    리뷰: '/mypage/reviews',
    '내 쿠폰': '/mypage/coupons',
    '쿠폰 받기': '/coupons',
    적립금: '/mypage/points',
    '배송지 관리': '/mypage/addresses',
    찜: '/wishlist',
    '최근 본 상품': '/recent',
    설정: '/mypage/settings',
  }
  for (const [name, href] of Object.entries(hrefs)) {
    expect(screen.getByRole('link', { name })).toHaveAttribute('href', href)
  }
  expect(screen.getAllByRole('link').map((a) => a.textContent)).toEqual(Object.keys(hrefs))
})
