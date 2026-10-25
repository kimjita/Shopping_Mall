import { useState } from 'react'
import { useDownloadableCoupons, useDownloadCoupon } from '../api/coupons'
import { ApiError } from '../api/client'
import { SectionError } from '../components/SectionError'
import { Spinner } from '../components/Spinner'
import { formatDateTime } from '../lib/format'
import { couponDiscountText, GRADE_LABEL, minOrderText } from '../lib/labels'

export function CouponsPage() {
  const q = useDownloadableCoupons()
  const download = useDownloadCoupon()
  const [pendingIds, setPendingIds] = useState<Set<number>>(new Set())

  if (!q.data) return q.isError ? <SectionError onRetry={() => q.refetch()} /> : <Spinner />
  // 안내는 공유 mutation의 가장 최근 결과이고, 대기 표시는 동시에 여러 쿠폰이 진행될 수 있어 id별로 따로 둔다.
  const setPending = (id: number, on: boolean) =>
    setPendingIds((prev) => {
      const next = new Set(prev)
      if (on) next.add(id)
      else next.delete(id)
      return next
    })
  const receive = (id: number) => {
    setPending(id, true)
    // mutate()의 콜백은 마지막 호출에만 불리므로, 호출마다 끝나는 mutateAsync로 해제한다. 오류는 download.error로 표시한다.
    download
      .mutateAsync(id)
      .catch(() => {})
      .finally(() => setPending(id, false))
  }

  return (
    <section className="mx-auto max-w-2xl space-y-4 px-4 py-6">
      <h1 className="text-xl font-bold">쿠폰 받기</h1>
      {download.isSuccess && (
        <p role="status" className="text-sm text-brand-dark">
          쿠폰을 받았습니다.
        </p>
      )}
      {download.isError && (
        <p role="alert" className="text-sm text-sale">
          {download.error instanceof ApiError ? download.error.message : '잠시 후 다시 시도해 주세요.'}
        </p>
      )}
      {q.data.length === 0 ? (
        <p className="py-10 text-center text-ink/60">지금 받을 수 있는 쿠폰이 없습니다.</p>
      ) : (
        <ul className="space-y-3">
          {q.data.map((c) => (
            <li key={c.couponId} className="space-y-1 rounded border border-ink/10 p-4">
              <p className="font-medium">{c.name}</p>
              <p className="text-sm font-semibold text-brand-dark">{couponDiscountText(c)}</p>
              {c.minOrderAmount > 0 && <p className="text-sm">{minOrderText(c.minOrderAmount)}</p>}
              <p className="text-sm text-ink/60">남은 수량 {c.remainingQuantity}장</p>
              <p className="text-sm text-ink/60">{formatDateTime(c.issueEndsAt)}까지 받을 수 있습니다.</p>
              <p className="text-sm text-ink/60">
                사용 기간 {formatDateTime(c.useStartsAt)} ~ {formatDateTime(c.useEndsAt)}
              </p>
              <button
                type="button"
                disabled={!c.eligible || pendingIds.has(c.couponId)}
                onClick={() => receive(c.couponId)}
                className="mt-2 rounded bg-brand px-4 py-2 text-sm font-semibold text-white disabled:opacity-40"
              >
                받기
              </button>
              {!c.eligible && c.requiredGrade && (
                <p className="text-sm text-ink/60">{GRADE_LABEL[c.requiredGrade]} 등급 이상 받을 수 있습니다.</p>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
