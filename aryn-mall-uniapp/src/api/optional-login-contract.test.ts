import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 「可选登录」接口契约守门。
 *
 * 真实缺陷：商品详情接口被加上 `skipToken: true` 后不再携带 satoken，
 * 后端 getApiSpuById 拿不到当前用户 → 浏览足迹一行不写、收藏状态永远未收藏；
 * 优惠券 byIds 同理，装修楼层刷新后丢失「已领取」。这类接口都在免登白名单里，
 * 但必须带着登录态（未登录时中间件本就不发 token）。类型检查发现不了「少了一个头」，
 * 因此按源码结构断言。
 */
const projectRoot = fileURLToPath(new URL('../../', import.meta.url))
const repoRoot = resolve(projectRoot, '..')

const GOODS_SPU_SERVICE = 'aryn-mall-java/aryn-product/aryn-product-biz/src/main/java/com/aryn/cloud/product/service/impl/GoodsSpuServiceImpl.java'

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

function repoSource(relativePath: string) {
  return readFileSync(resolve(repoRoot, relativePath), 'utf8')
}

function exportedFunctionBody(file: string, name: string) {
  const match = file.match(new RegExp(`export function ${name}\\([^)]*\\) \\{[\\s\\S]*?\\n\\}`))
  expect(match, `未找到 export function ${name}`).not.toBeNull()
  return match![0]
}

describe('optional-login request contract', () => {
  it('sends the login token on the goods detail request', () => {
    const api = source('src/api/product/spu.ts')
    // 详情接口必须让后端识别当前用户：去掉 skipToken 才能写入足迹、回填收藏
    expect(exportedFunctionBody(api, 'getById')).not.toContain('skipToken')
  })

  it('sends the login token on the coupon byIds request', () => {
    const api = source('src/api/promotion/couponInfo.ts')
    // 与分页列表同口径：带登录态才能回填 userReceiveCount
    expect(exportedFunctionBody(api, 'getByIds')).not.toContain('skipToken')
  })

  it('backend only records footprints when the request carries a logged-in user', () => {
    const service = repoSource(GOODS_SPU_SERVICE)
    const detail = service.match(/public GoodsSpu getApiSpuById\(String id\) \{[\s\S]*?\n\t\}/)
    expect(detail).not.toBeNull()
    expect(detail![0]).toContain('SecurityUtils.getUser()')
    expect(detail![0]).toContain('goodsFootprintMapper.insert')
  })
})
