import { screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import type { ProductSummary } from '../api/types'
import { renderWithProviders } from '../test/render'
import { ProductCard } from './ProductCard'

const product: ProductSummary = {
  id: 7,
  name: '수분 토너',
  brandName: '듀이랩',
  thumbnailUrl: 'https://img.example/t.jpg',
  price: 18000,
  salePrice: 18000,
  saleRate: null,
  soldOut: false,
  ratingAvg: 4.55,
  reviewCount: 12,
}

it('세일 상품은 할인율·세일가·정가를 보여 준다', () => {
  renderWithProviders(<ProductCard product={{ ...product, salePrice: 16200, saleRate: 10 }} />)
  expect(screen.getByText('10%')).toBeInTheDocument()
  expect(screen.getByText('16,200원')).toBeInTheDocument()
  expect(screen.getByText('18,000원')).toBeInTheDocument()
})

it('세일이 아니면 정가만 보여 주고 상품 링크와 평점이 있다', () => {
  renderWithProviders(<ProductCard product={product} />)
  expect(screen.getByRole('link', { name: /수분 토너/ })).toHaveAttribute('href', '/products/7')
  expect(screen.getByText('18,000원')).toBeInTheDocument()
  expect(screen.getByText('★ 4.6 (12)')).toBeInTheDocument()
})

it('리뷰가 없으면 평점을 숨긴다', () => {
  renderWithProviders(<ProductCard product={{ ...product, ratingAvg: null, reviewCount: 0 }} />)
  expect(screen.queryByText(/★/)).not.toBeInTheDocument()
})

it('썸네일이 없으면 자리 표시를 보여 준다', () => {
  renderWithProviders(<ProductCard product={{ ...product, thumbnailUrl: null }} />)
  expect(document.querySelector('img')).toBeNull()
  expect(screen.getByRole('img', { name: '수분 토너' })).toBeInTheDocument()
})

it('품절이면 품절 표시', () => {
  renderWithProviders(<ProductCard product={{ ...product, soldOut: true }} />)
  expect(screen.getByText('품절')).toBeInTheDocument()
})
