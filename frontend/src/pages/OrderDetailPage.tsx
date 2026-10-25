import { useState } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { ApiError } from '../api/client'
import { useCancelOrder, useConfirmOrder, useOrder } from '../api/orders'
import { useAuth } from '../auth/AuthProvider'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { PriceRows } from '../components/PriceRows'
import { ProductImage } from '../components/ProductImage'
import { SectionError } from '../components/SectionError'
import { Spinner } from '../components/Spinner'
import { useNow } from '../hooks/useNow'
import { formatDateTime, formatWon } from '../lib/format'
import { CANCEL_REASON_LABEL, ORDER_STATUS_LABEL } from '../lib/labels'
import { isOrderNumber, isPayable } from '../lib/orderName'
import { startTossPayment, TossCancelError } from '../payments/toss'

const errorMessage = (e: unknown) => (e instanceof ApiError ? e.message : '잠시 후 다시 시도해 주세요.')
const DIALOGS = {
  cancel: { label: '주문 취소', message: '주문을 취소할까요? 결제가 취소되고 쿠폰·적립금이 돌아옵니다.' },
  confirm: { label: '구매확정', message: '구매를 확정할까요? 확정 후에는 취소할 수 없습니다.' },
}
const pad = (n: number) => String(n).padStart(2, '0')

const NotFound = () => (
  <div className="space-y-4 py-16 text-center">
    <p>주문을 찾을 수 없습니다.</p>
    <Link to="/mypage/orders" className="inline-block rounded border border-ink/20 px-4 py-2 text-sm">
      주문 내역
    </Link>
  </div>
)

export function OrderDetailPage() {
  const { orderNumber = '' } = useParams()
  const valid = isOrderNumber(orderNumber)
  const q = useOrder(orderNumber)
  if (!valid || (q.error instanceof ApiError && q.error.status === 404)) return <NotFound />
  if (!q.data) return q.isError ? <SectionError onRetry={() => q.refetch()} /> : <Spinner />
  return <Detail order={q.data} />
}

function Detail({ order }: { order: NonNullable<ReturnType<typeof useOrder>['data']> }) {
  const { me } = useAuth()
  const notice = (useLocation().state as { notice?: unknown } | null)?.notice
  const now = useNow()
  const cancel = useCancelOrder()
  const confirm = useConfirmOrder()
  const [dialog, setDialog] = useState<'cancel' | 'confirm' | null>(null)
  const [paying, setPaying] = useState(false)
  const [payError, setPayError] = useState<string | null>(null)
  const { price, shipping } = order
  const payable = isPayable(order, now)
  const left = Math.max(0, Math.ceil((Date.parse(order.expiresAt) - now) / 1000))
  const mutation = dialog === 'confirm' ? confirm : cancel
  const actionError = cancel.error ?? confirm.error

  async function pay() {
    if (!me) return
    setPaying(true)
    setPayError(null)
    try {
      await startTossPayment(order, { email: me.email, name: me.name })
    } catch (e) {
      setPayError(
        e instanceof TossCancelError
          ? '결제가 취소되었습니다. 주문 상세에서 15분 안에 다시 결제할 수 있습니다.'
          : e instanceof Error && e.message
            ? e.message
            : '잠시 후 다시 시도해 주세요.',
      )
    }
    setPaying(false)
  }

  const close = () => setDialog(null)
  const run = () => mutation.mutate(order.orderNumber, { onSuccess: close, onError: close })

  const info: [string, string | null][] = [
    ['주문번호', order.orderNumber],
    ['주문 시각', formatDateTime(order.orderedAt)],
    ['상태', ORDER_STATUS_LABEL[order.status]],
    ['쿠폰', order.couponName],
    ['취소 시각', order.canceledAt && formatDateTime(order.canceledAt)],
    ['취소 사유', order.cancelReason && CANCEL_REASON_LABEL[order.cancelReason]],
    ['배송 완료 시각', order.deliveredAt && formatDateTime(order.deliveredAt)],
    ['구매확정 시각', order.confirmedAt && formatDateTime(order.confirmedAt)],
  ]

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      {typeof notice === 'string' && notice && (
        <p role="status" className="rounded bg-brand/10 p-3 text-sm">
          {notice}
        </p>
      )}
      <h1 className="text-xl font-bold">주문 상세</h1>

      <dl className="space-y-1 text-sm">
        {info.map(([label, value]) =>
          value ? (
            <div key={label} className="flex justify-between gap-4">
              <dt className="text-ink/60">{label}</dt>
              <dd className="break-all text-right">{value}</dd>
            </div>
          ) : null,
        )}
      </dl>

      <section className="space-y-2">
        <h2 className="font-bold">주문 상품</h2>
        <ul className="divide-y divide-ink/10">
          {order.items.map((i) => (
            <li key={i.skuId}>
              <Link to={`/products/${i.productId}`} className="flex gap-3 py-3">
                <ProductImage src={i.thumbnailUrl} alt={i.productName} className="size-16 shrink-0 rounded" />
                <div className="min-w-0 flex-1 space-y-1 text-sm">
                  <p className="font-medium">{i.productName}</p>
                  <p className="text-ink/60">{`${i.skuName} · ${i.quantity}개`}</p>
                  <p className="font-bold">{formatWon(i.lineTotal)}</p>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      </section>

      <section className="space-y-2">
        <h2 className="font-bold">배송지</h2>
        <div className="space-y-1 text-sm">
          <p className="font-medium">{shipping.recipient}</p>
          <p>{shipping.phone}</p>
          <p className="break-words">{`(${shipping.zipCode}) ${shipping.address1} ${shipping.address2 ?? ''}`}</p>
        </div>
      </section>

      <section className="space-y-3 rounded bg-brand/10 p-4">
        <PriceRows price={price} />

        {payable && (
          <p className="text-sm font-medium">{`남은 시간 ${pad(Math.floor(left / 60))}:${pad(left % 60)}`}</p>
        )}
        {order.status === 'PENDING' && !payable && <p className="text-sm text-sale">결제 기한이 지났습니다.</p>}
        {(payError || actionError) && (
          <p role="alert" className="text-sm text-sale">
            {payError ?? errorMessage(actionError)}
          </p>
        )}
        {payable && (
          <button type="button" disabled={paying} onClick={pay} className="w-full rounded bg-brand px-6 py-3 font-bold text-white disabled:opacity-40">
            결제하기
          </button>
        )}
        {order.status === 'PAID' && (
          <button type="button" onClick={() => setDialog('cancel')} className="w-full rounded border border-ink/20 px-6 py-3 text-sm">
            주문 취소
          </button>
        )}
        {order.status === 'DELIVERED' && (
          <button type="button" onClick={() => setDialog('confirm')} className="w-full rounded bg-brand px-6 py-3 font-bold text-white">
            구매확정
          </button>
        )}
      </section>

      <ConfirmDialog
        open={dialog !== null}
        title={DIALOGS[dialog ?? 'cancel'].label}
        message={DIALOGS[dialog ?? 'cancel'].message}
        confirmLabel={DIALOGS[dialog ?? 'cancel'].label}
        pending={mutation.isPending}
        onConfirm={run}
        onCancel={close}
      />
    </div>
  )
}
