import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 会员介绍弹层 + 「会员专享」文案契约守门。
 *
 * 这次改动有两处很容易回退：
 * 1) 「了解会员」被改回整页跳会员中心（会员介绍弹层从此不再可达）；
 * 2) 引导文案又被拼上等级名（「新客会员专享」重现）。
 * 类型检查发现不了这类「交互/文案被悄悄改回去」，只能按源码结构钉住：
 * 弹层由权益条唤起、弹层自身不拼路由、CTA 才进会员中心、引导文案只走统一前缀。
 */
const projectRoot = fileURLToPath(new URL('../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const SHEET_COMPONENT = 'src/components/member-intro-sheet/index.vue'
const SHEET_CONTENT = 'src/components/member-intro-sheet/member-intro.ts'
const TABS_COMPONENT = 'src/components/diy/diy-goods-tabs/index.vue'
const MEMBER_PRICE = 'src/utils/member-price.ts'

describe('会员介绍弹层契约', () => {
  it('用底部 wd-popup 呈现，并压住 H5 tabBar 层级', () => {
    const component = source(SHEET_COMPONENT)

    expect(component).toContain('<wd-popup')
    expect(component).toContain('position="bottom"')
    // H5 原生 tabBar 是 998，wd-popup 默认 10 会被盖住
    expect(component).toContain(':z-index="1100"')
    expect(component).toContain(':safe-area-inset-bottom="true"')
    expect(component).toContain('border-radius: 28rpx 28rpx 0 0')
    // 遮罩点击/下滑关闭要同步外部 v-model，否则宿主状态与弹层显示会脱节
    expect(component).toContain('@update:model-value="onPopupUpdate"')
  })

  it('渲染权益宫格、成长路径、会员专享价说明与两个动作', () => {
    const component = source(SHEET_COMPONENT)

    expect(component).toContain('v-for="benefit in benefits"')
    expect(component).toContain('v-for="(step, index) in steps"')
    expect(component).toContain('MEMBER_INTRO_PRICE_NOTE')
    expect(component).toContain('{{ view.statusText }}')
    expect(component).toContain('{{ view.ctaText || t(MEMBER_INTRO_CTA_TEXT_KEY) }}')
    expect(component).toContain('MEMBER_INTRO_DISMISS_TEXT')
    // 关闭入口：右上角 X + 底部「先逛逛」都能关
    expect(component).toContain('@click="close"')
  })

  it('弹层自身不拼路由：只发事件，由宿主决定去哪', () => {
    const component = source(SHEET_COMPONENT)

    expect(component).toContain('emit(\'member-center\')')
    expect(component).not.toContain('uni.navigateTo')
    expect(component).not.toContain('/sub-pages/user/member')
    expect(component).not.toContain('/pages/login')
  })

  it('展示口径收敛在纯函数里，模板不写死等级名/门槛/折数', () => {
    const component = source(SHEET_COMPONENT)
    const content = source(SHEET_CONTENT)

    expect(component).toContain('resolveMemberIntroView(')
    expect(component).toContain('MEMBER_INTRO_BENEFITS')
    expect(component).toContain('MEMBER_INTRO_STEPS')
    expect(content).toContain('export function resolveMemberIntroView')
    expect(content).toContain('export const MEMBER_INTRO_BENEFITS')
    expect(content).toContain('export const MEMBER_INTRO_STEPS')
    // 模板里不允许出现具体等级名（等级名是租户配置，会员态由 profile 透传）
    for (const levelName of ['新客会员', '银卡会员', '金卡会员', '铂金会员', '钻石会员']) {
      expect(component).not.toContain(levelName)
    }
  })
})

describe('「了解会员」交互契约', () => {
  it('登录未入会点权益条：先开弹层，而不是直接整页跳会员中心', () => {
    const tabs = source(TABS_COMPONENT)
    const handler = tabs.slice(
      tabs.indexOf('function onMemberBannerClick()'),
      tabs.indexOf('/** 弹层主按钮'),
    )

    expect(handler).toContain('memberIntroVisible.value = true')
    // member-center 动作不得在点击处理器里直接 navigateTo（游客态去登录仍保留）
    expect(handler).not.toContain('uni.navigateTo({ url: MEMBER_BANNER_MEMBER_CENTER_URL })')
    expect(handler).toContain('uni.navigateTo({ url: MEMBER_BANNER_LOGIN_URL })')
  })

  it('弹层挂在商品流组件内，并透传会员态与 CTA 处理', () => {
    const tabs = source(TABS_COMPONENT)

    expect(tabs).toContain('import MemberIntroSheet from \'@/components/member-intro-sheet/index.vue\'')
    expect(tabs).toContain('<member-intro-sheet')
    expect(tabs).toContain('v-model="memberIntroVisible"')
    expect(tabs).toContain(':is-member="memberBannerView?.state === \'member\'"')
    expect(tabs).toContain(':level-name="memberProfile?.levelName || \'\'"')
    expect(tabs).toContain('@member-center="onMemberIntroCenter"')
    // CTA 才真正跳会员中心
    expect(tabs).toContain('uni.navigateTo({ url: MEMBER_BANNER_MEMBER_CENTER_URL })')
  })

  it('弹层不额外发会员接口，复用权益条已查到的 profile', () => {
    const component = source(SHEET_COMPONENT)

    expect(component).not.toContain('getMemberCurrentInfo')
    expect(component).not.toContain('getMemberBenefits')
    expect(component).not.toContain('alovaInstance')
    expect(component).not.toContain('/app/member/')
  })
})

describe('「会员专享」引导文案契约', () => {
  it('统一走 MEMBER_GUIDE_PREFIX，函数不再接收/拼接等级名', () => {
    const util = source(MEMBER_PRICE)
    const fn = util.slice(
      util.indexOf('export function memberPriceGuideText('),
      util.indexOf('/** 资格三态归一化'),
    )

    expect(util).toContain('export const MEMBER_GUIDE_PREFIX = \'会员专享\'')
    expect(fn).toContain('MEMBER_GUIDE_PREFIX')
    expect(fn).not.toContain('minLevelName')
    expect(fn).not.toContain('专享')
  })

  it('列表卡片、商详、购物车都消费同一份 guideText，不各自拼文案', () => {
    const util = source(MEMBER_PRICE)
    // 商详/购物车（SKU 维度）与会员专区列表（SPU 维度）两处调用同一纯函数
    expect(util.match(/guideText: memberPriceGuideText\(hit\.memberPrice\)/g)).toHaveLength(2)

    // 列表卡片：直接渲染聚合结果里的 guideText（与商详/购物车同源）
    const tabs = source(TABS_COMPONENT)
    expect(tabs).toContain('item?.memberPriceDisplay?.guideText')
    expect(tabs).not.toContain('新客会员专享')
  })

  it('源码里不再出现「新客会员专享」这类拼等级名的旧文案', () => {
    for (const file of [SHEET_COMPONENT, SHEET_CONTENT, TABS_COMPONENT, MEMBER_PRICE]) {
      const text = source(file)
      expect(text).not.toContain('新客会员专享')
      expect(text).not.toContain('}专享 ¥')
    }
  })
})
