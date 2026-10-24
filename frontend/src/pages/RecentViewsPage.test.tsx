import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { expect, it } from 'vitest'
import type { ProductDetail, RecentView } from '../api/types'
import { guestRecentViews } from '../lib/guestRecentViews'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { RecentViewsPage } from './RecentViewsPage'

const detail = (id: number, name: string): ProductDetail => ({
  id,
  name,
  brand: { id: 1, name: '브랜드' },
  categoryPath: [],
  summary: null,
  description: '',
  ingredients: null,
  imageUrls: [],
  basePrice: 10000,
  saleRate: 10,
  soldOut: false,
  skus: [
    { id: 1, name: 'A', price: 12000, salePrice: 11000, soldOut: false },
    { id: 2, name: 'B', price: 10000, salePrice: 9000, soldOut: false },
  ],
  ratingAvg: null,
  reviewCount: 0,
})

const recent = (id: number, name: string): RecentView => ({
  product: {
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
  },
  available: true,
  viewedAt: '2026-10-01T00:00:00',
})

it('비회원은 localStorage 순서대로 상세를 불러 보여 준다', async () => {
  localStorage.setItem('dewy.recentViews', '[2,1]')
  server.use(http.get('*/api/products/:id', ({ params }) => HttpResponse.json(detail(Number(params.id), `상품${params.id}`))))
  renderWithProviders(<RecentViewsPage />, { route: '/recent' })
  const links = await screen.findAllByRole('link', { name: /상품/ })
  expect(links.map((l) => l.textContent)).toEqual([expect.stringContaining('상품2'), expect.stringContaining('상품1')])
  expect(screen.getByText('로그인하지 않으면 이 기기에만 저장됩니다.')).toBeInTheDocument()
  expect(screen.getAllByText('9,000원')).toHaveLength(2)
})

it('삭제된 상품은 목록과 저장소에서 뺀다', async () => {
  localStorage.setItem('dewy.recentViews', '[9,1]')
  server.use(
    http.get('*/api/products/9', () => HttpResponse.json({ code: 'PRODUCT_NOT_FOUND', message: '없습니다.', errors: [] }, { status: 404 })),
    http.get('*/api/products/1', () => HttpResponse.json(detail(1, '상품1'))),
  )
  renderWithProviders(<RecentViewsPage />, { route: '/recent' })
  expect(await screen.findByText('상품1')).toBeInTheDocument()
  await waitFor(() => expect(guestRecentViews.read()).toEqual([1]))
  expect(screen.getAllByRole('link')).toHaveLength(1)
})

it('깨진 저장값이면 빈 안내', async () => {
  localStorage.setItem('dewy.recentViews', 'oops')
  renderWithProviders(<RecentViewsPage />, { route: '/recent' })
  expect(await screen.findByText('최근 본 상품이 없습니다.')).toBeInTheDocument()
})

it('비회원의 삭제는 목록과 저장소에 바로 반영된다', async () => {
  localStorage.setItem('dewy.recentViews', '[2,1]')
  server.use(http.get('*/api/products/:id', ({ params }) => HttpResponse.json(detail(Number(params.id), `상품${params.id}`))))
  renderWithProviders(<RecentViewsPage />, { route: '/recent' })
  await screen.findByText('상품2')
  await userEvent.click(screen.getAllByRole('button', { name: '삭제' })[0])
  expect(screen.queryByText('상품2')).not.toBeInTheDocument()
  expect(guestRecentViews.read()).toEqual([1])
  await userEvent.click(screen.getByRole('button', { name: '전체 삭제' }))
  expect(await screen.findByText('최근 본 상품이 없습니다.')).toBeInTheDocument()
  expect(guestRecentViews.read()).toEqual([])
})

it('회원은 서버 목록을 보여 주고 전체 삭제를 부른다', async () => {
  let items = [recent(1, '서버상품'), { ...recent(2, '중지상품'), available: false }]
  let cleared = 0
  server.use(
    http.get('*/api/members/me/wishlist/ids', () => HttpResponse.json({ productIds: [] })),
    http.get('*/api/members/me/recent-views', () => HttpResponse.json(items)),
    http.delete('*/api/members/me/recent-views', () => {
      cleared++
      items = []
      return new HttpResponse(null, { status: 204 })
    }),
  )
  renderWithProviders(<RecentViewsPage />, { route: '/recent', member: {} })
  expect(await screen.findByText('서버상품')).toBeInTheDocument()
  expect(screen.getByText('판매 중지')).toBeInTheDocument()
  expect(screen.queryByText('로그인하지 않으면 이 기기에만 저장됩니다.')).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: '전체 삭제' }))
  expect(await screen.findByText('최근 본 상품이 없습니다.')).toBeInTheDocument()
  expect(cleared).toBe(1)
})

it('회원은 카드별 삭제로 한 상품을 지운다', async () => {
  let items = [recent(1, '서버상품'), recent(2, '둘째상품')]
  const deleted: string[] = []
  server.use(
    http.get('*/api/members/me/wishlist/ids', () => HttpResponse.json({ productIds: [] })),
    http.get('*/api/members/me/recent-views', () => HttpResponse.json(items)),
    http.delete('*/api/members/me/recent-views/:id', ({ params }) => {
      deleted.push(String(params.id))
      items = items.filter((i) => i.product.id !== Number(params.id))
      return new HttpResponse(null, { status: 204 })
    }),
  )
  renderWithProviders(<RecentViewsPage />, { route: '/recent', member: {} })
  await screen.findByText('서버상품')
  await userEvent.click(screen.getAllByRole('button', { name: '삭제' })[0])
  await waitFor(() => expect(screen.queryByText('서버상품')).not.toBeInTheDocument())
  expect(deleted).toEqual(['1'])
  expect(screen.getByText('둘째상품')).toBeInTheDocument()
})
