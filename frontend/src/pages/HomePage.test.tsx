import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { expect, it, vi } from 'vitest'
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

function mockHome(overrides: { banners?: Banner[]; rankingsStatus?: number; emptyLists?: boolean } = {}) {
  server.use(
    http.get('*/api/banners', () => HttpResponse.json(overrides.banners ?? [])),
    http.get('*/api/products', ({ request }) => {
      const q = new URL(request.url).searchParams
      const name = q.get('onSale') ? '특가 토너' : '신상 크림'
      // emptyLists여도 신상품은 남겨 두어 로딩이 끝난 시점을 알 수 있게 한다.
      if (overrides.emptyLists && q.get('onSale')) return HttpResponse.json({ items: [], nextCursor: null })
      return HttpResponse.json({ items: [product(q.get('onSale') ? 1 : 2, name)], nextCursor: null })
    }),
    http.get('*/api/rankings', () =>
      overrides.emptyLists
        ? HttpResponse.json({ items: [] })
        : overrides.rankingsStatus
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

it('랭킹 카드에 순위 배지와 안내 문구를 보인다', async () => {
  mockHome()
  renderWithProviders(<HomePage />)
  expect(await screen.findByText('랭킹 에센스')).toBeInTheDocument()
  expect(screen.getByText('1')).toBeInTheDocument()
  expect(screen.getByText('결제 기준 최근 7일 판매량 · 매시 정각 갱신')).toBeInTheDocument()
})

it('데이터가 빈 배너·특가·랭킹은 제목째 숨긴다', async () => {
  mockHome({ emptyLists: true })
  const { container } = renderWithProviders(<HomePage />)
  expect(await screen.findByText('신상 크림')).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: '오늘의 특가' })).not.toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: '랭킹' })).not.toBeInTheDocument()
  expect(container.querySelector('img')).toBeNull()
  expect(screen.queryByRole('status')).not.toBeInTheDocument()
})

it('안전하지 않은 배너 링크는 건너뛰고 나머지는 그린다', async () => {
  mockHome({
    banners: [
      { ...banner('http://'), id: 1, title: '깨진 배너' },
      { ...banner('//evil.com/x'), id: 2, title: '프로토콜 상대 배너' },
      { ...banner('/events/ok'), id: 3, title: '정상 배너' },
    ],
  })
  renderWithProviders(<HomePage />)
  expect(await screen.findByRole('link', { name: '정상 배너' })).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: '깨진 배너' })).not.toBeInTheDocument()
  expect(screen.queryByRole('link', { name: '프로토콜 상대 배너' })).not.toBeInTheDocument()
  expect(await screen.findByText('신상 크림')).toBeInTheDocument()
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

it('비회원이 고른 피부 타입의 추천이 비면 안내 문구를 보인다', async () => {
  mockHome()
  server.use(http.get('*/api/recommendations', () => HttpResponse.json({ skinType: 'OILY', items: [] })))
  renderWithProviders(<HomePage />)
  await userEvent.click(await screen.findByRole('radio', { name: '지성' }))
  expect(await screen.findByText('아직 추천할 상품이 없습니다.')).toBeInTheDocument()
  expect(screen.getByRole('radiogroup', { name: '피부 타입' })).toBeInTheDocument()
})

it('회원 피부 타입의 추천이 비면 섹션을 제목째 숨긴다', async () => {
  mockHome()
  let called = false
  server.use(
    http.get('*/api/members/me/wishlist/ids', () => HttpResponse.json({ productIds: [] })),
    http.get('*/api/recommendations', () => {
      called = true
      return HttpResponse.json({ skinType: 'DRY', items: [] })
    }),
  )
  renderWithProviders(<HomePage />, { member: { skinType: 'DRY' } })
  expect(await screen.findByText('신상 크림')).toBeInTheDocument()
  await vi.waitFor(() => expect(called).toBe(true))
  await vi.waitFor(() => expect(screen.queryByRole('heading', { name: '피부 타입 추천' })).not.toBeInTheDocument())
})
