import type { CartItemStatus, ProductSort, ReviewSort, SkinType } from '../api/types'

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
