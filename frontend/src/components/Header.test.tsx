import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { useLocation } from 'react-router'
import { expect, it } from 'vitest'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { Header } from './Header'

function Loc() {
  const l = useLocation()
  return <div data-testid="loc">{l.pathname + l.search}</div>
}

const ui = (
  <>
    <Header />
    <Loc />
  </>
)

it('공백만 입력한 검색은 이동하지 않는다', async () => {
  renderWithProviders(ui, { route: '/' })
  await userEvent.type(screen.getByRole('searchbox', { name: '상품 검색' }), '   {Enter}')
  expect(screen.getByTestId('loc')).toHaveTextContent(/^\/$/)
})

it('검색하면 trim한 키워드로 목록에 간다', async () => {
  renderWithProviders(ui, { route: '/' })
  await userEvent.type(screen.getByRole('searchbox', { name: '상품 검색' }), '  토너 {Enter}')
  expect(screen.getByTestId('loc')).toHaveTextContent('/products?keyword=%ED%86%A0%EB%84%88')
})

it('회원이면 장바구니 개수를 보여 준다', async () => {
  server.use(
    http.get('*/api/members/me/cart', () =>
      HttpResponse.json({
        items: [1, 2].map((id) => ({
          id,
          skuId: id,
          skuName: '기본',
          productId: id,
          productName: '상품',
          brandName: '브랜드',
          thumbnailUrl: null,
          quantity: 1,
          unitPrice: 1000,
          unitSalePrice: 1000,
          lineTotal: 1000,
          status: 'AVAILABLE',
        })),
      }),
    ),
  )
  renderWithProviders(ui, { member: {} })
  expect(await screen.findByRole('link', { name: '장바구니 2개' })).toHaveAttribute('href', '/cart')
  expect(screen.getByRole('button', { name: '로그아웃' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '찜' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '마이' })).toHaveAttribute('href', '/mypage')
})

it('비회원은 장바구니 개수 없이 로그인 링크를 본다', async () => {
  renderWithProviders(ui)
  expect(await screen.findByRole('link', { name: '로그인' })).toHaveAttribute('href', '/login')
  expect(screen.getByRole('link', { name: '장바구니' })).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: '마이' })).not.toBeInTheDocument()
})

it('카테고리를 고르면 목록으로 이동하고 닫힌다', async () => {
  server.use(
    http.get('*/api/categories', () =>
      HttpResponse.json([{ id: 1, name: '스킨케어', children: [{ id: 2, name: '토너', children: [] }] }]),
    ),
  )
  renderWithProviders(ui)
  await userEvent.click(screen.getByRole('button', { name: '카테고리' }))
  await userEvent.click(await screen.findByRole('button', { name: '토너' }))
  expect(screen.getByTestId('loc')).toHaveTextContent('/products?categoryId=2')
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
})
