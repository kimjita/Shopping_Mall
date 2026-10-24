// 로컬 백엔드에 데모 데이터를 관리자 API로 채운다. 사용법은 README "데모 데이터" 절에 있다.
import { brands, buildProducts, buildBanners } from './data.mjs'

const apiBase = process.env.API_BASE_URL ?? 'http://localhost:8080'
const imageBase = process.env.IMAGE_BASE_URL ?? 'http://localhost:5173'
const { ADMIN_EMAIL, ADMIN_PASSWORD } = process.env

if (!ADMIN_EMAIL || !ADMIN_PASSWORD) {
  console.error('사용법: ADMIN_EMAIL=… ADMIN_PASSWORD=… [API_BASE_URL=…] [IMAGE_BASE_URL=…] node scripts/demo/seed.mjs')
  process.exit(1)
}

let token
async function call(method, path, body) {
  const res = await fetch(apiBase + path, {
    method,
    headers: {
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  if (!res.ok) {
    console.error(`${method} ${path} 실패: ${res.status} ${text}`)
    process.exit(1)
  }
  return text ? JSON.parse(text) : null
}

token = (await call('POST', '/api/auth/login', { email: ADMIN_EMAIL, password: ADMIN_PASSWORD })).accessToken

const existing = await call('GET', '/api/brands')
if (existing.some((b) => brands.some((d) => d.name === b.name))) {
  console.log('이미 데모 데이터가 있습니다.')
  process.exit(0)
}

const brandIds = new Map()
for (const b of brands) brandIds.set(b.name, (await call('POST', '/api/admin/brands', b)).id)

const now = new Date()
const iso = (ms) => new Date(now.getTime() + ms).toISOString()
const products = buildProducts(imageBase)
for (const { brandName, saleRate, ...p } of products) {
  const { id } = await call('POST', '/api/admin/products', { ...p, brandId: brandIds.get(brandName) })
  if (saleRate) await call('PUT', `/api/admin/products/${id}/sale`, { rate: saleRate, startsAt: iso(-3600e3), endsAt: iso(14 * 86400e3) })
}

const banners = buildBanners(imageBase, now)
for (const b of banners) await call('POST', '/api/admin/banners', b)

console.log(`브랜드 ${brands.length}개, 상품 ${products.length}개, 배너 ${banners.length}개를 만들었습니다.`)
