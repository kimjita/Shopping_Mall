import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { expect, it } from 'vitest'
import type { MyCoupon } from '../api/types'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { MyCouponsPage } from './MyCouponsPage'

const coupon = (id: number, over: Partial<MyCoupon> = {}): MyCoupon => ({
  id,
  couponId: id,
  name: `쿠폰${id}`,
  discountType: 'FIXED',
  discountValue: 3000,
  maxDiscount: null,
  minOrderAmount: 20000,
  status: 'AVAILABLE',
  useStartsAt: '2020-01-01T00:00:00Z',
  useEndsAt: '2099-12-31T14:59:00Z',
  usedAt: null,
  issuedAt: '2020-01-01T00:00:00Z',
  ...over,
})

function setup(coupons: MyCoupon[]) {
  server.use(http.get('*/api/members/me/coupons', () => HttpResponse.json(coupons)))
  renderWithProviders(<MyCouponsPage />, { route: '/mypage/coupons', member: {} })
}

it('상태별로 묶어 보여 준다', async () => {
  setup([
    coupon(1, { status: 'EXPIRED' }),
    coupon(2, { status: 'USED', usedAt: '2026-10-25T03:10:30Z' }),
    coupon(3, { useStartsAt: '2098-01-01T00:00:00Z' }),
    coupon(4),
  ])
  const headings = await screen.findAllByRole('heading', { level: 2 })
  expect(headings.map((h) => h.textContent)).toEqual(['사용 가능', '사용 시작 전', '사용 완료', '기간 만료'])
  expect(screen.getAllByText('3,000원 할인')).toHaveLength(4)
  expect(screen.getAllByText('20,000원 이상 구매 시')).toHaveLength(4)
  expect(screen.getByText('2026. 10. 25. 12:10에 사용')).toBeInTheDocument()
  expect(screen.getAllByText(/^사용 기간 /)).toHaveLength(4)
  const available = screen.getByRole('heading', { name: '사용 가능' }).parentElement!
  expect(available).toHaveTextContent('쿠폰4')
  expect(available).not.toHaveTextContent('쿠폰3')
  expect(screen.getByRole('link', { name: '쿠폰 받으러 가기' })).toHaveAttribute('href', '/coupons')
})

it('빈 묶음은 제목째 숨긴다', async () => {
  setup([coupon(1)])
  await screen.findByText('쿠폰1')
  expect(screen.getByRole('heading', { name: '사용 가능' })).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: '사용 시작 전' })).not.toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: '사용 완료' })).not.toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: '기간 만료' })).not.toBeInTheDocument()
})

it('받은 쿠폰이 없으면 안내와 쿠폰 받기 링크', async () => {
  setup([])
  expect(await screen.findByText('받은 쿠폰이 없습니다.')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '쿠폰 받으러 가기' })).toHaveAttribute('href', '/coupons')
})
