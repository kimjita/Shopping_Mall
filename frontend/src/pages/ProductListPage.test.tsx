import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { expect, it } from 'vitest'
import { Route, Routes, useLocation } from 'react-router'
import type { Category, CursorPage, ProductSummary } from '../api/types'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { ProductListPage } from './ProductListPage'

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

const categories: Category[] = [{ id: 1, name: '스킨케어', children: [{ id: 2, name: '토너', children: [] }] }]

function Probe() {
  const l = useLocation()
  return <output data-testid="loc">{l.pathname + l.search}</output>
}

function setup(route: string, pages: CursorPage<ProductSummary>[] = [{ items: [product(1, '수분 토너')], nextCursor: null }]) {
  const requests: URLSearchParams[] = []
  server.use(
    http.get('*/api/categories', () => HttpResponse.json(categories)),
    http.get('*/api/brands', () => HttpResponse.json([{ id: 7, name: '라네즈', description: null }])),
    http.get('*/api/products', ({ request }) => {
      const q = new URL(request.url).searchParams
      requests.push(q)
      return HttpResponse.json(q.get('cursor') ? pages[1] : pages[0])
    }),
  )
  renderWithProviders(
    <>
      <Routes>
        <Route path="/products" element={<ProductListPage />} />
      </Routes>
      <Probe />
    </>,
    { route },
  )
  return requests
}

it('URL의 조건으로 검색하고 다음 페이지를 이어 붙인다', async () => {
  const requests = setup('/products?keyword=토너&sort=PRICE_ASC', [
    { items: [product(1, '첫 토너')], nextCursor: 'c1' },
    { items: [product(2, '둘째 토너')], nextCursor: null },
  ])
  expect(await screen.findByText('첫 토너')).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: '"토너" 검색 결과' })).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: '더보기' }))
  expect(await screen.findByText('둘째 토너')).toBeInTheDocument()
  expect(screen.getByText('첫 토너')).toBeInTheDocument()
  const second = requests[1]
  expect(second.get('cursor')).toBe('c1')
  expect(second.get('sort')).toBe('PRICE_ASC')
  expect(second.get('keyword')).toBe('토너')
  expect(second.get('size')).toBe('20')
  expect(screen.queryByRole('button', { name: '더보기' })).not.toBeInTheDocument()
})

it('정렬을 바꾸면 URL이 바뀌고 처음부터 읽는다', async () => {
  const requests = setup('/products?keyword=토너')
  await screen.findByText('수분 토너')
  await userEvent.selectOptions(screen.getByLabelText('정렬'), 'PRICE_DESC')
  await waitFor(() => expect(screen.getByTestId('loc')).toHaveTextContent('/products?keyword=%ED%86%A0%EB%84%88&sort=PRICE_DESC'))
  await waitFor(() => expect(requests).toHaveLength(2))
  expect(requests[1].get('sort')).toBe('PRICE_DESC')
  expect(requests[1].get('cursor')).toBeNull()
})

it('잘못된 쿼리여도 목록을 보여 준다', async () => {
  const requests = setup('/products?sort=FOO&minPrice=abc')
  expect(await screen.findByText('수분 토너')).toBeInTheDocument()
  expect(requests[0].has('sort')).toBe(false)
  expect(requests[0].has('minPrice')).toBe(false)
  expect(screen.getByRole('heading', { name: '전체 상품' })).toBeInTheDocument()
})

it('결과가 없으면 안내 문구', async () => {
  setup('/products?keyword=없음', [{ items: [], nextCursor: null }])
  expect(await screen.findByText('조건에 맞는 상품이 없습니다.')).toBeInTheDocument()
})

it('카테고리 이름을 제목으로 쓴다', async () => {
  setup('/products?categoryId=2')
  expect(await screen.findByRole('heading', { name: '토너' })).toBeInTheDocument()
})

it('필터를 적용하면 URL이 바뀐다', async () => {
  setup('/products?keyword=토너')
  await screen.findByText('수분 토너')
  await userEvent.click(screen.getByRole('button', { name: '필터' }))
  const dialog = await screen.findByRole('dialog')
  await userEvent.click(await within(dialog).findByRole('checkbox', { name: '라네즈' }))
  await userEvent.type(within(dialog).getByLabelText('최소 가격'), '1000')
  await userEvent.click(within(dialog).getByRole('checkbox', { name: '세일만 보기' }))
  await userEvent.click(within(dialog).getByRole('button', { name: '적용' }))
  await waitFor(() => {
    const loc = screen.getByTestId('loc').textContent ?? ''
    expect(loc).toContain('brandIds=7')
    expect(loc).toContain('minPrice=1000')
    expect(loc).toContain('onSale=true')
  })
})

it('목록 요청이 실패하면 다시 시도할 수 있다', async () => {
  server.use(
    http.get('*/api/categories', () => HttpResponse.json(categories)),
    http.get('*/api/products', () => HttpResponse.json({ code: 'INTERNAL', message: '오류', errors: [] }, { status: 500 })),
  )
  renderWithProviders(
    <Routes>
      <Route path="/products" element={<ProductListPage />} />
    </Routes>,
    { route: '/products' },
  )
  expect(await screen.findByRole('alert')).toBeInTheDocument()
})

it('다음 페이지만 실패하면 읽은 목록을 두고 짧게 알린다', async () => {
  setup('/products', [
    { items: [product(1, '첫 토너')], nextCursor: 'c1' },
    { items: [product(2, '둘째 토너')], nextCursor: null },
  ])
  await screen.findByText('첫 토너')
  server.use(http.get('*/api/products', () => HttpResponse.json({ code: 'INTERNAL', message: '오류', errors: [] }, { status: 500 })))
  await userEvent.click(screen.getByRole('button', { name: '더보기' }))
  expect(await screen.findByText('다음 상품을 불러오지 못했습니다.')).toBeInTheDocument()
  expect(screen.getByText('첫 토너')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '더보기' })).toBeEnabled()
})
