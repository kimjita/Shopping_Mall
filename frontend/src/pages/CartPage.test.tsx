import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { expect, it } from 'vitest'
import type { CartItem } from '../api/types'
import { renderWithProviders } from '../test/render'
import { server } from '../test/server'
import { CartPage } from './CartPage'

const line = (id: number, price: number, quantity = 1, status: CartItem['status'] = 'AVAILABLE'): CartItem => ({
  id,
  skuId: id,
  skuName: `옵션${id}`,
  productId: id,
  productName: `상품${id}`,
  brandName: '브랜드',
  thumbnailUrl: null,
  quantity,
  unitPrice: price,
  unitSalePrice: price,
  lineTotal: price * quantity,
  status,
})

function setup(items: CartItem[]) {
  const patches: { id: string; body: unknown }[] = []
  let current = items
  server.use(
    http.get('*/api/members/me/cart', () => HttpResponse.json({ items: current })),
    http.patch('*/api/members/me/cart/items/:id', async ({ params, request }) => {
      const body = (await request.json()) as { quantity: number }
      patches.push({ id: String(params.id), body })
      const found = current.find((i) => i.id === Number(params.id))!
      return HttpResponse.json({ ...found, quantity: body.quantity, lineTotal: found.unitSalePrice * body.quantity })
    }),
    http.delete('*/api/members/me/cart/items/:id', ({ params }) => {
      current = current.filter((i) => i.id !== Number(params.id))
      return new HttpResponse(null, { status: 204 })
    }),
  )
  renderWithProviders(<CartPage />, { member: {} })
  return patches
}

const mixed = () => [line(1, 10000), line(2, 5000), line(3, 3000, 1, 'NOT_ENOUGH_STOCK')]

it('처음에는 구매 가능한 줄만 선택되고 합계를 보여 준다', async () => {
  setup(mixed())
  expect(await screen.findByText('상품1')).toBeInTheDocument()
  expect(screen.getByRole('checkbox', { name: '상품1 선택' })).toBeChecked()
  expect(screen.getByRole('checkbox', { name: '상품3 선택' })).toBeDisabled()
  expect(screen.getByText('재고 부족')).toBeInTheDocument()
  expect(screen.getByRole('checkbox', { name: '전체 선택' })).toBeChecked()
  expect(screen.getByText('선택 상품 2개')).toBeInTheDocument()
  expect(screen.getByText('상품 금액 15,000원')).toBeInTheDocument()
  expect(screen.getByText('배송비와 쿠폰 할인은 주문서에서 계산됩니다.')).toBeInTheDocument()
})

it('선택을 풀면 합계가 줄어든다', async () => {
  setup(mixed())
  await userEvent.click(await screen.findByRole('checkbox', { name: '상품2 선택' }))
  expect(screen.getByText('선택 상품 1개')).toBeInTheDocument()
  expect(screen.getByText('상품 금액 10,000원')).toBeInTheDocument()
  expect(screen.getByRole('checkbox', { name: '전체 선택' })).not.toBeChecked()
  await userEvent.click(screen.getByRole('checkbox', { name: '전체 선택' }))
  expect(screen.getByText('상품 금액 15,000원')).toBeInTheDocument()
})

it('수량을 바꾸면 PATCH하고 줄 금액이 바뀐다', async () => {
  const patches = setup([line(1, 10000, 2)])
  await userEvent.click(await screen.findByRole('button', { name: '수량 늘리기' }))
  expect(await screen.findByText('상품 금액 30,000원')).toBeInTheDocument()
  expect(patches).toEqual([{ id: '1', body: { quantity: 3 } }])
})

it('수량을 입력할 때는 확정할 때 한 번만 PATCH한다', async () => {
  const patches = setup([line(1, 10000, 1)])
  const input = await screen.findByRole('spinbutton', { name: '수량' })
  await userEvent.clear(input)
  await userEvent.type(input, '12')
  expect(patches).toEqual([])
  await userEvent.tab()
  expect(await screen.findByText('상품 금액 120,000원')).toBeInTheDocument()
  expect(patches).toEqual([{ id: '1', body: { quantity: 12 } }])
})

it('수량 변경 실패 시 메시지를 보여 주고 서버 값으로 되돌린다', async () => {
  setup([line(1, 10000, 1)])
  server.use(
    http.patch('*/api/members/me/cart/items/:id', () =>
      HttpResponse.json({ code: 'OUT_OF_STOCK', message: '재고가 부족합니다.', errors: [] }, { status: 409 }),
    ),
  )
  await userEvent.click(await screen.findByRole('button', { name: '수량 늘리기' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('재고가 부족합니다.')
  expect(screen.getByRole('spinbutton', { name: '수량' })).toHaveValue(1)
  expect(screen.getByText('상품 금액 10,000원')).toBeInTheDocument()
})

it('입력 후 Enter로 확정한 수량이 거절되면 서버 값으로 되돌린다', async () => {
  const patches = setup([line(1, 10000, 1)])
  server.use(
    http.patch('*/api/members/me/cart/items/:id', async ({ params, request }) => {
      patches.push({ id: String(params.id), body: await request.json() })
      return HttpResponse.json({ code: 'OUT_OF_STOCK', message: '재고가 부족합니다.', errors: [] }, { status: 409 })
    }),
  )
  const input = await screen.findByRole('spinbutton', { name: '수량' })
  await userEvent.clear(input)
  await userEvent.type(input, '12{Enter}')
  expect(await screen.findByRole('alert')).toHaveTextContent('재고가 부족합니다.')
  expect(screen.getByRole('spinbutton', { name: '수량' })).toHaveValue(1)
  expect(patches).toEqual([{ id: '1', body: { quantity: 12 } }])
})

it('삭제하면 목록에서 사라진다', async () => {
  setup([line(1, 10000), line(2, 5000)])
  const row = (await screen.findByText('상품1')).closest('li')!
  await userEvent.click(within(row).getByRole('button', { name: '삭제' }))
  expect(await screen.findByText('상품 금액 5,000원')).toBeInTheDocument()
  expect(screen.queryByText('상품1')).not.toBeInTheDocument()
})

it('주문하기는 비활성', async () => {
  setup([line(1, 10000)])
  expect(await screen.findByRole('button', { name: '주문하기' })).toBeDisabled()
  expect(screen.getByText('주문 기능은 준비 중입니다.')).toBeInTheDocument()
})

it('비면 안내와 홈 링크를 보여 준다', async () => {
  setup([])
  expect(await screen.findByText('장바구니가 비어 있습니다.')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '쇼핑 계속하기' })).toHaveAttribute('href', '/')
})
