import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * 多 Tab 商品流「会员权益条」契约守门（需求 F2 §7.3-7.4）。
 *
 * 横幅很容易在三种状态下退化：只做了会员态忘了游客态、文案占位符没替换、
 * 接口失败把整条商品流拖住、切 Tab 每次重复请求会员接口。
 * 类型检查发现不了这类「渲染逻辑漏一支」，只能按源码结构把契约钉住：
 * Tab 头下方渲染、三态数据源齐备、失败静默、请求只发一次、只消费 C 端会员接口。
 */
const projectRoot = fileURLToPath(new URL('../../../../', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

const TABS_COMPONENT = 'src/components/diy/diy-goods-tabs/index.vue'
const MEMBER_BANNER = 'src/components/diy/member-banner.ts'
const MEMBER_API = 'src/api/user/member.ts'

describe('goods-tabs member banner contract', () => {
  it('keeps the Tab-level memberBanner config when normalizing published JSON', () => {
    const component = source(TABS_COMPONENT)
    const normalize = component.slice(
      component.indexOf('function normalizeMemberBanner('),
      component.indexOf('function normalizeTabs('),
    )
    // 装修 JSON 的 Tab 级配置形状：{ enabled, memberCopy?, guestCopy? }
    expect(normalize).toContain('enabled')
    expect(normalize).toContain('memberCopy')
    expect(normalize).toContain('guestCopy')
    // enabled 不为 true 等价关闭（渲染侧只需判空）
    expect(normalize).toContain('record.enabled !== true')

    // normalizeTabs 必须把 memberBanner 透传下去，否则配置永远到不了渲染层
    const tabs = component.slice(
      component.indexOf('function normalizeTabs('),
      component.indexOf('const showData = computed'),
    )
    expect(tabs).toContain('memberBanner: normalizeMemberBanner(record.memberBanner)')
  })

  it('renders the banner below the tab bar and above the goods feed', () => {
    const component = source(TABS_COMPONENT)
    const barAt = component.indexOf('class="goods-tabs__bar"')
    const bannerAt = component.indexOf('class="goods-tabs__member-banner"')
    const feedAt = component.indexOf('class="goods-tabs__inner"')

    expect(barAt).toBeGreaterThan(-1)
    expect(bannerAt).toBeGreaterThan(barAt)
    expect(feedAt).toBeGreaterThan(bannerAt)
    // 配置关闭 / 未查到会员信息时不渲染（v-if 由 memberBannerView 判空）
    expect(component).toContain('v-if="memberBannerView"')
    expect(component).toContain('{{ memberBannerView.text }}')
  })

  it('covers all three user states through the shared pure resolver', () => {
    const component = source(TABS_COMPONENT)
    // 三态（会员 / 登录无等级 / 未登录）判定收敛在 member-banner.ts，组件不各写一份
    expect(component).toContain('resolveMemberBannerView(config, profile)')
    expect(component).toContain("memberBannerView.state === 'guest'")

    const banner = source(MEMBER_BANNER)
    // 会员态：确定性文案（等级名 + 「会员专享价」），两态合并为一句、不含折数；
    // 文案落在 zh 词条、渲染侧 translate 调用时求值（切语言实时刷新）
    expect(banner).toContain("translate('diy.memberBanner.memberCopy'")
    expect(banner).not.toContain('MEMBER_BANNER_MEMBER_COPY_NO_DISCOUNT')
    expect(banner).toContain('formatMemberDiscountZhe')
    // {discount} 占位只为运营自定义文案保留（会员折扣率权益，与会员价两套体系）
    expect(banner).toContain('{discount}')
    // 游客态：统一内置文案，登录但无等级跳会员中心、未登录跳登录
    expect(banner).toContain("translate('diy.memberBanner.guestCopy'")
    expect(banner).toContain("action: profile.isLoggedIn ? 'member-center' : 'login'")
    // 会员态内置文案必须是确定性标签：中文原文逐字保留、不含折数、不引用 {discount}
    const zhLine = source('src/locales/modules/diy.zh-CN.ts')
      .split('\n')
      .find(line => line.includes('memberCopy:'))
    expect(zhLine).toBeDefined()
    expect(zhLine).toContain('本区享会员专享价')
    expect(zhLine).toContain('{levelName}')
    expect(zhLine).not.toContain('折')
    expect(zhLine).not.toContain('{discount}')
    expect(banner).toContain('MEMBER_BANNER_LOGIN_URL')
    expect(banner).toContain('MEMBER_BANNER_MEMBER_CENTER_URL')
    expect(banner).toContain('/pages/login/index')
    expect(banner).toContain('/sub-pages/user/member/index')
  })

  it('only requests the C-side member APIs, in parallel and logged-in only', () => {
    const component = source(TABS_COMPONENT)
    const loader = component.slice(
      component.indexOf('async function loadMemberProfile('),
      component.indexOf('/** 当前 Tab 的会员权益条展示结果'),
    )
    // 登录才请求：未登录直接游客态，不为横幅发无谓请求
    expect(loader).toContain('if (!authStore.isLoggedIn)')
    // 两请求并行（等级 + 权益），串行会让横幅晚一个往返才出现
    expect(loader).toContain('Promise.all([')
    expect(loader).toContain('getMemberCurrentInfo()')
    expect(loader).toContain('getMemberBenefits()')
    // 只消费 /app/member/* C 端接口：从 api/user/member 封装引入，不在组件里拼 URL
    expect(component).toContain('from \'@/api/user/member\'')
    expect(component).not.toContain('/mall-user/app/member')
    expect(component).not.toContain('/admin')
  })

  it('fails silently and never blocks the goods feed', () => {
    const component = source(TABS_COMPONENT)
    const loader = component.slice(
      component.indexOf('async function loadMemberProfile('),
      component.indexOf('/** 当前 Tab 的会员权益条展示结果'),
    )
    // 接口失败 → 不渲染横幅（null），绝不冒泡到商品流
    expect(loader).toContain('catch')
    expect(loader).toContain('memberProfile.value = null')
    // 异步触发不 await：商品流首屏/翻页不等待会员接口
    expect(component).toContain('void loadMemberProfile()')
    expect(component).not.toContain('await loadMemberProfile()')
  })

  it('requests at most once per login state instead of on every tab switch', () => {
    const component = source(TABS_COMPONENT)
    // 缓存哨兵：已查过（成功或失败）直接返回
    expect(component).toContain('memberProfileRequested')
    const loader = component.slice(
      component.indexOf('async function loadMemberProfile('),
      component.indexOf('/** 当前 Tab 的会员权益条展示结果'),
    )
    expect(loader.indexOf('if (memberProfileRequested)')).toBeLessThan(loader.indexOf('memberProfileRequested = true'))
    // 只有当前 Tab 开启时才查询；切 Tab 走同一缓存
    const ensure = component.slice(
      component.indexOf('function ensureMemberProfile('),
      component.indexOf('watch(() => activeTab'),
    )
    expect(ensure).toContain('memberBanner?.enabled')
    expect(ensure).toContain('void loadMemberProfile()')
    expect(component).toContain('watch(() => activeTab.value?.id, ensureMemberProfile')
    // 登录/退出后等级会变：重置缓存按新身份重查
    expect(component).toContain('watch(() => authStore.isLoggedIn')
    expect(component).toContain('memberProfileRequested = false')
  })
})

describe('member benefits API wrapper', () => {
  it('adds a typed C-side benefits wrapper next to current-info', () => {
    const api = source(MEMBER_API)
    expect(api).toContain('export function getMemberBenefits')
    expect(api).toContain('/mall-user/app/member/benefits')
    // 与 current-info 同域前缀：双模式改写由请求封装统一处理，页面/组件不拼 /boot
    expect(api).toContain('/mall-user/app/member/current-info')
    expect(api).not.toContain('/boot')
    expect(api).toContain('discountRate')
  })
})
