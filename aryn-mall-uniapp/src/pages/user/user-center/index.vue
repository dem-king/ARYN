<script setup lang="ts">
import type { DeliveryEligibility } from '@/api/delivery'
import type { EnterpriseBenefitsVO } from '@/api/vessel'
import type { OrderId, ServiceId, UserCenterIcon, WorkbenchId } from '@/components/diy/schema/user-center'
import { onPageScroll, onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ensureSessionTenant, isTenantIdentityError } from '@/api/core/tenant-identity'
import {
  exchangeDeliveryIdentity,
  getDeliveryEligibility,
  getMyDeliveryStaff,
} from '@/api/delivery'
import { getCount as getOrderCount } from '@/api/order/orderInfo'
import { getSalesWorkbench } from '@/api/sales/entity'
import { getVesselEnterpriseBenefits } from '@/api/vessel'
// 引入组件
import DiyPage from '@/components/diy/index.vue'
import { migratePageContent } from '@/components/diy/schema/migrate'
import { normalizeUserCenter, userCenterHeroStyle, userCenterOrderIds } from '@/components/diy/schema/user-center'
import WaterfallGoods from '@/components/waterfall-goods/index.vue'
// 页面滚动广播：装修商品楼层与底部「猜你喜欢」都靠它自动续页
import { useDecorationInfiniteScroll } from '@/composables/useDecorationInfiniteScroll'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { useLocaleSwitch } from '@/composables/useLocaleSwitch'
import { usePageDecoration } from '@/composables/usePageDecoration'
import { useTabBarLocale } from '@/composables/useTabBarLocale'
import { useMessageStore } from '@/store/messageStore'
import { PURCHASE_SCENE_PERSONAL, useShipContextStore } from '@/store/shipContextStore'
import { useTenantCapabilityStore } from '@/store/tenantCapabilityStore'
import { formatDiscountValue } from '@/utils/discount-price'
import { Local } from '@/utils/storage'

const { handlePageScroll } = useFrostedNavbar()

definePage({
  name: 'user-center',
  layout: 'tabbar',
  style: {
    navigationStyle: 'custom',
    // 页头是品牌渐变，状态栏前景必须转白，否则黑字压在深色渐变上看不清
    navigationBarTextStyle: 'white',
    navigationBarTitleText: '个人中心',
  },
})
interface MyOrder {
  icon: string
  name: string
  status: string
}
interface MyService {
  icon: string
  name: string
  url: string
}

/** 页头底色：与导航栏同源，二者取值必须一致，否则顶部会露出一道色差 */
const BRAND_COLOR = 'var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237))'

const { t, locale } = useI18n()
useTabBarLocale()
// 滚动接近底部时广播检查：装修楼层与「猜你喜欢」各自测距后自动加载下一页
useDecorationInfiniteScroll()
// 页头右侧语言快捷键：展示可切换到的另一种语言，一键互切
const { switchLabel, toggleLanguage } = useLocaleSwitch()

// 定义变量
const orderCountArray = ref<Record<string, number>>({})
const userStore = useUserStore()
const myOrder = computed<MyOrder[]>(() => [
  {
    icon: 'i-carbon:wallet',
    name: t('userCenter.orderPendingPayment'),
    status: '1',
  },
  {
    icon: 'i-carbon:package-node',
    name: t('userCenter.orderPendingShipment'),
    status: '2',
  },
  {
    icon: 'i-carbon:delivery-truck',
    name: t('userCenter.orderPendingReceipt'),
    status: '3',
  },
  {
    icon: 'i-carbon:chat',
    name: t('userCenter.orderPendingReview'),
    status: '4',
  },
  {
    icon: 'i-carbon:right-panel-open',
    name: t('userCenter.orderRefund'),
    status: '5',
  },
])
const serviceById = computed<Record<ServiceId, MyService>>(() => ({
  message: {
    icon: 'i-carbon:notification-new',
    name: t('userCenter.serviceMessage'),
    url: '/sub-pages/message/notice/index',
  },
  membership: {
    icon: 'i-carbon:user-profile',
    name: t('userCenter.serviceMember'),
    url: '/sub-pages/user/member/index',
  },
  address: {
    icon: 'i-carbon:location',
    name: t('userCenter.serviceAddress'),
    url: '/sub-pages/user/address/index',
  },
  review: {
    icon: 'i-carbon:edit',
    name: t('userCenter.serviceAppraise'),
    url: '/sub-pages/user/appraise/index',
  },
  footprint: {
    icon: 'i-carbon:time',
    name: t('userCenter.serviceFootprint'),
    url: '/sub-pages/user/footprint/index',
  },
  favorite: {
    icon: 'i-carbon:star',
    name: t('userCenter.serviceCollect'),
    url: '/sub-pages/user/collect/index',
  },
  coupon: {
    icon: 'i-carbon:ticket',
    name: t('userCenter.serviceCoupon'),
    url: '/sub-pages/promotion/coupon/coupon-user/index',
  },
  support: {
    icon: 'i-carbon:headset',
    name: t('userCenter.serviceSupport'),
    url: '/sub-pages/message/chat/index',
  },
}))

const router = useRouter()
const authStore = useAuthStore()
const messageStore = useMessageStore()
const { show: showToast } = useGlobalToast()

// 个人中心发布版同时承载原生模块设置和运营内容。
const { pageContentData, loading: decorationLoading, fetch: fetchDecoration } = usePageDecoration('4')
const decoration = computed(() => pageContentData.value ? migratePageContent(pageContentData.value) : undefined)
const userCenter = computed(() => normalizeUserCenter(decoration.value?.page.userCenter))
const embeddedContent = computed(() => decoration.value?.components.length ? pageContentData.value : null)
const heroStyle = computed(() => userCenterHeroStyle(userCenter.value.hero))

const iconClass: Record<UserCenterIcon, string> = {
  default: '',
  bookmark: 'i-carbon:bookmark',
  pin: 'i-carbon:pin',
  star: 'i-carbon:star',
  grid: 'i-carbon:grid',
  sparkles: 'i-carbon:idea',
}

function configuredIcon(icon: UserCenterIcon | undefined, fallback: string) {
  return (icon && iconClass[icon]) || fallback
}

function localized(value: { zh: string, en: string }, fallback = '') {
  return (locale.value.startsWith('en') ? value.en : value.zh).trim() || fallback
}

function orderIcon(id: OrderId | undefined, fallback: string) {
  return configuredIcon(id && userCenter.value.orders.icons[id], fallback)
}

// ===================== 资产条 =====================

/**
 * 页头资产条：优惠券 / 积分 / 余额（**仅个人态展示**）。
 *
 * 三者都是 userStore 里已有的数据（登录后由 fetchUserInfo 拉取并持久化），
 * 这里只是把它们从「藏在会员中心二级页」提到一屏可见的位置——
 * 原来页头只有一个「优惠券 0」灰胶囊，既看不出信息层级，也漏掉了积分与余额。
 * 企业态（船供/企业采购场景或已命中企业等级徽章）下积分与余额不参与企业采购结算，
 * 整条资产条隐藏，避免给出「企业单可用个人积分/余额」的错误预期（见 personalAssetsVisible）。
 */
const assetCoupon = computed(() => userStore.getCouponCount ?? 0)
const assetPoint = computed(() => userStore.getPoint ?? 0)
const assetBalance = computed(() => userStore.getBalance ?? 0)

const assetById = computed(() => ({
  coupon: { label: t('userCenter.coupon'), value: assetCoupon.value, url: '/sub-pages/promotion/coupon/coupon-user/index' },
  points: { label: t('userCenter.points'), value: assetPoint.value, url: '/sub-pages/user/member/points-record' },
  balance: { label: t('userCenter.balance'), value: assetBalance.value, url: '/sub-pages/user/member/balance-record' },
}))

// ===================== 船舶入口 =====================

/**
 * 船舶入口只在「已登录 + 租户开放船供能力」时出现。
 *
 * 首页状态条只对有船用户展示靠港信息，未绑定用户完全不显示，
 * 因此绑定入口必须在这里给出，否则新用户会再次陷入「知道缺什么却无处可去」。
 */
const tenantCapabilityStore = useTenantCapabilityStore()
const shipContextStore = useShipContextStore()
const vesselBound = ref(false)
const vesselName = ref('')
/**
 * 双轨会员企业轨徽章（2026-11-14 B2B）：船上下文存在时拉船舶主体的企业等级，
 * 未定级/查询失败一律空串不渲染（fail-closed，不与个人等级徽章冲突）。
 */
const enterpriseLevelName = ref('')
/** 弹层展示的权益聚合（与徽章同源数据，弹层打开时按最新数据渲染） */
const enterpriseBenefits = ref<EnterpriseBenefitsVO | null>(null)

/**
 * 企业态判定：采购场景非个人（船供/企业采购），或已命中企业等级徽章。
 *
 * 徽章异步到达前也按场景判定，避免企业用户先看到一屏个人资产再收起。
 */
const isEnterpriseContext = computed(() =>
  shipContextStore.purchaseScene !== PURCHASE_SCENE_PERSONAL
  || Boolean(enterpriseLevelName.value))

/** 资产条仅个人态展示（企业态隐藏优惠券/积分/余额整条） */
const personalAssetsVisible = computed(() => authStore.isLoggedIn && !isEnterpriseContext.value)
/**
 * 是否已登记为在职销售（销售工作台入口的可见性依据）。
 *
 * 入口只在 `isSales=true` 时渲染，避免非销售用户点进去看到一个「非销售账号」页；
 * 请求失败与「非销售」同样按隐藏处理（不阻塞个人中心其余内容）。
 * 声明前置：onShow 的 else 分支要清它（ts/no-use-before-define）。
 */
const isSales = ref(false)

const vesselEntranceVisible = computed(() => {
  // resolved 之前不渲染：否则纯零售租户会先闪出一个船供入口再收起
  if (!tenantCapabilityStore.resolved)
    return false
  if (!authStore.isLoggedIn)
    return false
  return tenantCapabilityStore.shipSupplyEnabled
})

/**
 * 副标题枚举页内主要能力：入口名叫「我的船舶」，看不出来里面还有
 * 港口报备车辆（靠港打车拨司机电话）这类与绑定无关的功能，只能靠这里预告。
 */
const vesselEntranceSubtitle = computed(() =>
  vesselBound.value
    ? t('userCenter.vesselLinkedSubtitle', { name: vesselName.value || t('userCenter.vesselLinked') })
    : t('userCenter.vesselUnlinkedSubtitle'),
)

/** 仅用于入口文案；失败时不影响入口本身可点 */
function loadVesselState() {
  getMyVessels()
    .then((vessels) => {
      vesselBound.value = !!vessels && vessels.length > 0
      vesselName.value = vessels?.[0]?.vesselName ?? ''
    })
    .catch(() => {
      vesselBound.value = false
      vesselName.value = ''
    })
}

/** 企业等级徽章：随船上下文拉取（切船/登出由 onShow 重算），失败静默不渲染 */
function loadEnterpriseLevel() {
  const vesselId = shipContextStore.vesselId
  if (!authStore.isLoggedIn || !vesselId) {
    enterpriseLevelName.value = ''
    return
  }
  getVesselEnterpriseBenefits(vesselId)
    .then((benefits) => {
      enterpriseBenefits.value = benefits
      enterpriseLevelName.value = benefits?.levelName || ''
    })
    .catch(() => {
      enterpriseBenefits.value = null
      enterpriseLevelName.value = ''
    })
}

// ===================== 企业权益弹层（双轨会员企业轨展示） =====================

const enterpriseSheetVisible = ref(false)

function openEnterpriseSheet() {
  if (!enterpriseBenefits.value)
    return
  enterpriseSheetVisible.value = true
}

/**
 * 折扣率权益文案：0.95 → 中文「9.5折」/ 英文「5% OFF」；null/无力度=未配置。
 *
 * 复用全站折扣文案口径（`formatDiscountValue`），不再硬编码「折」——
 * 英文界面出现「9.5折」这类混合文案等于没翻译。
 */
const enterpriseDiscountText = computed(() => {
  const rate = Number(enterpriseBenefits.value?.discountRate)
  if (!Number.isFinite(rate) || rate <= 0 || rate >= 1)
    return ''
  return formatDiscountValue({ discountType: 1, discountValue: rate })
})

// ===================== 配送工作台入口 =====================

/** 配送资格（服务端判断，资格请求完成前不渲染入口，避免先显示后隐藏跳动） */
const deliveryEligibility = ref<DeliveryEligibility | null>(null)
/** 本地是否已有配送员 token（仅作减少换取次数的提示，不作为授权依据） */
const hasDeliveryToken = ref(false)
/** 换取/探活请求进行中，防止连续点击重复换取 */
const deliveryEntering = ref(false)

onShow(() => {
  fetchDecoration()
  if (authStore.isLoggedIn) {
    getUserOrderCount()
    void messageStore.refreshUnread()
    messageStore.connect()
    loadDeliveryEligibility()
    loadSalesEligibility()
    /**
     * 本页在路由白名单里，守卫不会替它拉用户信息，而页头的昵称/头像/等级
     * 与资产条全部取自 store —— 冷启动时可能只恢复了 token 而没有用户数据，
     * 不补这一次请求就会顶着「登录/注册」渲染已登录用户的页面。
     */
    if (!userStore.getUserInfo) {
      // fetchUserInfo 会并行刷新优惠券/积分/收藏，失败静默降级为 0
      userStore.fetchUserInfo().catch(() => {})
    }
    else {
      // 已有用户信息时只需刷新资产条的数字，避免每次切 Tab 都打一次用户信息接口
      userStore.refreshPointsInfo().catch(() => {})
      userStore.refreshUserCouponCount().catch(() => {})
    }
    void tenantCapabilityStore.ensureLoaded().then(() => {
      if (tenantCapabilityStore.shipSupplyEnabled) {
        loadVesselState()
      }
    })
    loadEnterpriseLevel()
  }
  else {
    deliveryEligibility.value = null
    hasDeliveryToken.value = false
    vesselBound.value = false
    vesselName.value = ''
    isSales.value = false
    enterpriseLevelName.value = ''
  }
})

/**
 * 查询配送资格：请求失败或无资格时一律隐藏入口（token 不能作为授权依据）
 */
function loadDeliveryEligibility() {
  hasDeliveryToken.value = !!Local.get('deliveryToken')
  getDeliveryEligibility()
    .send()
    .then((eligibility) => {
      deliveryEligibility.value = eligibility ?? null
    })
    .catch(() => {
      // 资格查询失败：清理展示状态并隐藏入口，留在个人中心
      deliveryEligibility.value = null
      hasDeliveryToken.value = false
    })
}

/**
 * 入口是否可见：
 * ACTIVE 正常展示；ACCOUNT_DISABLED（员工账号停用）展示灰色提示卡；
 * UNBOUND / PERMISSION_MISSING / STAFF_INVALID 及请求失败一律不展示。
 */
const deliveryEntranceVisible = computed(() => {
  const eligibility = deliveryEligibility.value
  if (!eligibility) {
    return false
  }
  return eligibility.eligible || eligibility.status === 'ACCOUNT_DISABLED'
})

/** 入口副标题 */
const deliveryEntranceSubtitle = computed(() => {
  const eligibility = deliveryEligibility.value
  if (eligibility?.eligible) {
    return eligibility.pendingTaskCount && eligibility.pendingTaskCount > 0
      ? t('userCenter.deliveryPendingTasks', { count: eligibility.pendingTaskCount })
      : t('userCenter.deliveryReady')
  }
  if (eligibility?.status === 'ACCOUNT_DISABLED') {
    return t('userCenter.deliveryDisabled')
  }
  return t('userCenter.deliveryEnter')
})

// ===================== 销售工作台入口 =====================

async function loadSalesEligibility() {
  try {
    const workbench = await getSalesWorkbench()
    isSales.value = Boolean(workbench?.isSales)
  }
  catch {
    isSales.value = false
  }
}

/** 入口角标：待处理任务数（最多显示 99+） */
const deliveryEntranceBadge = computed(() =>
  formatBadge(deliveryEligibility.value?.pendingTaskCount ?? 0),
)

const workbenchById = computed(() => ({
  delivery: {
    visible: authStore.isLoggedIn && deliveryEntranceVisible.value,
    icon: 'i-carbon:delivery-truck',
    title: t('userCenter.deliveryWorkbench'),
    description: deliveryEntranceSubtitle.value,
    badge: deliveryEntranceBadge.value,
    muted: !deliveryEligibility.value?.eligible,
    busy: deliveryEntering.value,
  },
  vessel: {
    visible: vesselEntranceVisible.value,
    icon: 'i-carbon:sailboat-coastal',
    title: t('userCenter.myVessels'),
    description: vesselEntranceSubtitle.value,
    badge: '',
    muted: false,
    busy: false,
  },
  sales: {
    visible: authStore.isLoggedIn && isSales.value,
    icon: 'i-carbon:chart-line',
    title: t('userCenter.salesWorkbench'),
    description: t('userCenter.salesWorkbenchDesc'),
    badge: '',
    muted: false,
    busy: false,
  },
}))
const visibleWorkbenchIds = computed(() => userCenter.value.workbenches.order.filter(id => workbenchById.value[id].visible))

function openWorkbench(id: WorkbenchId) {
  if (!workbenchById.value[id].visible)
    return
  if (id === 'delivery')
    void enterDeliveryWorkspace()
  else
    toRoute(id === 'vessel' ? '/sub-pages/vessel/bind/index' : '/sub-pages/user/sales/index')
}

/**
 * 清理本地配送登录态（停用、解绑或 token 失效后调用）
 */
function clearDeliveryAuth() {
  Local.remove('deliveryToken')
  Local.remove('deliveryStaffInfo')
  hasDeliveryToken.value = false
}

/**
 * 保存配送登录态并进入工作台
 */
async function saveDeliveryAuthAndEnter(token: string) {
  // 候选 token 先过 delivery session 校验，再保存/拉资料/进入；
  // 身份校验失败（错租户/配置错误）不得被下面的资料空 catch 降级为「保存空资料并导航」
  await ensureSessionTenant('delivery', token)
  Local.set('deliveryToken', token)
  try {
    const staffResponse = await getMyDeliveryStaff().send()
    Local.set('deliveryStaffInfo', staffResponse?.data || staffResponse || {})
  }
  catch {
    Local.set('deliveryStaffInfo', {})
  }
  router.push({ path: '/pages/delivery/index' })
}

/**
 * 用商城登录态换取配送员身份（供首次进入与 token 失效重试）
 */
async function exchangeAndEnter(): Promise<boolean> {
  const response = await exchangeDeliveryIdentity().send()
  const token = response?.tokenValue
  if (!token) {
    showToast(t('userCenter.deliveryUnavailable'))
    return false
  }
  await saveDeliveryAuthAndEnter(token)
  return true
}

/**
 * 点击入口：
 * 1. 停用状态：清理本地配送态并提示，不发起换取
 * 2. 本地有 token：先探活，有效直接进入；失效清理后重新换取一次
 * 3. 无 token：直接换取
 */
async function enterDeliveryWorkspace() {
  const eligibility = deliveryEligibility.value
  if (eligibility && !eligibility.eligible) {
    if (eligibility.status === 'ACCOUNT_DISABLED') {
      clearDeliveryAuth()
      showToast(t('userCenter.deliveryDisabledContact'))
    }
    return
  }
  if (deliveryEntering.value) {
    return
  }
  deliveryEntering.value = true
  try {
    if (hasDeliveryToken.value) {
      try {
        // 轻量探活：避免资格正常时重复换取
        await ensureSessionTenant('delivery', String(Local.get('deliveryToken') || ''))
        await getMyDeliveryStaff().send()
        router.push({ path: '/pages/delivery/index' })
        return
      }
      catch (error: any) {
        // 仅 401（旧 token 真失效）清理后重新换取；403（身份配置/权限）保留 token
        // 不自动换取；网络/守卫错误保留 token 停止进入，避免把配置错误洗成重登
        if (isTenantIdentityError(error)) {
          showToast(t('userCenter.tenantUnavailable'))
          return
        }
        const code = error?.code
        if (code !== 401) {
          showToast(t('userCenter.deliveryUnavailable'))
          return
        }
        clearDeliveryAuth()
      }
    }
    await exchangeAndEnter()
  }
  catch {
    // 401 已引导商城登录，403/业务错误已 toast，均留在个人中心
  }
  finally {
    deliveryEntering.value = false
  }
}
/**
 * 查询订单数量
 */
function getUserOrderCount() {
  getOrderCount({}).then((response) => {
    orderCountArray.value = response
  })
}

/** 角标文案：0 / 空值不出角标，超过 99 收敛为 99+ */
function formatBadge(count: number | undefined): string {
  if (!count || count <= 0) {
    return ''
  }
  return count > 99 ? '99+' : String(count)
}

/** 订单角标按状态码取值：接口返回的是 { '1': n, ... } 映射，不是数组 */
function orderBadge(status: string): string {
  return formatBadge(orderCountArray.value?.[status])
}

/**
 * 跳转订单页
 * @param status 订单状态
 */
function toOrder(status: string) {
  if (status === '5') {
    // 跳转退款售后页面
    router.push({
      path: '/sub-pages/order/order-refunds/refunds-list/index',
    })
  }
  else {
    router.push({
      path: `/sub-pages/order/order-list/index?status=${status}`,
    })
  }
}
/**
 *  跳转
 * @param url 页面url
 */
function toRoute(url: string) {
  router.push({
    path: url,
  })
}
/**
 * 跳转登录页
 */
function toLogin() {
  if (authStore.isLoggedIn) {
    return
  }
  router.replaceAll({
    name: 'login',
  })
}

// 顶栏磨砂玻璃：字面量注册滚动（小程序按页面源码生成滚动位标记，包进 composable 会静默失效）
onPageScroll(handlePageScroll)
</script>

<template>
  <hr-navbar
    :left-arrow="false"
    :title="t('userCenter.title')"
    :background-color="heroStyle.backgroundColor || BRAND_COLOR"
    text-color="#ffffff"
  />
  <!--
    页头：品牌渐变铺底，右侧是语言切换 + 设置入口。
    未登录时整个页头（头像 + 「登录/注册」+ 权益说明）就是唯一的登录入口，
    下方不再重复一条登录引导卡——同一个动作在一屏里出现两次只会分散注意力。
  -->
  <view
    class="uc-hero"
    :class="{
      'uc-hero--guest': !personalAssetsVisible,
      'uc-hero--solid': userCenter.hero.memberStyle === 'solid',
    }"
    :style="heroStyle"
  >
    <view class="uc-hero__profile" @click="toLogin">
      <image
        class="uc-hero__avatar"
        :src="userStore.getUserAvatar || '/static/default-avatar.png'"
        mode="aspectFill"
      />
      <view class="uc-hero__meta">
        <view class="uc-hero__name-row">
          <text class="uc-hero__name">
            {{ userStore.getUserNickname || t('userCenter.loginOrRegister') }}
          </text>
          <text
            v-if="authStore.isLoggedIn && userStore.getLevelName"
            class="uc-hero__level"
          >
            {{ userStore.getLevelName }}
          </text>
          <text
            v-if="enterpriseLevelName"
            class="uc-hero__level uc-hero__level--enterprise"
            @click.stop="openEnterpriseSheet"
          >
            {{ enterpriseLevelName }}
          </text>
        </view>
        <text v-if="!authStore.isLoggedIn" class="uc-hero__hint">
          {{ localized(userCenter.hero.loginHint, t('userCenter.loginHint')) }}
        </text>
        <text v-else-if="localized(userCenter.hero.memberDescription)" class="uc-hero__hint">
          {{ localized(userCenter.hero.memberDescription) }}
        </text>
      </view>
    </view>
    <!-- 页头右侧动作组：语言切换与设置并排，两者同一材质、同一条中线 -->
    <view class="uc-hero__actions">
      <!--
        语言切换：导航栏右上角被「居中标题 + 微信胶囊」夹住（375pt 屏只剩约 37px），
        放不下带文字的按钮，因此收进页头右侧，做成与齿轮同材质的玻璃胶囊。
      -->
      <view
        class="uc-hero__lang"
        hover-class="uc-hero__lang--pressed"
        :hover-stay-time="80"
        :aria-label="switchLabel"
        aria-role="button"
        @click="toggleLanguage"
      >
        <text class="i-carbon:translate uc-hero__lang-icon" />
        <text class="uc-hero__lang-label">
          {{ switchLabel }}
        </text>
      </view>
      <view
        class="uc-hero__setting"
        hover-class="uc-hero__setting--pressed"
        @click="toJumpUrl('/sub-pages/user/user-setting/index')"
      >
        <text class="i-carbon:settings uc-hero__setting-icon" />
      </view>
    </view>
  </view>
  <view
    v-if="personalAssetsVisible"
    class="uc-assets"
    :class="`uc-skin--${userCenter.assets.style}`"
  >
    <view
      v-for="id in userCenter.assets.order"
      :key="id"
      class="uc-assets__item"
      hover-class="uc-assets__item--pressed"
      @click="toRoute(assetById[id].url)"
    >
      <text class="uc-assets__value">
        {{ assetById[id].value }}
      </text>
      <text class="uc-assets__label">
        {{ assetById[id].label }}
      </text>
    </view>
  </view>
  <template v-if="userCenter.contentPosition === 'beforeOrders'">
    <view v-if="embeddedContent" class="uc-decoration">
      <DiyPage :page-content-data="embeddedContent" embedded />
    </view>
    <view v-else-if="decorationLoading" class="uc-decoration-skeleton" />
  </template>
  <!-- 我的订单 -->
  <view class="uc-card" :class="`uc-skin--${userCenter.orders.style}`">
    <view class="uc-card__head">
      <text class="uc-card__title">
        {{ localized(userCenter.orders.title, t('userCenter.myOrders')) }}
      </text>
      <view
        class="uc-card__more"
        hover-class="uc-card__more--pressed"
        @click="toOrder('')"
      >
        <text>{{ t('userCenter.viewAll') }}</text>
        <text class="i-carbon:chevron-right uc-card__more-arrow" />
      </view>
    </view>
    <view class="uc-grid uc-grid--5">
      <view
        v-for="(item, index) in myOrder"
        :key="item.status"
        class="uc-grid__item"
        hover-class="uc-grid__item--pressed"
        @click="toOrder(item.status)"
      >
        <view class="uc-grid__icon">
          <text :class="orderIcon(userCenterOrderIds[index], item.icon)" class="uc-grid__glyph" />
          <text v-if="orderBadge(item.status)" class="uc-grid__badge">
            {{ orderBadge(item.status) }}
          </text>
        </view>
        <text class="uc-grid__label">
          {{ item.name }}
        </text>
      </view>
    </view>
  </view>
  <view v-if="userCenter.services.order.length || visibleWorkbenchIds.length" class="uc-card">
    <view class="uc-card__head">
      <text class="uc-card__title">
        {{ t('userCenter.myServices') }}
      </text>
    </view>
    <view v-if="userCenter.services.order.length" class="uc-grid" :class="`uc-grid--${userCenter.services.columns}`">
      <view
        v-for="id in userCenter.services.order"
        :key="id"
        class="uc-grid__item"
        hover-class="uc-grid__item--pressed"
        @click="toRoute(serviceById[id].url)"
      >
        <view class="uc-grid__icon">
          <text :class="configuredIcon(userCenter.services.icons[id], serviceById[id].icon)" class="uc-grid__glyph" />
          <text v-if="id === 'message' && formatBadge(messageStore.totalUnread)" class="uc-grid__badge">
            {{ formatBadge(messageStore.totalUnread) }}
          </text>
        </view>
        <text class="uc-grid__label">
          {{ serviceById[id].name }}
        </text>
      </view>
    </view>
    <view
      v-for="id in visibleWorkbenchIds"
      :key="id"
      class="uc-entry"
      :class="[
        `uc-skin--${userCenter.workbenches.style}`,
        { 'uc-entry--muted': workbenchById[id].muted, 'uc-entry--busy': workbenchById[id].busy },
      ]"
      hover-class="uc-entry--pressed"
      @click="openWorkbench(id)"
    >
      <view class="uc-entry__icon">
        <text :class="configuredIcon(userCenter.workbenches.icons[id], workbenchById[id].icon)" class="uc-entry__glyph" />
      </view>
      <view class="uc-entry__body">
        <text class="uc-entry__title">
          {{ workbenchById[id].title }}
        </text>
        <text class="uc-entry__desc">
          {{ workbenchById[id].description }}
        </text>
      </view>
      <text v-if="workbenchById[id].badge" class="uc-entry__count">
        {{ workbenchById[id].badge }}
      </text>
      <text class="i-carbon:chevron-right uc-entry__arrow" />
    </view>
  </view>
  <view v-if="userCenter.contentPosition === 'afterServices' && embeddedContent" class="uc-decoration">
    <DiyPage :page-content-data="embeddedContent" embedded />
  </view>
  <template v-if="!decorationLoading && userCenter.recommendations.visible">
    <view class="uc-like">
      <view class="uc-like__rule" />
      <text class="uc-like__text">
        {{ localized(userCenter.recommendations.title, t('userCenter.guessYouLike')) }}
      </text>
      <view class="uc-like__rule" />
    </view>
    <WaterfallGoods :source="userCenter.recommendations.source" />
  </template>
  <!-- 悬浮玻璃底栏是 fixed 覆盖层：留一条等高空白，推荐流最后一行能滚到栏上方 -->
  <view class="tabbar-bottom-space" />
  <!--
    企业权益弹层：点页头企业等级徽章展开。双轨会员企业轨的权益感知层——
    折扣/免邮在结算里已自动生效，这里解释「为什么价格不一样」；
    返利率/账期为预留权益（二期资金域启用），灰显标注「即将上线」。
  -->
  <wd-popup
    v-model="enterpriseSheetVisible"
    position="bottom"
    custom-class="uc-enterprise-sheet"
    :safe-area-inset-bottom="true"
    :z-index="1100"
    @close="enterpriseSheetVisible = false"
  >
    <view v-if="enterpriseBenefits" class="uc-ent-sheet">
      <view class="uc-ent-sheet__head">
        <text class="uc-ent-sheet__title">
          {{ t('userCenter.enterpriseSheet.title') }}
        </text>
        <text class="uc-ent-sheet__level">
          {{ enterpriseBenefits.levelName }}
        </text>
      </view>
      <view
        v-if="enterpriseDiscountText
          || enterpriseBenefits.freeShipping
          || enterpriseBenefits.invoicingEnabled
          || enterpriseBenefits.dedicatedService"
        class="uc-ent-sheet__list"
      >
        <view v-if="enterpriseDiscountText" class="uc-ent-sheet__row">
          <text class="i-carbon:percentage uc-ent-sheet__icon" />
          <text class="uc-ent-sheet__text">
            {{ t('userCenter.enterpriseSheet.discount', { rate: enterpriseDiscountText }) }}
          </text>
        </view>
        <view v-if="enterpriseBenefits.freeShipping" class="uc-ent-sheet__row">
          <text class="i-carbon:delivery uc-ent-sheet__icon" />
          <text class="uc-ent-sheet__text">
            {{ t('userCenter.enterpriseSheet.freeShipping') }}
          </text>
        </view>
        <view v-if="enterpriseBenefits.invoicingEnabled" class="uc-ent-sheet__row">
          <text class="i-carbon:document-signed uc-ent-sheet__icon" />
          <text class="uc-ent-sheet__text">
            {{ t('userCenter.enterpriseSheet.invoicing') }}
          </text>
        </view>
        <view v-if="enterpriseBenefits.dedicatedService" class="uc-ent-sheet__row">
          <text class="i-carbon:user-multiple uc-ent-sheet__icon" />
          <text class="uc-ent-sheet__text">
            {{ t('userCenter.enterpriseSheet.dedicatedService') }}
          </text>
        </view>
      </view>
      <view v-else class="uc-ent-sheet__empty">
        {{ t('userCenter.enterpriseSheet.noBenefits') }}
      </view>
      <view
        v-if="enterpriseBenefits.rebateRate || enterpriseBenefits.creditDays"
        class="uc-ent-sheet__reserved"
      >
        <text v-if="enterpriseBenefits.rebateRate" class="uc-ent-sheet__reserved-item">
          {{ t('userCenter.enterpriseSheet.reserved') }} · {{ t('userCenter.enterpriseSheet.rebate') }}
        </text>
        <text v-if="enterpriseBenefits.creditDays" class="uc-ent-sheet__reserved-item">
          {{ t('userCenter.enterpriseSheet.reserved') }} · {{ t('userCenter.enterpriseSheet.creditDays', { days: enterpriseBenefits.creditDays }) }}
        </text>
      </view>
      <text class="uc-ent-sheet__hint">
        {{ t('userCenter.enterpriseSheet.tierHint') }}
      </text>
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
/* 文字色阶与卡片口径沿用全站约定：接入语义色变量（App.vue 定义），深色模式自动跟随 */
$uc-title: var(--app-text);
$uc-text: var(--app-text-2);
$uc-muted: var(--app-text-3);
$uc-hairline: var(--app-border);
/* 卡片左右留白与全站 mx-20rpx 对齐 */
$uc-gutter: 20rpx;

.uc-decoration {
  /* 装修块自带底色，这里不再叠加第四种灰（全站已用 #f5f5f5/#f2f3f5/#F8F8F8） */
  min-height: 0;
  /* 与上一张卡（资产条 / 未登录时的页头）留出全站统一的卡片间距；
     区块自己的 marginY 由运营在装修页配置，二者叠加互不覆盖 */
  margin-top: 20rpx;
}

.uc-decoration-skeleton {
  height: 200rpx;
  margin: 20rpx $uc-gutter;
  border-radius: 24rpx;
  background: var(--app-card-2);
}

/* ===================== 页头 ===================== */

.uc-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  /* 底部多留一截给资产条上移压盖，渐变收口才落在卡片背后而不是露出一条灰边 */
  padding: 24rpx $uc-gutter 104rpx;
  background: linear-gradient(180deg, var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237)) 0%, var(--theme-color-secondary, var(--wot-color-theme-secondary, #FF6B7A)) 100%);

  /*
   * 未登录没有资产条压盖，收窄渐变底部留白：
   * 否则头像下面会吊着一大片纯品牌色空块（原设计留白是给白色资产卡的）。
   */
  &--guest {
    padding-bottom: 44rpx;
  }

  &__profile {
    display: flex;
    align-items: center;
    flex: 1;
    min-width: 0;
  }

  &__avatar {
    width: 104rpx;
    height: 104rpx;
    flex-shrink: 0;
    border: 3rpx solid rgba(255, 255, 255, 0.55);
    border-radius: 50%;
    background-color: #fff;
  }

  &__meta {
    display: flex;
    flex-direction: column;
    margin-left: 24rpx;
    min-width: 0;
  }

  &__name-row {
    display: flex;
    align-items: center;
  }

  &__name {
    max-width: 340rpx;
    overflow: hidden;
    color: #fff;
    font-size: 36rpx;
    font-weight: 700;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  /* 等级徽章做半透明玻璃片，压在渐变上不抢昵称的主次 */
  &__level {
    margin-left: 14rpx;
    padding: 4rpx 16rpx;
    flex-shrink: 0;
    border-radius: 999rpx;
    background: rgba(255, 255, 255, 0.24);
    color: #fff;
    font-size: 20rpx;
    line-height: 1.6;
  }

  /* 企业等级徽章：同材质描边变体，与个人等级徽章并排可区分 */
  &__level--enterprise {
    background: rgba(255, 255, 255, 0.08);
    border: 1rpx solid rgba(255, 255, 255, 0.55);
  }

  &__hint {
    margin-top: 10rpx;
    color: rgba(255, 255, 255, 0.82);
    font-size: 24rpx;
  }

  /* 右侧动作组：把语言切换和齿轮收成一组，和左侧资料区共用一条中线 */
  &__actions {
    display: flex;
    align-items: center;
    flex-shrink: 0;
    margin-left: 16rpx;
    gap: 16rpx;
  }

  /*
   * 语言切换：玻璃胶囊。底色/描边/内高光与齿轮同源，压在渐变上不抢昵称的主次；
   * 白字 + 半透明白底在任意品牌色上都成立（主题色由租户配置，不能写死颜色）。
   */
  &__lang {
    display: flex;
    align-items: center;
    height: 72rpx;
    padding: 0 26rpx 0 22rpx;
    border: 1rpx solid rgba(255, 255, 255, 0.48);
    border-radius: 999rpx;
    background: linear-gradient(180deg, rgba(255, 255, 255, 0.30) 0%, rgba(255, 255, 255, 0.15) 100%);
    box-shadow: 0 4rpx 16rpx rgba(120, 40, 0, 0.14), inset 0 1rpx 0 rgba(255, 255, 255, 0.26);
    transition: background-color 0.18s ease, transform 0.18s ease;
  }

  &__lang--pressed {
    background: rgba(255, 255, 255, 0.38);
    transform: scale(0.95);
  }

  &__lang-icon {
    margin-right: 6rpx;
    color: #fff;
    font-size: 24rpx;
  }

  &__lang-label {
    color: #fff;
    font-size: 24rpx;
    font-weight: 600;
    line-height: 1;
    letter-spacing: 0.4rpx;
  }

  &__setting {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 72rpx;
    height: 72rpx;
    flex-shrink: 0;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.18);
  }

  &__setting--pressed {
    background: rgba(255, 255, 255, 0.32);
  }

  &__setting-icon {
    color: #fff;
    font-size: 40rpx;
  }
}

/* ===================== 资产条 ===================== */

.uc-assets {
  position: relative;
  display: flex;
  align-items: stretch;
  margin: -72rpx $uc-gutter 0;
  padding: 28rpx 0;
  border-radius: 24rpx;
  background: var(--app-card);
  box-shadow: 0 8rpx 24rpx rgba(31, 44, 65, 0.06);

  &__item {
    display: flex;
    flex: 1;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    /* 分栏竖线：用边框而不是伪元素，小程序端不用额外生成节点 */
    border-left: 1rpx solid $uc-hairline;
  }

  &__item:first-child {
    border-left: 0;
  }

  &__item--pressed {
    opacity: 0.6;
  }

  &__value {
    color: $uc-title;
    font-size: 40rpx;
    font-weight: 700;
    line-height: 1.2;
  }

  &__label {
    margin-top: 8rpx;
    color: $uc-muted;
    font-size: 24rpx;
  }
}

/* ===================== 卡片与宫格 ===================== */

.uc-card {
  margin: 20rpx $uc-gutter 0;
  padding: 8rpx 20rpx 4rpx;
  border-radius: 24rpx;
  background: var(--app-card);
  box-shadow: 0 8rpx 24rpx rgba(31, 44, 65, 0.04);

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 88rpx;
  }

  &__title {
    color: $uc-title;
    font-size: 30rpx;
    font-weight: 700;
  }

  &__more {
    display: flex;
    align-items: center;
    color: $uc-muted;
    font-size: 24rpx;
  }

  &__more--pressed {
    opacity: 0.6;
  }

  &__more-arrow {
    font-size: 26rpx;
  }
}

.uc-grid {
  display: flex;
  flex-wrap: wrap;
  padding-bottom: 20rpx;

  &__item {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 12rpx 0 20rpx;
  }

  &--5 &__item {
    width: 20%;
  }

  &--3 &__item {
    width: calc(100% / 3);
  }

  &--4 &__item {
    width: 25%;
  }

  &__item--pressed {
    opacity: 0.6;
  }

  &__icon {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 76rpx;
    height: 76rpx;
    border-radius: 24rpx;
    background: var(--app-card-2);
  }

  &__glyph {
    color: $uc-text;
    font-size: 40rpx;
  }

  /* 角标锚在图标方块右上角：全部用 rpx 定位，混用 px 会在不同机型上漂移 */
  &__badge {
    position: absolute;
    top: -10rpx;
    right: -16rpx;
    min-width: 32rpx;
    height: 32rpx;
    padding: 0 8rpx;
    box-sizing: border-box;
    border-radius: 16rpx;
    background: var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237));
    color: #fff;
    font-size: 20rpx;
    line-height: 32rpx;
    text-align: center;
  }

  &__label {
    margin-top: 12rpx;
    color: $uc-text;
    font-size: 24rpx;
  }
}

/* ===================== 角色入口行（配送工作台 / 我的船舶） ===================== */

/* 行样式而非独立卡：宿主是「我的服务」白卡，这里只负责行内布局与发丝分隔线 */
.uc-entry {
  display: flex;
  align-items: center;
  padding: 24rpx 32rpx;
  border-top: 1rpx solid $uc-hairline;

  &--pressed {
    opacity: 0.6;
  }

  /* 停用/换取中只降透明度，不隐藏：入口本身仍要看得见、点得到 */
  &--muted {
    opacity: 0.6;
  }

  &--busy {
    opacity: 0.5;
  }

  &__icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 88rpx;
    height: 88rpx;
    flex-shrink: 0;
    border-radius: 28rpx;
    background: var(--app-card-2);
  }

  &__glyph {
    color: var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237));
    font-size: 48rpx;
  }

  &__body {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-width: 0;
    margin-left: 24rpx;
  }

  &__title {
    color: $uc-title;
    font-size: 28rpx;
    font-weight: 700;
  }

  &__desc {
    margin-top: 6rpx;
    overflow: hidden;
    color: $uc-muted;
    font-size: 24rpx;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__count {
    margin-right: 12rpx;
    padding: 2rpx 14rpx;
    flex-shrink: 0;
    border-radius: 999rpx;
    background: var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237));
    color: #fff;
    font-size: 20rpx;
    line-height: 1.6;
  }

  &__arrow {
    color: $uc-muted;
    font-size: 28rpx;
  }
}

/* ===================== 猜你喜欢 ===================== */

.uc-like {
  display: flex;
  align-items: center;
  padding: 40rpx 60rpx 8rpx;

  &__rule {
    flex: 1;
    height: 1rpx;
    background: var(--app-border);
  }

  &__text {
    margin: 0 24rpx;
    color: $uc-title;
    font-size: 28rpx;
    font-weight: 700;
  }
}

// 企业权益弹层（点页头企业徽章展开）：底部弹层 + 权益行列表，
// 视觉走页头同款玻璃语言（白底卡片 + 主题色图标）
.uc-enterprise-sheet {
  border-radius: 24rpx 24rpx 0 0;
  padding: 32rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
}

.uc-ent-sheet {
  &__head {
    align-items: baseline;
    display: flex;
    gap: 16rpx;
    justify-content: space-between;
    margin-bottom: 24rpx;
  }

  &__title {
    color: #303133;
    font-size: 32rpx;
    font-weight: 700;
  }

  &__level {
    background: rgba(255, 34, 55, 0.08);
    border-radius: 999rpx;
    color: var(--wot-color-theme-primary, #ff2237);
    font-size: 24rpx;
    padding: 4rpx 18rpx;
  }

  &__list {
    display: flex;
    flex-direction: column;
    gap: 20rpx;
  }

  &__row {
    align-items: center;
    background: #f7f8fa;
    border-radius: 16rpx;
    display: flex;
    gap: 16rpx;
    padding: 20rpx 24rpx;
  }

  &__icon {
    color: var(--wot-color-theme-primary, #ff2237);
    font-size: 34rpx;
  }

  &__text {
    color: #303133;
    font-size: 26rpx;
    line-height: 1.6;
  }

  &__empty {
    background: #f7f8fa;
    border-radius: 16rpx;
    color: #909399;
    font-size: 26rpx;
    padding: 32rpx 24rpx;
    text-align: center;
  }

  &__reserved {
    display: flex;
    gap: 16rpx;
    margin-top: 16rpx;
  }

  &__reserved-item {
    background: #f4f4f5;
    border-radius: 999rpx;
    color: #909399;
    font-size: 20rpx;
    padding: 4rpx 16rpx;
  }

  &__hint {
    color: #909399;
    display: block;
    font-size: 22rpx;
    margin-top: 24rpx;
    text-align: center;
  }
}

.uc-hero--solid .uc-hero__level {
  color: #172b42;
  background: #fff;
}

.uc-skin--outline {
  box-shadow: inset 0 0 0 1px var(--app-border, #dce3ea);
}

.uc-skin--tinted {
  background: var(--app-card-2);
}

.uc-entry.uc-skin--outline,
.uc-entry.uc-skin--tinted {
  margin-top: 12rpx;
  padding-inline: 16rpx;
  border-radius: 16rpx;
}
</style>
