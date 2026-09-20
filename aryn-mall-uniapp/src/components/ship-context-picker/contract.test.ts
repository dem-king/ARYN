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

  it('is reachable from every screen that asks the user to choose or switch', () => {
    for (const page of [
      'src/components/ship-workbench/index.vue',
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
    expect(cart).toContain('结算前请点此切换船舶')
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
