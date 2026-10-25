import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { useAddresses } from '../api/addresses'
import { useCheckoutPreview } from '../api/checkout'
import { ApiError } from '../api/client'
import { useMyCoupons } from '../api/coupons'
import { useCreateOrder } from '../api/orders'
import type { CheckoutPreview, Order } from '../api/types'
import { useAuth } from '../auth/AuthProvider'
import { AddressPicker } from '../components/AddressPicker'
import { PriceRows } from '../components/PriceRows'
import { ProductImage } from '../components/ProductImage'
import { SectionError } from '../components/SectionError'
import { Spinner } from '../components/Spinner'
import { maxUsablePoints, parseCheckoutItems, toPoints } from '../lib/checkoutParams'
import { formatWon } from '../lib/format'
import { couponDiscountText } from '../lib/labels'
import { isOrderNumber } from '../lib/orderName'
import { isTossConfigured, startTossPayment, TossCancelError } from '../payments/toss'

const errorMessage = (e: unknown) => (e instanceof ApiError ? e.message : '잠시 후 다시 시도해 주세요.')
const LINE_ERRORS = ['CART_ITEM_UNAVAILABLE', 'CART_ITEM_NOT_FOUND']
const linkClass = 'inline-block rounded border border-ink/20 px-4 py-2 text-sm'

const CartLink = () => (
  <Link to="/cart" className={linkClass}>
    장바구니로 가기
  </Link>
)

function PreviewError({ error }: { error: unknown }) {
  return (
    <div role="alert" className="space-y-3 rounded bg-sale/10 p-4 text-sm text-sale">
      <p>{errorMessage(error)}</p>
      {error instanceof ApiError && LINE_ERRORS.includes(error.code) && <CartLink />}
    </div>
  )
}

export function CheckoutPage() {
  const [params] = useSearchParams()
  const ids = parseCheckoutItems(params.get('items'))
  if (!ids)
    return (
      <div className="space-y-4 py-16 text-center">
        <p>주문할 상품을 다시 선택해 주세요.</p>
        <CartLink />
      </div>
    )
  return <Checkout key={ids.join(',')} ids={ids} />
}

function Checkout({ ids }: { ids: number[] }) {
  const { me } = useAuth()
  const addresses = useAddresses()
  const coupons = useMyCoupons()
  const create = useCreateOrder()
  const [addressId, setAddressId] = useState<number | null>(null)
  const [pickedCouponId, setCouponId] = useState<number | null>(null)
  const [draft, setDraft] = useState('0') // 입력 중인 적립금. 적용해야 미리보기에 반영한다.
  const [points, setPoints] = useState(0)
  const [paying, setPaying] = useState(false)
  const [now] = useState(Date.now) // 쿠폰 사용 기간은 화면을 연 시각으로 판단한다.
  const usableCoupons = (coupons.data ?? []).filter(
    (c) => c.status === 'AVAILABLE' && Date.parse(c.useStartsAt) <= now && now < Date.parse(c.useEndsAt),
  )
  // 고른 쿠폰이 다시 받은 목록에서 빠지면(예: 대기 주문이 썼다) 선택을 푼다.
  const couponId = usableCoupons.some((c) => c.id === pickedCouponId) ? pickedCouponId : null
  const [payError, setPayError] = useState<{ message: string; orderNumber?: string } | null>(null)
  const q = useCheckoutPreview(ids, couponId, points)

  // 마지막으로 성공한 미리보기. 다음 미리보기가 실패해도 줄과 금액을 그대로 보여 준다.
  const [good, setGood] = useState<{ couponId: number | null; data: CheckoutPreview } | null>(null)
  if (q.data && !q.isPlaceholderData && good?.data !== q.data) setGood({ couponId, data: q.data })

  if (!good) {
    if (!q.isError) return <Spinner />
    return q.error instanceof ApiError ? <PreviewError error={q.error} /> : <SectionError onRetry={() => q.refetch()} />
  }

  const { lines, price, pointBalance } = good.data
  const address = addresses.data?.find((a) => a.id === addressId) ?? addresses.data?.[0]
  const unapplied = toPoints(draft) !== points
  const canPay = !!address && !!me && q.isSuccess && !q.isPlaceholderData && !paying && !unapplied

  const apply = (n: number) => {
    setDraft(String(n))
    setPoints(n)
  }

  async function pay() {
    if (!address || !me) return
    setPaying(true)
    setPayError(null)
    if (!isTossConfigured()) {
      setPayError({ message: '결제 설정이 없습니다. 관리자에게 문의해 주세요.' })
      setPaying(false)
      return
    }
    let order: Order
    try {
      order = await create.mutateAsync({ cartItemIds: ids, memberCouponId: couponId, points, addressId: address.id })
    } catch (e) {
      setPayError({ message: errorMessage(e) })
      setPaying(false)
      return
    }
    try {
      await startTossPayment(order, { email: me.email, name: me.name })
    } catch (e) {
      const message =
        e instanceof TossCancelError
          ? '결제가 취소되었습니다. 주문 상세에서 15분 안에 다시 결제할 수 있습니다.'
          : e instanceof Error && e.message
            ? e.message
            : '잠시 후 다시 시도해 주세요.'
      setPayError({ message, orderNumber: order.orderNumber })
    }
    setPaying(false)
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-xl font-bold">주문서</h1>

      <section className="space-y-2">
        <div className="flex items-center justify-between">
          <h2 className="font-bold">배송지</h2>
          {addresses.data && <AddressPicker selectedId={address?.id ?? null} onSelect={setAddressId} />}
        </div>
        {!addresses.data ? (
          addresses.isError ? (
            <SectionError onRetry={() => addresses.refetch()} />
          ) : (
            <Spinner />
          )
        ) : address ? (
          <div className="space-y-1 text-sm">
            <p className="font-medium">{address.recipient}</p>
            <p>{address.phone}</p>
            <p className="break-words">{`(${address.zipCode}) ${address.address1} ${address.address2 ?? ''}`}</p>
          </div>
        ) : (
          <p className="text-sm text-ink/60">배송지를 등록해 주세요.</p>
        )}
      </section>

      <section className="space-y-2">
        <h2 className="font-bold">주문 상품</h2>
        <ul className="divide-y divide-ink/10">
          {lines.map((l) => (
            <li key={l.cartItemId} className="flex gap-3 py-3">
              <ProductImage src={l.thumbnailUrl} alt={l.productName} className="size-16 shrink-0 rounded" />
              <div className="min-w-0 flex-1 space-y-1 text-sm">
                <p className="font-medium">{l.productName}</p>
                <p className="text-ink/60">{`${l.skuName} · ${l.quantity}개`}</p>
                <p className="font-bold">{formatWon(l.lineTotal)}</p>
              </div>
            </li>
          ))}
        </ul>
      </section>

      <section className="space-y-2">
        <h2 className="font-bold">쿠폰</h2>
        <select
          aria-label="쿠폰"
          value={couponId ?? ''}
          onChange={(e) => setCouponId(e.target.value === '' ? null : Number(e.target.value))}
          className="w-full rounded border border-ink/20 px-3 py-2 text-sm"
        >
          <option value="">쿠폰 사용 안 함</option>
          {usableCoupons.map((c) => {
            const short = price.itemsSalePrice < c.minOrderAmount
            return (
              <option key={c.id} value={c.id} disabled={short}>
                {`${c.name} · ${couponDiscountText(c)}${short ? ' (최소 주문금액 미달)' : ''}`}
              </option>
            )
          })}
        </select>
      </section>

      <section className="space-y-2">
        <div className="flex items-center justify-between">
          <h2 className="font-bold">적립금</h2>
          <span className="text-sm text-ink/60">보유 {formatWon(pointBalance)}</span>
        </div>
        <div className="flex gap-2">
          <input
            type="number"
            min={0}
            step={1}
            inputMode="numeric"
            aria-label="사용할 적립금"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onBlur={() => setDraft(String(toPoints(draft)))}
            className="min-w-0 flex-1 rounded border border-ink/20 px-3 py-2 text-sm"
          />
          <button type="button" onClick={() => apply(toPoints(draft))} className="rounded border border-ink/20 px-3 py-2 text-sm">
            적용
          </button>
          {/* 모두 사용은 지금 고른 쿠폰으로 계산한 미리보기가 있어야 정확하다. */}
          <button
            type="button"
            disabled={good.couponId !== couponId}
            onClick={() => apply(maxUsablePoints(price, pointBalance))}
            className="rounded border border-ink/20 px-3 py-2 text-sm disabled:opacity-40"
          >
            모두 사용
          </button>
        </div>
        {unapplied && (
          <p role="status" className="text-sm text-sale">
            적용을 눌러야 적립금이 반영됩니다.
          </p>
        )}
      </section>

      <section className="space-y-3 rounded bg-brand/10 p-4">
        <PriceRows price={price} />
        {q.isError && <PreviewError error={q.error} />}
        {payError && (
          <div role="alert" className="space-y-3 text-sm text-sale">
            <p>{payError.message}</p>
            {payError.orderNumber && isOrderNumber(payError.orderNumber) && (
              <Link to={`/mypage/orders/${payError.orderNumber}`} className={linkClass}>
                주문 상세 보기
              </Link>
            )}
          </div>
        )}
        <button
          type="button"
          disabled={!canPay}
          onClick={pay}
          className="w-full rounded bg-brand px-6 py-3 font-bold text-white disabled:opacity-40"
        >
          {formatWon(price.paymentAmount)} 결제하기
        </button>
      </section>
    </div>
  )
}
