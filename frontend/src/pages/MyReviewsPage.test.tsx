import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { expect, it, vi } from 'vitest'
import type { MyReview, WritableReview } from '../api/types'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { MyReviewsPage } from './MyReviewsPage'

const writable = (id: number, over: Partial<WritableReview> = {}): WritableReview => ({
  orderItemId: id,
  orderNumber: `20261025-ORDER00${id}`,
  productId: 7,
  productName: `토너${id}`,
  skuName: '기본',
  thumbnailUrl: null,
  confirmedAt: '2026-10-25T03:10:30Z',
  ...over,
})
const review = (id: number, over: Partial<MyReview> = {}): MyReview => ({
  reviewId: id,
  orderItemId: id,
  productId: 7,
  productName: `토너${id}`,
  skuName: '기본',
  thumbnailUrl: null,
  rating: 5,
  content: `촉촉하고 순해서 좋아요 ${id}`,
  imageUrls: [],
  skinType: null,
  skinTone: null,
  skinConcerns: [],
  createdAt: '2026-10-25T03:10:30Z',
  updatedAt: '2026-10-25T03:10:30Z',
  ...over,
})

const dialog = (name: string | RegExp = /^리뷰 (쓰기|수정)$/) => screen.getByRole('dialog', { name, hidden: true })

function setup(opts: { writable?: WritableReview[]; mine?: MyReview[]; route?: string } = {}) {
  const calls = { writableGets: 0, post: undefined as unknown, put: undefined as unknown, deleted: [] as string[] }
  server.use(
    http.get('*/api/members/me/reviews/writable', () => {
      calls.writableGets++
      return HttpResponse.json({ items: opts.writable ?? [], nextCursor: null })
    }),
    http.get('*/api/members/me/reviews', () => HttpResponse.json({ items: opts.mine ?? [], nextCursor: null })),
    http.post('*/api/members/me/reviews', async ({ request }) => {
      calls.post = await request.json()
      return HttpResponse.json(review(1), { status: 201 })
    }),
    http.put('*/api/members/me/reviews/:id', async ({ request }) => {
      calls.put = await request.json()
      return HttpResponse.json(review(1))
    }),
    http.delete('*/api/members/me/reviews/:id', ({ params }) => {
      calls.deleted.push(String(params.id))
      return new HttpResponse(null, { status: 204 })
    }),
  )
  const r = renderWithProviders(<MyReviewsPage />, { route: opts.route ?? '/mypage/reviews', member: {} })
  return { calls, ...r }
}

const fill = async (stars: number, text: string) => {
  const d = within(dialog())
  await userEvent.click(d.getByRole('radio', { name: `별점 ${stars}점`, hidden: true }))
  const box = d.getByLabelText('리뷰 내용')
  await userEvent.clear(box)
  await userEvent.type(box, text)
}

it('작성 가능한 리뷰에 쓰면 POST하고 목록을 다시 읽는다', async () => {
  const { calls } = setup({ writable: [writable(1)] })
  const row = (await screen.findByText('토너1')).closest('li')!
  expect(row).toHaveTextContent('기본')
  expect(row).toHaveTextContent('2026. 10. 25. 12:10 구매확정')
  await userEvent.click(within(row).getByRole('button', { name: '리뷰 쓰기' }))
  await fill(4, '촉촉하고 순해서 좋아요')
  const before = calls.writableGets
  await userEvent.click(within(dialog()).getByRole('button', { name: '등록', hidden: true }))
  expect(await screen.findByRole('status')).toHaveTextContent('리뷰를 등록했습니다.')
  expect(calls.post).toEqual({ orderItemId: 1, rating: 4, content: '촉촉하고 순해서 좋아요', imageUrls: [] })
  await vi.waitFor(() => expect(calls.writableGets).toBeGreaterThan(before))
  expect(document.querySelector('dialog[open]')).toBeNull()
})

it('작성할 리뷰가 없으면 안내한다', async () => {
  setup()
  expect(await screen.findByText('작성할 리뷰가 없습니다.')).toBeInTheDocument()
})

it('tab 쿼리로 내 리뷰 탭을 연다', async () => {
  setup({ mine: [review(1)], route: '/mypage/reviews?tab=mine' })
  expect(await screen.findByText('촉촉하고 순해서 좋아요 1')).toBeInTheDocument()
  expect(screen.getByRole('tab', { name: '내 리뷰' })).toHaveAttribute('aria-selected', 'true')
  const row = screen.getByText('토너1').closest('li')!
  expect(within(row).getByRole('link', { name: '토너1' })).toHaveAttribute('href', '/products/7')
  expect(row).toHaveTextContent('★ 5')
})

it('탭을 누르면 내 리뷰로 바뀐다', async () => {
  setup({ writable: [writable(1)], mine: [review(2)] })
  await screen.findByText('토너1')
  expect(screen.getByRole('tab', { name: '작성 가능한 리뷰' })).toHaveAttribute('aria-selected', 'true')
  await userEvent.click(screen.getByRole('tab', { name: '내 리뷰' }))
  expect(await screen.findByText('촉촉하고 순해서 좋아요 2')).toBeInTheDocument()
})

it('내 리뷰가 없으면 안내한다', async () => {
  setup({ route: '/mypage/reviews?tab=mine' })
  expect(await screen.findByText('작성한 리뷰가 없습니다.')).toBeInTheDocument()
})

it('사진이 있는 리뷰를 고치면 사진 목록을 그대로 보낸다', async () => {
  const imageUrls = ['https://img.example.com/a.jpg', 'https://img.example.com/b.jpg']
  const { calls } = setup({ mine: [review(1, { imageUrls })], route: '/mypage/reviews?tab=mine' })
  await userEvent.click(await screen.findByRole('button', { name: '수정' }))
  expect(within(dialog()).getByText('사진은 그대로 유지됩니다.')).toBeInTheDocument()
  expect(within(dialog()).getAllByRole('img', { hidden: true })).toHaveLength(2)
  expect(within(dialog()).getByRole('radio', { name: '별점 5점', hidden: true })).toBeChecked()
  await fill(3, '조금 건조하지만 무난해요')
  await userEvent.click(within(dialog()).getByRole('button', { name: '수정', hidden: true }))
  expect(await screen.findByRole('status')).toHaveTextContent('리뷰를 수정했습니다.')
  expect(calls.put).toEqual({ rating: 3, content: '조금 건조하지만 무난해요', imageUrls })
})

it('삭제는 확인 뒤 DELETE하고 상품 평점 캐시를 무효화한다', async () => {
  const { calls, queryClient } = setup({ mine: [review(1)], route: '/mypage/reviews?tab=mine' })
  queryClient.setQueryData(['product', 7], { id: 7 })
  await userEvent.click(await screen.findByRole('button', { name: '삭제' }))
  const d = within(dialog('리뷰 삭제'))
  expect(d.getByText('리뷰를 삭제할까요? 삭제하면 같은 상품에 다시 쓸 수 있습니다.')).toBeInTheDocument()
  expect(calls.deleted).toEqual([])
  await userEvent.click(d.getByRole('button', { name: '삭제', hidden: true }))
  await vi.waitFor(() => expect(calls.deleted).toEqual(['1']))
  await vi.waitFor(() => expect(queryClient.getQueryState(['product', 7])?.isInvalidated).toBe(true))
})

it('이미 쓴 리뷰면 서버 메시지를 dialog에 보여 준다', async () => {
  setup({ writable: [writable(1)] })
  server.use(
    http.post('*/api/members/me/reviews', () =>
      HttpResponse.json({ code: 'REVIEW_ALREADY_EXISTS', message: '이미 리뷰를 작성한 상품입니다.', errors: [] }, { status: 409 }),
    ),
  )
  await userEvent.click(await screen.findByRole('button', { name: '리뷰 쓰기' }))
  await fill(5, '촉촉하고 순해서 좋아요')
  await userEvent.click(within(dialog()).getByRole('button', { name: '등록', hidden: true }))
  expect(await within(dialog()).findByRole('alert', { hidden: true })).toHaveTextContent('이미 리뷰를 작성한 상품입니다.')
  expect(dialog()).toHaveAttribute('open')
  // 다시 열면 오류가 사라진다.
  await userEvent.click(within(dialog()).getByRole('button', { name: '취소', hidden: true }))
  await userEvent.click(screen.getByRole('button', { name: '리뷰 쓰기' }))
  expect(within(dialog()).queryByRole('alert', { hidden: true })).not.toBeInTheDocument()
})

it('다음 페이지 실패는 인라인으로 알린다', async () => {
  server.use(
    http.get('*/api/members/me/reviews/writable', ({ request }) =>
      new URL(request.url).searchParams.get('cursor')
        ? HttpResponse.json({ code: 'X', message: '실패', errors: [] }, { status: 500 })
        : HttpResponse.json({ items: [writable(1)], nextCursor: '1' }),
    ),
  )
  renderWithProviders(<MyReviewsPage />, { route: '/mypage/reviews', member: {} })
  await screen.findByText('토너1')
  await userEvent.click(screen.getByRole('button', { name: '더보기' }))
  expect(await screen.findByText('다음 리뷰를 불러오지 못했습니다.')).toBeInTheDocument()
  expect(screen.getByText('토너1')).toBeInTheDocument()
})
