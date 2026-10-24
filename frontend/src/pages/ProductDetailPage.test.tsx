import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse, type RequestHandler } from 'msw'
import { expect, it } from 'vitest'
import { Link, Route, Routes, useLocation } from 'react-router'
import type { CartItem, CursorPage, Me, ProductDetail, Review } from '../api/types'
import { guestRecentViews } from '../lib/guestRecentViews'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { ProductDetailPage } from './ProductDetailPage'

const detail = (over: Partial<ProductDetail> = {}): ProductDetail => ({
  id: 5,
  name: '수분 토너',
  brand: { id: 1, name: '라네즈' },
  categoryPath: [],
  summary: '촉촉한 토너',
  description: '자세한 설명입니다',
  ingredients: null,
  imageUrls: ['https://img/a.jpg', 'https://img/b.jpg'],
  basePrice: 10000,
  saleRate: null,
  soldOut: false,
  skus: [
    { id: 1, name: '200ml', price: 10000, salePrice: 10000, soldOut: false },
    { id: 2, name: '500ml', price: 20000, salePrice: 20000, soldOut: true },
  ],
  ratingAvg: 4.5,
  reviewCount: 1,
  ...over,
})

const review = (id: number, over: Partial<Review> = {}): Review => ({
  reviewId: id,
  authorName: '김**',
  rating: 5,
  content: `리뷰 본문 ${id}`,
  imageUrls: [],
  skuName: '200ml',
  skinType: 'OILY',
  skinTone: null,
  skinConcerns: [],
  createdAt: '2026-10-21T02:00:00Z',
  updatedAt: '2026-10-21T02:00:00Z',
  ...over,
})

function Probe() {
  const l = useLocation()
  return <output data-testid="loc">{l.pathname + l.search}</output>
}

interface Opts {
  product?: ProductDetail
  member?: Partial<Me>
  route?: string
  // 기본 핸들러보다 먼저 적용되도록 마지막에 등록한다.
  handlers?: RequestHandler[]
}

function setup(opts: Opts = {}) {
  const reviewReqs: URLSearchParams[] = []
  const recentPosts: unknown[] = []
  server.use(
    http.get('*/api/products/:id', ({ params }) =>
      HttpResponse.json(
        params.id === '6'
          ? detail({ id: 6, name: '다른 토너', skus: [{ id: 9, name: '단일', price: 3000, salePrice: 3000, soldOut: false }], imageUrls: [] })
          : (opts.product ?? detail()),
      ),
    ),
    http.get('*/api/products/:id/reviews', ({ request }) => {
      const q = new URL(request.url).searchParams
      reviewReqs.push(q)
      const page: CursorPage<Review> = q.get('cursor')
        ? { items: [review(2)], nextCursor: null }
        : { items: [review(1)], nextCursor: null }
      return HttpResponse.json(page)
    }),
    http.get('*/api/members/me/wishlist/ids', () => HttpResponse.json({ productIds: [] })),
    http.get('*/api/members/me/cart', () => HttpResponse.json({ items: [] })),
    http.post('*/api/members/me/recent-views', async ({ request }) => {
      recentPosts.push(await request.json())
      return new HttpResponse(null, { status: 204 })
    }),
  )
  server.use(...(opts.handlers ?? []))
  const { queryClient } = renderWithProviders(
    <>
      <Routes>
        <Route path="/products/:productId" element={<ProductDetailPage />} />
        <Route path="/login" element={<p>로그인 화면</p>} />
        <Route path="/cart" element={<p>장바구니 화면</p>} />
        <Route path="/" element={<p>홈 화면</p>} />
      </Routes>
      <Probe />
      <Link to="/products/6">다른 상품</Link>
    </>,
    { route: opts.route ?? '/products/5', member: opts.member },
  )
  return { reviewReqs, recentPosts, queryClient }
}

const cartItem: CartItem = {
  id: 1,
  skuId: 1,
  skuName: '200ml',
  productId: 5,
  productName: '수분 토너',
  brandName: '라네즈',
  thumbnailUrl: null,
  quantity: 2,
  unitPrice: 10000,
  unitSalePrice: 10000,
  lineTotal: 20000,
  status: 'AVAILABLE',
}

it('옵션을 고르고 담으면 장바구니 API를 부르고 안내를 띄운다', async () => {
  const posts: unknown[] = []
  server.use(
    http.post('*/api/members/me/cart/items', async ({ request }) => {
      posts.push(await request.json())
      return HttpResponse.json(cartItem, { status: 201 })
    }),
  )
  setup({ member: {} })
  const user = userEvent.setup()
  const select = await screen.findByRole('combobox', { name: '옵션' })
  expect(screen.getByRole('option', { name: '500ml (품절)' })).toBeDisabled()
  await user.selectOptions(select, '200ml')
  const qty = screen.getByRole('spinbutton', { name: '수량' })
  await user.clear(qty)
  await user.type(qty, '2')
  await user.click(screen.getByRole('button', { name: '장바구니 담기' }))
  expect(await screen.findByText('장바구니에 담았습니다.')).toBeInTheDocument()
  expect(posts).toEqual([{ skuId: 1, quantity: 2 }])
  await user.click(screen.getByRole('button', { name: '장바구니로 가기' }))
  expect(await screen.findByText('장바구니 화면')).toBeInTheDocument()
})

it('옵션을 고르지 않고 담으면 안내한다', async () => {
  setup({ member: {} })
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: '장바구니 담기' }))
  expect(screen.getByText('옵션을 선택해 주세요.')).toBeInTheDocument()
})

it('비회원이 담기를 누르면 로그인으로 보낸다', async () => {
  setup()
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: '장바구니 담기' }))
  expect(await screen.findByText('로그인 화면')).toBeInTheDocument()
  expect(screen.getByTestId('loc')).toHaveTextContent('/login?redirect=%2Fproducts%2F5')
})

it('담기 실패 시 서버 메시지를 보여 준다', async () => {
  server.use(
    http.post('*/api/members/me/cart/items', () =>
      HttpResponse.json({ code: 'NOT_ENOUGH_STOCK', message: '재고가 부족합니다.', errors: [] }, { status: 409 }),
    ),
  )
  setup({ member: {} })
  const user = userEvent.setup()
  await user.selectOptions(await screen.findByRole('combobox', { name: '옵션' }), '200ml')
  await user.click(screen.getByRole('button', { name: '장바구니 담기' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('재고가 부족합니다.')
})

it('옵션이 하나면 미리 선택하고 상품이 품절이면 담기를 막는다', async () => {
  setup({ product: detail({ skus: [{ id: 9, name: '단일', price: 1000, salePrice: 1000, soldOut: true }], soldOut: true }) })
  expect(await screen.findByRole('button', { name: '품절' })).toBeDisabled()
  expect(screen.getByRole('combobox', { name: '옵션' })).toHaveValue('9')
})

it('없는 상품은 안내를 보여 준다', async () => {
  setup({
    route: '/products/999',
    handlers: [
      http.get('*/api/products/:id', () =>
        HttpResponse.json({ code: 'PRODUCT_NOT_FOUND', message: '상품을 찾을 수 없습니다.', errors: [] }, { status: 404 }),
      ),
    ],
  })
  expect(await screen.findByText('판매하지 않는 상품입니다.')).toBeInTheDocument()
  await userEvent.setup().click(screen.getByRole('link', { name: '홈으로' }))
  expect(await screen.findByText('홈 화면')).toBeInTheDocument()
})

it('잘못된 상품 번호는 요청 없이 안내한다', async () => {
  setup({ route: '/products/abc' })
  expect(await screen.findByText('판매하지 않는 상품입니다.')).toBeInTheDocument()
})

it('비회원이 보면 localStorage에 기록한다', async () => {
  setup()
  await screen.findByText('수분 토너')
  await waitFor(() => expect(guestRecentViews.read()).toEqual([5]))
})

it('회원이 보면 최근 본 상품 API를 부른다', async () => {
  const { recentPosts } = setup({ member: {} })
  await screen.findByText('수분 토너')
  await waitFor(() => expect(recentPosts).toEqual([{ productId: 5 }]))
  expect(guestRecentViews.read()).toEqual([])
})

it('리뷰 탭에서 리뷰를 보여 주고 정렬을 바꾸면 sort로 다시 부른다', async () => {
  const { reviewReqs } = setup()
  const user = userEvent.setup()
  await user.click(await screen.findByRole('tab', { name: '리뷰(1)' }))
  expect(await screen.findByText('리뷰 본문 1')).toBeInTheDocument()
  expect(screen.getByText('2026. 10. 21.')).toBeInTheDocument()
  expect(reviewReqs[0].get('sort')).toBe('LATEST')
  expect(reviewReqs[0].has('skinType')).toBe(false)
  await user.selectOptions(screen.getByRole('combobox', { name: '리뷰 정렬' }), 'RATING_DESC')
  await waitFor(() => expect(reviewReqs.some((q) => q.get('sort') === 'RATING_DESC')).toBe(true))
})

it('같은 피부타입만 보기를 켜면 skinType으로 부른다', async () => {
  const { reviewReqs } = setup({ member: { skinType: 'OILY' } })
  const user = userEvent.setup()
  await user.click(await screen.findByRole('tab', { name: '리뷰(1)' }))
  await user.click(await screen.findByRole('checkbox', { name: '나와 같은 피부타입(지성)만 보기' }))
  await waitFor(() => expect(reviewReqs.some((q) => q.get('skinType') === 'OILY')).toBe(true))
})

it('리뷰 더보기로 다음 페이지를 이어 붙이고 실패하면 다시 시도를 안내한다', async () => {
  let fail = true
  setup({
    handlers: [
      http.get('*/api/products/:id/reviews', ({ request }) => {
        const cursor = new URL(request.url).searchParams.get('cursor')
        if (!cursor) return HttpResponse.json({ items: [review(1)], nextCursor: 'c1' })
        if (fail) return HttpResponse.json({ code: 'X', message: 'x', errors: [] }, { status: 500 })
        return HttpResponse.json({ items: [review(2)], nextCursor: null })
      }),
    ],
  })
  const user = userEvent.setup()
  await user.click(await screen.findByRole('tab', { name: '리뷰(1)' }))
  await screen.findByText('리뷰 본문 1')
  await user.click(screen.getByRole('button', { name: '리뷰 더보기' }))
  expect(await screen.findByText('리뷰를 더 불러오지 못했습니다.')).toBeInTheDocument()
  expect(screen.getByText('리뷰 본문 1')).toBeInTheDocument()
  fail = false
  await user.click(screen.getByRole('button', { name: '리뷰 더보기' }))
  expect(await screen.findByText('리뷰 본문 2')).toBeInTheDocument()
})

it('리뷰가 없으면 안내하고, 전성분이 없으면 안내한다', async () => {
  setup({
    product: detail({ reviewCount: 0 }),
    handlers: [http.get('*/api/products/:id/reviews', () => HttpResponse.json({ items: [], nextCursor: null }))],
  })
  const user = userEvent.setup()
  await user.click(await screen.findByRole('tab', { name: '전성분' }))
  expect(screen.getByText('등록된 전성분 정보가 없습니다.')).toBeInTheDocument()
  await user.click(screen.getByRole('tab', { name: '리뷰(0)' }))
  expect(await screen.findByText('아직 리뷰가 없습니다.')).toBeInTheDocument()
  expect(within(screen.getByRole('tablist')).getAllByRole('tab')).toHaveLength(3)
})

it('캐시된 다른 상품으로 이동해도 선택한 옵션·수량을 비운다', async () => {
  const { queryClient } = setup()
  // 캐시가 있으면 로딩 없이 같은 화면이 이어지므로 상태가 남기 쉽다.
  queryClient.setQueryData(['product', 6], {
    ...detail({ id: 6, name: '다른 토너', imageUrls: [] }),
    skus: [{ id: 9, name: '단일', price: 3000, salePrice: 3000, soldOut: false }],
  })
  const user = userEvent.setup()
  await user.selectOptions(await screen.findByRole('combobox', { name: '옵션' }), '200ml')
  await user.click(screen.getByRole('button', { name: '수량 늘리기' }))
  expect(screen.getByRole('spinbutton', { name: '수량' })).toHaveValue(2)
  await user.click(screen.getByRole('link', { name: '다른 상품' }))
  expect(await screen.findByRole('heading', { name: '다른 토너' })).toBeInTheDocument()
  expect(screen.getByRole('combobox', { name: '옵션' })).toHaveValue('9')
  expect(screen.getByRole('spinbutton', { name: '수량' })).toHaveValue(1)
})

it('다시 읽기가 실패해도 보던 화면과 고른 옵션을 둔다', async () => {
  const { queryClient } = setup()
  const user = userEvent.setup()
  await user.selectOptions(await screen.findByRole('combobox', { name: '옵션' }), '200ml')
  server.use(http.get('*/api/products/:id', () => HttpResponse.json({ code: 'INTERNAL', message: '오류', errors: [] }, { status: 500 })))
  await act(() => queryClient.invalidateQueries({ queryKey: ['product', 5] }))
  await waitFor(() => expect(queryClient.getQueryState(['product', 5])?.status).toBe('error'))
  expect(screen.getByRole('heading', { name: '수분 토너' })).toBeInTheDocument()
  expect(screen.getByRole('combobox', { name: '옵션' })).toHaveValue('1')
})

it('옵션 안내는 보조 기술에 알린다', async () => {
  setup({ member: {} })
  await userEvent.setup().click(await screen.findByRole('button', { name: '장바구니 담기' }))
  expect(screen.getByRole('alert')).toHaveTextContent('옵션을 선택해 주세요.')
})
