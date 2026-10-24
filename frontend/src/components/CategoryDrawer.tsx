import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router'
import { useCategories } from '../api/catalog'
import type { Category } from '../api/types'
import { SectionError } from './SectionError'
import { Spinner } from './Spinner'

function Tree({ nodes, onPick }: { nodes: Category[]; onPick: (id: number) => void }) {
  return (
    <ul className="pl-3">
      {nodes.map((c) => (
        <li key={c.id}>
          <button type="button" onClick={() => onPick(c.id)} className="py-2 text-left">
            {c.name}
          </button>
          {c.children.length > 0 && <Tree nodes={c.children} onPick={onPick} />}
        </li>
      ))}
    </ul>
  )
}

// 열릴 때만 마운트되므로 카테고리는 처음 열 때 불러온다.
export function CategoryDrawer({ onClose }: { onClose: () => void }) {
  const ref = useRef<HTMLDialogElement>(null)
  const navigate = useNavigate()
  const { data, isPending, isError, refetch } = useCategories()

  useEffect(() => {
    const dialog = ref.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  return (
    <dialog
      ref={ref}
      aria-label="카테고리"
      onClose={onClose}
      onClick={(e) => e.target === ref.current && onClose()}
      className="m-0 h-full max-h-none w-72 max-w-[85vw] overflow-y-auto p-4 backdrop:bg-ink/40"
    >
      <button type="button" onClick={onClose} className="mb-2 text-sm">
        닫기
      </button>
      {isPending && <Spinner />}
      {isError && <SectionError onRetry={() => refetch()} />}
      {data && (
        <Tree
          nodes={data}
          onPick={(id) => {
            navigate(`/products?categoryId=${id}`)
            onClose()
          }}
        />
      )}
    </dialog>
  )
}
