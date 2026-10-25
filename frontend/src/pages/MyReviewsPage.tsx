import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ApiError } from '../api/client'
import { useDeleteReview, useEditReview, useMyReviews, useWritableReviews, useWriteReview } from '../api/myReviews'
import type { MyReview, WritableReview } from '../api/types'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { InfiniteSentinel } from '../components/InfiniteSentinel'
import { ProductImage } from '../components/ProductImage'
import { ReviewForm } from '../components/ReviewForm'
import { SectionError } from '../components/SectionError'
import { Spinner } from '../components/Spinner'
import { formatDateTime } from '../lib/format'

const errorMessage = (e: unknown) => (e instanceof ApiError ? e.message : '잠시 후 다시 시도해 주세요.')
type Target = { write: WritableReview } | { edit: MyReview } | null

// target이 있을 때만 children을 그려 열 때마다 입력을 새로 시작한다.
function FormDialog({ label, open, onClose, children }: { label: string; open: boolean; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])
  return (
    <dialog
      ref={ref}
      aria-label={label}
      onCancel={(e) => {
        e.preventDefault()
        onClose()
      }}
      className="m-auto w-96 max-w-[90vw] rounded p-4 backdrop:bg-ink/40"
    >
      {open && children}
    </dialog>
  )
}

function Writable({ onWrite }: { onWrite: (w: WritableReview) => void }) {
  const q = useWritableReviews()
  const items = q.data?.pages.flatMap((p) => p.items) ?? []
  if (!q.data) return q.isError ? <SectionError onRetry={() => q.refetch()} /> : <Spinner />
  if (items.length === 0) return <p className="py-10 text-center text-ink/60">작성할 리뷰가 없습니다.</p>
  return (
    <>
      <ul className="divide-y divide-ink/10">
        {items.map((w) => (
          <li key={w.orderItemId} className="flex gap-3 py-3">
            <ProductImage src={w.thumbnailUrl} alt={w.productName} className="size-16 shrink-0 rounded" />
            <div className="min-w-0 flex-1 space-y-1 text-sm">
              <p className="font-medium">{w.productName}</p>
              <p className="text-ink/60">{w.skuName}</p>
              <p className="text-ink/60">{`${formatDateTime(w.confirmedAt)} 구매확정`}</p>
              <button type="button" onClick={() => onWrite(w)} className="rounded bg-brand px-3 py-1.5 font-semibold text-white">
                리뷰 쓰기
              </button>
            </div>
          </li>
        ))}
      </ul>
      <InfiniteSentinel
        onReach={() => q.fetchNextPage()}
        hasNext={q.hasNextPage}
        loading={q.isFetchingNextPage}
        error={q.isFetchNextPageError}
        errorText="다음 리뷰를 불러오지 못했습니다."
      />
    </>
  )
}

function Photos({ urls }: { urls: string[] }) {
  return (
    <div className="flex flex-wrap gap-2">
      {urls.map((u, i) => (
        <ProductImage key={u} src={u} alt={`리뷰 사진 ${i + 1}`} className="size-16 rounded" />
      ))}
    </div>
  )
}

function Mine({ onEdit, onDelete }: { onEdit: (r: MyReview) => void; onDelete: (r: MyReview) => void }) {
  const q = useMyReviews()
  const items = q.data?.pages.flatMap((p) => p.items) ?? []
  if (!q.data) return q.isError ? <SectionError onRetry={() => q.refetch()} /> : <Spinner />
  if (items.length === 0) return <p className="py-10 text-center text-ink/60">작성한 리뷰가 없습니다.</p>
  return (
    <>
      <ul className="divide-y divide-ink/10">
        {items.map((r) => (
          <li key={r.reviewId} className="space-y-2 py-4 text-sm">
            <Link to={`/products/${r.productId}`} className="font-medium underline">
              {r.productName}
            </Link>
            <p className="text-ink/60">{r.skuName}</p>
            <p className="font-bold text-brand-dark">{`★ ${r.rating}`}</p>
            <p className="whitespace-pre-line break-words">{r.content}</p>
            {r.imageUrls.length > 0 && <Photos urls={r.imageUrls} />}
            <p className="text-ink/60">{formatDateTime(r.createdAt)}</p>
            <div className="flex gap-3">
              <button type="button" onClick={() => onEdit(r)} className="underline">
                수정
              </button>
              <button type="button" onClick={() => onDelete(r)} className="text-ink/60 underline">
                삭제
              </button>
            </div>
          </li>
        ))}
      </ul>
      <InfiniteSentinel
        onReach={() => q.fetchNextPage()}
        hasNext={q.hasNextPage}
        loading={q.isFetchingNextPage}
        error={q.isFetchNextPageError}
        errorText="다음 리뷰를 불러오지 못했습니다."
      />
    </>
  )
}

const TABS = [
  ['writable', '작성 가능한 리뷰'],
  ['mine', '내 리뷰'],
] as const

export function MyReviewsPage() {
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') === 'mine' ? 'mine' : 'writable'
  const write = useWriteReview()
  const edit = useEditReview()
  const remove = useDeleteReview()
  const [target, setTarget] = useState<Target>(null)
  const [deleting, setDeleting] = useState<MyReview | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  // 새 동작을 시작하면 이전 성공 메시지와 오류를 지운다.
  const begin = () => {
    setMessage(null)
    write.reset()
    edit.reset()
    remove.reset()
  }
  const close = () => setTarget(null)
  const done = (text: string) => ({
    onSuccess: () => {
      setMessage(text)
      close()
    },
  })

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <h1 className="text-xl font-bold">리뷰</h1>
      <div role="tablist" className="flex border-b border-ink/10">
        {TABS.map(([key, label]) => (
          <button
            key={key}
            type="button"
            role="tab"
            aria-selected={tab === key}
            onClick={() => {
              begin()
              setParams({ tab: key }, { replace: true })
            }}
            className={`flex-1 border-b-2 py-3 text-sm ${tab === key ? 'border-brand font-bold' : 'border-transparent text-ink/60'}`}
          >
            {label}
          </button>
        ))}
      </div>
      {message && (
        <p role="status" className="rounded bg-brand/10 p-3 text-sm">
          {message}
        </p>
      )}
      {remove.isError && (
        <p role="alert" className="text-sm text-sale">
          {errorMessage(remove.error)}
        </p>
      )}
      <div role="tabpanel">
        {tab === 'writable' ? (
          <Writable
            onWrite={(w) => {
              begin()
              setTarget({ write: w })
            }}
          />
        ) : (
          <Mine
            onEdit={(r) => {
              begin()
              setTarget({ edit: r })
            }}
            onDelete={(r) => {
              begin()
              setDeleting(r)
            }}
          />
        )}
      </div>

      <FormDialog label={target && 'edit' in target ? '리뷰 수정' : '리뷰 쓰기'} open={target !== null} onClose={close}>
        {target && 'write' in target && (
          <>
            <p className="mb-4 font-medium">{target.write.productName}</p>
            <ReviewForm
              submitLabel="등록"
              pending={write.isPending}
              error={write.isError ? errorMessage(write.error) : undefined}
              onCancel={close}
              onSubmit={({ rating, content }) =>
                write.mutate(
                  { productId: target.write.productId, body: { orderItemId: target.write.orderItemId, rating, content, imageUrls: [] } },
                  done('리뷰를 등록했습니다.'),
                )
              }
            />
          </>
        )}
        {target && 'edit' in target && (
          <>
            <p className="mb-2 font-medium">{target.edit.productName}</p>
            {target.edit.imageUrls.length > 0 && (
              <div className="mb-4 space-y-1">
                <Photos urls={target.edit.imageUrls} />
                <p className="text-xs text-ink/60">사진은 그대로 유지됩니다.</p>
              </div>
            )}
            <ReviewForm
              initial={target.edit}
              submitLabel="수정"
              pending={edit.isPending}
              error={edit.isError ? errorMessage(edit.error) : undefined}
              onCancel={close}
              onSubmit={({ rating, content }) =>
                edit.mutate(
                  // 사진 수정은 지원하지 않아 기존 목록을 그대로 보낸다.
                  { reviewId: target.edit.reviewId, productId: target.edit.productId, body: { rating, content, imageUrls: target.edit.imageUrls } },
                  done('리뷰를 수정했습니다.'),
                )
              }
            />
          </>
        )}
      </FormDialog>
      <ConfirmDialog
        open={deleting !== null}
        title="리뷰 삭제"
        message="리뷰를 삭제할까요? 삭제하면 같은 상품에 다시 쓸 수 있습니다."
        confirmLabel="삭제"
        pending={remove.isPending}
        onCancel={() => setDeleting(null)}
        onConfirm={() =>
          deleting &&
          remove.mutate(
            { reviewId: deleting.reviewId, productId: deleting.productId },
            { onSuccess: () => setMessage('리뷰를 삭제했습니다.'), onSettled: () => setDeleting(null) },
          )
        }
      />
    </div>
  )
}
