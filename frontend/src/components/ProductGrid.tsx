import { useWishIds } from '../api/wishlist'
import type { ProductSummary } from '../api/types'
import { ProductCard } from './ProductCard'

export function ProductGrid({ products }: { products: ProductSummary[] }) {
  const wishIds = useWishIds(products.map((p) => p.id))
  return (
    <ul className="grid grid-cols-2 gap-x-3 gap-y-6 md:grid-cols-3 lg:grid-cols-4">
      {products.map((p) => (
        <li key={p.id}>
          <ProductCard product={p} wished={wishIds.has(p.id)} />
        </li>
      ))}
    </ul>
  )
}
