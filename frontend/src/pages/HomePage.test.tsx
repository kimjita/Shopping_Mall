import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { expect, it } from 'vitest'
import type { Banner, ProductSummary } from '../api/types'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { HomePage } from './HomePage'

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

const banner = (linkUrl: string): Banner => ({
  id: 1,
  title: '가을 이벤트',
  imageUrl: 'https://img.example.com/b.png',
  linkUrl,
  startsAt: '2026-10-01T00:00:00',
  endsAt: '2026-11-01T00:00:00',
  sortOrder: 1,
})

function mockHome(overrides: { banners?: Banner[]; rankingsStatus?: number } = {}) {
  server.use(
    http.get('*/api/banners', () => HttpResponse.json(overrides.banners ?? [])),
    http.get('*/api/products', ({ request }) => {
      const q = new URL(request.url).searchParams
      const name = q.get('onSale') ? '특가 토너' : '신상 크림'
      return HttpResponse.json({ items: [product(q.get('onSale') ? 1 : 2, name)], nextCursor: null })
    }),
    http.get('*/api/rankings', () =>
      overrides.rankingsStatus
        ? HttpResponse.json({ code: 'INTERNAL', message: '오류', errors: [] }, { status: overrides.rankingsStatus })
        : HttpResponse.json({ items: [{ rank: 1, product: product(3, '랭킹 에센스') }] }),
    ),
  )
}

const recommendation = (skinType: string) =>
  HttpResponse.json({
    skinType,
    items: [{ rank: 1, product: product(4, '추천 로션'), sameSkinReviewCount: 12, sameSkinRatingAvg: 4.56 }],
  })

it('랭킹이 실패해도 다른 섹션은 보인다', async () => {
  mockHome({ rankingsStatus: 500 })
  renderWithProviders(<HomePage />)
  expect(await screen.findByText('특가 토너')).toBeInTheDocument()
  expect(await screen.findByText('불러오지 못했습니다.')).toBeInTheDocument()
  expect(await screen.findByText('신상 크림')).toBeInTheDocument()
})

it('외부 배너는 새 탭으로 연다', async () => {
  mockHome({ banners: [banner('https://example.com/e')] })
  renderWithProviders(<HomePage />)
  const link = await screen.findByRole('link', { name: '가을 이벤트' })
  expect(link).toHaveAttribute('target', '_blank')
  expect(link.getAttribute('rel')).toContain('noopener')
})

it('내부 배너는 같은 창에서 연다', async () => {
  mockHome({ banners: [banner('/events/autumn')] })
  renderWithProviders(<HomePage />)
  const link = await screen.findByRole('link', { name: '가을 이벤트' })
  expect(link).toHaveAttribute('href', '/events/autumn')
  expect(link).not.toHaveAttribute('target')
})

it('랭킹 카드에 순위를 보이고 빈 섹션은 제목째 숨긴다', async () => {
  mockHome()
  renderWithProviders(<HomePage />)
  expect(await screen.findByText('랭킹 에센스')).toBeInTheDocument()
  expect(screen.getByText('결제 기준 최근 7일 판매량 · 매시 정각 갱신')).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: '이벤트' })).not.toBeInTheDocument()
})

it('비회원이 피부 타입을 고르면 추천을 부른다', async () => {
  mockHome()
  let requested: string | null = null
  server.use(
    http.get('*/api/recommendations', ({ request }) => {
      requested = new URL(request.url).searchParams.get('skinType')
      return recommendation('OILY')
    }),
  )
  renderWithProviders(<HomePage />)
  const chip = await screen.findByRole('radio', { name: '지성' })
  expect(chip).toHaveAttribute('aria-checked', 'false')
  await userEvent.click(chip)
  expect(await screen.findByText('추천 로션')).toBeInTheDocument()
  expect(requested).toBe('OILY')
  expect(screen.getByRole('radio', { name: '지성' })).toHaveAttribute('aria-checked', 'true')
  expect(screen.getByText('같은 피부 12명 평균 ★4.6')).toBeInTheDocument()
})

it('회원 피부 타입이 있으면 칩 없이 바로 추천을 부른다', async () => {
  mockHome()
  let requested: string | null = null
  server.use(
    http.get('*/api/members/me/wishlist/ids', () => HttpResponse.json({ productIds: [] })),
    http.get('*/api/recommendations', ({ request }) => {
      requested = new URL(request.url).searchParams.get('skinType')
      return recommendation('DRY')
    }),
  )
  renderWithProviders(<HomePage />, { member: { skinType: 'DRY' } })
  expect(await screen.findByText('추천 로션')).toBeInTheDocument()
  expect(requested).toBe('DRY')
  expect(screen.queryByRole('radiogroup')).not.toBeInTheDocument()
})
