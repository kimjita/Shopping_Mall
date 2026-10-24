import { screen } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { expect, it } from 'vitest'
import type { ProductSummary } from '../api/types'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { ProductGrid } from './ProductGrid'

const make = (id: number): ProductSummary => ({
  id,
  name: `상품${id}`,
  brandName: '브랜드',
  thumbnailUrl: null,
  price: 1000,
  salePrice: 1000,
  saleRate: null,
  soldOut: false,
  ratingAvg: null,
  reviewCount: 0,
})

it('회원이면 찜한 상품의 하트를 한 번에 채운다', async () => {
  server.use(
    http.get('*/api/members/me/wishlist/ids', ({ request }) => {
      expect(new URL(request.url).searchParams.get('productIds')).toBe('1,2')
      return HttpResponse.json({ productIds: [2] })
    }),
  )
  renderWithProviders(<ProductGrid products={[make(1), make(2)]} />, { member: {} })
  expect(await screen.findByRole('button', { name: '찜 취소' })).toHaveAttribute('aria-pressed', 'true')
  expect(screen.getAllByRole('button', { name: '찜하기' })).toHaveLength(1)
})

it('비회원이면 찜 조회 요청 없이 그린다', async () => {
  renderWithProviders(<ProductGrid products={[make(1)]} />)
  expect(await screen.findByRole('button', { name: '찜하기' })).toBeInTheDocument()
})
