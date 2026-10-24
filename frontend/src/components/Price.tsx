import { formatWon } from '../lib/format'

interface Props {
  price: number
  salePrice: number
  saleRate: number | null
}

export function Price({ price, salePrice, saleRate }: Props) {
  if (!saleRate) return <span className="font-bold">{formatWon(price)}</span>
  return (
    <span className="flex flex-wrap items-baseline gap-x-1.5">
      <span className="font-bold text-sale">{saleRate}%</span>
      <span className="font-bold">{formatWon(salePrice)}</span>
      <s className="text-xs text-ink/50">{formatWon(price)}</s>
    </span>
  )
}
