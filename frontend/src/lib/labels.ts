import type {
  CancelReason,
  CartItemStatus,
  DiscountType,
  Grade,
  OrderStatus,
  ProductSort,
  ReviewSort,
  SkinType,
} from '../api/types'
import { formatWon } from './format'

export const SKIN_TYPE_LABEL: Record<SkinType, string> = {
  DRY: '건성',
  OILY: '지성',
  COMBINATION: '복합성',
  SENSITIVE: '민감성',
  NORMAL: '중성',
}

export const PRODUCT_SORT_LABEL: Record<ProductSort, string> = {
  NEWEST: '신상품순',
  SALES: '판매순',
  PRICE_ASC: '낮은 가격순',
  PRICE_DESC: '높은 가격순',
  RATING: '평점순',
  REVIEW_COUNT: '리뷰 많은순',
}

export const REVIEW_SORT_LABEL: Record<ReviewSort, string> = {
  LATEST: '최신순',
  RATING_DESC: '별점 높은순',
  RATING_ASC: '별점 낮은순',
}

// AVAILABLE은 화면에 표시하지 않는다.
export const CART_STATUS_LABEL: Record<Exclude<CartItemStatus, 'AVAILABLE'>, string> = {
  NOT_ENOUGH_STOCK: '재고 부족',
  SOLD_OUT: '품절',
  UNAVAILABLE: '판매 종료',
}

export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  PENDING: '결제 대기',
  PAID: '결제 완료',
  PREPARING: '상품 준비 중',
  SHIPPING: '배송 중',
  DELIVERED: '배송 완료',
  CONFIRMED: '구매확정',
  CANCELED: '취소',
}

export const CANCEL_REASON_LABEL: Record<CancelReason, string> = {
  EXPIRED: '결제 기한 지남',
  REPLACED: '새 주문으로 대체',
  USER: '고객 취소',
  ADMIN: '판매자 취소',
}

export const GRADE_LABEL: Record<Grade, string> = {
  BABY: '베이비',
  PINK: '핑크',
  GREEN: '그린',
  BLACK: '블랙',
  GOLD: '골드',
}

export const couponDiscountText = (c: { discountType: DiscountType; discountValue: number; maxDiscount: number | null }) =>
  c.discountType === 'FIXED'
    ? `${formatWon(c.discountValue)} 할인`
    : `${c.discountValue}% 할인` + (c.maxDiscount === null ? '' : ` (최대 ${formatWon(c.maxDiscount)})`)

export const minOrderText = (min: number) => (min > 0 ? `${formatWon(min)} 이상 구매 시` : '')
