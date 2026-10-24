import { Link } from 'react-router'
import type { ProductSummary } from '../api/types'
import { formatRating } from '../lib/format'
import { Price } from './Price'
import { ProductImage } from './ProductImage'
import { WishButton } from './WishButton'

interface Props {
  product: ProductSummary
  wished?: boolean
  rank?: number
}

export function ProductCard({ product: p, wished = false, rank }: Props) {
  return (
    <div className="relative">
      <Link to={`/products/${p.id}`} className="block">
        <div className="relative">
          <ProductImage src={p.thumbnailUrl} alt={p.name} className="aspect-square w-full rounded" />
          {rank !== undefined && (
            <span className="absolute left-0 top-0 rounded-br bg-ink px-2 py-0.5 text-sm font-bold text-white">{rank}</span>
          )}
          {p.soldOut && (
            <span className="absolute inset-0 grid place-items-center rounded bg-ink/50 font-bold text-white">품절</span>
          )}
        </div>
        <div className="mt-2 space-y-0.5 text-sm">
          <p className="text-xs text-ink/60">{p.brandName}</p>
          <p className="line-clamp-2">{p.name}</p>
          <Price price={p.price} salePrice={p.salePrice} saleRate={p.saleRate} />
          {p.reviewCount > 0 && p.ratingAvg !== null && (
            <p className="text-xs text-ink/60">
              ★ {formatRating(p.ratingAvg)} ({p.reviewCount})
            </p>
          )}
        </div>
      </Link>
      <WishButton productId={p.id} wished={wished} className="absolute right-1 top-1" />
    </div>
  )
}
