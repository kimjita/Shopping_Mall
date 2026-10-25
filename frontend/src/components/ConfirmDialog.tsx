import { useEffect, useRef } from 'react'

interface Props {
  open: boolean
  title: string
  message: string
  confirmLabel: string
  onConfirm: () => void
  onCancel: () => void
  pending?: boolean
}

export function ConfirmDialog({ open, title, message, confirmLabel, onConfirm, onCancel, pending }: Props) {
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
      aria-label={title}
      // Esc로 스스로 닫히지 않게 막고, 닫기는 open prop으로만 한다.
      onCancel={(e) => {
        e.preventDefault()
        onCancel()
      }}
      className="m-auto w-80 max-w-[90vw] rounded p-4 backdrop:bg-ink/40"
    >
      <h2 className="font-bold">{title}</h2>
      <p className="mt-2 text-sm">{message}</p>
      <div className="mt-4 flex justify-end gap-2">
        <button type="button" onClick={onCancel} className="rounded border border-ink/20 px-4 py-2 text-sm">
          취소
        </button>
        <button
          type="button"
          onClick={onConfirm}
          disabled={pending}
          className="rounded bg-brand-dark px-4 py-2 text-sm text-white disabled:opacity-50"
        >
          {confirmLabel}
        </button>
      </div>
    </dialog>
  )
}
