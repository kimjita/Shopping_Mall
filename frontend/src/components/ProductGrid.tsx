import type { ReactNode } from 'react'
import { useWishIds } from '../api/wishlist'
import type { ProductSummary } from '../api/types'
import { ProductCard } from './ProductCard'

interface Props {
  products: ProductSummary[]
  // 순위 배지·추가 문구가 필요한 곳이 카드를 직접 그린다.
  renderItem?: (product: ProductSummary, wished: boolean, index: number) => ReactNode
}

export function ProductGrid({ products, renderItem }: Props) {
  const wishIds = useWishIds(products.map((p) => p.id))
  return (
    <ul className="grid grid-cols-2 gap-x-3 gap-y-6 md:grid-cols-3 lg:grid-cols-4">
      {products.map((p, i) => (
        <li key={p.id}>
          {renderItem ? renderItem(p, wishIds.has(p.id), i) : <ProductCard product={p} wished={wishIds.has(p.id)} />}
        </li>
      ))}
    </ul>
  )
}
