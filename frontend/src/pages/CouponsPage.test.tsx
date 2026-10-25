import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { expect, it, vi } from 'vitest'
import type { DownloadableCoupon } from '../api/types'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { CouponsPage } from './CouponsPage'

const coupon = (over: Partial<DownloadableCoupon> = {}): DownloadableCoupon => ({
  couponId: 1,
  name: '3천원 쿠폰',
  discountType: 'FIXED',
  discountValue: 3000,
  maxDiscount: null,
  minOrderAmount: 10000,
  requiredGrade: null,
  eligible: true,
  remainingQuantity: 5,
  issueEndsAt: '2026-10-31T03:00:00Z',
  useStartsAt: '2026-10-25T03:00:00Z',
  useEndsAt: '2026-11-25T03:00:00Z',
  ...over,
})

function setup(list: DownloadableCoupon[], downloadResponse?: () => Response) {
  const state = { listCalls: 0, downloads: [] as string[] }
  server.use(
    http.get('*/api/members/me/coupons/downloadable', () => {
      state.listCalls++
      return HttpResponse.json(list)
    }),
    http.get('*/api/members/me/coupons', () => HttpResponse.json([])),
    http.post('*/api/members/me/coupons/:id/download', ({ params }) => {
      state.downloads.push(String(params.id))
      return downloadResponse?.() ?? HttpResponse.json({ id: 1, couponId: 1 }, { status: 201 })
    }),
  )
  renderWithProviders(<CouponsPage />, { member: {} })
  return state
}

it('받기를 누르면 다운로드 API를 부르고 안내를 보여 준다', async () => {
  const state = setup([coupon()])
  expect(await screen.findByText('3천원 쿠폰')).toBeInTheDocument()
  expect(screen.getByText('3,000원 할인')).toBeInTheDocument()
  expect(screen.getByText('10,000원 이상 구매 시')).toBeInTheDocument()
  expect(screen.getByText('남은 수량 5장')).toBeInTheDocument()
  expect(screen.getByText('2026. 10. 31. 12:00까지 받을 수 있습니다.')).toBeInTheDocument()
  expect(screen.getByText('사용 기간 2026. 10. 25. 12:00 ~ 2026. 11. 25. 12:00')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: '받기' }))
  expect(await screen.findByRole('status')).toHaveTextContent('쿠폰을 받았습니다.')
  expect(state.downloads).toEqual(['1'])
  await vi.waitFor(() => expect(state.listCalls).toBe(2))
})

it('등급이 안 되면 버튼이 막히고 안내한다', async () => {
  setup([coupon({ requiredGrade: 'PINK', eligible: false })])
  expect(await screen.findByText('핑크 등급 이상 받을 수 있습니다.')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '받기' })).toBeDisabled()
})

it('소진되면 서버 메시지를 보여 준다', async () => {
  setup([coupon()], () =>
    HttpResponse.json({ code: 'COUPON_SOLD_OUT', message: '쿠폰이 모두 소진되었습니다.' }, { status: 409 }),
  )
  await userEvent.click(await screen.findByRole('button', { name: '받기' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('쿠폰이 모두 소진되었습니다.')
  expect(screen.queryByRole('status')).toBeNull()
})

it('받을 쿠폰이 없으면 안내 문구', async () => {
  setup([])
  expect(await screen.findByText('지금 받을 수 있는 쿠폰이 없습니다.')).toBeInTheDocument()
})

it('누른 쿠폰만 대기하고 안내는 가장 최근 결과로 바뀐다', async () => {
  let release: () => void = () => {}
  const gate = new Promise<void>((r) => (release = r))
  server.use(
    http.get('*/api/members/me/coupons/downloadable', () =>
      HttpResponse.json([coupon(), coupon({ couponId: 2, name: '두 번째 쿠폰' })]),
    ),
    http.get('*/api/members/me/coupons', () => HttpResponse.json([])),
    http.post('*/api/members/me/coupons/1/download', async () => {
      await gate
      return HttpResponse.json({ id: 1, couponId: 1 }, { status: 201 })
    }),
    http.post('*/api/members/me/coupons/2/download', () =>
      HttpResponse.json({ code: 'COUPON_ALREADY_ISSUED', message: '이미 받은 쿠폰입니다.' }, { status: 409 }),
    ),
  )
  renderWithProviders(<CouponsPage />, { member: {} })
  const [first, second] = await screen.findAllByRole('button', { name: '받기' })
  await userEvent.click(first)
  await vi.waitFor(() => expect(first).toBeDisabled())
  expect(second).toBeEnabled()
  release()
  expect(await screen.findByRole('status')).toBeInTheDocument()
  await userEvent.click(second)
  expect(await screen.findByRole('alert')).toHaveTextContent('이미 받은 쿠폰입니다.')
  expect(screen.queryByRole('status')).toBeNull()
})

it('먼저 누른 쿠폰의 요청이 끝나기 전에 다른 쿠폰을 눌러도 먼저 누른 버튼은 계속 막힌다', async () => {
  let release: () => void = () => {}
  const gate = new Promise<void>((r) => (release = r))
  const downloads: string[] = []
  server.use(
    http.get('*/api/members/me/coupons/downloadable', () =>
      HttpResponse.json([coupon(), coupon({ couponId: 2, name: '두 번째 쿠폰' })]),
    ),
    http.get('*/api/members/me/coupons', () => HttpResponse.json([])),
    http.post('*/api/members/me/coupons/:id/download', async ({ params }) => {
      downloads.push(String(params.id))
      if (params.id === '1') await gate
      return HttpResponse.json({ id: 1, couponId: 1 }, { status: 201 })
    }),
  )
  renderWithProviders(<CouponsPage />, { member: {} })
  const [first, second] = await screen.findAllByRole('button', { name: '받기' })
  await userEvent.click(first)
  await vi.waitFor(() => expect(first).toBeDisabled())
  await userEvent.click(second)
  await screen.findByRole('status')
  expect(first).toBeDisabled()
  release()
  await vi.waitFor(() => expect(first).toBeEnabled())
  expect(downloads).toEqual(['1', '2'])
})

it('받기에 성공하면 내 쿠폰 목록을 다시 받도록 표시한다', async () => {
  server.use(
    http.get('*/api/members/me/coupons/downloadable', () => HttpResponse.json([coupon()])),
    http.post('*/api/members/me/coupons/1/download', () => HttpResponse.json({ id: 1, couponId: 1 }, { status: 201 })),
  )
  const { queryClient } = renderWithProviders(<CouponsPage />, { member: {} })
  queryClient.setQueryData(['myCoupons'], [])
  expect(queryClient.getQueryState(['myCoupons'])?.isInvalidated).toBe(false)
  await userEvent.click(await screen.findByRole('button', { name: '받기' }))
  await screen.findByRole('status')
  expect(queryClient.getQueryState(['myCoupons'])?.isInvalidated).toBe(true)
})
