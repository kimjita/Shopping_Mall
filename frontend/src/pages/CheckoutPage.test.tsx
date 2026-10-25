import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, expect, it, vi } from 'vitest'
import type { Address, CheckoutPreview, MyCoupon, Order } from '../api/types'
import { isTossConfigured, startTossPayment, TossCancelError } from '../payments/toss'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { CheckoutPage } from './CheckoutPage'

vi.mock('../payments/toss', () => ({
  startTossPayment: vi.fn(),
  isTossConfigured: vi.fn(),
  TossCancelError: class TossCancelError extends Error {},
}))

beforeEach(() => {
  vi.mocked(startTossPayment).mockReset().mockResolvedValue(undefined)
  vi.mocked(isTossConfigured).mockReset().mockReturnValue(true)
})

const NO = '20261025-Y1GAX7TR'
const DAY = 86_400_000
const iso = (offset: number) => new Date(Date.now() + offset).toISOString()

const addr = (id: number, isDefault = false): Address => ({
  id,
  recipient: `받는이${id}`,
  phone: `0101234000${id}`,
  zipCode: '06236',
  address1: `서울시 ${id}번지`,
  address2: '101호',
  isDefault,
})

const coupon = (id: number, over: Partial<MyCoupon> = {}): MyCoupon => ({
  id,
  couponId: id,
  name: `쿠폰${id}`,
  discountType: 'FIXED',
  discountValue: 3000,
  maxDiscount: null,
  minOrderAmount: 0,
  status: 'AVAILABLE',
  useStartsAt: iso(-DAY),
  useEndsAt: iso(DAY),
  usedAt: null,
  issuedAt: iso(-DAY),
  ...over,
})

// 줄 id별 [정가, 세일가]. 수량은 1이다.
const PRICES: Record<number, [number, number]> = { 1: [12000, 10000], 2: [5000, 5000], 3: [20000, 20000] }

type Body = { cartItemIds: number[]; memberCouponId: number | null; points: number }

function previewOf({ cartItemIds, memberCouponId, points }: Body, coupons: MyCoupon[], balance: number) {
  const lines = cartItemIds.map((id) => ({
    cartItemId: id,
    skuId: id,
    productName: `상품${id}`,
    skuName: `옵션${id}`,
    thumbnailUrl: null,
    quantity: 1,
    unitPrice: PRICES[id][0],
    unitSalePrice: PRICES[id][1],
    lineTotal: PRICES[id][1],
  }))
  const itemsPrice = lines.reduce((s, l) => s + l.unitPrice, 0)
  const itemsSalePrice = lines.reduce((s, l) => s + l.lineTotal, 0)
  const couponDiscount = coupons.find((c) => c.id === memberCouponId)?.discountValue ?? 0
  const shippingFee = itemsSalePrice - couponDiscount >= 20000 ? 0 : 2500
  const paymentAmount = itemsSalePrice - couponDiscount + shippingFee - points
  if (paymentAmount < 100)
    return HttpResponse.json({ code: 'POINTS_EXCEED_LIMIT', message: '결제금액이 100원 이상 남아야 합니다.', errors: [] }, { status: 400 })
  const price = { itemsPrice, saleDiscount: itemsPrice - itemsSalePrice, itemsSalePrice, couponDiscount, shippingFee, pointsUsed: points, paymentAmount }
  return HttpResponse.json({ lines, price, pointBalance: balance } satisfies CheckoutPreview)
}

function setup({
  items = '1,2',
  addresses = [addr(1, true), addr(2)],
  coupons = [] as MyCoupon[],
  balance = 50000,
}: { items?: string; addresses?: Address[]; coupons?: MyCoupon[]; balance?: number } = {}) {
  const calls = { previews: [] as Body[], orders: [] as unknown[] }
  server.use(
    http.get('*/api/members/me/addresses', () => HttpResponse.json(addresses)),
    http.get('*/api/members/me/coupons', () => HttpResponse.json(coupons)),
    http.post('*/api/members/me/checkout/preview', async ({ request }) => {
      const body = (await request.json()) as Body
      calls.previews.push(body)
      return previewOf(body, coupons, balance)
    }),
    http.post('*/api/members/me/orders', async ({ request }) => {
      calls.orders.push(await request.json())
      return HttpResponse.json({ orderNumber: NO, status: 'PENDING' } as Order, { status: 201 })
    }),
  )
  const { queryClient } = renderWithProviders(<CheckoutPage />, { member: {}, route: `/checkout?items=${items}` })
  return { ...calls, queryClient }
}

const row = (label: string) => screen.getByText(label).closest('div')!
const payButton = () => screen.findByRole('button', { name: /결제하기$/ })

it('잘못된 items면 요청 없이 안내한다', async () => {
  const calls = setup({ items: '1,1' })
  expect(await screen.findByText('주문할 상품을 다시 선택해 주세요.')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '장바구니로 가기' })).toHaveAttribute('href', '/cart')
  expect(calls.previews).toHaveLength(0)
})

it('기본 배송지와 미리보기 금액을 보여 준다', async () => {
  const calls = setup()
  expect(await screen.findByText('받는이1')).toBeInTheDocument()
  expect(screen.getByText('01012340001')).toBeInTheDocument()
  expect(screen.getByText('(06236) 서울시 1번지 101호')).toBeInTheDocument()
  expect(await screen.findByText('상품1')).toBeInTheDocument()
  expect(screen.getByText('옵션2 · 1개')).toBeInTheDocument()
  expect(row('상품 금액')).toHaveTextContent('17,000원')
  expect(row('세일 할인')).toHaveTextContent('-2,000원')
  expect(row('쿠폰 할인')).toHaveTextContent('-0원')
  expect(row('배송비')).toHaveTextContent('2,500원')
  expect(row('적립금 사용')).toHaveTextContent('-0원')
  expect(row('결제금액')).toHaveTextContent('17,500원')
  expect(screen.getByText('보유 50,000원')).toBeInTheDocument()
  expect(await payButton()).toHaveAccessibleName('17,500원 결제하기')
  expect(screen.getByRole('button', { name: '17,500원 결제하기' })).toBeEnabled()
  expect(calls.previews).toEqual([{ cartItemIds: [1, 2], memberCouponId: null, points: 0 }])
})

it('변경으로 다른 배송지를 고른다', async () => {
  setup()
  await userEvent.click(await screen.findByRole('button', { name: '변경' }))
  const dialog = screen.getByRole('dialog', { name: '배송지 선택' })
  await userEvent.click(within(dialog).getByRole('radio', { name: /받는이2/ }))
  await userEvent.click(within(dialog).getByRole('button', { name: '선택' }))
  expect(screen.getByText('(06236) 서울시 2번지 101호')).toBeInTheDocument()
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
})

it('쿠폰을 고르면 memberCouponId로 다시 미리보기한다', async () => {
  const calls = setup({
    coupons: [
      coupon(7, { name: '3천원 쿠폰' }),
      coupon(8, { status: 'USED' }),
      coupon(9, { useEndsAt: iso(-1000) }),
      coupon(10, { useStartsAt: iso(DAY) }),
    ],
  })
  const select = await screen.findByRole('combobox', { name: '쿠폰' })
  await screen.findByText('상품1')
  expect(within(select).getAllByRole('option').map((o) => o.textContent)).toEqual(['쿠폰 사용 안 함', '3천원 쿠폰 · 3,000원 할인'])
  await userEvent.selectOptions(select, '3천원 쿠폰 · 3,000원 할인')
  await waitFor(() => expect(row('쿠폰 할인')).toHaveTextContent('-3,000원'))
  expect(calls.previews.at(-1)).toEqual({ cartItemIds: [1, 2], memberCouponId: 7, points: 0 })
})

it('최소 주문금액 미달 쿠폰은 고를 수 없다', async () => {
  setup({ coupons: [coupon(7, { name: '큰 쿠폰', minOrderAmount: 50000 })] })
  await screen.findByText('상품1')
  expect(screen.getByRole('option', { name: '큰 쿠폰 · 3,000원 할인 (최소 주문금액 미달)' })).toBeDisabled()
})

it('적립금 모두 사용은 결제금액 100원을 남긴다', async () => {
  const calls = setup({ items: '3' })
  await screen.findByText('상품3')
  await userEvent.click(screen.getByRole('button', { name: '모두 사용' }))
  await waitFor(() => expect(row('결제금액')).toHaveTextContent('100원'))
  expect(screen.getByRole('spinbutton', { name: '사용할 적립금' })).toHaveValue(19900)
  expect(calls.previews.at(-1)).toEqual({ cartItemIds: [3], memberCouponId: null, points: 19900 })
})

it('적립금 입력의 음수·소수는 0 이상 정수가 된다', async () => {
  const calls = setup()
  await screen.findByText('상품1')
  const input = screen.getByRole('spinbutton', { name: '사용할 적립금' })
  await userEvent.clear(input)
  await userEvent.type(input, '-500')
  await userEvent.tab()
  expect(input).toHaveValue(0)
  await userEvent.clear(input)
  await userEvent.type(input, '1.5')
  await userEvent.click(screen.getByRole('button', { name: '적용' }))
  expect(input).toHaveValue(1)
  await waitFor(() => expect(row('적립금 사용')).toHaveTextContent('-1원'))
  expect(calls.previews.map((b) => b.points)).toEqual([0, 1])
})

it('미리보기 오류면 메시지를 보여 주고 결제하기를 막는다', async () => {
  setup()
  await screen.findByText('상품1')
  const input = screen.getByRole('spinbutton', { name: '사용할 적립금' })
  await userEvent.clear(input)
  await userEvent.type(input, '17450')
  await userEvent.click(screen.getByRole('button', { name: '적용' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('결제금액이 100원 이상 남아야 합니다.')
  expect(screen.queryByRole('link', { name: '장바구니로 가기' })).not.toBeInTheDocument()
  expect(screen.getByText('상품1')).toBeInTheDocument()
  expect(await payButton()).toBeDisabled()
})

it('줄 문제 오류면 장바구니 링크를 함께 보여 준다', async () => {
  setup()
  server.use(
    http.post('*/api/members/me/checkout/preview', () =>
      HttpResponse.json({ code: 'CART_ITEM_UNAVAILABLE', message: '구매할 수 없는 상품이 있습니다.', errors: [] }, { status: 409 }),
    ),
  )
  expect(await screen.findByRole('alert')).toHaveTextContent('구매할 수 없는 상품이 있습니다.')
  expect(screen.getByRole('link', { name: '장바구니로 가기' })).toHaveAttribute('href', '/cart')
})

it('결제하기는 주문을 만들고 결제창을 연다', async () => {
  let resolve!: () => void
  vi.mocked(startTossPayment).mockImplementation(() => new Promise<void>((r) => (resolve = r)))
  const calls = setup()
  await screen.findByText('상품1')
  await userEvent.click(await payButton())
  await waitFor(() => expect(startTossPayment).toHaveBeenCalledTimes(1))
  expect(calls.orders).toEqual([{ cartItemIds: [1, 2], memberCouponId: null, points: 0, addressId: 1 }])
  expect(startTossPayment).toHaveBeenCalledWith({ orderNumber: NO, status: 'PENDING' }, { email: 'user@example.com', name: '홍길동' })
  expect(await payButton()).toBeDisabled()
  await act(async () => resolve())
  expect(await payButton()).toBeEnabled()
})

it('주문 생성 실패면 서버 메시지를 보여 준다', async () => {
  setup()
  server.use(
    http.post('*/api/members/me/orders', () =>
      HttpResponse.json({ code: 'STOCK_NOT_ENOUGH', message: '재고가 부족합니다.', errors: [] }, { status: 409 }),
    ),
  )
  await screen.findByText('상품1')
  await userEvent.click(await payButton())
  expect(await screen.findByRole('alert')).toHaveTextContent('재고가 부족합니다.')
  expect(startTossPayment).not.toHaveBeenCalled()
  expect(await payButton()).toBeEnabled()
})

it('결제창을 닫으면 다시 결제 안내와 주문 상세 링크', async () => {
  vi.mocked(startTossPayment).mockRejectedValue(new TossCancelError('결제를 취소했습니다.'))
  setup()
  await screen.findByText('상품1')
  await userEvent.click(await payButton())
  expect(await screen.findByRole('alert')).toHaveTextContent('결제가 취소되었습니다. 주문 상세에서 15분 안에 다시 결제할 수 있습니다.')
  expect(screen.getByRole('link', { name: '주문 상세 보기' })).toHaveAttribute('href', `/mypage/orders/${NO}`)
})

it('결제창 오류면 그 메시지와 주문 상세 링크', async () => {
  vi.mocked(startTossPayment).mockRejectedValue(new Error('결제 설정이 없습니다. 관리자에게 문의해 주세요.'))
  setup()
  await screen.findByText('상품1')
  await userEvent.click(await payButton())
  expect(await screen.findByRole('alert')).toHaveTextContent('결제 설정이 없습니다. 관리자에게 문의해 주세요.')
  expect(screen.getByRole('link', { name: '주문 상세 보기' })).toHaveAttribute('href', `/mypage/orders/${NO}`)
})

it('배송지가 없으면 피커에서 추가한 배송지를 고른다', async () => {
  let list: Address[] = []
  setup({ addresses: [] })
  server.use(
    http.get('*/api/members/me/addresses', () => HttpResponse.json(list)),
    http.post('*/api/members/me/addresses', async ({ request }) => {
      list = [{ ...((await request.json()) as Omit<Address, 'id'>), id: 5 }]
      return HttpResponse.json(list[0], { status: 201 })
    }),
  )
  expect(await screen.findByText('배송지를 등록해 주세요.')).toBeInTheDocument()
  await screen.findByText('상품1')
  expect(await payButton()).toBeDisabled()
  await userEvent.click(screen.getByRole('button', { name: '배송지 등록' }))
  const dialog = screen.getByRole('dialog', { name: '배송지 선택' })
  await userEvent.type(within(dialog).getByLabelText('받는 사람'), '새사람')
  await userEvent.type(within(dialog).getByLabelText('휴대폰 번호'), '01099998888')
  await userEvent.type(within(dialog).getByLabelText('우편번호'), '12345')
  await userEvent.type(within(dialog).getByLabelText('기본 주소'), '부산시 1번지')
  await userEvent.click(within(dialog).getByRole('button', { name: '저장' }))
  expect(await screen.findByText('새사람')).toBeInTheDocument()
  expect(screen.queryByText('배송지를 등록해 주세요.')).not.toBeInTheDocument()
  expect(await payButton()).toBeEnabled()
})

it('적용하지 않은 적립금이 있으면 결제하기를 막고 안내한다', async () => {
  const calls = setup()
  await screen.findByText('상품1')
  const input = screen.getByRole('spinbutton', { name: '사용할 적립금' })
  expect(screen.queryByRole('status')).not.toBeInTheDocument()
  await userEvent.clear(input)
  await userEvent.type(input, '5000')
  expect(screen.getByRole('status')).toHaveTextContent('적용을 눌러야 적립금이 반영됩니다.')
  expect(await payButton()).toBeDisabled()
  await userEvent.click(screen.getByRole('button', { name: '적용' }))
  await waitFor(() => expect(row('적립금 사용')).toHaveTextContent('-5,000원'))
  expect(calls.previews.at(-1)).toEqual({ cartItemIds: [1, 2], memberCouponId: null, points: 5000 })
  expect(screen.queryByRole('status')).not.toBeInTheDocument()
  expect(await payButton()).toBeEnabled()
})

it('고른 쿠폰이 더는 쓸 수 없으면 선택을 풀고 다시 미리보기한다', async () => {
  const usable = coupon(1, { name: '3천원 쿠폰' })
  const calls = setup({ coupons: [usable] })
  const select = await screen.findByRole('combobox', { name: '쿠폰' })
  await screen.findByText('상품1')
  await userEvent.selectOptions(select, '3천원 쿠폰 · 3,000원 할인')
  await waitFor(() => expect(calls.previews.at(-1)?.memberCouponId).toBe(1))
  server.use(http.get('*/api/members/me/coupons', () => HttpResponse.json([{ ...usable, status: 'USED' }])))
  await act(() => calls.queryClient.invalidateQueries({ queryKey: ['myCoupons'] }))
  await waitFor(() => expect(select).toHaveValue(''))
  await waitFor(() => expect(calls.previews.at(-1)?.memberCouponId).toBeNull())
})

it('토스 키가 없으면 주문을 만들지 않고 안내한다', async () => {
  vi.mocked(isTossConfigured).mockReturnValue(false)
  const calls = setup()
  await screen.findByText('상품1')
  await userEvent.click(await payButton())
  expect(await screen.findByRole('alert')).toHaveTextContent('결제 설정이 없습니다. 관리자에게 문의해 주세요.')
  expect(calls.orders).toHaveLength(0)
  expect(startTossPayment).not.toHaveBeenCalled()
  expect(await payButton()).toBeEnabled()
})
