import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { useAddToCart } from '../api/cart'
import { useProduct } from '../api/catalog'
import { useRecordRecentView } from '../api/recentViews'
import { useProductReviews } from '../api/reviews'
import type { ProductDetail, ReviewSort } from '../api/types'
import { useWishIds } from '../api/wishlist'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthProvider'
import { loginPath } from '../auth/RequireAuth'
import { Price } from '../components/Price'
import { ProductImage } from '../components/ProductImage'
import { QuantityStepper } from '../components/QuantityStepper'
import { SectionError } from '../components/SectionError'
import { Spinner } from '../components/Spinner'
import { WishButton } from '../components/WishButton'
import { formatRating } from '../lib/format'
import { REVIEW_SORT_LABEL, SKIN_TYPE_LABEL } from '../lib/labels'

const dateFormat = new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' })

function NotSold() {
  return (
    <div className="py-16 text-center">
      <p>판매하지 않는 상품입니다.</p>
      <Link to="/" className="mt-4 inline-block rounded border border-ink/20 px-4 py-2 text-sm">
        홈으로
      </Link>
    </div>
  )
}

function Reviews({ productId }: { productId: number }) {
  const { me } = useAuth()
  const [sort, setSort] = useState<ReviewSort>('LATEST')
  const [sameSkin, setSameSkin] = useState(false)
  const skinType = sameSkin && me?.skinType ? me.skinType : null
  const q = useProductReviews(productId, sort, skinType)
  const reviews = q.data?.pages.flatMap((p) => p.items) ?? []

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3 text-sm">
        <select aria-label="리뷰 정렬" value={sort} onChange={(e) => setSort(e.target.value as ReviewSort)} className="rounded border border-ink/20 px-2 py-1">
          {(Object.keys(REVIEW_SORT_LABEL) as ReviewSort[]).map((s) => (
            <option key={s} value={s}>
              {REVIEW_SORT_LABEL[s]}
            </option>
          ))}
        </select>
        {me?.skinType && (
          <label className="flex items-center gap-1">
            <input type="checkbox" checked={sameSkin} onChange={(e) => setSameSkin(e.target.checked)} />
            나와 같은 피부타입({SKIN_TYPE_LABEL[me.skinType]})만 보기
          </label>
        )}
      </div>
      {q.isError && !q.data ? (
        <SectionError onRetry={() => q.refetch()} />
      ) : q.isPending ? (
        <Spinner />
      ) : reviews.length === 0 ? (
        <p className="py-10 text-center">아직 리뷰가 없습니다.</p>
      ) : (
        <ul className="divide-y divide-ink/10">
          {reviews.map((r) => (
            <li key={r.reviewId} className="space-y-2 py-4">
              <div className="flex flex-wrap items-center gap-x-2 text-sm">
                <span aria-label={`별점 ${r.rating}점`} className="text-brand-dark">
                  {'★'.repeat(r.rating)}
                </span>
                <span className="font-bold">{r.authorName}</span>
                <span className="text-ink/60">
                  {r.skuName}
                  {r.skinType && ` · ${SKIN_TYPE_LABEL[r.skinType]}`}
                </span>
              </div>
              <p className="whitespace-pre-line">{r.content}</p>
              {r.imageUrls.length > 0 && (
                <div className="flex gap-2 overflow-x-auto">
                  {r.imageUrls.map((url) => (
                    <ProductImage key={url} src={url} alt="리뷰 사진" className="size-20 shrink-0 rounded" />
                  ))}
                </div>
              )}
              <time dateTime={r.createdAt} className="block text-xs text-ink/50">
                {dateFormat.format(new Date(r.createdAt))}
              </time>
            </li>
          ))}
        </ul>
      )}
      {q.isFetchNextPageError && <p className="text-center text-sm">리뷰를 더 불러오지 못했습니다.</p>}
      {q.hasNextPage && (
        <div className="flex justify-center">
          <button
            type="button"
            onClick={() => q.fetchNextPage()}
            disabled={q.isFetchingNextPage}
            className="rounded border border-ink/20 px-6 py-2 text-sm disabled:opacity-50"
          >
            리뷰 더보기
          </button>
        </div>
      )}
    </div>
  )
}

function AddedDialog({ onClose }: { onClose: () => void }) {
  const ref = useRef<HTMLDialogElement>(null)
  const navigate = useNavigate()
  useEffect(() => {
    const d = ref.current
    if (d && !d.open) d.showModal()
  }, [])
  return (
    <dialog ref={ref} onClose={onClose} aria-label="장바구니 안내" className="m-auto w-[90vw] max-w-sm rounded p-5 backdrop:bg-ink/40">
      <p className="mb-4 text-center">장바구니에 담았습니다.</p>
      <div className="flex gap-2">
        <button type="button" onClick={() => ref.current?.close()} className="flex-1 rounded border border-ink/20 py-2 text-sm">
          계속 쇼핑하기
        </button>
        <button type="button" onClick={() => navigate('/cart')} className="flex-1 rounded bg-brand py-2 text-sm font-bold text-white">
          장바구니로 가기
        </button>
      </div>
    </dialog>
  )
}

type Tab = 'description' | 'ingredients' | 'reviews'

function Detail({ product }: { product: ProductDetail }) {
  const { status } = useAuth()
  const navigate = useNavigate()
  const { pathname, search } = useLocation()
  const wished = useWishIds([product.id]).has(product.id)
  const addToCart = useAddToCart()
  const [imageIdx, setImageIdx] = useState(0)
  const [pickedSku, setPickedSku] = useState<number | null>(null)
  const [quantity, setQuantity] = useState(1)
  const [message, setMessage] = useState<string | null>(null)
  const [added, setAdded] = useState(false)
  const [tab, setTab] = useState<Tab>('description')

  const skuId = pickedSku ?? (product.skus.length === 1 ? product.skus[0].id : null)
  const sku = product.skus.find((s) => s.id === skuId)
  const lowest = product.skus.reduce<ProductDetail['skus'][number] | undefined>((a, s) => (!a || s.salePrice < a.salePrice ? s : a), undefined)
  const shown = sku ?? lowest ?? { price: product.basePrice, salePrice: product.basePrice }
  const saleRate = shown.salePrice < shown.price ? product.saleRate : null

  const submit = () => {
    if (status !== 'member') {
      navigate(loginPath(pathname + search))
      return
    }
    if (!sku) {
      setMessage('옵션을 선택해 주세요.')
      return
    }
    setMessage(null)
    addToCart.mutate({ skuId: sku.id, quantity }, { onSuccess: () => setAdded(true) })
  }

  const tabs: [Tab, string][] = [
    ['description', '상세 설명'],
    ['ingredients', '전성분'],
    ['reviews', `리뷰(${product.reviewCount})`],
  ]

  return (
    <div className="space-y-8">
      <div className="grid gap-6 md:grid-cols-2">
        <div className="space-y-2">
          <ProductImage src={product.imageUrls[imageIdx] ?? null} alt={product.name} className="aspect-square w-full rounded" />
          {product.imageUrls.length > 1 && (
            <div className="flex gap-2 overflow-x-auto">
              {product.imageUrls.map((url, i) => (
                <button
                  key={url}
                  type="button"
                  aria-label={`이미지 ${i + 1}`}
                  aria-current={i === imageIdx}
                  onClick={() => setImageIdx(i)}
                  className={`shrink-0 rounded border-2 ${i === imageIdx ? 'border-brand' : 'border-transparent'}`}
                >
                  <ProductImage src={url} alt="" className="size-16 rounded" />
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="space-y-4">
          <div className="flex items-start justify-between gap-2">
            <div>
              <p className="text-sm text-ink/60">{product.brand.name}</p>
              <h1 className="text-xl font-bold">{product.name}</h1>
            </div>
            <WishButton productId={product.id} wished={wished} className="border border-ink/10" />
          </div>
          {product.summary && <p className="text-sm text-ink/70">{product.summary}</p>}
          <p className="text-sm">
            <span className="text-brand-dark">★</span> {product.ratingAvg === null ? '평점 없음' : formatRating(product.ratingAvg)} · 리뷰 {product.reviewCount}
          </p>
          <div className="text-lg">
            <Price price={shown.price} salePrice={shown.salePrice} saleRate={saleRate} />
          </div>
          <select
            aria-label="옵션"
            value={skuId ?? ''}
            onChange={(e) => {
              setPickedSku(e.target.value ? Number(e.target.value) : null)
              setMessage(null)
            }}
            className="w-full rounded border border-ink/20 px-3 py-2"
          >
            <option value="">옵션 선택</option>
            {product.skus.map((s) => (
              <option key={s.id} value={s.id} disabled={s.soldOut}>
                {s.soldOut ? `${s.name} (품절)` : s.name}
              </option>
            ))}
          </select>
          <QuantityStepper value={quantity} onChange={setQuantity} />
          {message && (
            <p role="alert" className="text-sm text-sale">
              {message}
            </p>
          )}
          {addToCart.isError && (
            <p role="alert" className="text-sm text-sale">
              {addToCart.error instanceof ApiError ? addToCart.error.message : '잠시 후 다시 시도해 주세요.'}
            </p>
          )}
          <button
            type="button"
            onClick={submit}
            disabled={product.soldOut || addToCart.isPending}
            className="w-full rounded bg-brand py-3 font-bold text-white disabled:bg-ink/30"
          >
            {product.soldOut ? '품절' : '장바구니 담기'}
          </button>
        </div>
      </div>

      <div>
        <div role="tablist" className="flex border-b border-ink/10">
          {tabs.map(([key, label]) => (
            <button
              key={key}
              type="button"
              role="tab"
              aria-selected={tab === key}
              onClick={() => setTab(key)}
              className={`flex-1 border-b-2 py-3 text-sm ${tab === key ? 'border-brand font-bold' : 'border-transparent text-ink/60'}`}
            >
              {label}
            </button>
          ))}
        </div>
        <div role="tabpanel" className="py-6">
          {tab === 'description' && <p className="whitespace-pre-line">{product.description}</p>}
          {tab === 'ingredients' && <p className="whitespace-pre-line">{product.ingredients ?? '등록된 전성분 정보가 없습니다.'}</p>}
          {tab === 'reviews' && <Reviews productId={product.id} />}
        </div>
      </div>
      {added && <AddedDialog onClose={() => setAdded(false)} />}
    </div>
  )
}

export function ProductDetailPage() {
  const { productId } = useParams()
  const id = /^\d+$/.test(productId ?? '') ? Number(productId) : 0
  const query = useProduct(id)
  useRecordRecentView(id, query.isSuccess)

  if (id <= 0 || (query.error instanceof ApiError && query.error.status === 404)) return <NotSold />
  if (!query.data) return query.isError ? <SectionError onRetry={() => query.refetch()} /> : <Spinner />
  return <Detail key={query.data.id} product={query.data} />
}
