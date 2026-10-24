import type { ReactNode } from 'react'
import type { ProductSummary } from '../api/types'
import { ProductCard } from './ProductCard'

interface Props {
  product: ProductSummary
  wished: boolean
  available?: boolean
  // 카드 아래에 붙는 동작(예: 삭제 버튼)이다.
  children?: ReactNode
}

// 찜·최근 본 상품에서 판매가 중지된 상품은 흐리게 두되 상세로는 갈 수 있다.
export function AvailabilityCard({ product, wished, available = true, children }: Props) {
  return (
    <div className="relative">
      <div className={available ? '' : 'opacity-50'}>
        <ProductCard product={product} wished={wished} />
      </div>
      {!available && (
        <span className="absolute left-1 top-1 rounded bg-ink px-2 py-0.5 text-xs font-bold text-white">판매 중지</span>
      )}
      {children}
    </div>
  )
}
