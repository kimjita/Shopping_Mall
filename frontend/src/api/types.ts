export type SkinType = 'DRY' | 'OILY' | 'COMBINATION' | 'SENSITIVE' | 'NORMAL'
export type ProductSort = 'NEWEST' | 'SALES' | 'PRICE_ASC' | 'PRICE_DESC' | 'RATING' | 'REVIEW_COUNT'
export type ReviewSort = 'LATEST' | 'RATING_DESC' | 'RATING_ASC'
export type Grade = 'BABY' | 'PINK' | 'GREEN' | 'BLACK' | 'GOLD'
export type OrderStatus = 'PENDING' | 'PAID' | 'PREPARING' | 'SHIPPING' | 'DELIVERED' | 'CONFIRMED' | 'CANCELED'
export type CancelReason = 'EXPIRED' | 'REPLACED' | 'USER' | 'ADMIN'
export type DiscountType = 'FIXED' | 'RATE'
export type CartItemStatus = 'AVAILABLE' | 'NOT_ENOUGH_STOCK' | 'SOLD_OUT' | 'UNAVAILABLE'
export interface ErrorBody {
  code: string
  message: string
  errors: { field: string; reason: string }[]
}
export interface TokenResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
}
export interface Me {
  id: number
  email: string
  name: string
  phone: string
  role: 'USER' | 'ADMIN'
  grade: Grade
  skinType: SkinType | null
  skinTone: string | null
  skinConcerns: string[]
}
export interface ProductSummary {
  id: number
  name: string
  brandName: string
  thumbnailUrl: string | null
  price: number
  salePrice: number
  saleRate: number | null
  soldOut: boolean
  ratingAvg: number | null
  reviewCount: number
}
export interface CursorPage<T> {
  items: T[]
  nextCursor: string | null
}
export interface Category {
  id: number
  name: string
  children: Category[]
}
export interface Brand {
  id: number
  name: string
  description: string | null
}
export interface ProductDetail {
  id: number
  name: string
  brand: { id: number; name: string }
  categoryPath: { id: number; name: string }[]
  summary: string | null
  description: string
  ingredients: string | null
  imageUrls: string[]
  basePrice: number
  saleRate: number | null
  soldOut: boolean
  skus: { id: number; name: string; price: number; salePrice: number; soldOut: boolean }[]
  ratingAvg: number | null
  reviewCount: number
}
export interface RankingItem {
  rank: number
  product: ProductSummary
}
export interface Recommendations {
  skinType: SkinType
  items: { rank: number; product: ProductSummary; sameSkinReviewCount: number; sameSkinRatingAvg: number }[]
}
export interface Banner {
  id: number
  title: string
  imageUrl: string
  linkUrl: string
  startsAt: string
  endsAt: string
  sortOrder: number
}
export interface Review {
  reviewId: number
  authorName: string
  rating: number
  content: string
  imageUrls: string[]
  skuName: string
  skinType: SkinType | null
  skinTone: string | null
  skinConcerns: string[]
  createdAt: string
  updatedAt: string
}
export interface WishItem {
  product: ProductSummary
  available: boolean
  wishedAt: string
}
export interface RecentView {
  product: ProductSummary
  available: boolean
  viewedAt: string
}
export interface CartItem {
  id: number
  skuId: number
  skuName: string
  productId: number
  productName: string
  brandName: string
  thumbnailUrl: string | null
  quantity: number
  unitPrice: number
  unitSalePrice: number
  lineTotal: number
  status: CartItemStatus
}
export interface Address {
  id: number
  recipient: string
  phone: string
  zipCode: string
  address1: string
  address2: string | null
  isDefault: boolean
}
export interface AddressInput {
  recipient: string
  phone: string
  zipCode: string
  address1: string
  address2: string
  isDefault: boolean
}
export interface DownloadableCoupon {
  couponId: number
  name: string
  discountType: DiscountType
  discountValue: number
  maxDiscount: number | null
  minOrderAmount: number
  requiredGrade: Grade | null
  eligible: boolean
  remainingQuantity: number
  issueEndsAt: string
  useStartsAt: string
  useEndsAt: string
}
export interface MyCoupon {
  id: number
  couponId: number
  name: string
  discountType: DiscountType
  discountValue: number
  maxDiscount: number | null
  minOrderAmount: number
  status: 'AVAILABLE' | 'USED' | 'EXPIRED'
  useStartsAt: string
  useEndsAt: string
  usedAt: string | null
  issuedAt: string
}
export interface PriceBreakdown {
  itemsPrice: number
  saleDiscount: number
  itemsSalePrice: number
  couponDiscount: number
  shippingFee: number
  pointsUsed: number
  paymentAmount: number
}
export interface CheckoutPreview {
  lines: {
    cartItemId: number
    skuId: number
    productName: string
    skuName: string
    thumbnailUrl: string | null
    quantity: number
    unitPrice: number
    unitSalePrice: number
    lineTotal: number
  }[]
  price: PriceBreakdown
  pointBalance: number
}
export interface OrderItem {
  productId: number
  skuId: number
  productName: string
  skuName: string
  thumbnailUrl: string | null
  unitPrice: number
  unitSalePrice: number
  quantity: number
  lineTotal: number
}
export interface Order {
  orderNumber: string
  status: OrderStatus
  orderedAt: string
  expiresAt: string
  items: OrderItem[]
  price: PriceBreakdown
  couponName: string | null
  shipping: { recipient: string; phone: string; zipCode: string; address1: string; address2: string | null }
  canceledAt: string | null
  cancelReason: CancelReason | null
  deliveredAt: string | null
  confirmedAt: string | null
}
export interface OrderSummary {
  orderNumber: string
  status: OrderStatus
  orderedAt: string
  firstProductName: string
  thumbnailUrl: string | null
  itemCount: number
  paymentAmount: number
}
