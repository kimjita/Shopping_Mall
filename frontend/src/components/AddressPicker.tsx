import { useEffect, useRef, useState } from 'react'
import { useAddresses } from '../api/addresses'
import { AddressForm } from './AddressForm'

interface Props {
  selectedId: number | null
  onSelect: (id: number) => void
}

// 배송지가 없으면 목록 대신 등록 폼을 바로 보여 주고, 저장한 배송지를 고른다.
export function AddressPicker({ selectedId, onSelect }: Props) {
  const q = useAddresses()
  const list = q.data ?? []
  const [open, setOpen] = useState(false)
  const [choice, setChoice] = useState<number | null>(null)
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  const pick = (id: number) => {
    onSelect(id)
    setOpen(false)
  }

  return (
    <>
      <button
        type="button"
        disabled={!q.data}
        onClick={() => {
          setChoice(selectedId)
          setOpen(true)
        }}
        className="rounded border border-ink/20 px-3 py-1.5 text-sm disabled:opacity-40"
      >
        {list.length === 0 ? '배송지 등록' : '변경'}
      </button>
      <dialog
        ref={ref}
        aria-label="배송지 선택"
        onCancel={(e) => {
          e.preventDefault()
          setOpen(false)
        }}
        className="m-auto w-96 max-w-[90vw] rounded p-4 backdrop:bg-ink/40"
      >
        <h2 className="mb-3 font-bold">배송지 선택</h2>
        {open &&
          (list.length === 0 ? (
            <AddressForm onSaved={(a) => pick(a.id)} onCancel={() => setOpen(false)} />
          ) : (
            <form
              onSubmit={(e) => {
                e.preventDefault()
                if (choice !== null) pick(choice)
              }}
              className="space-y-4"
            >
              <ul className="max-h-80 divide-y divide-ink/10 overflow-y-auto">
                {list.map((a) => (
                  <li key={a.id}>
                    <label className="flex gap-2 py-3 text-sm">
                      <input
                        type="radio"
                        name="address"
                        checked={choice === a.id}
                        onChange={() => setChoice(a.id)}
                        className="mt-0.5 size-4 shrink-0 accent-brand"
                      />
                      <span className="min-w-0 break-words">
                        <span className="font-medium">{a.recipient}</span> · ({a.zipCode}) {a.address1} {a.address2}
                      </span>
                    </label>
                  </li>
                ))}
              </ul>
              <div className="flex justify-end gap-2">
                <button type="button" onClick={() => setOpen(false)} className="rounded border border-ink/20 px-4 py-2 text-sm">
                  취소
                </button>
                <button
                  type="submit"
                  disabled={choice === null}
                  className="rounded bg-brand px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
                >
                  선택
                </button>
              </div>
            </form>
          ))}
      </dialog>
    </>
  )
}
