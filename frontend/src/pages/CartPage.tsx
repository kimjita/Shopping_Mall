import { useState } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../api/client'
import { useCart, useChangeQuantity, useRemoveCartItem } from '../api/cart'
import type { CartItem } from '../api/types'
import { ProductImage } from '../components/ProductImage'
import { QuantityStepper } from '../components/QuantityStepper'
import { SectionError } from '../components/SectionError'
import { Spinner } from '../components/Spinner'
import { selectedTotal } from '../lib/cartTotals'
import { formatWon } from '../lib/format'
import { CART_STATUS_LABEL } from '../lib/labels'

const errorMessage = (e: unknown) => (e instanceof ApiError ? e.message : '잠시 후 다시 시도해 주세요.')

function CartLine({ item, checked, onToggle }: { item: CartItem; checked: boolean; onToggle: () => void }) {
  // 입력 중인 수량. 서버 응답(성공·실패)이 오면 버리고 서버 값을 따른다.
  const [draft, setDraft] = useState<number | null>(null)
  const change = useChangeQuantity()
  const remove = useRemoveCartItem()
  const statusLabel = item.status === 'AVAILABLE' ? null : CART_STATUS_LABEL[item.status]
  const error = change.isError ? change.error : remove.isError ? remove.error : null

  const commit = (quantity: number) => {
    if (change.isPending) return
    if (quantity === item.quantity) return setDraft(null)
    change.mutate({ id: item.id, quantity }, { onSettled: () => setDraft(null) })
  }

  return (
    <li className="flex gap-3 py-4">
      <input
        type="checkbox"
        aria-label={`${item.productName} 선택`}
        checked={checked}
        disabled={statusLabel !== null}
        onChange={onToggle}
        className="mt-1 size-5 accent-brand"
      />
      <ProductImage src={item.thumbnailUrl} alt={item.productName} className="size-20 shrink-0 rounded" />
      <div className="min-w-0 flex-1 space-y-1">
        <p className="text-xs text-ink/60">{item.brandName}</p>
        <Link to={`/products/${item.productId}`} className="block font-medium hover:underline">
          {item.productName}
        </Link>
        <p className="text-xs text-ink/60">{item.skuName}</p>
        {statusLabel && <p className="text-sm font-medium text-sale">{statusLabel}</p>}
        <p className="text-sm">
          <span className="font-bold">{formatWon(item.unitSalePrice)}</span>
          {item.unitSalePrice < item.unitPrice && <s className="ml-1.5 text-xs text-ink/50">{formatWon(item.unitPrice)}</s>}
        </p>
        <div className="flex flex-wrap items-center gap-3">
          {/* 요청 중에는 fieldset이 버튼과 입력을 함께 막아 줄마다 요청이 겹치지 않는다. */}
          <fieldset disabled={change.isPending} className="contents">
            <QuantityStepper value={draft ?? item.quantity} onChange={setDraft} onCommit={commit} />
          </fieldset>
          <span className="font-bold">{formatWon(item.lineTotal)}</span>
          <button
            type="button"
            disabled={remove.isPending}
            onClick={() => remove.mutate(item.id)}
            className="text-sm text-ink/60 underline disabled:opacity-40"
          >
            삭제
          </button>
        </div>
        {error && (
          <p role="alert" className="text-sm text-sale">
            {errorMessage(error)}
          </p>
        )}
      </div>
    </li>
  )
}

export function CartPage() {
  const q = useCart()
  const items = q.data?.items
  // 처음 불러온 뒤에는 구매 가능한 줄을 모두 선택하고, 이후에는 사라지거나 구매 불가가 된 줄만 뺀다.
  const [selected, setSelected] = useState<Set<number> | null>(null)
  if (items) {
    const availableIds = new Set(items.filter((i) => i.status === 'AVAILABLE').map((i) => i.id))
    if (selected === null) setSelected(availableIds)
    else if ([...selected].some((id) => !availableIds.has(id))) setSelected(new Set([...selected].filter((id) => availableIds.has(id))))
  }

  if (!q.data) return q.isError ? <SectionError onRetry={() => q.refetch()} /> : <Spinner />
  const sel = selected ?? new Set<number>()
  const availableItems = q.data.items.filter((i) => i.status === 'AVAILABLE')
  const allChecked = availableItems.length > 0 && availableItems.every((i) => sel.has(i.id))
  const { count, amount } = selectedTotal(q.data.items, sel)

  const toggle = (id: number) => {
    const next = new Set(sel)
    if (!next.delete(id)) next.add(id)
    setSelected(next)
  }

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">장바구니</h1>
      {q.data.items.length === 0 ? (
        <div className="space-y-4 py-10 text-center">
          <p className="text-ink/60">장바구니가 비어 있습니다.</p>
          <Link to="/" className="inline-block rounded bg-brand px-4 py-2 text-white">
            쇼핑 계속하기
          </Link>
        </div>
      ) : (
        <>
          <label className="flex items-center gap-2 border-b border-ink/10 pb-3">
            <input
              type="checkbox"
              checked={allChecked}
              disabled={availableItems.length === 0}
              onChange={() => setSelected(allChecked ? new Set() : new Set(availableItems.map((i) => i.id)))}
              className="size-5 accent-brand"
            />
            전체 선택
          </label>
          <ul className="divide-y divide-ink/10">
            {q.data.items.map((item) => (
              <CartLine key={item.id} item={item} checked={sel.has(item.id)} onToggle={() => toggle(item.id)} />
            ))}
          </ul>
          <section className="space-y-2 rounded bg-brand/10 p-4">
            <p>선택 상품 {count}개</p>
            <p className="text-lg font-bold">상품 금액 {formatWon(amount)}</p>
            <p className="text-sm text-ink/60">배송비와 쿠폰 할인은 주문서에서 계산됩니다.</p>
            <div className="flex items-center gap-3">
              <button type="button" disabled className="rounded bg-brand px-6 py-3 font-bold text-white disabled:opacity-40">
                주문하기
              </button>
              <span className="text-sm text-ink/60">주문 기능은 준비 중입니다.</span>
            </div>
          </section>
        </>
      )}
    </div>
  )
}
