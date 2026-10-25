import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { ConfirmDialog } from './ConfirmDialog'

const setup = (props: { open?: boolean; pending?: boolean } = {}) => {
  const onConfirm = vi.fn()
  const onCancel = vi.fn()
  render(
    <ConfirmDialog
      open={props.open ?? true}
      title="주문 취소"
      message="주문을 취소할까요?"
      confirmLabel="주문 취소하기"
      pending={props.pending}
      onConfirm={onConfirm}
      onCancel={onCancel}
    />,
  )
  return { onConfirm, onCancel }
}

it('확인을 누르면 onConfirm, 취소를 누르면 onCancel을 부른다', async () => {
  const { onConfirm, onCancel } = setup()
  expect(screen.getByRole('dialog', { name: '주문 취소' })).toHaveTextContent('주문을 취소할까요?')
  await userEvent.click(screen.getByRole('button', { name: '주문 취소하기' }))
  expect(onConfirm).toHaveBeenCalledTimes(1)
  await userEvent.click(screen.getByRole('button', { name: '취소' }))
  expect(onCancel).toHaveBeenCalledTimes(1)
})

it('닫혀 있으면 보이지 않는다', () => {
  setup({ open: false })
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
})

it('pending이면 확인 버튼이 비활성이다', () => {
  setup({ pending: true })
  expect(screen.getByRole('button', { name: '주문 취소하기' })).toBeDisabled()
})

it('Esc로 닫으려 하면 onCancel을 부른다', () => {
  const { onCancel } = setup()
  screen.getByRole('dialog').dispatchEvent(new Event('cancel', { cancelable: true }))
  expect(onCancel).toHaveBeenCalledTimes(1)
})
