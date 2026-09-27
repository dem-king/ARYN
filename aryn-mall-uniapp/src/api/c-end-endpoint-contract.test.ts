import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const projectRoot = fileURLToPath(new URL('../..', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

/**
 * C 端页面不得直连管理端接口的契约测试。
 *
 * 背景（真实缺陷）：签到页调用了 `/mall-user/signinrecord/page` 与
 * `/mall-user/signinconfig/page`，这两个是管理端接口，标着
 * `@SaCheckPermission("user:signinrecord:page")` 等权限。而 C 端登录
 * （TocLoginService）从不给 token 灌权限，于是服务端一律返回 403；
 * 前端又把 403 当成登录过期，清 token 跳登录页 —— 表现为「登录一直失效」，
 * 重新登录也无济于事。
 *
 * 同批修复的还有积分记录、余额记录、充值配置三处（同一个错误模式）。
 * 这类缺陷编译、类型检查、既有测试全绿，只有真机进页面才暴露，
 * 因此这里把「C 端页面用 C 端接口」绑成一条静态契约。
 */
describe('C-end api layer must not call admin endpoints', () => {
  /** 管理端接口（C 端调用必然 403）：路径 -> 需要的管理端权限 */
  const ADMIN_ONLY_PATHS = [
    '/mall-user/signinrecord/page',
    '/mall-user/signinconfig/page',
    '/mall-user/pointsrecord/user/page',
    '/mall-user/balancerecord/user/page',
    '/mall-user/rechargeconfig/page',
  ]

  /** C 端 api 层文件（移动端全部请求都从这里发出） */
  const C_END_API_FILES = [
    'src/api/user/signIn.ts',
    'src/api/user/points.ts',
    'src/api/user/balance.ts',
    'src/api/user/recharge.ts',
    'src/api/user/member.ts',
    'src/api/user/user.ts',
    'src/api/user/address.ts',
    'src/api/user/benefit.ts',
  ]

  it('keeps every C-end request on an /app/ endpoint', () => {
    const offenders: string[] = []
    for (const file of C_END_API_FILES) {
      const content = source(file)
      for (const adminPath of ADMIN_ONLY_PATHS) {
        if (content.includes(adminPath)) {
          offenders.push(`${file} -> ${adminPath}`)
        }
      }
    }
    expect(offenders).toEqual([])
  })

  it('routes the sign-in page to C-end endpoints', () => {
    const signInSource = source('src/api/user/signIn.ts')

    expect(signInSource).toContain('/mall-user/app/signin/configs')
    expect(signInSource).toContain('/mall-user/app/signin/records')
    expect(signInSource).toContain('/mall-user/app/signin')
  })

  it('routes points, balance and recharge to C-end endpoints', () => {
    expect(source('src/api/user/points.ts')).toContain('/mall-user/app/points/records')
    expect(source('src/api/user/balance.ts')).toContain('/mall-user/app/balance/records')
    expect(source('src/api/user/recharge.ts')).toContain('/mall-user/app/recharge/config/list')
  })

  it('never passes another user id as a query parameter', () => {
    // 管理端接口靠 userId 参数查任意用户；C 端必须由服务端从登录态取本人 ID，
    // 否则改一个参数就能看别人的记录。
    const offenders: string[] = []
    for (const file of C_END_API_FILES) {
      const content = source(file)
      if (/userId/.test(content)) {
        offenders.push(file)
      }
    }
    expect(offenders).toEqual([])
  })
})
