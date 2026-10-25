import type { PriceBreakdown } from '../api/types'
import { formatWon } from '../lib/format'

export function PriceRows({ price }: { price: PriceBreakdown }) {
  return (
    <dl className="space-y-1 text-sm">
      {(
        [
          ['상품 금액', formatWon(price.itemsPrice)],
          ['세일 할인', `-${formatWon(price.saleDiscount)}`],
          ['쿠폰 할인', `-${formatWon(price.couponDiscount)}`],
          ['배송비', formatWon(price.shippingFee)],
          ['적립금 사용', `-${formatWon(price.pointsUsed)}`],
        ] as const
      ).map(([label, value]) => (
        <div key={label} className="flex justify-between">
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
      <div className="flex justify-between border-t border-ink/10 pt-2 text-lg font-bold">
        <dt>결제금액</dt>
        <dd>{formatWon(price.paymentAmount)}</dd>
      </div>
    </dl>
  )
}
