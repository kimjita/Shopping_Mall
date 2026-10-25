import { usePointBalance, usePointHistory } from '../api/points'
import { InfiniteSentinel } from '../components/InfiniteSentinel'
import { SectionError } from '../components/SectionError'
import { Spinner } from '../components/Spinner'
import { formatDateTime, formatWon } from '../lib/format'
import { POINT_TYPE_LABEL, signedPoints } from '../lib/labels'

export function PointsPage() {
  const balance = usePointBalance()
  const q = usePointHistory()
  const items = q.data?.pages.flatMap((p) => p.items) ?? []

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <h1 className="text-xl font-bold">적립금</h1>
      {balance.data && (
        <p className="rounded bg-ink/5 p-4">
          <span className="text-sm text-ink/60">보유 적립금</span>
          <span className="block text-2xl font-bold">{formatWon(balance.data.balance)}</span>
        </p>
      )}
      {!q.data ? (
        q.isError ? <SectionError onRetry={() => q.refetch()} /> : <Spinner />
      ) : items.length === 0 ? (
        <p className="py-10 text-center text-ink/60">적립금 내역이 없습니다.</p>
      ) : (
        <>
          <ul className="divide-y divide-ink/10">
            {items.map((i) => (
              <li key={i.id} className="flex items-start justify-between gap-3 py-3 text-sm">
                <div className="min-w-0 space-y-1">
                  <p className="text-ink/60">{formatDateTime(i.createdAt)}</p>
                  <p className="font-medium">{POINT_TYPE_LABEL[i.type]}</p>
                  {i.reason && <p className="break-words text-ink/60">{i.reason}</p>}
                </div>
                <div className="shrink-0 text-right">
                  <p className={i.type === 'USE' ? 'font-bold text-sale' : 'font-bold text-brand-dark'}>{signedPoints(i.type, i.amount)}</p>
                  <p className="text-ink/60">잔액 {formatWon(i.balanceAfter)}</p>
                </div>
              </li>
            ))}
          </ul>
          <InfiniteSentinel
            onReach={() => q.fetchNextPage()}
            hasNext={q.hasNextPage}
            loading={q.isFetchingNextPage}
            error={q.isFetchNextPageError}
            errorText="다음 내역을 불러오지 못했습니다."
          />
        </>
      )}
    </div>
  )
}
