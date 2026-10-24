import { useEffect } from 'react'
import { useWishlist } from '../api/wishlist'
import { AvailabilityCard } from '../components/AvailabilityCard'
import { InfiniteSentinel } from '../components/InfiniteSentinel'
import { ProductGrid } from '../components/ProductGrid'
import { SectionError } from '../components/SectionError'
import { Spinner } from '../components/Spinner'

export function WishlistPage() {
  const q = useWishlist()
  const items = q.data?.pages.flatMap((p) => p.items) ?? []
  const lastEmpty = q.data !== undefined && q.data.pages[q.data.pages.length - 1].items.length === 0

  // 삭제된 상품만 있던 빈 페이지는 사용자 조작 없이 이어 읽는다.
  useEffect(() => {
    if (lastEmpty && q.hasNextPage && !q.isFetchingNextPage && !q.isError) void q.fetchNextPage()
  }, [lastEmpty, q])

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">찜한 상품</h1>
      {!q.data ? (
        q.isError ? <SectionError onRetry={() => q.refetch()} /> : <Spinner />
      ) : items.length === 0 ? (
        q.isFetchNextPageError ? (
          <SectionError onRetry={() => q.fetchNextPage()} />
        ) : q.hasNextPage ? (
          <Spinner />
        ) : (
          <p className="py-10 text-center text-ink/60">찜한 상품이 없습니다.</p>
        )
      ) : (
        <>
          <ProductGrid
            products={items.map((i) => i.product)}
            renderItem={(p, _wished, index) => <AvailabilityCard product={p} wished available={items[index].available} />}
          />
          <InfiniteSentinel onReach={() => q.fetchNextPage()} hasNext={q.hasNextPage} loading={q.isFetchingNextPage} error={q.isFetchNextPageError} />
        </>
      )}
    </div>
  )
}
