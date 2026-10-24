import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { expect, it } from 'vitest'
import { Route, Routes } from 'react-router'
import { RequireAuth } from '../auth/RequireAuth'
import type { CursorPage, ProductSummary, WishItem } from '../api/types'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { WishlistPage } from './WishlistPage'

const product = (id: number, name: string): ProductSummary => ({
  id,
  name,
  brandName: '브랜드',
  thumbnailUrl: null,
  price: 10000,
  salePrice: 10000,
  saleRate: null,
  soldOut: false,
  ratingAvg: null,
  reviewCount: 0,
})

const wish = (id: number, name: string, available = true): WishItem => ({
  product: product(id, name),
  available,
  wishedAt: '2026-10-01T00:00:00',
})

function setup(pages: CursorPage<WishItem>[], member = true) {
  server.use(
    http.get('*/api/members/me/wishlist/ids', ({ request }) =>
      HttpResponse.json({ productIds: new URL(request.url).searchParams.get('productIds')?.split(',').map(Number) ?? [] }),
    ),
    http.get('*/api/members/me/wishlist', ({ request }) =>
      HttpResponse.json(new URL(request.url).searchParams.get('cursor') ? pages[1] : pages[0]),
    ),
  )
  renderWithProviders(
    <Routes>
      <Route path="/wishlist" element={<RequireAuth><WishlistPage /></RequireAuth>} />
      <Route path="/login" element={<p>로그인 화면</p>} />
    </Routes>,
    { route: '/wishlist', member: member ? {} : undefined },
  )
}

it('빈 페이지라도 nextCursor가 있으면 이어 읽는다', async () => {
  setup([
    { items: [], nextCursor: 'c1' },
    { items: [wish(1, '상품A')], nextCursor: null },
  ])
  expect(await screen.findByText('상품A')).toBeInTheDocument()
})

it('판매 중지 상품은 표시를 단다', async () => {
  setup([{ items: [wish(1, '상품A', false), wish(2, '상품B')], nextCursor: null }])
  expect(await screen.findByText('상품A')).toBeInTheDocument()
  expect(screen.getAllByText('판매 중지')).toHaveLength(1)
})

it('비면 안내를 보여 준다', async () => {
  setup([{ items: [], nextCursor: null }])
  expect(await screen.findByText('찜한 상품이 없습니다.')).toBeInTheDocument()
})

it('비회원은 로그인으로 보낸다', async () => {
  setup([{ items: [], nextCursor: null }], false)
  expect(await screen.findByText('로그인 화면')).toBeInTheDocument()
})
