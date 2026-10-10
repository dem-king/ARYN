<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { getCount as getOrderCount } from '@/api/order/orderInfo'
import {
  type DeliveryEligibility,
  exchangeDeliveryIdentity,
  getDeliveryEligibility,
  getMyDeliveryStaff,
} from '@/api/delivery'
// 引入组件
import DiyPage from '@/components/diy/index.vue'
import WaterfallGoods from '@/components/waterfall-goods/index.vue'
import { usePageDecoration } from '@/composables/usePageDecoration'
import { ensureSessionTenant, isTenantIdentityError } from '@/api/core/tenant-identity'
import { useMessageStore } from '@/store/messageStore'
import { useTenantCapabilityStore } from '@/store/tenantCapabilityStore'
import { Local } from '@/utils/storage'

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

// 定义变量
const orderCountArray = ref<Record<string, number>>({})
const userStore = useUserStore()
const myOrder = ref<MyOrder[]>([
  {
    icon: 'i-carbon:wallet',
    name: '待付款',
    status: '1',
  },
  {
    icon: 'i-carbon:package-node',
    name: '待发货',
    status: '2',
  },
  {
    icon: 'i-carbon:delivery-truck',
    name: '待收货',
    status: '3',
  },
  {
    icon: 'i-carbon:chat',
    name: '待评价',
    status: '4',
  },
  {
    icon: 'i-carbon:right-panel-open',
    name: '退款/售后',
    status: '5',
  },
])
/**
 * 服务宫格：8 项按 4 列铺满两行。
 * 消息中心排首位，未读角标是这里的首要信息；顺带把「5+3」的断行补齐成整行，
 * 末行只剩 3 项会在视觉上留下一个豁口。
 */
const myService = ref<MyService[]>([
  {
    icon: 'i-carbon:notification-new',
    name: '消息中心',
    url: '/sub-pages/message/notice/index',
  },
  {
    icon: 'i-carbon:user-profile',
    name: '会员中心',
    url: '/sub-pages/user/member/index',
  },
  {
    icon: 'i-carbon:location',
    name: '收货地址',
    url: '/sub-pages/user/address/index',
  },
  {
    icon: 'i-carbon:edit',
    name: '我的评价',
    url: '/sub-pages/user/appraise/index',
  },
  {
    icon: 'i-carbon:time',
    name: '浏览记录',
    url: '/sub-pages/user/footprint/index',
  },
  {
    icon: 'i-carbon:star',
    name: '我的收藏',
    url: '/sub-pages/user/collect/index',
  },
  {
    icon: 'i-carbon:ticket',
    name: '我的优惠券',
    url: '/sub-pages/promotion/coupon/coupon-user/index',
  },
  {
    icon: 'i-carbon:share-knowledge',
    name: '分销中心',
    url: '/sub-pages/user/distribution/index',
  },
])

const router = useRouter()
const authStore = useAuthStore()
const messageStore = useMessageStore()
const { show: showToast } = useGlobalToast()

// 个人中心页装修（pageType=4）：用户信息卡下方、订单入口上方，可放会员活动 / 优惠券 / 公告
const { pageContentData, loading: decorationLoading, fetch: fetchDecoration } = usePageDecoration('4')

// ===================== 资产条 =====================

/**
 * 页头资产条：优惠券 / 积分 / 余额。
 *
 * 三者都是 userStore 里已有的数据（登录后由 fetchUserInfo 拉取并持久化），
 * 这里只是把它们从「藏在会员中心二级页」提到一屏可见的位置——
 * 原来页头只有一个「优惠券 0」灰胶囊，既看不出信息层级，也漏掉了积分与余额。
 */
const assetCoupon = computed(() => userStore.getCouponCount ?? 0)
const assetPoint = computed(() => userStore.getPoint ?? 0)
const assetBalance = computed(() => userStore.getBalance ?? 0)

// ===================== 船舶入口 =====================

/**
 * 船舶入口只在「已登录 + 租户开放船供能力」时出现。
 *
 * 首页状态条只对有船用户展示靠港信息，未绑定用户完全不显示，
 * 因此绑定入口必须在这里给出，否则新用户会再次陷入「知道缺什么却无处可去」。
 */
const tenantCapabilityStore = useTenantCapabilityStore()
const vesselBound = ref(false)
const vesselName = ref('')

const vesselEntranceVisible = computed(() => {
  // resolved 之前不渲染：否则纯零售租户会先闪出一个船供入口再收起
  if (!tenantCapabilityStore.resolved)
    return false
  if (!authStore.isLoggedIn)
    return false
  return tenantCapabilityStore.shipSupplyEnabled
})

const vesselEntranceSubtitle = computed(() =>
  vesselBound.value
    ? `${vesselName.value || '已关联船舶'} · 查看靠港与成员`
    : '未关联船舶，加入后可用靠港配送',
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

onShow(() => {
  fetchDecoration()
  if (authStore.isLoggedIn) {
    getUserOrderCount()
    void messageStore.refreshUnread()
    messageStore.connect()
    loadDeliveryEligibility()
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
  }
  else {
    deliveryEligibility.value = null
    hasDeliveryToken.value = false
    vesselBound.value = false
    vesselName.value = ''
  }
})

// ===================== 配送工作台入口 =====================

/** 配送资格（服务端判断，资格请求完成前不渲染入口，避免先显示后隐藏跳动） */
const deliveryEligibility = ref<DeliveryEligibility | null>(null)
/** 本地是否已有配送员 token（仅作减少换取次数的提示，不作为授权依据） */
const hasDeliveryToken = ref(false)
/** 换取/探活请求进行中，防止连续点击重复换取 */
const deliveryEntering = ref(false)

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
      ? `${eligibility.pendingTaskCount} 项任务待处理`
      : '已为你开通配送权限'
  }
  if (eligibility?.status === 'ACCOUNT_DISABLED') {
    return '配送账号已停用'
  }
  return '进入配送工作台'
})

/** 入口角标：待处理任务数（最多显示 99+） */
const deliveryEntranceBadge = computed(() =>
  formatBadge(deliveryEligibility.value?.pendingTaskCount ?? 0),
)

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
    showToast('暂时无法进入配送工作台，请稍后重试')
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
      showToast('配送账号已停用，请联系管理员')
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
          showToast('应用配置暂不可用，请稍后重试')
          return
        }
        const code = error?.code
        if (code !== 401) {
          showToast('暂时无法进入配送工作台，请稍后重试')
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
</script>

<template>
  <hr-navbar
    :left-arrow="false"
    title="个人中心"
    :bordered="false"
    :background-color="BRAND_COLOR"
    text-color="#ffffff"
  />
  <!-- 页头：品牌渐变铺底，右上角齿轮是设置入口 -->
  <view class="uc-hero">
    <view class="uc-hero__profile" @click="toLogin">
      <image
        class="uc-hero__avatar"
        :src="userStore.getUserAvatar || '/static/default-avatar.png'"
        mode="aspectFill"
      />
      <view class="uc-hero__meta">
        <view class="uc-hero__name-row">
          <text class="uc-hero__name">
            {{ userStore.getUserNickname || "登录/注册" }}
          </text>
          <text
            v-if="authStore.isLoggedIn && userStore.getLevelName"
            class="uc-hero__level"
          >
            {{ userStore.getLevelName }}
          </text>
        </view>
        <text v-if="!authStore.isLoggedIn" class="uc-hero__hint">
          登录后同步订单、优惠券与积分
        </text>
      </view>
    </view>
    <view
      class="uc-hero__setting"
      hover-class="uc-hero__setting--pressed"
      @click="toJumpUrl('/sub-pages/user/user-setting/index')"
    >
      <text class="i-carbon:settings uc-hero__setting-icon" />
    </view>
  </view>
  <!-- 资产条：上移压在渐变收口处，把优惠券/积分/余额提到首屏 -->
  <view
    v-if="authStore.isLoggedIn"
    class="uc-assets"
  >
    <view
      class="uc-assets__item"
      hover-class="uc-assets__item--pressed"
      @click="toRoute('/sub-pages/promotion/coupon/coupon-user/index')"
    >
      <text class="uc-assets__value">
        {{ assetCoupon }}
      </text>
      <text class="uc-assets__label">
        优惠券
      </text>
    </view>
    <view
      class="uc-assets__item"
      hover-class="uc-assets__item--pressed"
      @click="toRoute('/sub-pages/user/member/points-record')"
    >
      <text class="uc-assets__value">
        {{ assetPoint }}
      </text>
      <text class="uc-assets__label">
        积分
      </text>
    </view>
    <view
      class="uc-assets__item"
      hover-class="uc-assets__item--pressed"
      @click="toRoute('/sub-pages/user/member/balance-record')"
    >
      <text class="uc-assets__value">
        {{ assetBalance }}
      </text>
      <text class="uc-assets__label">
        余额
      </text>
    </view>
  </view>
  <!-- 未登录没有可展示的资产，换成一条登录引导，不摆三个 0 -->
  <view
    v-else
    class="uc-assets uc-assets--guest"
    hover-class="uc-assets__item--pressed"
    @click="toLogin"
  >
    <text class="uc-assets__guest-text">
      登录后查看优惠券、积分与余额
    </text>
    <text class="i-carbon:chevron-right uc-assets__guest-arrow" />
  </view>
  <!-- 个人中心装修区域：用户信息卡下方、订单入口上方，可放会员活动 / 优惠券 / 公告 -->
  <view v-if="pageContentData" class="uc-decoration">
    <DiyPage :page-content-data="pageContentData" embedded />
  </view>
  <view v-else-if="decorationLoading" class="uc-decoration-skeleton" />
  <!-- 我的订单 -->
  <view class="uc-card">
    <view class="uc-card__head">
      <text class="uc-card__title">
        我的订单
      </text>
      <view
        class="uc-card__more"
        hover-class="uc-card__more--pressed"
        @click="toOrder('')"
      >
        <text>查看全部</text>
        <text class="i-carbon:chevron-right uc-card__more-arrow" />
      </view>
    </view>
    <view class="uc-grid uc-grid--5">
      <view
        v-for="item in myOrder"
        :key="item.status"
        class="uc-grid__item"
        hover-class="uc-grid__item--pressed"
        @click="toOrder(item.status)"
      >
        <view class="uc-grid__icon">
          <text :class="item.icon" class="uc-grid__glyph" />
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
  <!-- 我的服务 -->
  <view class="uc-card">
    <view class="uc-card__head">
      <text class="uc-card__title">
        我的服务
      </text>
    </view>
    <view class="uc-grid uc-grid--4">
      <view
        v-for="item in myService"
        :key="item.name"
        class="uc-grid__item"
        hover-class="uc-grid__item--pressed"
        @click="toRoute(item.url)"
      >
        <view class="uc-grid__icon">
          <text :class="item.icon" class="uc-grid__glyph" />
          <text
            v-if="item.name === '消息中心' && formatBadge(messageStore.totalUnread)"
            class="uc-grid__badge"
          >
            {{ formatBadge(messageStore.totalUnread) }}
          </text>
        </view>
        <text class="uc-grid__label">
          {{ item.name }}
        </text>
      </view>
    </view>
  </view>
  <!-- 配送工作台入口：仅已登录且具备配送资格的用户可见 -->
  <view
    v-if="deliveryEntranceVisible"
    class="uc-entry"
    :class="{
      'uc-entry--muted': deliveryEligibility && !deliveryEligibility.eligible,
      'uc-entry--busy': deliveryEntering,
    }"
    hover-class="uc-entry--pressed"
    @click="enterDeliveryWorkspace"
  >
    <view class="uc-entry__icon">
      <text class="i-carbon:delivery-truck uc-entry__glyph" />
    </view>
    <view class="uc-entry__body">
      <text class="uc-entry__title">
        配送工作台
      </text>
      <text class="uc-entry__desc">
        {{ deliveryEntranceSubtitle }}
      </text>
    </view>
    <text v-if="deliveryEntranceBadge" class="uc-entry__count">
      {{ deliveryEntranceBadge }}
    </text>
    <text class="i-carbon:chevron-right uc-entry__arrow" />
  </view>
  <!-- 船舶入口：首页状态条只服务「有船用户」，未绑定用户的出路收进这里 -->
  <view
    v-if="vesselEntranceVisible"
    class="uc-entry"
    hover-class="uc-entry--pressed"
    @click="toRoute('/sub-pages/vessel/bind/index')"
  >
    <view class="uc-entry__icon">
      <text class="i-carbon:sailboat-coastal uc-entry__glyph" />
    </view>
    <view class="uc-entry__body">
      <text class="uc-entry__title">
        我的船舶
      </text>
      <text class="uc-entry__desc">
        {{ vesselEntranceSubtitle }}
      </text>
    </view>
    <text class="i-carbon:chevron-right uc-entry__arrow" />
  </view>
  <view class="uc-like">
    <view class="uc-like__rule" />
    <text class="uc-like__text">
      猜你喜欢
    </text>
    <view class="uc-like__rule" />
  </view>
  <WaterfallGoods />
</template>

<style lang="scss" scoped>
/* 文字色阶与卡片口径沿用全站约定：标题 #1f2329 / 正文 #4e5969 / 辅助 #9ca3af */
$uc-title: #1f2329;
$uc-text: #4e5969;
$uc-muted: #9ca3af;
$uc-hairline: #eef0f3;
/* 卡片左右留白与全站 mx-20rpx 对齐 */
$uc-gutter: 20rpx;

.uc-decoration {
  /* 装修块自带底色，这里不再叠加第四种灰（全站已用 #f5f5f5/#f2f3f5/#F8F8F8） */
  min-height: 0;
}

.uc-decoration-skeleton {
  height: 200rpx;
  margin: 20rpx $uc-gutter;
  border-radius: 24rpx;
  background: #ececf0;
}

/* ===================== 页头 ===================== */

.uc-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  /* 底部多留一截给资产条上移压盖，渐变收口才落在卡片背后而不是露出一条灰边 */
  padding: 24rpx $uc-gutter 104rpx;
  background: linear-gradient(180deg, var(--theme-color-primary, var(--wot-color-theme-primary, #FF2237)) 0%, var(--theme-color-secondary, var(--wot-color-theme-secondary, #FF6B7A)) 100%);

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

  &__hint {
    margin-top: 10rpx;
    color: rgba(255, 255, 255, 0.82);
    font-size: 24rpx;
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
  display: flex;
  align-items: stretch;
  margin: -72rpx $uc-gutter 0;
  padding: 28rpx 0;
  border-radius: 24rpx;
  background: #fff;
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

  &--guest {
    align-items: center;
    justify-content: space-between;
    padding: 36rpx 32rpx;
  }

  &__guest-text {
    color: $uc-text;
    font-size: 26rpx;
  }

  &__guest-arrow {
    color: $uc-muted;
    font-size: 32rpx;
  }
}

/* ===================== 卡片与宫格 ===================== */

.uc-card {
  margin: 20rpx $uc-gutter 0;
  padding: 8rpx 20rpx 4rpx;
  border-radius: 24rpx;
  background: #fff;
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
    background: #f5f6f8;
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

/* ===================== 横幅入口（配送工作台 / 我的船舶） ===================== */

.uc-entry {
  display: flex;
  align-items: center;
  margin: 20rpx $uc-gutter 0;
  padding: 28rpx 32rpx;
  border-radius: 24rpx;
  background: #fff;
  box-shadow: 0 8rpx 24rpx rgba(31, 44, 65, 0.04);

  &--pressed {
    background: #fafbfc;
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
    background: #f5f6f8;
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
    background: #e4e6eb;
  }

  &__text {
    margin: 0 24rpx;
    color: $uc-title;
    font-size: 28rpx;
    font-weight: 700;
  }
}
</style>
