import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 配送端消息中心契约守门。
 *
 * 真实缺口：站内信一直有发给配送员（派单通知、COD 收款提醒），但配送端
 * 没有任何消息入口，消息发了司机看不到。本契约钉住「入口 + 通道 + 身份」三件事：
 *
 * 1. 入口必须在工作台与个人中心都可达，并带未读角标；
 * 2. 通道复用工作人员链路（司机在服务端就是 SYS_USER），不能新建一套；
 * 3. 必须用配送 token（authScope: 'delivery'）——配送员与商城用户是两套身份，
 *    混用会出现「商城登录态有未读、配送登录态没有」的错位。
 */
// 本文件在 src/pages/delivery/ 下，回退三层才是项目根
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const DELIVERY_MESSAGE_API = 'src/api/delivery-message.ts'
const DELIVERY_MESSAGE_STORE = 'src/store/deliveryMessageStore.ts'
const DELIVERY_MESSAGE_PAGE = 'src/pages/delivery/message.vue'
const WORKBENCH = 'src/pages/delivery/index.vue'
const PROFILE = 'src/pages/delivery/profile.vue'

describe('delivery message entry contract', () => {
  it('工作台与个人中心都有消息入口且带未读角标', () => {
    const workbench = source(WORKBENCH)
    const profile = source(PROFILE)

    expect(workbench).toContain('deliveryMessageStore')
    expect(workbench).toContain('toMessage')
    expect(workbench).toContain('noticeUnread')
    expect(workbench).toContain('name: \'delivery-message\'')

    expect(profile).toContain('toMessage')
    expect(profile).toContain('noticeUnread')
  })

  it('消息页走工作人员站内信接口，不新建一套', () => {
    const api = source(DELIVERY_MESSAGE_API)
    const store = source(DELIVERY_MESSAGE_STORE)

    // 司机在服务端就是 SYS_USER：站内信与 WS 都复用 /message/staff 链路
    expect(api).toContain('\'/message/staff/notice\'')
    expect(api).toContain('\'/message/staff/notice/unread/count\'')
    expect(store).toContain('\'/message/ws/staff\'')
  })

  it('配送端消息请求必须带 authScope: delivery（否则拿商城 token 对不上身份）', () => {
    const api = source(DELIVERY_MESSAGE_API)

    expect(api).toContain('authScope: \'delivery\'')
    // 三个接口都要带标记，漏一个就会用商城 token 调工作人员接口
    const matches = api.match(/DELIVERY_SCOPE/g) ?? []
    expect(matches.length).toBeGreaterThanOrEqual(3)
  })

  it('响应侧按 meta.deliveryRequest 判定 401 归属，不只看 URL', () => {
    const handlers = source('src/api/core/handlers.ts')
    const instance = source('src/api/core/instance.ts')

    // authScope 头在 beforeRequest 里被删除，响应侧只能靠 meta 判断；
    // 否则 /message/staff/** 的 401 会错清商城登录态并把司机踢到商城登录页
    expect(handlers).toContain('deliveryRequest')
    expect(instance).toContain('deliveryRequest,')
  })

  it('消息页声明为配送端页面并注册进 pages.json', () => {
    const page = source(DELIVERY_MESSAGE_PAGE)
    const pagesJson = source('src/pages.json')

    expect(page).toContain('name: \'delivery-message\'')
    expect(pagesJson).toContain('pages/delivery/message')
    expect(pagesJson).toContain('"name": "delivery-message"')
  })

  it('通知点击只标记已读，不跳管理端路由', () => {
    const page = source(DELIVERY_MESSAGE_PAGE)

    // jumpPayload 里的 /order/** 是管理端页面，司机没有权限，点过去只会 403
    expect(page).toContain('markStaffNoticeRead')
    expect(page).not.toContain('\'/order/order-info\'')
  })

  it('退出配送登录时先断消息通道', () => {
    const profile = source(PROFILE)

    // 不断开的话重连循环会拿着已清掉的 token 反复尝试
    expect(profile).toMatch(/messageStore\.disconnect\(\)[\s\S]{0,200}remove\('deliveryToken'\)/)
  })
})
