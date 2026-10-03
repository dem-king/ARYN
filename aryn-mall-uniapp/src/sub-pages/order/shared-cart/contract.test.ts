import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 共享购物车前后端契约守门。
 *
 * 这类问题的典型漏网方式是：页面写好了但忘了在 pages.json 注册，
 * 或前端调用的路径与后端 @RequestMapping 漂移——两者都不会被类型检查发现。
 */
const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))
const repoRoot = resolve(projectRoot, '..')

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

function repoSource(relativePath: string) {
  return readFileSync(resolve(repoRoot, relativePath), 'utf8')
}

const API_BASE = '/mall-order/app/shared-cart'
const ORDER_BIZ = 'aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order'
const ORDER_API = 'aryn-mall-java/aryn-order/aryn-order-api/src/main/java/com/aryn/cloud/order'

describe('shared cart routing contract', () => {
  it('registers both shared cart pages in pages.json', () => {
    const pages = source('src/pages.json')
    expect(pages).toContain('"path": "order/shared-cart/list"')
    expect(pages).toContain('"name": "shared-cart-list"')
    expect(pages).toContain('"path": "order/shared-cart/detail"')
    expect(pages).toContain('"name": "shared-cart-detail"')
  })

  it('keeps the list page navigating to the detail page', () => {
    const list = source('src/sub-pages/order/shared-cart/list.vue')
    expect(list).toContain('/sub-pages/order/shared-cart/detail?id=')
  })

  it('keeps the detail page entering ship supply in shared-cart picking mode', () => {
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    expect(detail).toContain('sharedCartId=')
    expect(detail).toContain('/sub-pages/product/ship-supply/index')
  })

  it('keeps ship supply supporting shared-cart picking mode', () => {
    const shipSupply = source('src/sub-pages/product/ship-supply/index.vue')
    expect(shipSupply).toContain('sharedCartId')
    expect(shipSupply).toContain('addSharedCartItem')
    // 共享车模式下不得再写入个人购物车，否则同一件商品会进两处
    expect(shipSupply).toContain('isSharedMode')
  })

  it('detail page wires plan/fulfil editing to the backend plan endpoint', () => {
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    const api = source('src/api/order/sharedCart.ts')

    // 从函数体里取实际拼出的路径，避免"路径写错但别处碰巧有同样字样"蒙混过关
    const fnBody = api.slice(
      api.indexOf('export function updateSharedCartItemPlan'),
      api.indexOf('export function removeSharedCartItem'),
    )
    // 用正则而非普通字符串，避免 no-template-curly-in-string（与文件内既有写法一致）
    expect(fnBody).toMatch(/\$\{BASE\}\/\$\{id\}\/items\/plan/)
    expect(detail).toContain('updateSharedCartItemPlan')

    // 后端映射必须同段
    const controller = repoSource(`${ORDER_BIZ}/controller/app/AppSharedCartController.java`)
    expect(controller).toContain('@PutMapping("/{id}/items/plan")')
    expect(controller).toContain('@RequestMapping("/app/shared-cart")')
  })

  it('plan submission distinguishes clear-plan from leave-unchanged', () => {
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    const call = detail.slice(
      detail.indexOf('updateSharedCartItemPlan(cartId.value'),
      detail.indexOf('function handleRemoveItem'),
    )
    // 清空输入框 = 取消计划，必须显式带 clearPlanned，否则后端把 null 当成"不改"，
    // 用户清空后保存会发现计划没被清掉
    expect(call).toContain('clearPlanned: planned === null')
    // 只改已采量不能顺手把计划清掉（后端靠 fulfilledQuantity 非 null 判断）
    expect(call).toContain('fulfilledQuantity: fulfilled')
  })

  it('clear-plan must be written explicitly, not through updateById', () => {
    // 全库 update-strategy=not_null：updateById 会跳过 null 字段。
    // 后端若用 setPlannedQuantity(null) + updateById，接口照 200 返回、对象也是 null，
    // 但库里 planned_quantity 纹丝不动 —— 前端「清空保存」等于没生效且无任何报错。
    const impl = repoSource(`${ORDER_BIZ}/service/impl/SharedCartServiceImpl.java`)
    const fnStart = impl.indexOf('public SharedCartItem updateItemPlan')
    const fnBody = impl.slice(fnStart, impl.indexOf('public void removeItem', fnStart))
    expect(fnBody).toContain('lambdaUpdate')
    expect(fnBody).toMatch(/set\(SharedCartItem::getPlannedQuantity,\s*null\)/)
  })

  it('plan entry only shows to confirmer, not to every member', () => {
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    // 权限以服务端 viewerCanConfirm 为准，不能前端按 userId 猜
    expect(detail).toContain('viewerCanConfirm')
    expect(detail).not.toMatch(/const\s+canPlan\s*=\s*computed\(\(\)\s*=>\s*isMine/)
  })

  it('detail progress uses the same calculator as the home card', () => {
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    const card = source('src/components/diy/diy-replenish-card/index.vue')
    // 两处都走纯函数，避免"首页 65% / 详情 62%"这类漂移
    expect(detail).toContain('summarizeReplenishItems')
    expect(card).toContain('buildReplenishProgressView')
  })

  it('detail page exposes history reuse and posts to the reuse endpoint', () => {
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    const api = source('src/api/order/sharedCart.ts')

    const fnStart = api.indexOf('export function reuseSharedCartFromHistory')
    const fnBody = api.slice(fnStart, api.indexOf('\n}', fnStart) + 2)
    // 路径要连结尾反引号一起断言：只写 /reuse 会被 /reused 这种前缀匹配蒙混过关
    // （缺陷注入实测：把路径改成 /reused，第一版断言仍然全绿）。
    expect(fnBody).toMatch(/\$\{BASE\}\/\$\{id\}\/reuse`/)
    expect(detail).toContain('reuseSharedCartFromHistory')

    const controller = repoSource(`${ORDER_BIZ}/controller/app/AppSharedCartController.java`)
    expect(controller).toContain('@PostMapping("/{id}/reuse")')
  })

  it('import page uses the mall-order domain path in both boot and cloud modes', () => {
    const api = source('src/sub-pages/api/order/sharedCartImport.ts')
    const fnBody = api.slice(
      api.indexOf('export function previewSharedCartImport'),
      api.indexOf('export function downloadSharedCartImportTemplate'),
    )
    // 双模式强制：首段必须是微服务域 mall-order，再经 rewriteBootUrl 改写，
    // 不能在页面里硬编码 /boot（boot 模式由 context-path 统一加前缀）
    expect(fnBody).toContain('/import/preview')
    // 路径改写集中在 importPath 一处，避免每个调用各自拼 /boot 前缀
    const pathFn = api.slice(
      api.indexOf('function importPath'),
      api.indexOf('export function getSharedCartImportTemplateUrl'),
    )
    expect(pathFn).toContain('parseOpenBoot')
    expect(pathFn).toContain('rewriteBootUrl')
    expect(api).not.toContain('\'/boot/')

    const controller = repoSource(`${ORDER_BIZ}/controller/app/AppSharedCartController.java`)
    expect(controller).toContain('@RequestMapping("/app/shared-cart")')
    expect(controller).toContain('@PostMapping("/{id}/import/preview")')
    expect(controller).toContain('@PostMapping("/{id}/imports/{importId}/confirm")')
    expect(controller).toContain('@GetMapping("/import/template")')
    // Controller 上不得出现硬编码 /boot 前缀
    expect(controller).not.toContain('@RequestMapping("/boot')
  })

  it('import preview accepts the platform success code instead of a hardcoded 200', () => {
    const api = source('src/sub-pages/api/order/sharedCartImport.ts')
    const fnBody = api.slice(
      api.indexOf('export function previewSharedCartImport'),
      api.indexOf('export function downloadSharedCartImportTemplate'),
    )
    // 本平台成功码是 0（handlers.ts 以 code !== 0 判错）。曾经这里写死 !== 200，
    // 解析明明成功也会弹「上传解析失败」，导入功能等于不可用。
    // 成功判定只认 HTTP 状态 + data 存在，不得再出现字面量 200 比对。
    expect(fnBody).not.toMatch(/code[^\n]*!==\s*200/)
    expect(fnBody).not.toMatch(/code[^\n]*===\s*200/)
    expect(fnBody).toContain('res.statusCode')
    expect(fnBody).toContain('payload.data')
  })

  it('import template download keeps auth and goes through the gateway domain', () => {
    const api = source('src/sub-pages/api/order/sharedCartImport.ts')
    const fnBody = api.slice(
      api.indexOf('export function getSharedCartImportTemplateUrl'),
      api.indexOf('export function previewSharedCartImport'),
    )
    expect(fnBody).toContain('/import/template')
    expect(fnBody).toContain('rewriteBootUrl')

    // 模板下载要带鉴权头：模板接口挂在 C 端登录态下，不能为了省事挂到网关白名单
    expect(api).toContain('satoken')
    expect(api).toContain('uni.downloadFile')
    const page = source('src/sub-pages/order/shared-cart/import.vue')
    expect(page).toContain('downloadTemplate')
  })

  it('template lists on-sale goods by category so only the quantity column needs filling', () => {
    // 客户视角的核心诉求：打开模板就能看到自己商品按分类排好，只改数量列。
    // 后端必须走「目录导出」而不是给一行示例，否则客户仍要手敲编码与品名。
    const controller = repoSource(`${ORDER_BIZ}/controller/app/AppSharedCartController.java`)
    expect(controller).toContain('exportCatalog')
    expect(controller).toContain('catalogHead')
    expect(controller).toContain('catalogRow')

    const excel = repoSource(`${ORDER_BIZ}/support/ReplenishImportExcel.java`)
    // 分类列必须存在且在解析列里排第一：模板按分类铺开才谈得上「方便快速修改」
    expect(excel).toMatch(/CATEGORY_TITLE\s*=\s*"分类"/)
    // 参考列（库存/售价；起订量/步长已随船供包装资料下线移除）不参与解析：
    // 客户改了不算数，混进解析会污染数量与备注
    const referenceBlock = excel.slice(
      excel.indexOf('REFERENCE_TITLES'),
      excel.indexOf('TITLE_TO_FIELD'),
    )
    expect(referenceBlock).toContain('库存')
    expect(referenceBlock).toContain('售价')
    expect(referenceBlock).not.toContain('起订量')
    expect(referenceBlock).not.toContain('步长')
    // 两处列定义的长度必须一致，否则 EasyExcel 会静默错列
    expect(excel).toContain('catalogHead')
  })

  it('import report distinguishes not-filled rows from quantity errors', () => {
    // 目录模板铺满在售商品，客户只填要买的几行。没填数量的行必须是
    // 「未填数量」而不是「数量异常」，否则 273 行的模板会刷出 268 条红色报错。
    const page = source('src/sub-pages/order/shared-cart/import.vue')
    expect(page).toContain('NOT_FILLED')
    expect(page).toContain('notFilledRows')
    expect(page).toContain('未填数量')

    // 未填数量的行不进「待处置」列表：它是正常状态，不该占用处置位
    const actionable = page.slice(
      page.indexOf('const actionableRows'),
      page.indexOf('const passiveRows'),
    )
    expect(actionable).toContain('NOT_FILLED')

    const controller = repoSource(`${ORDER_BIZ}/controller/app/AppSharedCartController.java`)
    expect(controller).toContain('@GetMapping("/import/template")')
  })

  it('import page registers in pages.json and is reachable from detail', () => {
    const pages = source('src/pages.json')
    expect(pages).toContain('"path": "order/shared-cart/import"')
    expect(pages).toContain('"name": "shared-cart-import"')

    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    expect(detail).toContain('/sub-pages/order/shared-cart/import?cartId=')
    // 入口按服务端权限标记显示，前端不自行推断角色
    expect(detail).toContain('cart.viewerCanConfirm')
  })

  it('import entry wizard registers in pages.json and shortcuts into import', () => {
    const pages = source('src/pages.json')
    expect(pages).toContain('"path": "order/shared-cart/import-entry"')
    expect(pages).toContain('"name": "shared-cart-import-entry"')

    const entry = source('src/sub-pages/order/shared-cart/import-entry.vue')
    // 有进行中购物车：摘要复用后直达导入，不让用户感知"购物车"这一层
    expect(entry).toContain('getActiveSharedCartSummary')
    expect(entry).toContain('/sub-pages/order/shared-cart/import?cartId=')
    // 无进行中购物车：内联创建，同船期复用口径与列表页一致
    expect(entry).toContain('createSharedCart')
    expect(entry).toContain('adoptedExisting')
    // 有效期统一由服务端按创建时刻 +24h 决定，前端不得传 expiresAt
    expect(entry).not.toContain('expiresAt')
  })

  it('import entry is reachable from the home replenish card placeholder', () => {
    const card = source('src/components/diy/diy-replenish-card/index.vue')
    expect(card).toContain('/sub-pages/order/shared-cart/import-entry')
    expect(card).toContain('/sub-pages/order/shared-cart/list')
  })

  it('import confirm only sends actions, never row content', () => {
    const page = source('src/sub-pages/order/shared-cart/import.vue')
    // 客户端只回传「行号 → 处置动作」；行内容一律以服务端解析行为准
    expect(page).toContain('confirmSharedCartImport')
    expect(page).toContain('rowNo')
    expect(page).toContain('action')
    // 不得把服务端返回的行原文再回传（那是可被篡改的输入）
    expect(page).not.toMatch(/confirmSharedCartImport\([^)]*rawQuantity/)

    const api = source('src/api/order/sharedCart.ts')
    expect(api).toContain('SharedCartImportActionPayload')

    // 处置入参只允许三个字段：行号、动作、动作所需参数。
    // 一旦塞进 raw*（Excel 原文）就说明客户端开始回传行内容，服务端就失去了
    // "以库中解析行为准"的前提 —— 数量可被篡改（缺陷注入实测：只查页面文本会漏网）。
    const payloadStart = api.indexOf('export interface SharedCartImportActionPayload')
    const payloadBody = api.slice(payloadStart, api.indexOf('\n}', payloadStart))
    expect(payloadBody).toContain('rowNo')
    expect(payloadBody).toContain('action')
    expect(payloadBody).not.toMatch(/raw[A-Z]/)
  })

  it('import page does not fake a parse progress bar', () => {
    const page = source('src/sub-pages/order/shared-cart/import.vue')
    // 解析同步完成、没有真实进度可回传；画百分比进度条就是假进度
    expect(page).not.toContain('parseBar')
    expect(page).not.toMatch(/progress.*=\s*\d+/)
    expect(page).toContain('正在解析')
  })

  it('reuse only offered for finished carts, never the active one', () => {
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    // 进行中的单本身就是本轮清单，复用会命中同一张车并把明细全部按「已存在」跳过
    expect(detail).toContain('canReuse')
    expect(detail).toContain('cartReadonly.value')
  })

  it('reuse binds the CURRENT vessel call, not the source cart one', () => {
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    const call = detail.slice(
      detail.indexOf('reuseSharedCartFromHistory(cart.value.id'),
      detail.indexOf('function openConfirm'),
    )
    // 复用出来的是本轮采购，配送窗口必须落在本次靠港
    expect(call).toContain('vesselCallId: shipContextStore.vesselCallId')
    // 船必须与源单一致
    expect(call).toContain('vesselId: cart.value.vesselId')
  })

  it('reuse reports partial success instead of swallowing skipped items', () => {
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    // 跳过项（本次清单已有该商品 / 原明细无可用数量）必须如实告知。
    // 断到「跳过 N 项」这个完整文案：只查 skippedCount 或「已复用」的话，
    // 把提示改成永远说「已复用 N 项」也照样通过（缺陷注入实测如此）。
    expect(detail).toMatch(/已复用 \$\{result\.reusedCount\} 项，跳过 \$\{skipped\} 项/)
  })

  it('exposes every shared cart page from an existing entry point', () => {
    const cart = source('src/pages/user/shopping-cart/index.vue')
    const shipSupply = source('src/sub-pages/product/ship-supply/index.vue')
    expect(cart).toContain('/sub-pages/order/shared-cart/list')
    expect(shipSupply).toContain('/sub-pages/order/shared-cart/list')
  })
})

describe('shared cart detail page layout contract', () => {
  /**
   * 2026-09 详情页重构的守门：这些是「重排后容易改回去」的结构性决定，
   * 不是样式细节 —— 每一条都对应一次实际的可用性缺陷。
   */
  it('keeps every editing form in a bottom sheet, not inline in the page', () => {
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    // 原来表单内联在长列表下方，点「排计划」后要往下滚很久才看到表单
    for (const state of ['nameState', 'inviteState', 'planState', 'editState', 'confirmState']) {
      expect(detail).toMatch(new RegExp(`v-model="${state}\\.visible"[\\s\\S]{0,120}wd-popup|wd-popup[\\s\\S]{0,120}v-model="${state}\\.visible"`))
    }
  })

  it('binds the confirm sheet with a viewport-scoped scroll area', () => {
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    // 明细多时表单会超过屏幕，只让 body 滚，标题与提交按钮始终可见
    expect(detail).toContain('confirm-body')
    expect(detail).toContain('scroll-view scroll-y')
  })

  it('never renders raw user ids or raw SKU ids as the primary row label', () => {
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    // 「SKU 9550000000000094」「来自 2103140502970413057」对用户没有意义
    expect(detail).toContain('memberLabel')
    expect(detail).toContain('contributorLabel')
    // 两个兜底函数各自都要截短，不能一个截一个不截
    // （只查全文件里出现过 slice(-4) 会被另一个函数蒙混过关 —— 缺陷注入实测如此）
    for (const fn of ['memberLabel', 'contributorLabel']) {
      const start = detail.indexOf(`function ${fn}(`)
      const body = detail.slice(start, detail.indexOf('\n}', start))
      expect(body).toContain('userId.slice(-4)')
    }
  })

  it('does not draw an empty progress track when nothing is planned', () => {
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    // 无计划时画空槽 + 「—」会被读成「加载失败」
    expect(detail).toContain('hasPlan')
    // 进度条本身必须挂在 hasPlan 上：hasPlan 与 progressPercent 之间不得再有别的分支
    const bar = detail.slice(detail.indexOf('v-if="hasPlan"'))
    expect(bar.slice(0, 400)).toContain('progressPercent')
  })

  it('gives each planned row its own progress bar', () => {
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    // 行内进度条回答「这一项还差几件」，是确认人扫清单时最先要看的
    expect(detail).toMatch(/rowViews\[item\.id\]\?\.percent/)
  })

  it('pins the bottom action bar and reserves space for it', () => {
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    expect(detail).toContain('action-bar')
    // 不留占位会把最后一张卡片压在固定条下面
    expect(detail).toContain('footer-spacer')
    expect(detail).toMatch(/bottom:\s*var\(--window-bottom/)
  })

  it('keeps close-cart away from the primary submit action', () => {
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    // 关闭不可逆，不再与「提交整船订单」并排等权，避免误触
    const bar = detail.slice(
      detail.indexOf('class="action-bar"'),
      detail.indexOf('<!-\u002D 明细行操作面板'),
    )
    expect(bar).toContain('primaryAction')
    expect(bar).not.toContain('handleClose')
  })

  it('renders the primary CTA with the brand gradient, not uni default blue', () => {
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    // 原生 button type=primary 走 uni 默认蓝，与全站品牌色不一致。
    // 断言按钮的**起始标签**是 view：类名在标签内部，只看类名之后的内容
    // 会把 `<button type="primary" class="action-bar__primary">` 当成合规
    // （缺陷注入实测如此），必须回看到最近的 '<'。
    expect(detail).toMatch(
      /linear-gradient\(135deg,\s*var\(--wot-color-theme-secondary,\s*#ff8a00\),\s*var\(--wot-color-theme-primary,\s*#ff4d2e\)\)/,
    )
    const barStart = detail.indexOf('class="action-bar"')
    const classAt = detail.indexOf('class="action-bar__primary"', barStart)
    expect(classAt).toBeGreaterThan(barStart)
    const openTag = detail.slice(detail.lastIndexOf('<', classAt), classAt)
    expect(openTag).toMatch(/^<view\b/)
    expect(openTag).not.toContain('<button')
  })

  it('shows the collect deadline as a live countdown plus the absolute time', () => {
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    // 倒计时回答「还有多久」，绝对时刻用于核对「几点截止」
    expect(detail).toContain('formatExpiryCountdown')
    expect(detail).toContain('expiryCountdown')
    expect(detail).toContain('expiryPoint')
    // 需要自己走时，否则倒计时停在打开页面那一刻
    expect(detail).toContain('useCountdownTicker')
  })

  it('drives row actions from one sheet instead of three inline buttons per row', () => {
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    // 12 行 × 3 个彩色文字按钮会把清单刷成噪声
    expect(detail).toContain('openRowActions')
    // 面板从自身容器的开头切起，才能覆盖到「排计划」那一项
    const sheetStart = detail.indexOf('v-model="rowActionState.visible"')
    const rowSheet = detail.slice(sheetStart, detail.indexOf('我的姓名（配送贴标签用）'))
    expect(rowSheet).toContain('canEditItem(rowActionState.item)')
    expect(rowSheet).toContain('canPlan')
    expect(rowSheet).toContain('handleRemoveItem(rowActionState.item)')
  })
})

describe('shared cart API contract', () => {
  it('declares the C-end base path and the my-carts endpoint', () => {
    const api = source('src/api/order/sharedCart.ts')
    expect(api).toContain(`const BASE = '${API_BASE}'`)
    expect(api).toContain('`${BASE}/my`')
  })

  it('matches the backend controller base path and my-carts mapping', () => {
    const controller = repoSource(
      'aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/controller/app/AppSharedCartController.java',
    )
    expect(controller).toContain('@RequestMapping("/app/shared-cart")')
    expect(controller).toContain('@GetMapping("/my")')
    // 详情必须返回带 viewer* 权限标记的 VO，前端不自行推断权限
    expect(controller).toContain('Result<SharedCartVO>')
  })

  it('declares the same mutating endpoints as the backend controller', () => {
    const api = source('src/api/order/sharedCart.ts')
    const controller = repoSource(
      'aryn-mall-java/aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/controller/app/AppSharedCartController.java',
    )
    for (const mapping of [
      '@PostMapping("/{id}/members")',
      '@PostMapping("/{id}/items")',
      '@PutMapping("/{id}/items/{itemId}")',
      '@DeleteMapping("/{id}/items/{itemId}")',
      '@PostMapping("/{id}/confirm")',
      '@PostMapping("/{id}/close")',
    ]) {
      expect(controller).toContain(mapping)
    }
    expect(api).toContain('`${BASE}/${id}/members`')
    expect(api).toContain('`${BASE}/${id}/items`')
    expect(api).toContain('`${BASE}/${id}/items/${itemId}`')
    expect(api).toContain('`${BASE}/${id}/confirm`')
    expect(api).toContain('`${BASE}/${id}/close`')
  })

  it('不再由前端计算有效期，收集截止统一由服务端按创建时刻 +24h 决定', () => {
    // 客户端时钟不可信，且历史上「不限期」会导致购物车永不过期；
    // 有效期改由 SharedCartServiceImpl.create 写入 now + 24h。
    const list = source('src/sub-pages/order/shared-cart/list.vue')
    expect(list).not.toContain('payload.expiresAt')
    expect(list).not.toContain('windowHours')
    expect(list).toContain('24 小时')
  })

  it('复用已有购物车时提示用户，而非谎称新建', () => {
    // 同船已有进行中的采购时服务端返回 adoptedExisting=true，
    // 前端必须区分「新建」与「并入已有」，否则用户会误以为重复创建成功。
    const list = source('src/sub-pages/order/shared-cart/list.vue')
    expect(list).toContain('adoptedExisting')
  })
})

/**
 * 2026-09 列表页重设计的守门。
 *
 * 每一条都对应一次实际的可用性缺陷，而不是样式偏好：
 * 改回去会让页面重新变成「四张一模一样的卡片 + 一个不知道能不能点的按钮」。
 */
describe('shared cart list page layout contract', () => {
  it('splits the list into in-progress and history instead of one flat run', () => {
    const list = source('src/sub-pages/order/shared-cart/list.vue')
    // 分区逻辑集中在纯函数里（可单测），页面只负责渲染
    expect(list).toContain('groupSharedCarts')
    expect(list).toContain('section')
    // 不能再退回「一个 v-for 平铺所有记录」
    expect(list).not.toMatch(/v-for="cart in records"/)
  })

  it('never draws an empty progress track when nothing is planned', () => {
    // 没排计划时画空槽 + 「—」会被读成「加载失败」；进度条本身必须挂在 hasPlan 上。
    // 断言回看到最近的 '<'：只看类名之后的内容会把「轨道无条件渲染、
    // 父级才有 v-if」当成合规（与详情页同一条守则）。
    const list = source('src/sub-pages/order/shared-cart/list.vue')
    const trackAt = list.indexOf('class="card__track"')
    expect(trackAt).toBeGreaterThan(-1)
    const openTag = list.slice(list.lastIndexOf('<', trackAt), trackAt)
    expect(openTag).toContain('v-if')
    expect(openTag).toContain('progress.hasPlan')
    // 百分比只信服务端算好的值，前端不再除一遍
    expect(list).toContain('progress.percent')
  })

  it('takes its progress wording from the shared calculator, not a local formula', () => {
    // 首页卡片、详情页、列表三处口径必须同源，否则会出现
    // 「首页说还差 4 项、列表说还差 5 项」这种自相矛盾
    const list = source('src/sub-pages/order/shared-cart/list.vue')
    expect(list).toContain('buildReplenishSummaryView')
    const util = source('src/utils/replenish-progress.ts')
    expect(util).toContain('buildReplenishProgressText')
  })

  it('labels the card action with a verb instead of a generic "view"', () => {
    const list = source('src/sub-pages/order/shared-cart/list.vue')
    expect(list).toContain('cartActionLabel')
    const util = source('src/utils/shared-cart.ts')
    // 每个分支都要给出可执行的动词，让用户不点进去就知道会发生什么
    for (const verb of ['查看订单', '排计划', '去加货']) {
      expect(util).toContain(verb)
    }
  })

  it('shows the collect deadline as a countdown plus the absolute time', () => {
    const list = source('src/sub-pages/order/shared-cart/list.vue')
    expect(list).toContain('cartDeadlineView')
    expect(list).toContain('expiresPoint')
    // 倒计时要走时，否则停在打开页面那一刻
    expect(list).toContain('useCountdownTicker')
  })

  it('keeps the create form in a bottom sheet, not inline above the list', () => {
    // 表单曾内联在页面顶部，收起/展开会让列表整体跳动；收集设置是低频决策，
    // 用弹层承载后列表首屏只剩「上下文 + 单据」
    const list = source('src/sub-pages/order/shared-cart/list.vue')
    expect(list).toContain('createState.visible')
    expect(list).toMatch(/v-model="createState\.visible"[\s\S]{0,120}wd-popup|wd-popup[\s\S]{0,120}v-model="createState\.visible"/)
  })

  it('enables pull-down refresh, which the handler alone never did', () => {
    // onPullDownRefresh 早就写了，但 definePage 没开 enablePullDownRefresh，
    // 手势根本不会被触发 —— 回调成了死代码。
    const list = source('src/sub-pages/order/shared-cart/list.vue')
    expect(list).toContain('enablePullDownRefresh: true')
    expect(list).toContain('onPullDownRefresh')
    expect(list).toContain('uni.stopPullDownRefresh()')
  })

  it('uses the brand CTA gradient rather than the uni default blue', () => {
    const list = source('src/sub-pages/order/shared-cart/list.vue')
    // 与详情页「提交整船订单」、购物车「去结算」同一套橙红渐变
    // （2026-10-02 起接主题变量：辅色→主色，fallback 维持原渐变色值）
    expect(list).toMatch(
      /linear-gradient\(135deg,\s*var\(--wot-color-theme-secondary,\s*#ff8a00\),\s*var\(--wot-color-theme-primary,\s*#ff4d2e\)\)/,
    )
    // 原生 button type=primary 走 uni 默认蓝，创建按钮不得用它
    expect(list).not.toMatch(/<button[^>]*type="primary"/)
  })

  it('distinguishes loading, failure, logged-out and empty states', () => {
    const list = source('src/sub-pages/order/shared-cart/list.vue')
    // 四态共用一句「暂无数据」会让用户不知道是该等待、该登录还是该去创建
    for (const text of ['加载中...', '加载失败', '登录后可查看共享购物车', '还没有共享购物车']) {
      expect(list).toContain(text)
    }
    expect(list).toContain('showEmpty')
  })

  it('keeps the create flow opening the ship picker instead of dead-ending', () => {
    const list = source('src/sub-pages/order/shared-cart/list.vue')
    expect(list).toContain('shipPickerVisible.value = true')
    expect(list).toContain('ShipContextPicker')
  })

  it('counts submitted rows too, so finished carts never read "0 项商品"', () => {
    // 真实缺陷：提交时明细行被置为 ITEM_CONFIRMED，而列表统计写的是
    // eq(status, ITEM_PENDING)，于是已提交的单全部显示「0 项商品 · 1 名成员」。
    // 统计口径必须是「未移除」而不是「待确认」。
    const impl = repoSource(`${ORDER_BIZ}/service/impl/SharedCartServiceImpl.java`)
    const buildStart = impl.indexOf('private List<SharedCartVO> buildVOs')
    const build = impl.slice(buildStart, impl.indexOf('listMembers', buildStart))
    expect(build).toContain('ITEM_REMOVED')
    expect(build).not.toMatch(/eq\(SharedCartItem::getStatus,\s*SharedCartItem\.ITEM_PENDING\)/)
  })

  it('carries the replenish progress summary on the list VO', () => {
    // 列表卡片要回答「这单还差多少」，与首页卡片同一口径；
    // 摘要由服务端用 ReplenishProgressCalculator 算好，前端不做二次除法。
    const vo = repoSource(`${ORDER_API}/api/vo/SharedCartVO.java`)
    expect(vo).toContain('ReplenishProgressVO.Summary progress')
    const impl = repoSource(`${ORDER_BIZ}/service/impl/SharedCartServiceImpl.java`)
    expect(impl).toContain('ReplenishProgressCalculator.summarize')
    // 前端类型同步声明，否则拿不到字段
    const api = source('src/api/order/sharedCart.ts')
    expect(api).toMatch(/progress\?: ReplenishSummaryProgress \| null/)
  })
})

describe('shared cart permission contract', () => {
  it('drives detail page actions from backend viewer flags', () => {
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    expect(detail).toContain('viewerCanEdit')
    expect(detail).toContain('viewerCanConfirm')
    expect(detail).toContain('viewerIsOwner')
  })

  it('never uses a shadowed vue api name for page state', () => {
    // `readonly` 与 vue 的 readonly() 冲突，模板中会解析成函数导致条件恒真
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    expect(detail).not.toMatch(/const readonly\s*=/)
  })

  it('keeps delivery way labels single-sourced', () => {
    const shipSupply = source('src/sub-pages/product/ship-supply/index.vue')
    const detail = source('src/sub-pages/order/shared-cart/detail.vue')
    for (const file of [shipSupply, detail]) {
      expect(file).not.toMatch(/deliveryWay === '1'[\s\S]{0,80}\?[\s\S]{0,40}普通快递/)
    }
  })
})
