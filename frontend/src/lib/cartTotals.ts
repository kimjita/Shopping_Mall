import type { CartItem } from '../api/types'

export function selectedTotal(items: CartItem[], selected: ReadonlySet<number>): { count: number; amount: number } {
  const lines = items.filter((i) => i.status === 'AVAILABLE' && selected.has(i.id))
  return { count: lines.length, amount: lines.reduce((sum, i) => sum + i.lineTotal, 0) }
}
