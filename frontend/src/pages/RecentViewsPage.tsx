import { useState } from 'react'
import { useClearRecentViews, useGuestRecentProducts, useRecentViews, useRemoveRecentView } from '../api/recentViews'
import type { ProductSummary } from '../api/types'
import { useAuth } from '../auth/AuthProvider'
import { AvailabilityCard } from '../components/AvailabilityCard'
import { ProductGrid } from '../components/ProductGrid'
import { SectionError } from '../components/SectionError'
import { Spinner } from '../components/Spinner'
import { guestRecentViews } from '../lib/guestRecentViews'

interface ListProps {
  products: ProductSummary[]
  availability?: boolean[]
  onRemove: (productId: number) => void
  onClear: () => void
}

function RecentList({ products, availability, onRemove, onClear }: ListProps) {
  if (products.length === 0) return <p className="py-10 text-center text-ink/60">최근 본 상품이 없습니다.</p>
  return (
    <>
      <div className="flex justify-end">
        <button type="button" onClick={onClear} className="rounded border border-ink/20 px-4 py-2 text-sm">
          전체 삭제
        </button>
      </div>
      <ProductGrid
        products={products}
        renderItem={(p, wished, index) => (
          <AvailabilityCard product={p} wished={wished} available={availability?.[index]}>
            <button type="button" onClick={() => onRemove(p.id)} className="mt-1 w-full rounded border border-ink/20 py-1 text-xs">
              삭제
            </button>
          </AvailabilityCard>
        )}
      />
    </>
  )
}

function MemberRecent() {
  const q = useRecentViews()
  const remove = useRemoveRecentView()
  const clear = useClearRecentViews()
  if (q.isPending) return <Spinner />
  if (q.isError) return <SectionError onRetry={() => q.refetch()} />
  return (
    <RecentList
      products={q.data.map((v) => v.product)}
      availability={q.data.map((v) => v.available)}
      onRemove={(id) => remove.mutate(id)}
      onClear={() => clear.mutate()}
    />
  )
}

function GuestRecent() {
  // 삭제가 화면에 바로 보이도록 번호 목록을 상태로 들고 있다.
  const [ids, setIds] = useState(guestRecentViews.read)
  const { products, isPending } = useGuestRecentProducts(ids)
  return (
    <>
      <p className="text-sm text-ink/60">로그인하지 않으면 이 기기에만 저장됩니다.</p>
      {isPending ? (
        <Spinner />
      ) : (
        <RecentList
          products={products}
          onRemove={(id) => {
            guestRecentViews.remove(id)
            setIds((prev) => prev.filter((i) => i !== id))
          }}
          onClear={() => {
            guestRecentViews.clear()
            setIds([])
          }}
        />
      )}
    </>
  )
}

export function RecentViewsPage() {
  const { status } = useAuth()
  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">최근 본 상품</h1>
      {status === 'loading' ? <Spinner /> : status === 'member' ? <MemberRecent /> : <GuestRecent />}
    </div>
  )
}
