import { useDownloadableCoupons, useDownloadCoupon } from '../api/coupons'
import { ApiError } from '../api/client'
import { SectionError } from '../components/SectionError'
import { Spinner } from '../components/Spinner'
import { formatDateTime } from '../lib/format'
import { couponDiscountText, GRADE_LABEL, minOrderText } from '../lib/labels'

export function CouponsPage() {
  const q = useDownloadableCoupons()
  const download = useDownloadCoupon()

  if (!q.data) return q.isError ? <SectionError onRetry={() => q.refetch()} /> : <Spinner />
  // 같은 mutation 인스턴스라 가장 최근 요청의 결과만 남고, 누른 쿠폰만 대기 표시를 한다.
  const pendingId = download.isPending ? download.variables : null

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
                disabled={!c.eligible || pendingId === c.couponId}
                onClick={() => download.mutate(c.couponId)}
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
