import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { useBrands, useCategories, useProductSearch } from '../api/catalog'
import type { Category, ProductSort } from '../api/types'
import { InfiniteSentinel } from '../components/InfiniteSentinel'
import { ProductGrid } from '../components/ProductGrid'
import { SectionError } from '../components/SectionError'
import { Spinner } from '../components/Spinner'
import { PRODUCT_SORT_LABEL } from '../lib/labels'
import { parseListParams, toSearchParams, type ListParams } from '../lib/listParams'

function findCategory(tree: Category[], id: number): Category | undefined {
  for (const c of tree) {
    const hit = c.id === id ? c : findCategory(c.children, id)
    if (hit) return hit
  }
}

const toPrice = (v: string) => (v === '' ? undefined : Number(v))

function FilterDialog({ params, onApply, onClose }: { params: ListParams; onApply: (p: ListParams) => void; onClose: () => void }) {
  const ref = useRef<HTMLDialogElement>(null)
  const brands = useBrands()
  const [brandIds, setBrandIds] = useState(params.brandIds)
  const [min, setMin] = useState(params.minPrice?.toString() ?? '')
  const [max, setMax] = useState(params.maxPrice?.toString() ?? '')
  const [onSale, setOnSale] = useState(params.onSale)

  useEffect(() => {
    const d = ref.current
    if (d && !d.open) d.showModal()
  }, [])

  const submit = (e: FormEvent) => {
    e.preventDefault()
    const minPrice = toPrice(min)
    const maxPrice = toPrice(max)
    onApply({
      ...params,
      brandIds,
      onSale,
      minPrice,
      // 최대가 최소보다 작으면 URL 파싱 규칙과 같이 최대를 버린다.
      maxPrice: maxPrice !== undefined && minPrice !== undefined && maxPrice < minPrice ? undefined : maxPrice,
    })
  }

  return (
    <dialog ref={ref} onClose={onClose} aria-label="필터" className="m-auto w-[90vw] max-w-md rounded p-4 backdrop:bg-ink/40">
      <form onSubmit={submit} className="space-y-4">
        <fieldset className="space-y-1">
          <legend className="font-bold">브랜드</legend>
          {brands.isPending && <Spinner />}
          {brands.isError && <SectionError onRetry={() => brands.refetch()} />}
          <div className="max-h-48 overflow-y-auto">
            {brands.data?.map((b) => (
              <label key={b.id} className="flex items-center gap-2 py-1">
                <input
                  type="checkbox"
                  checked={brandIds.includes(b.id)}
                  onChange={(e) => setBrandIds(e.target.checked ? [...brandIds, b.id] : brandIds.filter((id) => id !== b.id))}
                />
                {b.name}
              </label>
            ))}
          </div>
        </fieldset>
        <div className="flex items-end gap-2">
          <label className="flex-1 text-sm">
            최소 가격
            <input type="number" min={0} value={min} onChange={(e) => setMin(e.target.value)} className="mt-1 w-full rounded border border-ink/20 px-2 py-1" />
          </label>
          <label className="flex-1 text-sm">
            최대 가격
            <input type="number" min={0} value={max} onChange={(e) => setMax(e.target.value)} className="mt-1 w-full rounded border border-ink/20 px-2 py-1" />
          </label>
        </div>
        <label className="flex items-center gap-2">
          <input type="checkbox" checked={onSale} onChange={(e) => setOnSale(e.target.checked)} />
          세일만 보기
        </label>
        <div className="flex justify-end gap-2">
          <button type="button" onClick={() => ref.current?.close()} className="rounded border border-ink/20 px-4 py-2 text-sm">
            닫기
          </button>
          <button type="submit" className="rounded bg-brand px-4 py-2 text-sm font-bold text-white">
            적용
          </button>
        </div>
      </form>
    </dialog>
  )
}

export function ProductListPage() {
  const [sp, setSearchParams] = useSearchParams()
  const params = useMemo(() => parseListParams(sp), [sp])
  const [filterOpen, setFilterOpen] = useState(false)
  const categories = useCategories(params.categoryId !== undefined)
  const q = useProductSearch(params)

  const title = params.keyword
    ? `"${params.keyword}" 검색 결과`
    : ((params.categoryId !== undefined && categories.data && findCategory(categories.data, params.categoryId)?.name) || '전체 상품')
  const products = q.data?.pages.flatMap((p) => p.items) ?? []
  const apply = (next: ListParams) => setSearchParams(toSearchParams(next))

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-bold">{title}</h1>
      <div className="flex items-center justify-between gap-2">
        <button type="button" onClick={() => setFilterOpen(true)} className="rounded border border-ink/20 px-4 py-2 text-sm">
          필터
        </button>
        <select
          aria-label="정렬"
          value={params.sort}
          onChange={(e) => apply({ ...params, sort: e.target.value as ProductSort })}
          className="rounded border border-ink/20 px-2 py-2 text-sm"
        >
          {Object.entries(PRODUCT_SORT_LABEL).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </div>
      {filterOpen && (
        <FilterDialog
          params={params}
          onApply={(next) => {
            apply(next)
            setFilterOpen(false)
          }}
          onClose={() => setFilterOpen(false)}
        />
      )}
      {!q.data ? (
        q.isError ? <SectionError onRetry={() => q.refetch()} /> : <Spinner />
      ) : products.length === 0 ? (
        <p className="py-10 text-center text-ink/60">조건에 맞는 상품이 없습니다.</p>
      ) : (
        <>
          <ProductGrid products={products} />
          <InfiniteSentinel onReach={() => q.fetchNextPage()} hasNext={q.hasNextPage} loading={q.isFetchingNextPage} error={q.isFetchNextPageError} />
        </>
      )}
    </div>
  )
}
