import { useEffect, useRef } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { ApiError } from '../api/client'
import { useConfirmPayment } from '../api/orders'
import { Spinner } from '../components/Spinner'
import { isOrderNumber } from '../lib/orderName'

export function PaymentSuccessPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const confirm = useConfirmPayment()
  const sent = useRef(false)

  const paymentKey = params.get('paymentKey') ?? ''
  const orderId = params.get('orderId') ?? ''
  const amountText = params.get('amount') ?? ''
  const valid = paymentKey.length >= 1 && paymentKey.length <= 200 && isOrderNumber(orderId) && /^[1-9]\d*$/.test(amountText)
  const vars = { orderNumber: orderId, paymentKey, amount: Number(amountText) }

  // StrictMode의 두 번째 실행에서 승인 요청이 다시 나가지 않도록 ref로 막는다.
  useEffect(() => {
    if (!valid || sent.current) return
    sent.current = true
    confirm.mutate(vars)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const error = confirm.error instanceof ApiError ? confirm.error : null
  const notPayable = error?.code === 'ORDER_NOT_PAYABLE'
  useEffect(() => {
    // 상세 화면이 현재 주문 상태를 다시 읽는다.
    if (confirm.isSuccess) navigate(`/mypage/orders/${orderId}`, { replace: true, state: { notice: '결제가 완료되었습니다.' } })
    else if (notPayable) navigate(`/mypage/orders/${orderId}`, { replace: true, state: { notice: error.message } })
  }, [confirm.isSuccess, notPayable, error, orderId, navigate])

  if (!valid) {
    return (
      <div className="py-16 text-center">
        <p role="alert">결제 정보가 올바르지 않습니다.</p>
        <Link to="/" className="mt-4 inline-block rounded border border-ink/20 px-4 py-2 text-sm">홈으로</Link>
      </div>
    )
  }
  if (confirm.isError && !notPayable) {
    const delayed = error?.code === 'PAYMENT_GATEWAY_ERROR'
    return (
      <div className="py-16 text-center">
        <p role="alert">{confirm.error.message}</p>
        {delayed && <p className="mt-2 text-sm text-ink/70">결제 확인이 지연되고 있습니다. 잠시 후 다시 확인해 주세요.</p>}
        {delayed ? (
          <button type="button" onClick={() => confirm.mutate(vars)} className="mt-4 rounded bg-brand px-4 py-2 text-sm text-white">
            다시 확인
          </button>
        ) : (
          <Link to={`/mypage/orders/${orderId}`} className="mt-4 inline-block rounded border border-ink/20 px-4 py-2 text-sm">
            주문 상세로
          </Link>
        )}
      </div>
    )
  }
  return (
    <div className="py-16 text-center">
      <Spinner />
      <p>결제를 확인하고 있습니다.</p>
    </div>
  )
}
