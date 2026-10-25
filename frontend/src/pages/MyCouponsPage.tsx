import { useState } from 'react'
import { Link } from 'react-router'
import { useMyCoupons } from '../api/coupons'
import type { MyCoupon } from '../api/types'
import { SectionError } from '../components/SectionError'
import { Spinner } from '../components/Spinner'
import { formatDateTime } from '../lib/format'
import { couponDiscountText, minOrderText } from '../lib/labels'

export function MyCouponsPage() {
  const q = useMyCoupons()
  const [now] = useState(Date.now) // 사용 시작 전 여부는 화면을 연 시각으로 판단한다.

  if (!q.data) return q.isError ? <SectionError onRetry={() => q.refetch()} /> : <Spinner />

  // 서버 순서(최근 발급순)를 묶음 안에서도 그대로 둔다.
  const startsAfterNow = (c: MyCoupon) => new Date(c.useStartsAt).getTime() > now
  const groups: { title: string; coupons: MyCoupon[] }[] = [
    { title: '사용 가능', coupons: q.data.filter((c) => c.status === 'AVAILABLE' && !startsAfterNow(c)) },
    { title: '사용 시작 전', coupons: q.data.filter((c) => c.status === 'AVAILABLE' && startsAfterNow(c)) },
    { title: '사용 완료', coupons: q.data.filter((c) => c.status === 'USED') },
    { title: '기간 만료', coupons: q.data.filter((c) => c.status === 'EXPIRED') },
  ]

  return (
    <section className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-xl font-bold">내 쿠폰</h1>
      <Link to="/coupons" className="inline-block rounded border border-ink/20 px-4 py-2 text-sm">
        쿠폰 받으러 가기
      </Link>
      {q.data.length === 0 && <p className="py-10 text-center text-ink/60">받은 쿠폰이 없습니다.</p>}
      {groups
        .filter((g) => g.coupons.length > 0)
        .map((g) => (
          <div key={g.title} className="space-y-3">
            <h2 className="font-bold">{g.title}</h2>
            <ul className="space-y-3">
              {g.coupons.map((c) => (
                <li key={c.id} className="space-y-1 rounded border border-ink/10 p-4">
                  <p className="font-medium">{c.name}</p>
                  <p className="text-sm font-semibold text-brand-dark">{couponDiscountText(c)}</p>
                  {c.minOrderAmount > 0 && <p className="text-sm">{minOrderText(c.minOrderAmount)}</p>}
                  <p className="text-sm text-ink/60">
                    사용 기간 {formatDateTime(c.useStartsAt)} ~ {formatDateTime(c.useEndsAt)}
                  </p>
                  {c.status === 'USED' && c.usedAt && <p className="text-sm text-ink/60">{formatDateTime(c.usedAt)}에 사용</p>}
                </li>
              ))}
            </ul>
          </div>
        ))}
    </section>
  )
}
