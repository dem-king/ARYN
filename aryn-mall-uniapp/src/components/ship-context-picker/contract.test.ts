import { readdirSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 船舶/靠港选择链路的契约守门。
 *
 * 背景（调研报告 P1-1）：`switchVessel` 与 `getVesselCalls` 曾经是**死代码**——
 * 只在 store 定义与单测中出现，没有任何页面调用。但界面却反复要求用户
 * 「结算前请切换船舶」「请先在首页选择船舶和靠港计划」，引导用户做一件做不到的事。
 *
 * 这组断言防止该链路再次断开：只要选择器被摘掉或入口被改回纯文案，测试即失败。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

/**
 * 只取 `<template>` 段，并剥掉其中的 HTML 注释。
 *
 * 组件里为解释「为什么必须 portal / 为什么不用手输」会把这些关键字写进注释，
 * 全文匹配会把注释也算成实现，断言就失去意义。
 */
function templateOf(vueSource: string) {
  const template = vueSource.match(/<template>[\s\S]*<\/template>/)?.[0] ?? ''
  return template.replace(/<!--[\s\S]*?-->/g, '')
}

/** 收集 src 下所有 .vue 源码，用于断言「某 API 至少被一个页面调用」 */
function allVueSources() {
  const srcDir = resolve(projectRoot, 'src')
  const files = readdirSync(srcDir, { recursive: true }) as string[]
  return files
    .filter(file => file.endsWith('.vue'))
    .map(file => readFileSync(resolve(srcDir, file), 'utf8'))
}

describe('ship context picker contract', () => {
  it('wires the previously-dead vessel APIs into a real caller', () => {
    const picker = source('src/components/ship-context-picker/index.vue')
    expect(picker).toContain('getVesselCalls')
    expect(picker).toContain('switchVessel')
    expect(picker).toContain('setVesselCall')
    expect(picker).toContain('getMyVessels')
  })

  it('clears the old port call when switching vessel to prevent cross-vessel mixing', () => {
    const picker = source('src/components/ship-context-picker/index.vue')
    // 换船必须先 switchVessel（内部会清空靠港上下文），再 setVesselCall
    const switchIndex = picker.indexOf('shipContextStore.switchVessel')
    const callIndex = picker.indexOf('shipContextStore.setVesselCall')
    expect(switchIndex).toBeGreaterThan(-1)
    expect(callIndex).toBeGreaterThan(switchIndex)
  })

  /**
   * 申报表单的 ETA/ETD 曾是两个自由文本 input，placeholder 直接写着
   * `yyyy-MM-dd HH:mm:ss`：手机上要逐字符敲 19 位，且格式错一位就被后端
   * LocalDateTime 反序列化判 400。改为日历式选择器后，下面几条防止
   * 它们再退回去，也守住「离港不能早于到港」不再只靠提交时报错兜底。
   */
  it('replaces the free-text ETA/ETD inputs with calendar date-time pickers', () => {
    const picker = source('src/components/ship-context-picker/index.vue')
    const template = templateOf(picker)

    // 两个时间各一个日历选择器
    expect(template.match(/<wd-calendar/g)?.length).toBe(2)
    // 日期时间精度（type=datetime），不是只选日期
    expect(template.match(/type="datetime"/g)?.length).toBe(2)
    // 日历选完日期后预填的时刻必须显式给定：不传时库内默认 00:00，
    // 申报「今天」靠港的默认值会落在过去
    expect(template.match(/:default-time="declareDefaultTime"/g)?.length).toBe(2)
    // 旧的手输口径必须彻底消失：占位文案与绑定到时间字段的 input
    expect(template).not.toContain('yyyy-MM-dd HH:mm:ss')
    expect(template).not.toMatch(/<input[\s\S]{0,200}declareState\.(eta|etd)/)
    // 提交值必须经格式化函数转成后端口径，不能把时间戳直接发出去
    expect(picker).toContain('formatDeclareTime(form.eta)')
    expect(picker).toContain('formatDeclareTime(form.etd)')
  })

  /**
   * 申报提交必须以弹层内选中的船为准：store.vesselId 只在确认靠港
   * （chooseCall → switchVessel）后写入，而「无靠港 → 申报」恰恰是
   * 弹层已自动选中船、store 仍为空的场景。曾因此把整条申报出路
   * 用「请先选择船舶」堵死；多船用户在弹层内换船申报时还会串到 store 里的旧船。
   */
  it('validates the declaration against the picked vessel, not the store', () => {
    const picker = source('src/components/ship-context-picker/index.vue')
    const fn = picker.match(/function submitDeclare\(\) \{[\s\S]*?\n\}/)?.[0] ?? ''
    expect(fn).toContain('const vesselId = pickedVesselId.value')
    expect(fn).not.toContain('shipContextStore.vesselId')
  })

  it('constrains the picker panel above the sheet and out of the scroll-view', () => {
    const picker = source('src/components/ship-context-picker/index.vue')
    const template = templateOf(picker)

    // 申报表单嵌在 scroll-view 内，小程序端 fixed 会被裁掉，必须 portal 出去
    expect(template.match(/root-portal/g)?.length).toBe(2)
    // 弹层本体是 900/901，H5 原生 tabBar 是 998，选择器面板必须压过两者
    expect(template.match(/:z-index="1000"/g)?.length).toBe(2)
  })

  it('keeps ETD bounded by the chosen ETA as early as selection time', () => {
    const picker = source('src/components/ship-context-picker/index.vue')

    // 离港下限跟随到港，避免用户选出一个提交时才被拒的组合；
    // 且必须比 ETA 晚一分钟——后端要求严格早于，取 ETA 本身仍能选出相等时刻
    expect(picker).toContain('const etdMinDate')
    expect(picker).toMatch(/etdMinDate = computed\(\(\) =>\s*typeof declareState\.value\.eta === 'number' \? declareState\.value\.eta \+ 60_000/)
    expect(picker).toMatch(/:min-date="etdMinDate"/)
    // 先选离港再改到港时，失效的离港值要当场清掉而不是留到提交才报错
    expect(picker).toMatch(/watch\(\(\) => declareState\.value\.eta[\s\S]{0,400}declareState\.value\.etd = null/)
  })

  it('is reachable from every screen that asks the user to choose or switch', () => {
    for (const page of [
      'src/components/diy/diy-ship-workbench/index.vue',
      'src/pages/user/shopping-cart/index.vue',
      'src/sub-pages/product/ship-supply/index.vue',
      'src/sub-pages/order/shared-cart/list.vue',
    ]) {
      expect(source(page)).toContain('ShipContextPicker')
    }
  })

  it('keeps switchVessel reachable from at least one page component', () => {
    const callers = allVueSources().filter(vue => vue.includes('switchVessel'))
    expect(callers.length).toBeGreaterThan(0)
  })

  it('keeps getVesselCalls reachable from at least one page component', () => {
    const callers = allVueSources().filter(vue => vue.includes('getVesselCalls'))
    expect(callers.length).toBeGreaterThan(0)
  })
})

describe('no dead-end guidance remains', () => {
  it('makes the cross-port-call cart hint actionable', () => {
    const cart = source('src/pages/user/shopping-cart/index.vue')
    // 文案在 2026-10-04 的 UI 重排中去掉了括号（分组头已改细窄说明行，读起来更顺），
    // 但「提示必须是可点的入口」这条不变：它必须与 openShipPicker 同屏出现
    expect(cart).toContain('结算前请切换船舶')
    expect(cart).toContain('openShipPicker')
    // 旧的纯文案提示必须已被替换
    expect(cart).not.toContain('（结算前请切换船舶）')
  })

  it('makes the missing-context hint on ship supply actionable', () => {
    const shipSupply = source('src/sub-pages/product/ship-supply/index.vue')
    expect(shipSupply).toContain('openShipPicker')
    expect(shipSupply).toContain('选择船舶和靠港计划')
    // 旧文案把用户指向「首页」，而首页当时并没有入口
    expect(shipSupply).not.toContain('请在首页选择船舶和靠港计划')
  })

  it('lets the shared cart create flow open the picker instead of dead-ending', () => {
    const list = source('src/sub-pages/order/shared-cart/list.vue')
    expect(list).toContain('shipPickerVisible.value = true')
    expect(list).not.toContain('需先关联船舶并选择靠港计划')
  })

  it('does not point users back to a page without an entry point', () => {
    const confirm = source('src/sub-pages/order/order-confirm/index.vue')
    expect(confirm).not.toContain('请先在首页选择船舶和靠港计划')
  })
})
