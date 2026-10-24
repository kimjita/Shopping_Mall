import { test } from 'node:test'
import assert from 'node:assert/strict'
import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { brands, buildProducts, buildBanners } from './data.mjs'

const demoDir = fileURLToPath(new URL('../../frontend/public/demo/', import.meta.url))
const products = buildProducts('http://x')

const parentOf = {}
const groups = { 1: [8, 12], 2: [13, 15], 3: [16, 19], 4: [20, 22], 6: [26, 28], 7: [29, 31], 5: [32, 40] }
for (const [parent, [from, to]] of Object.entries(groups)) {
  for (let id = from; id <= to; id++) parentOf[id] = Number(parent)
}

test('브랜드는 8개이고 이름이 겹치지 않는다', () => {
  assert.equal(brands.length, 8)
  assert.equal(new Set(brands.map((b) => b.name)).size, 8)
})

test('상품 수와 카테고리 분포', () => {
  assert.ok(products.length >= 36 && products.length <= 44)
  for (const p of products) assert.ok(parentOf[p.categoryId], `말단이 아님: ${p.categoryId}`)
  for (const parent of [1, 2, 3, 4, 5, 6, 7]) {
    const n = products.filter((p) => parentOf[p.categoryId] === parent).length
    assert.ok(n >= 3, `상위 ${parent}: ${n}개`)
  }
})

test('이름·SKU·가격 규칙', () => {
  const brandNames = new Set(brands.map((b) => b.name))
  const seen = new Set()
  for (const p of products) {
    assert.ok(brandNames.has(p.brandName), p.brandName)
    const key = `${p.brandName}/${p.name}`
    assert.ok(!seen.has(key), `중복: ${key}`)
    seen.add(key)
    assert.ok(p.skus.length >= 1 && p.skus.length <= 3)
    assert.equal(new Set(p.skus.map((s) => s.name)).size, p.skus.length)
    for (const s of p.skus) assert.ok(s.name.length >= 1 && s.name.length <= 50 && s.stock >= 0)
    assert.equal(p.basePrice % 100, 0)
    assert.ok(p.basePrice >= 3000 && p.basePrice <= 60000)
  }
})

test('품절과 세일', () => {
  const soldOut = products.filter((p) => p.skus.every((s) => s.stock === 0))
  assert.ok(soldOut.length >= 4)
  assert.ok(products.some((p) => p.skus.length > 1 && p.skus.some((s) => s.stock === 0) && p.skus.some((s) => s.stock > 0)))
  const onSale = products.filter((p) => p.saleRate !== undefined)
  assert.equal(onSale.length, 10)
  for (const p of onSale) assert.ok(p.saleRate >= 10 && p.saleRate <= 40)
})

test('이미지 URL과 파일', () => {
  for (const p of products) {
    assert.ok(p.imageUrls.length >= 1)
    for (const url of p.imageUrls) {
      assert.ok(url.startsWith('http://x/demo/'), url)
      assert.ok(existsSync(demoDir + url.slice('http://x/demo/'.length)), url)
    }
  }
})

test('배너', () => {
  const banners = buildBanners('http://x', new Date('2026-10-25T00:00:00Z'))
  assert.equal(banners.length, 3)
  assert.equal(banners.filter((b) => b.linkUrl.startsWith('https://')).length, 1)
  for (const b of banners) {
    assert.match(b.linkUrl, /^(https?:\/\/\S+|\/(?![/\\])\S*)$/)
    assert.ok(existsSync(demoDir + b.imageUrl.slice('http://x/demo/'.length)), b.imageUrl)
  }
})
