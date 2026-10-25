import { useEffect, useRef, useState } from 'react'
import { useAddresses, useDeleteAddress, useSaveAddress } from '../api/addresses'
import { ApiError } from '../api/client'
import type { Address } from '../api/types'
import { AddressForm } from '../components/AddressForm'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { SectionError } from '../components/SectionError'
import { Spinner } from '../components/Spinner'

const MAX = 10
const errorMessage = (e: unknown) => (e instanceof ApiError ? e.message : '잠시 후 다시 시도해 주세요.')

// 폼 대화상자. editing이 null이 아닐 때만 폼을 그려 열 때마다 입력을 새로 시작한다.
function FormDialog({ editing, onClose }: { editing: Address | 'new' | null; onClose: () => void }) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (editing && !dialog.open) dialog.showModal()
    if (!editing && dialog.open) dialog.close()
  }, [editing])
  return (
    <dialog
      ref={ref}
      aria-label={editing === 'new' ? '배송지 추가' : '배송지 수정'}
      onCancel={(e) => {
        e.preventDefault()
        onClose()
      }}
      className="m-auto w-96 max-w-[90vw] rounded p-4 backdrop:bg-ink/40"
    >
      {editing && (
        <AddressForm initial={editing === 'new' ? undefined : editing} onSaved={onClose} onCancel={onClose} />
      )}
    </dialog>
  )
}

export function AddressesPage() {
  const q = useAddresses()
  const save = useSaveAddress()
  const remove = useDeleteAddress()
  const [editing, setEditing] = useState<Address | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Address | null>(null)

  if (!q.data) return q.isError ? <SectionError onRetry={() => q.refetch()} /> : <Spinner />
  const full = q.data.length >= MAX
  const error = save.isError ? save.error : remove.isError ? remove.error : null

  const makeDefault = ({ id, ...a }: Address) =>
    save.mutate({ id, input: { ...a, address2: a.address2 ?? '', isDefault: true } })

  return (
    <section className="mx-auto max-w-2xl space-y-4 px-4 py-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold">배송지 관리</h1>
        <button
          type="button"
          disabled={full}
          onClick={() => setEditing('new')}
          className="rounded bg-brand px-4 py-2 text-sm font-semibold text-white disabled:opacity-40"
        >
          배송지 추가
        </button>
      </div>
      {full && <p className="text-sm text-ink/60">배송지는 최대 10개까지 등록할 수 있습니다.</p>}
      {error && (
        <p role="alert" className="text-sm text-sale">
          {errorMessage(error)}
        </p>
      )}
      {q.data.length === 0 ? (
        <p className="py-10 text-center text-ink/60">등록된 배송지가 없습니다.</p>
      ) : (
        <ul className="divide-y divide-ink/10">
          {q.data.map((a) => (
            <li key={a.id} className="space-y-1 py-4">
              <p className="flex items-center gap-2 font-medium">
                {a.recipient}
                {a.isDefault && <span className="rounded bg-brand/10 px-2 py-0.5 text-xs text-brand-dark">기본</span>}
              </p>
              <p className="text-sm">{a.phone}</p>
              <p className="text-sm">
                ({a.zipCode}) {a.address1} {a.address2}
              </p>
              <div className="flex gap-3 pt-1 text-sm">
                <button type="button" onClick={() => setEditing(a)} className="underline">
                  수정
                </button>
                <button type="button" onClick={() => setDeleting(a)} className="text-ink/60 underline">
                  삭제
                </button>
                {!a.isDefault && (
                  <button type="button" disabled={save.isPending} onClick={() => makeDefault(a)} className="underline disabled:opacity-40">
                    기본으로 지정
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
      <FormDialog editing={editing} onClose={() => setEditing(null)} />
      <ConfirmDialog
        open={deleting !== null}
        title="배송지 삭제"
        message="배송지를 삭제할까요?"
        confirmLabel="삭제"
        pending={remove.isPending}
        onCancel={() => setDeleting(null)}
        onConfirm={() => deleting && remove.mutate(deleting.id, { onSettled: () => setDeleting(null) })}
      />
    </section>
  )
}
