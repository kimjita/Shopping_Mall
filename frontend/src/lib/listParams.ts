import type { ProductSort } from '../api/types'

export interface ListParams {
  keyword?: string
  categoryId?: number
  brandIds: number[]
  minPrice?: number
  maxPrice?: number
  onSale: boolean
  sort: ProductSort
}

const SORTS: ProductSort[] = ['NEWEST', 'SALES', 'PRICE_ASC', 'PRICE_DESC', 'RATING', 'REVIEW_COUNT']
const MAX_KEYWORD = 50
const MAX_BRANDS = 20

// 0 이상 정수 문자열만 받는다. 안전 정수 범위를 넘으면 버린다.
function toInt(v: string | null): number | undefined {
  if (v === null || !/^\d+$/.test(v)) return undefined
  const n = Number(v)
  return Number.isSafeInteger(n) ? n : undefined
}

function hasControlChar(s: string) {
  return [...s].some((c) => c.charCodeAt(0) <= 0x1f || c.charCodeAt(0) === 0x7f)
}

// URL은 사용자가 고칠 수 있으므로 잘못된 값은 오류 대신 버린다.
export function parseListParams(sp: URLSearchParams): ListParams {
  const p: ListParams = { brandIds: [], onSale: sp.get('onSale') === 'true', sort: 'NEWEST' }
  const keyword = sp.get('keyword')?.trim()
  if (keyword && keyword.length <= MAX_KEYWORD && !hasControlChar(keyword)) p.keyword = keyword
  const categoryId = toInt(sp.get('categoryId'))
  if (categoryId && categoryId >= 1) p.categoryId = categoryId
  const ids = sp
    .getAll('brandIds')
    .flatMap((v) => v.split(','))
    .map((v) => toInt(v.trim()))
    .filter((n): n is number => n !== undefined && n >= 1)
  p.brandIds = [...new Set(ids)].slice(0, MAX_BRANDS)
  const minPrice = toInt(sp.get('minPrice'))
  const maxPrice = toInt(sp.get('maxPrice'))
  if (minPrice !== undefined) p.minPrice = minPrice
  if (maxPrice !== undefined && (minPrice === undefined || maxPrice >= minPrice)) p.maxPrice = maxPrice
  const sort = sp.get('sort')
  if (SORTS.includes(sort as ProductSort)) p.sort = sort as ProductSort
  return p
}

export function toSearchParams(p: ListParams): URLSearchParams {
  const sp = new URLSearchParams()
  if (p.keyword) sp.set('keyword', p.keyword)
  if (p.categoryId !== undefined) sp.set('categoryId', String(p.categoryId))
  if (p.brandIds.length > 0) sp.set('brandIds', p.brandIds.join(','))
  if (p.minPrice !== undefined) sp.set('minPrice', String(p.minPrice))
  if (p.maxPrice !== undefined) sp.set('maxPrice', String(p.maxPrice))
  if (p.onSale) sp.set('onSale', 'true')
  if (p.sort !== 'NEWEST') sp.set('sort', p.sort)
  return sp
}
