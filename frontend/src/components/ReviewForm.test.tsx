import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it, vi } from 'vitest'
import { ReviewForm } from './ReviewForm'

function setup(props: Partial<Parameters<typeof ReviewForm>[0]> = {}) {
  const onSubmit = vi.fn()
  render(<ReviewForm submitLabel="등록" pending={false} onSubmit={onSubmit} onCancel={() => {}} {...props} />)
  return onSubmit
}
const type = (text: string) => userEvent.type(screen.getByLabelText('리뷰 내용'), text)
const submit = () => userEvent.click(screen.getByRole('button', { name: '등록' }))

it('공백을 뺀 글자 수를 보여 준다', async () => {
  setup()
  await type('  좋아요  ')
  expect(screen.getByText('3/1,000자')).toBeInTheDocument()
})

it('별점은 처음에 고르지 않은 상태다', () => {
  setup()
  expect(screen.getByRole('radiogroup', { name: '별점' })).toBeInTheDocument()
  expect(screen.getAllByRole('radio')).toHaveLength(5)
  expect(screen.getAllByRole('radio').some((r) => (r as HTMLInputElement).checked)).toBe(false)
})

it('별점이 없으면 제출하지 않는다', async () => {
  const onSubmit = setup()
  await type('촉촉하고 순해서 좋아요')
  await submit()
  expect(screen.getByRole('alert')).toHaveTextContent('별점을 골라 주세요.')
  expect(onSubmit).not.toHaveBeenCalled()
})

it('공백만이거나 10자 미만이면 제출하지 않는다', async () => {
  const onSubmit = setup()
  await userEvent.click(screen.getByRole('radio', { name: '별점 4점' }))
  await type('            ')
  await submit()
  expect(screen.getByRole('alert')).toHaveTextContent('리뷰는 10자 이상 써 주세요.')
  await type('짧은 리뷰')
  await submit()
  expect(screen.getByRole('alert')).toHaveTextContent('리뷰는 10자 이상 써 주세요.')
  expect(onSubmit).not.toHaveBeenCalled()
})

it('1,000자를 넘으면 제출하지 않는다', async () => {
  const onSubmit = setup()
  await userEvent.click(screen.getByRole('radio', { name: '별점 5점' }))
  await userEvent.click(screen.getByLabelText('리뷰 내용'))
  await userEvent.paste('가'.repeat(1001))
  expect(screen.getByText('1001/1,000자')).toBeInTheDocument()
  await submit()
  expect(screen.getByRole('alert')).toHaveTextContent('리뷰는 1,000자 이하로 써 주세요.')
  expect(onSubmit).not.toHaveBeenCalled()
})

it('규칙을 지키면 원문으로 onSubmit을 부른다', async () => {
  const onSubmit = setup()
  await userEvent.click(screen.getByRole('radio', { name: '별점 4점' }))
  await type('  촉촉하고 순해서 좋아요  ')
  await submit()
  expect(onSubmit).toHaveBeenCalledWith({ rating: 4, content: '  촉촉하고 순해서 좋아요  ' })
})

it('initial과 서버 오류를 보여 준다', () => {
  setup({ initial: { rating: 2, content: '처음 쓴 리뷰 내용입니다' }, error: '이미 리뷰를 썼습니다.' })
  expect(screen.getByRole('radio', { name: '별점 2점' })).toBeChecked()
  expect(screen.getByLabelText('리뷰 내용')).toHaveValue('처음 쓴 리뷰 내용입니다')
  expect(screen.getByRole('alert')).toHaveTextContent('이미 리뷰를 썼습니다.')
})
