import { act, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Navigate, Route, Routes } from 'react-router'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import type { Order } from '../api/types'
import { startTossPayment, TossCancelError } from '../payments/toss'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { OrderDetailPage } from './OrderDetailPage'

vi.mock('../payments/toss', () => ({ startTossPayment: vi.fn(), TossCancelError: class TossCancelError extends Error {} }))

const NO = '20261025-ORDER001'
const NOW = Date.parse('2026-10-25T03:10:30Z')

const order = (over: Partial<Order> = {}): Order => ({
  orderNumber: NO,
  status: 'PAID',
  orderedAt: '2026-10-25T03:10:30Z',
  expiresAt: '2026-10-25T03:25:30Z',
  items: [
    { productId: 7, skuId: 1, productName: '토너', skuName: '기본', thumbnailUrl: null, unitPrice: 10000, unitSalePrice: 10000, quantity: 2, lineTotal: 20000 },
  ],
  price: { itemsPrice: 20000, saleDiscount: 0, itemsSalePrice: 20000, couponDiscount: 3000, shippingFee: 0, pointsUsed: 1000, paymentAmount: 16000 },
  couponName: '3천원 쿠폰',
  shipping: { recipient: '홍길동', phone: '01012345678', zipCode: '06236', address1: '서울시 강남구', address2: '101호' },
  canceledAt: null,
  cancelReason: null,
  deliveredAt: null,
  confirmedAt: null,
  ...over,
})

beforeEach(() => {
  vi.mocked(startTossPayment).mockReset().mockResolvedValue(undefined)
})
afterEach(() => {
  vi.useRealTimers()
})

const detailRoute = <Route path="/mypage/orders/:orderNumber" element={<OrderDetailPage />} />

function setup(initial: Order, route = `/mypage/orders/${NO}`) {
  server.use(http.get('*/api/members/me/orders/:no', () => HttpResponse.json(initial)))
  return renderWithProviders(<Routes>{detailRoute}</Routes>, { route, member: {} })
}

it('결제 대기 주문은 남은 시간과 결제하기를 보여 주고 결제창을 연다', async () => {
  vi.useFakeTimers({ shouldAdvanceTime: true })
  vi.setSystemTime(NOW)
  setup(order({ status: 'PENDING' }))
  expect(await screen.findByText(/^남은 시간 (15:00|14:5\d)$/)).toBeInTheDocument()
  await userEvent.setup({ advanceTimers: vi.advanceTimersByTime }).click(screen.getByRole('button', { name: '결제하기' }))
  expect(startTossPayment).toHaveBeenCalledWith(expect.objectContaining({ orderNumber: NO }), { email: 'user@example.com', name: '홍길동' })
})

it('만료 0.5초 전에는 00:01을 보여 준다', async () => {
  vi.useFakeTimers({ shouldAdvanceTime: true })
  vi.setSystemTime(Date.parse('2026-10-25T03:25:30Z') - 500)
  setup(order({ status: 'PENDING' }))
  expect(await screen.findByText('남은 시간 00:01')).toBeInTheDocument()
})

it('결제창을 닫으면 안내하고 상세 링크 없이 다시 결제할 수 있다', async () => {
  vi.useFakeTimers({ shouldAdvanceTime: true })
  vi.setSystemTime(NOW)
  vi.mocked(startTossPayment).mockRejectedValue(new TossCancelError('결제를 취소했습니다.'))
  setup(order({ status: 'PENDING' }))
  await userEvent.setup({ advanceTimers: vi.advanceTimersByTime }).click(await screen.findByRole('button', { name: '결제하기' }))
  expect(await screen.findByText('결제가 취소되었습니다. 남은 시간 안에 다시 결제할 수 있습니다.')).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: '주문 상세 보기' })).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: '결제하기' })).toBeEnabled()
})

it('기한이 지나면 결제하기가 사라진다', async () => {
  vi.useFakeTimers({ shouldAdvanceTime: true })
  vi.setSystemTime(NOW)
  setup(order({ status: 'PENDING' }))
  expect(await screen.findByRole('button', { name: '결제하기' })).toBeInTheDocument()
  await act(() => vi.advanceTimersByTimeAsync(16 * 60_000))
  expect(screen.getByText('결제 기한이 지났습니다.')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '결제하기' })).not.toBeInTheDocument()
})

it('결제 완료 주문은 확인 뒤 취소한다', async () => {
  const canceled = order({ status: 'CANCELED', canceledAt: '2026-10-25T03:20:00Z', cancelReason: 'USER' })
  setup(order())
  server.use(http.post(`*/api/members/me/orders/${NO}/cancel`, () => HttpResponse.json(canceled)))
  await userEvent.click(await screen.findByRole('button', { name: '주문 취소' }))
  const dialog = screen.getByRole('dialog', { hidden: true })
  expect(within(dialog).getByText('주문을 취소할까요? 결제가 취소되고 쿠폰·적립금이 돌아옵니다.')).toBeInTheDocument()
  await userEvent.click(within(dialog).getByRole('button', { name: '주문 취소', hidden: true }))
  expect(await screen.findByText('고객 취소')).toBeInTheDocument()
  expect(screen.getByText('취소', { selector: 'dd' })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '주문 취소' })).not.toBeInTheDocument()
})

it('배송 완료 주문은 확인 뒤 구매확정한다', async () => {
  setup(order({ status: 'DELIVERED', deliveredAt: '2026-10-26T03:00:00Z' }))
  server.use(
    http.post(`*/api/members/me/orders/${NO}/confirm`, () =>
      HttpResponse.json(order({ status: 'CONFIRMED', deliveredAt: '2026-10-26T03:00:00Z', confirmedAt: '2026-10-27T03:00:00Z' })),
    ),
  )
  await userEvent.click(await screen.findByRole('button', { name: '구매확정' }))
  const dialog = screen.getByRole('dialog', { hidden: true })
  expect(within(dialog).getByText('구매를 확정할까요? 확정 후에는 취소할 수 없습니다.')).toBeInTheDocument()
  await userEvent.click(within(dialog).getByRole('button', { name: '구매확정', hidden: true }))
  expect(await screen.findByText('구매확정 시각')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '구매확정' })).not.toBeInTheDocument()
})

it('취소 실패는 서버 메시지를 보여 준다', async () => {
  setup(order())
  server.use(
    http.post(`*/api/members/me/orders/${NO}/cancel`, () =>
      HttpResponse.json({ code: 'PAYMENT_GATEWAY_ERROR', message: '결제 취소에 실패했습니다.', errors: [] }, { status: 502 }),
    ),
  )
  await userEvent.click(await screen.findByRole('button', { name: '주문 취소' }))
  await userEvent.click(within(screen.getByRole('dialog', { hidden: true })).getByRole('button', { name: '주문 취소', hidden: true }))
  expect(await screen.findByRole('alert')).toHaveTextContent('결제 취소에 실패했습니다.')
})

it('notice가 있으면 맨 위에 보여 준다', async () => {
  server.use(http.get('*/api/members/me/orders/:no', () => HttpResponse.json(order())))
  renderWithProviders(
    <Routes>
      <Route path="/start" element={<Navigate to={`/mypage/orders/${NO}`} state={{ notice: '결제가 완료되었습니다.' }} />} />
      {detailRoute}
    </Routes>,
    { route: '/start', member: {} },
  )
  expect(await screen.findByText(NO)).toBeInTheDocument()
  expect(screen.getByRole('status')).toHaveTextContent('결제가 완료되었습니다.')
})

it('없는 주문은 안내한다', async () => {
  server.use(http.get('*/api/members/me/orders/:no', () => HttpResponse.json({ code: 'ORDER_NOT_FOUND', message: '없음', errors: [] }, { status: 404 })))
  renderWithProviders(<Routes>{detailRoute}</Routes>, { route: `/mypage/orders/${NO}`, member: {} })
  expect(await screen.findByText('주문을 찾을 수 없습니다.')).toBeInTheDocument()
})

it('잘못된 주문번호는 요청 없이 안내한다', async () => {
  let called = false
  server.use(http.get('*/api/members/me/orders/:no', () => ((called = true), HttpResponse.json(order()))))
  renderWithProviders(<Routes>{detailRoute}</Routes>, { route: '/mypage/orders/abc', member: {} })
  expect(await screen.findByText('주문을 찾을 수 없습니다.')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '주문 내역' })).toHaveAttribute('href', '/mypage/orders')
  expect(called).toBe(false)
})
