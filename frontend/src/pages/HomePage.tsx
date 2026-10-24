import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { useBanners, useNewArrivals, useRankings, useRecommendations, useTodaySale } from '../api/home'
import type { SkinType } from '../api/types'
import { useAuth } from '../auth/AuthProvider'
import { ProductCard } from '../components/ProductCard'
import { ProductGrid } from '../components/ProductGrid'
import { SectionError } from '../components/SectionError'
import { Spinner } from '../components/Spinner'
import { formatRating } from '../lib/format'
import { SKIN_TYPE_LABEL } from '../lib/labels'
import { isExternalLink } from '../lib/links'

const SKIN_ORDER: SkinType[] = ['DRY', 'OILY', 'COMBINATION', 'SENSITIVE', 'NORMAL']

interface QueryLike {
  isPending: boolean
  isError: boolean
  refetch: () => unknown
}

function Section({ title, note, children }: { title: string; note?: string; children: ReactNode }) {
  return (
    <section className="space-y-3">
      <div className="flex items-baseline gap-2">
        <h2 className="text-lg font-bold">{title}</h2>
        {note && <span className="text-xs text-ink/60">{note}</span>}
      </div>
      {children}
    </section>
  )
}

// 로딩·오류를 처리하고, 데이터가 있을 때만 children을 그린다.
function State({ q, children }: { q: QueryLike; children: ReactNode }) {
  if (q.isPending) return <Spinner />
  if (q.isError) return <SectionError onRetry={() => q.refetch()} />
  return <>{children}</>
}

function BannerStrip() {
  const q = useBanners()
  if (q.isPending) return <Spinner />
  if (q.isError) return <SectionError onRetry={() => q.refetch()} />
  if (q.data.length === 0) return null
  return (
    <ul className="-mx-4 flex snap-x snap-mandatory gap-3 overflow-x-auto px-4">
      {q.data.map((b) => {
        const img = <img src={b.imageUrl} alt={b.title} className="h-40 w-[85vw] max-w-xl rounded object-cover" />
        return (
          <li key={b.id} className="shrink-0 snap-center">
            {isExternalLink(b.linkUrl) ? (
              <a href={b.linkUrl} target="_blank" rel="noopener noreferrer">
                {img}
              </a>
            ) : (
              <Link to={b.linkUrl}>{img}</Link>
            )}
          </li>
        )
      })}
    </ul>
  )
}

function TodaySale() {
  const q = useTodaySale()
  if (!q.isPending && !q.isError && q.data.items.length === 0) return null
  return (
    <Section title="오늘의 특가">
      <State q={q}>{q.data && <ProductGrid products={q.data.items} />}</State>
    </Section>
  )
}

function Rankings() {
  const q = useRankings()
  if (!q.isPending && !q.isError && q.data.items.length === 0) return null
  const items = q.data?.items.slice(0, 10) ?? []
  return (
    <Section title="랭킹" note="결제 기준 최근 7일 판매량 · 매시 정각 갱신">
      <State q={q}>
        <ProductGrid
          products={items.map((i) => i.product)}
          renderItem={(p, wished, idx) => <ProductCard product={p} wished={wished} rank={items[idx].rank} />}
        />
      </State>
    </Section>
  )
}

function Recommendations() {
  const { me } = useAuth()
  const [picked, setPicked] = useState<SkinType | null>(null)
  const memberSkin = me?.skinType ?? null
  const skinType = memberSkin ?? picked
  const q = useRecommendations(skinType)
  return (
    <Section title="피부 타입 추천">
      {memberSkin === null && (
        <>
          <p className="text-sm text-ink/70">피부 타입을 고르면 같은 피부 고객이 높게 평가한 상품을 보여 드립니다.</p>
          <div role="radiogroup" aria-label="피부 타입" className="flex flex-wrap gap-2">
            {SKIN_ORDER.map((s) => (
              <button
                key={s}
                type="button"
                role="radio"
                aria-checked={picked === s}
                onClick={() => setPicked(s)}
                className="rounded-full border border-ink/20 px-4 py-1.5 text-sm aria-checked:border-brand aria-checked:bg-brand aria-checked:text-white"
              >
                {SKIN_TYPE_LABEL[s]}
              </button>
            ))}
          </div>
        </>
      )}
      {skinType !== null && (
        <State q={q}>
          {q.data && (
            <ProductGrid
              products={q.data.items.map((i) => i.product)}
              renderItem={(p, wished, idx) => {
                const r = q.data.items[idx]
                return (
                  <>
                    <ProductCard product={p} wished={wished} />
                    <p className="mt-1 text-xs text-ink/60">
                      같은 피부 {r.sameSkinReviewCount}명 평균 ★{formatRating(r.sameSkinRatingAvg)}
                    </p>
                  </>
                )
              }}
            />
          )}
        </State>
      )}
    </Section>
  )
}

function NewArrivals() {
  const q = useNewArrivals()
  return (
    <Section title="신상품">
      <State q={q}>
        {q.data && (q.data.items.length === 0 ? <p>상품이 없습니다.</p> : <ProductGrid products={q.data.items} />)}
      </State>
    </Section>
  )
}

export function HomePage() {
  return (
    <div className="space-y-10">
      <BannerStrip />
      <TodaySale />
      <Rankings />
      <Recommendations />
      <NewArrivals />
    </div>
  )
}
