import { Link } from 'react-router'
import { useOrders } from '../api/orders'
import { InfiniteSentinel } from '../components/InfiniteSentinel'
import { ProductImage } from '../components/ProductImage'
import { SectionError } from '../components/SectionError'
import { Spinner } from '../components/Spinner'
import { formatDateTime, formatWon } from '../lib/format'
import { ORDER_STATUS_LABEL } from '../lib/labels'

export function OrdersPage() {
  const q = useOrders()
  const orders = q.data?.pages.flatMap((p) => p.items) ?? []

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <h1 className="text-xl font-bold">주문 내역</h1>
      {!q.data ? (
        q.isError ? <SectionError onRetry={() => q.refetch()} /> : <Spinner />
      ) : orders.length === 0 ? (
        <p className="py-10 text-center text-ink/60">주문 내역이 없습니다.</p>
      ) : (
        <>
          <ul className="divide-y divide-ink/10">
            {orders.map((o) => (
              <li key={o.orderNumber}>
                <Link to={`/mypage/orders/${o.orderNumber}`} className="flex gap-3 py-3">
                  <ProductImage src={o.thumbnailUrl} alt={o.firstProductName} className="size-16 shrink-0 rounded" />
                  <div className="min-w-0 flex-1 space-y-1 text-sm">
                    <p className="text-ink/60">{formatDateTime(o.orderedAt)}</p>
                    <p className="font-medium">{o.itemCount > 1 ? `${o.firstProductName} 외 ${o.itemCount - 1}건` : o.firstProductName}</p>
                    <p>
                      <span className="text-brand-dark">{ORDER_STATUS_LABEL[o.status]}</span>
                      {' · '}
                      <span className="font-bold">{formatWon(o.paymentAmount)}</span>
                    </p>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
          <InfiniteSentinel onReach={() => q.fetchNextPage()} hasNext={q.hasNextPage} loading={q.isFetchingNextPage} error={q.isFetchNextPageError} />
        </>
      )}
    </div>
  )
}
