import { Link, useSearchParams } from 'react-router'
import { isOrderNumber } from '../lib/orderName'

export function PaymentFailPage() {
  const [params] = useSearchParams()
  const orderId = params.get('orderId') ?? ''
  const link = 'mt-4 inline-block rounded border border-ink/20 px-4 py-2 text-sm'
  return (
    <div className="py-16 text-center">
      <h1 className="mb-2 text-lg font-bold">결제에 실패했습니다.</h1>
      <p role="alert">{params.get('message') || '결제가 완료되지 않았습니다.'}</p>
      {isOrderNumber(orderId) ? (
        <Link to={`/mypage/orders/${orderId}`} className={link}>주문 상세에서 다시 결제하기</Link>
      ) : (
        <Link to="/mypage/orders" className={link}>주문 내역 보기</Link>
      )}
    </div>
  )
}
