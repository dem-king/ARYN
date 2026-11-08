<script setup lang="ts">
/**
 * 导入清单下单（向导入口，直下单模式）。
 *
 * 场景：管事/大副或岸上企业客户线下已统计好采购清单（Excel），
 * 要的是「传文件 → 核对 → 下单」，不创建也不感知共享购物车这张单据。
 * 向导只负责收集**配送目标**（船供=靠港 / 岸上=收货地址），随后进导入报告页
 * 上传核对；确认后复核清单经 directImportOrderStore 交给结算页，
 * 走既有下单链路直接生成订单（createWay=2）。
 *
 * 2026-11-07 重做：不再创建/复用共享购物车（此前「创建并导入 Excel」会先建车）。
 * 补给单多人收集流程仍在：列表页发起 → 详情页「导入 Excel」。
 *
 * 2026-11-01 视觉重构：全站冻结的「A. 现代简约」语言（白卡 + 发丝线 + 轻投影 +
 * 中性灰 + 品牌色点状强调）；直接进入本页时先装载船舶上下文，有船/有靠港的
 * 用户不再被要求「再选一次」。
 */
import { onPageScroll, onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getPage as getAddressPage } from '@/api/user/address'

import hrNavbar from '@/components/hr-navbar/index.vue'
import ShipContextPicker from '@/components/ship-context-picker/index.vue'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { useRequireLogin } from '@/composables/useRequireLogin'
import { useShipContextLoad } from '@/composables/useShipContextLoad'
import { useAuthStore } from '@/store/authStore'
import { useDirectImportOrderStore } from '@/store/directImportOrderStore'
import { PURCHASE_SCENE_ENTERPRISE, PURCHASE_SCENE_SHIP_SUPPLY, useShipContextStore } from '@/store/shipContextStore'
import { useTenantCapabilityStore } from '@/store/tenantCapabilityStore'

const { handlePageScroll } = useFrostedNavbar()

definePage({
  name: 'shared-cart-import-entry',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '导入清单下单',
  },
})

const authStore = useAuthStore()
const shipContextStore = useShipContextStore()
const directImportOrderStore = useDirectImportOrderStore()
const tenantCapabilityStore = useTenantCapabilityStore()
const { t } = useI18n()
const { load: loadShipContext } = useShipContextLoad()

const resolving = ref(true)
/** 纯零售租户：船供能力关闭，向导整体不可用 */
const unsupported = ref(false)

/** 岸上收货地址（地址簿行；字段口径与列表页发起弹层一致） */
interface ShoreAddress {
  id: string
  recipientName?: string
  telephone?: string
  provinceName?: string
  cityName?: string
  areaName?: string
  detailAddress?: string
  isDefault?: string
}

/** 配送目标：ship = 送本次靠港（船供采购）；shore = 送收货地址（岸上企业采购） */
type ImportTargetMode = 'ship' | 'shore'

/** 向导表单：只收集配送目标与备注，清单在下一步上传 */
const createState = ref({
  mode: 'ship' as ImportTargetMode,
  remark: '',
  /** 岸上：已选收货地址ID */
  addressId: '',
  /** 岸上：地址簿候选 */
  addresses: [] as ShoreAddress[],
  addressLoading: false,
})
const shipPickerVisible = ref(false)
const addressSheetVisible = ref(false)
const remarkFocused = ref(false)
/** 默认模式只在首次装载时定（有船走船供、没船走岸上），之后不覆盖用户手选 */
let modeInitialized = false
/** 从地址表单返回本页时重拉地址簿（新增地址后行内即时回显） */
let returningFromAddressForm = false

/** 船舶与靠港都已就绪（船供目标可发） */
const vesselReady = computed(() => shipContextStore.hasVesselContext)
/** 有船但还没落到可用靠港：CTA / 选择行都引导去补靠港 */
const hasVessel = computed(() => !!shipContextStore.vesselId)

const vesselTitle = computed(() =>
  shipContextStore.vesselName || t('sharedCart.importEntry.chooseVesselCall'))

const vesselSubtitle = computed(() => {
  if (vesselReady.value) {
    const place = [shipContextStore.portName, shipContextStore.berth]
      .filter(Boolean)
      .join(' · ')
    return place || t('sharedCart.common.portPending')
  }
  if (hasVessel.value)
    return t('sharedCart.importEntry.noCallHint')
  return t('sharedCart.importEntry.noVesselHint')
})

/** 后端返回 yyyy-MM-dd HH:mm:ss，这里压成「MM-dd HH:mm」，避免一整串时间把选择行撑爆 */
function shortDateTime(value?: string) {
  if (!value)
    return ''
  const match = value.match(/^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/)
  if (!match)
    return value
  return `${match[2]}-${match[3]} ${match[4]}:${match[5]}`
}

/** 配送时间窗只在上下文就绪时出现：它是「送到哪一段」的补充，不参与选船决策 */
const deliveryWindowText = computed(() => {
  if (!vesselReady.value)
    return ''
  const start = shortDateTime(shipContextStore.deliveryWindowStart)
  const end = shortDateTime(shipContextStore.deliveryWindowEnd)
  if (!start && !end)
    return ''
  if (!start || !end)
    return start || end
  // 同一天的窗口只写一次日期，避免窄屏折行成「11-05 09:00 - 11-05 / 17:00」
  const [startDate] = start.split(' ')
  const [endDate, endTime] = end.split(' ')
  if (startDate === endDate && endTime)
    return `${start} - ${endTime}`
  return `${start} - ${end}`
})

const vesselTagText = computed(() => {
  if (vesselReady.value)
    return t('sharedCart.importEntry.vesselReady')
  if (hasVessel.value)
    return t('sharedCart.importEntry.vesselCallPending')
  return t('sharedCart.importEntry.vesselPending')
})

/** 当前补给目标是否为岸上企业（按收货地址发起） */
const isShoreMode = computed(() => createState.value.mode === 'shore')

/** 上次导入仍可继续（待确认或已确认），且交接未过期 */
const hasResumableImport = computed(() =>
  directImportOrderStore.active
  && !!directImportOrderStore.importId
  && !directImportOrderStore.isExpired,
)

/** 回到上次导入：已确认任务在报告页可直接「继续去结算」 */
function resumeLastImport() {
  const handoffId = directImportOrderStore.handoffId
  if (!directImportOrderStore.matches(handoffId)) {
    uni.showToast({ title: t('sharedCart.importPage.handoffExpired'), icon: 'none' })
    return
  }
  uni.navigateTo({
    url: `/sub-pages/order/shared-cart/import?mode=direct&handoff=${encodeURIComponent(handoffId)}`,
  })
}

/** 卡片副标题按模式切换：挂靠港补给单 vs 按地址发起的采购单 */
const sectionDescText = computed(() =>
  isShoreMode.value
    ? t('sharedCart.importEntry.sectionDescShore')
    : t('sharedCart.importEntry.sectionDesc'))

const selectedAddress = computed(() =>
  createState.value.addresses.find(item => item.id === createState.value.addressId) ?? null)

/** 岸上行展示：省市区+详址拼一行，与地址快照口径一致 */
const addressLineText = computed(() => {
  const addr = selectedAddress.value
  if (!addr)
    return ''
  return [addr.provinceName, addr.cityName, addr.areaName, addr.detailAddress]
    .filter(Boolean)
    .join('')
})

const addressTitle = computed(() =>
  selectedAddress.value?.recipientName || t('sharedCart.importEntry.chooseAddress'))

const addressTagText = computed(() =>
  selectedAddress.value
    ? t('sharedCart.importEntry.vesselReady')
    : t('sharedCart.importEntry.vesselPending'))

const remarkLength = computed(() => createState.value.remark.length)

/** 说明卡三步：与导入页的真实规则同源（格式/行数上限、匹配口径、核对后并入） */
const guideSteps = computed(() => [
  {
    title: t('sharedCart.importEntry.guideUploadTitle'),
    desc: t('sharedCart.importEntry.guideUploadDesc'),
  },
  {
    title: t('sharedCart.importEntry.guideMatchTitle'),
    desc: t('sharedCart.importEntry.guideMatchDesc'),
  },
  {
    title: t('sharedCart.importEntry.guideReviewTitle'),
    desc: t('sharedCart.importEntry.guideReviewDesc'),
  },
])

// 统一登录守卫：记录当前页回跳（登录后回到向导），不再裸跳登录页丢来源
const { goLogin } = useRequireLogin()

function goCartList() {
  uni.navigateTo({ url: '/sub-pages/order/shared-cart/list' })
}

/** 进入直下单导入页：handoff 随 URL 传递，报告页确认后结算页只认这一次交接 */
function goDirectImport(handoffId: string) {
  uni.navigateTo({
    url: `/sub-pages/order/shared-cart/import?mode=direct&handoff=${encodeURIComponent(handoffId)}`,
  })
}

function openShipPicker() {
  shipPickerVisible.value = true
}

/** 拉地址簿候选并预选默认地址（岸上模式；口径与列表页发起弹层一致） */
function loadShoreAddresses() {
  createState.value.addressLoading = true
  getAddressPage({ current: 1, size: 50 })
    .then((res) => {
      const list: ShoreAddress[] = res?.records ?? []
      createState.value.addresses = list
      // 已选地址仍有效则保留；否则回落默认地址，再回落第一条
      const stillValid = list.some(item => item.id === createState.value.addressId)
      if (!stillValid) {
        const preferred = list.find(item => item.isDefault === '1') ?? list[0]
        createState.value.addressId = preferred?.id ?? ''
      }
    })
    .catch(() => {
      // 拉失败不清空已有候选（重开弹层时还能用上一次的），只保留当前选择
    })
    .finally(() => {
      createState.value.addressLoading = false
    })
}

/** 切换补给目标：岸上懒加载地址；船供缺上下文时直接弹选择器 */
function switchMode(mode: ImportTargetMode) {
  if (createState.value.mode === mode)
    return
  createState.value.mode = mode
  modeInitialized = true
  if (mode === 'shore' && createState.value.addresses.length === 0)
    loadShoreAddresses()
  if (mode === 'ship' && !shipContextStore.hasVesselContext)
    shipPickerVisible.value = true
}

function openAddressSheet() {
  if (createState.value.addresses.length === 0)
    loadShoreAddresses()
  addressSheetVisible.value = true
}

function chooseAddress(id: string) {
  createState.value.addressId = id
  addressSheetVisible.value = false
}

function toAddressForm() {
  returningFromAddressForm = true
  uni.navigateTo({ url: '/sub-pages/user/address/form' })
}

function clearRemark() {
  createState.value.remark = ''
}

async function resolveContext() {
  if (!authStore.isLoggedIn) {
    goLogin()
    return
  }
  resolving.value = true
  await tenantCapabilityStore.ensureLoaded()
  if (!tenantCapabilityStore.shipSupplyEnabled) {
    unsupported.value = true
    resolving.value = false
    return
  }
  // 本页可能被直接打开（深链 / 登录回跳 / 首页卡片），store 冷启动是空的；
  // 先按用户真实船舶/靠港装载，避免明明有船却让用户再选一次。
  await loadShipContext().catch(() => {})
  // 默认配送目标只定一次：有船走船供，没船就地走收货地址
  if (!modeInitialized) {
    createState.value.mode = shipContextStore.vesselId ? 'ship' : 'shore'
    modeInitialized = true
    if (createState.value.mode === 'shore' && createState.value.addresses.length === 0)
      loadShoreAddresses()
  }
  resolving.value = false
}

function submitCreate() {
  if (isShoreMode.value) {
    submitShoreImport()
    return
  }
  submitShipImport()
}

/** 船供：配送目标是本次靠港；上下文缺失时先补齐，而不是一句无法执行的提示 */
function submitShipImport() {
  // 未绑船不再踢回列表页：就地切到收货地址模式，同一条导入链路照样走通
  if (!shipContextStore.vesselId) {
    switchMode('shore')
    uni.showToast({ title: t('sharedCart.importEntry.toastSwitchShore'), icon: 'none' })
    return
  }
  if (!shipContextStore.vesselCallId) {
    shipPickerVisible.value = true
    return
  }
  // 靠港与备注写入交接；结算页按 handoff 锁定 way=4/scene=2。
  // 必须把 vesselId/vesselCallId 一起存下：shipContextStore 不持久化，
  // H5 刷新结算页后仍要能恢复配送目标，不能只依赖全局船舶上下文。
  directImportOrderStore.begin({
    scene: PURCHASE_SCENE_SHIP_SUPPLY,
    remark: createState.value.remark || undefined,
    vesselId: shipContextStore.vesselId,
    vesselCallId: shipContextStore.vesselCallId,
    vesselName: shipContextStore.vesselName,
    portName: shipContextStore.portName,
    berth: shipContextStore.berth,
  })
  goDirectImport(directImportOrderStore.handoffId)
}

/** 岸上：收货地址是配送终点，必选；归属与配送范围由结算/下单服务端校验 */
function submitShoreImport() {
  if (!createState.value.addressId) {
    uni.showToast({ title: t('sharedCart.list.addressRequired'), icon: 'none' })
    openAddressSheet()
    return
  }
  // 地址随 handoff 带给结算页预选；结算页按场景锁定 way=3/scene=3
  directImportOrderStore.begin({
    scene: PURCHASE_SCENE_ENTERPRISE,
    userAddressId: createState.value.addressId,
    remark: createState.value.remark || undefined,
  })
  goDirectImport(directImportOrderStore.handoffId)
}

onShow(() => {
  // 从地址表单返回：重拉地址簿，新增地址后行内即时回显
  if (returningFromAddressForm) {
    returningFromAddressForm = false
    loadShoreAddresses()
  }
  void resolveContext()
})

// 顶栏磨砂玻璃：字面量注册滚动（小程序按页面源码生成滚动位标记，包进 composable 会静默失效）
onPageScroll(handlePageScroll)
</script>

<template>
  <view class="import-entry">
    <hr-navbar :title="t('sharedCart.importEntry.navTitle')" />

    <!-- 解析中：骨架屏，别用一行「加载中」把整页留白撑给用户看 -->
    <view v-if="resolving" class="ie-loading">
      <view class="ie-skeleton ie-skeleton--steps">
        <view class="ie-skeleton__block ie-skeleton__block--dot" />
        <view class="ie-skeleton__block ie-skeleton__block--line" />
        <view class="ie-skeleton__block ie-skeleton__block--dot" />
        <view class="ie-skeleton__block ie-skeleton__block--line" />
      </view>
      <view class="ie-skeleton ie-skeleton--card">
        <view class="ie-skeleton__head">
          <view class="ie-skeleton__block ie-skeleton__block--icon" />
          <view class="ie-skeleton__head-lines">
            <view class="ie-skeleton__block ie-skeleton__block--title" />
            <view class="ie-skeleton__block ie-skeleton__block--sub" />
          </view>
        </view>
        <view class="ie-skeleton__block ie-skeleton__block--select" />
        <view class="ie-skeleton__block ie-skeleton__block--field" />
      </view>
      <view class="ie-skeleton ie-skeleton--card">
        <view class="ie-skeleton__block ie-skeleton__block--title" />
        <view class="ie-skeleton__block ie-skeleton__block--field" />
      </view>
    </view>

    <!-- 纯零售租户：给出说明与唯一事实，不留空白页 -->
    <view v-else-if="unsupported" class="ie-empty">
      <view class="ie-empty__icon">
        <text class="i-carbon:store ie-empty__glyph" />
      </view>
      <text class="ie-empty__title">
        {{ t('sharedCart.importEntry.unsupportedTitle') }}
      </text>
      <text class="ie-empty__desc">
        {{ t('sharedCart.importEntry.unsupported') }}
      </text>
    </view>

    <template v-else>
      <view class="ie-body">
        <!-- 流程指示：让用户一眼知道现在第几步、下一步做什么 -->
        <view class="ie-steps">
          <view class="ie-step ie-step--active">
            <text class="ie-step__num">
              1
            </text>
            <text class="ie-step__label">
              {{ t('sharedCart.importEntry.stepInfo') }}
            </text>
          </view>
          <view class="ie-step__line" />
          <view class="ie-step">
            <text class="ie-step__num">
              2
            </text>
            <text class="ie-step__label">
              {{ t('sharedCart.importEntry.stepUpload') }}
            </text>
          </view>
        </view>

        <!-- 本次补给信息：补给目标（靠港/收货地址）+ 备注，创建采购单的唯二输入 -->
        <view class="ie-card">
          <view class="ie-card__head">
            <view class="ie-card__icon ie-card__icon--brand">
              <text class="i-carbon:list-checked ie-card__glyph" />
            </view>
            <view class="ie-card__heading">
              <text class="ie-card__title">
                {{ t('sharedCart.importEntry.sectionTitle') }}
              </text>
              <text class="ie-card__desc">
                {{ sectionDescText }}
              </text>
            </view>
          </view>

          <!-- 补给目标：靠港（船供整车）或收货地址（岸上企业），二选一 -->
          <view class="ie-mode">
            <view
              class="ie-mode__item"
              :class="{ 'ie-mode__item--on': !isShoreMode }"
              hover-class="ie-mode__item--pressed"
              :hover-stay-time="80"
              @tap="switchMode('ship')"
            >
              <text class="ie-mode__title">
                {{ t('sharedCart.list.typeShip') }}
              </text>
              <text class="ie-mode__desc">
                {{ t('sharedCart.list.typeShipDesc') }}
              </text>
            </view>
            <view
              class="ie-mode__item"
              :class="{ 'ie-mode__item--on': isShoreMode }"
              hover-class="ie-mode__item--pressed"
              :hover-stay-time="80"
              @tap="switchMode('shore')"
            >
              <text class="ie-mode__title">
                {{ t('sharedCart.list.typeShore') }}
              </text>
              <text class="ie-mode__desc">
                {{ t('sharedCart.list.typeShoreDesc') }}
              </text>
            </view>
          </view>

          <!-- 船供：船舶与靠港，整行可点打开选择器；已选/待选一眼可辨 -->
          <template v-if="!isShoreMode">
            <view
              class="ie-vessel"
              hover-class="ie-vessel--pressed"
              :hover-stay-time="80"
              @tap="openShipPicker"
            >
              <view class="ie-vessel__icon">
                <text class="i-carbon:location ie-vessel__glyph" />
              </view>
              <view class="ie-vessel__body">
                <view class="ie-vessel__title-row">
                  <text class="ie-vessel__title">
                    {{ vesselTitle }}
                  </text>
                  <view class="ie-vessel__tag" :class="{ 'ie-vessel__tag--ready': vesselReady }">
                    <view class="ie-vessel__dot" />
                    <text>{{ vesselTagText }}</text>
                  </view>
                </view>
                <text class="ie-vessel__sub">
                  {{ vesselSubtitle }}
                </text>
                <view v-if="deliveryWindowText" class="ie-vessel__meta">
                  <text class="i-carbon:time ie-vessel__meta-icon" />
                  <text>{{ t('sharedCart.importEntry.deliveryWindow') }} {{ deliveryWindowText }}</text>
                </view>
              </view>
              <text class="i-carbon:chevron-right ie-vessel__chevron" />
            </view>

            <view v-if="!vesselReady" class="ie-vessel-hint">
              <text class="i-carbon:information ie-vessel-hint__icon" />
              <text class="ie-vessel-hint__text">
                {{ t('sharedCart.importEntry.vesselRequiredHint') }}
              </text>
            </view>
          </template>

          <!-- 岸上：收货地址行，整行可点打开地址弹层（配送终点与范围校验都以所选地址为准） -->
          <template v-else>
            <view
              class="ie-vessel"
              hover-class="ie-vessel--pressed"
              :hover-stay-time="80"
              @tap="openAddressSheet"
            >
              <view class="ie-vessel__icon">
                <text class="i-carbon:location ie-vessel__glyph" />
              </view>
              <view class="ie-vessel__body">
                <view class="ie-vessel__title-row">
                  <text class="ie-vessel__title">
                    {{ addressTitle }}
                  </text>
                  <view class="ie-vessel__tag" :class="{ 'ie-vessel__tag--ready': !!selectedAddress }">
                    <view class="ie-vessel__dot" />
                    <text>{{ addressTagText }}</text>
                  </view>
                </view>
                <text v-if="addressLineText" class="ie-vessel__sub">
                  {{ addressLineText }}
                </text>
              </view>
              <text class="i-carbon:chevron-right ie-vessel__chevron" />
            </view>

            <view v-if="!selectedAddress" class="ie-vessel-hint">
              <text class="i-carbon:information ie-vessel-hint__icon" />
              <text class="ie-vessel-hint__text">
                {{ t('sharedCart.importEntry.shoreRequiredHint') }}
              </text>
            </view>
          </template>

          <!-- 备注：标签 + 计数 + 清除态；聚焦时描边点亮，标明这是可输入区 -->
          <view class="ie-remark">
            <view class="ie-remark__head">
              <text class="ie-remark__label">
                {{ t('sharedCart.importEntry.remarkLabel') }}
              </text>
              <text class="ie-remark__optional">
                {{ t('sharedCart.importEntry.remarkOptional') }}
              </text>
              <text class="ie-remark__count">
                {{ remarkLength }}/120
              </text>
            </view>
            <view class="ie-remark__field" :class="{ 'ie-remark__field--focus': remarkFocused }">
              <text class="i-carbon:edit ie-remark__icon" />
              <input
                v-model="createState.remark"
                class="ie-remark__input"
                :placeholder="t('sharedCart.importEntry.remarkPlaceholder')"
                placeholder-class="ie-remark__placeholder"
                :maxlength="120"
                confirm-type="done"
                @focus="remarkFocused = true"
                @blur="remarkFocused = false"
              >
              <view
                v-if="remarkLength > 0"
                class="ie-remark__clear"
                hover-class="ie-remark__clear--pressed"
                :aria-label="t('sharedCart.importEntry.remarkClear')"
                @tap.stop="clearRemark"
              >
                <text class="i-carbon:close-filled ie-remark__clear-icon" />
              </view>
            </view>
          </view>
        </view>

        <!-- 导入说明：24h 有效期 + 三步流程，回答「点了之后会发生什么」 -->
        <view class="ie-card ie-card--guide">
          <view class="ie-card__head">
            <view class="ie-card__icon ie-card__icon--muted">
              <text class="i-carbon:document ie-card__glyph" />
            </view>
            <view class="ie-card__heading">
              <text class="ie-card__title">
                {{ t('sharedCart.importEntry.guideTitle') }}
              </text>
            </view>
          </view>

          <view class="ie-notice">
            <text class="i-carbon:time ie-notice__icon" />
            <text class="ie-notice__text">
              {{ t('sharedCart.importEntry.guideNotice') }}
            </text>
          </view>

          <view
            v-for="(step, index) in guideSteps"
            :key="index"
            class="ie-guide-step"
          >
            <text class="ie-guide-step__num">
              {{ index + 1 }}
            </text>
            <view class="ie-guide-step__body">
              <text class="ie-guide-step__title">
                {{ step.title }}
              </text>
              <text class="ie-guide-step__desc">
                {{ step.desc }}
              </text>
            </view>
          </view>
        </view>

        <!-- 上次导入可继续：待确认回报告页，已确认可直接恢复结算 -->
        <view
          v-if="hasResumableImport"
          class="ie-all-link ie-all-link--resume"
          hover-class="ie-all-link--pressed"
          :hover-stay-time="80"
          @tap="resumeLastImport"
        >
          <text>{{ t('sharedCart.importEntry.continueLastImport') }}</text>
          <text class="i-carbon:chevron-right ie-all-link__icon" />
        </view>

        <!-- 次级出口：给已经了解规则、想先看单据的用户 -->
        <view
          class="ie-all-link"
          hover-class="ie-all-link--pressed"
          :hover-stay-time="80"
          @tap="goCartList"
        >
          <text>{{ t('sharedCart.importEntry.viewAllCarts') }}</text>
          <text class="i-carbon:chevron-right ie-all-link__icon" />
        </view>
      </view>

      <view class="ie-footer-spacer" />

      <!-- 吸底主 CTA：全站同款品牌渐变，视觉高 88rpx（44px），按压有反馈 -->
      <view class="ie-footer">
        <view
          class="ie-footer__primary"
          hover-class="ie-footer__primary--pressed"
          :hover-stay-time="80"
          @tap="submitCreate"
        >
          <text class="i-carbon:upload ie-footer__icon" />
          <text class="ie-footer__label">
            {{ t('sharedCart.importEntry.startImport') }}
          </text>
        </view>
      </view>
    </template>

    <ShipContextPicker v-model="shipPickerVisible" />

    <!-- 收货地址弹层：点行即选中并收起，新增地址跳地址表单 -->
    <wd-popup
      v-model="addressSheetVisible"
      position="bottom"
      :safe-area-inset-bottom="true"
      custom-style="border-radius: 24rpx 24rpx 0 0; overflow: hidden;"
    >
      <view class="ie-sheet">
        <text class="ie-sheet__title">
          {{ t('sharedCart.list.shippingAddress') }}
        </text>
        <view v-if="createState.addressLoading && createState.addresses.length === 0" class="ie-sheet__state">
          {{ t('sharedCart.list.addressLoading') }}
        </view>
        <view v-else-if="createState.addresses.length === 0" class="ie-sheet__state ie-sheet__state--empty">
          <text class="ie-sheet__state-text">
            {{ t('sharedCart.list.noAddress') }}
          </text>
          <view
            class="ie-sheet__add"
            hover-class="ie-sheet__add--pressed"
            :hover-stay-time="80"
            @tap="toAddressForm"
          >
            {{ t('sharedCart.list.addAddress') }}
          </view>
        </view>
        <scroll-view v-else scroll-y class="ie-sheet__list">
          <view
            v-for="item in createState.addresses"
            :key="item.id"
            class="ie-sheet__addr"
            :class="{ 'ie-sheet__addr--on': item.id === createState.addressId }"
            hover-class="ie-sheet__addr--pressed"
            :hover-stay-time="80"
            @tap="chooseAddress(item.id)"
          >
            <view class="ie-sheet__addr-body">
              <view class="ie-sheet__addr-line">
                <text class="ie-sheet__addr-name">
                  {{ item.recipientName }}
                </text>
                <text class="ie-sheet__addr-phone">
                  {{ item.telephone }}
                </text>
                <text v-if="item.isDefault === '1'" class="ie-sheet__addr-default">
                  {{ t('sharedCart.list.defaultAddress') }}
                </text>
              </view>
              <text class="ie-sheet__addr-detail">
                {{ item.provinceName }}{{ item.cityName }}{{ item.areaName }}{{ item.detailAddress }}
              </text>
            </view>
            <text
              class="i-carbon:checkmark-filled ie-sheet__addr-check"
              :class="{ 'ie-sheet__addr-check--on': item.id === createState.addressId }"
            />
          </view>
          <view
            class="ie-sheet__add"
            hover-class="ie-sheet__add--pressed"
            :hover-stay-time="80"
            @tap="toAddressForm"
          >
            {{ t('sharedCart.list.addAddressInline') }}
          </view>
        </scroll-view>
      </view>
    </wd-popup>
  </view>
</template>

<style lang="scss" scoped>
/**
 * 「A. 现代简约」令牌唯一出处。
 * 中性色全部走 --app-* 语义变量（深色模式自动跟随），品牌色走主题变量，
 * 换肤后不会出现「红配蓝」。换算：750rpx = 屏宽，1rpx = 0.5px@375。
 */
.import-entry {
  min-height: 100vh;
  background: var(--app-bg, #f6f7f9);
}

/* ------------------------------------------------------------------ 流程指示 */
.ie-body {
  padding: 20rpx 24rpx 0;
}

.ie-steps {
  display: flex;
  align-items: center;
  padding: 4rpx 8rpx 0;
}

.ie-step {
  display: flex;
  flex: none;
  align-items: center;
  gap: 12rpx;
}

.ie-step__num {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  background: var(--app-card-2, #f7f8fa);
  font-size: 24rpx;
  font-weight: 600;
  color: var(--app-text-2, #666);
}

.ie-step__label {
  font-size: 26rpx;
  color: var(--app-text-2, #666);
}

.ie-step--active .ie-step__num {
  background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff8a00), var(--wot-color-theme-primary, #ff4d2e));
  color: #fff;
}

.ie-step--active .ie-step__label {
  font-weight: 600;
  color: var(--app-text, #333);
}

.ie-step__line {
  flex: 1;
  height: 2rpx;
  margin: 0 20rpx;
  border-radius: 2rpx;
  background: var(--app-border, #ebeef0);
}

/* -------------------------------------------------------------------- 白卡 */
.ie-card {
  margin-top: 28rpx;
  padding: 28rpx;
  border: 1rpx solid var(--app-border, #ebeef0);
  border-radius: 28rpx;
  background: var(--app-card, #fff);
  box-shadow: 0 2rpx 12rpx rgb(23 32 51 / 5%);
}

.ie-card--guide {
  margin-top: 24rpx;
}

.ie-card__head {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
}

.ie-card__icon {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  border-radius: 22rpx;
}

.ie-card__icon--brand {
  background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff8a00), var(--wot-color-theme-primary, #ff4d2e));
  color: #fff;
}

.ie-card__icon--muted {
  background: var(--app-card-2, #f7f8fa);
  color: var(--wot-color-theme-primary, #ff4d2e);
}

.ie-card__glyph {
  font-size: 36rpx;
}

.ie-card__heading {
  flex: 1;
  min-width: 0;
}

.ie-card__title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  line-height: 1.35;
  color: var(--app-text, #333);
}

.ie-card__desc {
  display: block;
  margin-top: 6rpx;
  font-size: 26rpx;
  line-height: 1.5;
  color: var(--app-text-2, #666);
}

/* ------------------------------------------------------------ 补给目标切换 */
.ie-mode {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16rpx;
  margin-top: 24rpx;
}

.ie-mode__item {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  padding: 20rpx 24rpx;
  border: 1rpx solid var(--app-border, #ebeef0);
  border-radius: 20rpx;
  background: var(--app-card-2, #f7f8fa);
  transition: border-color 160ms ease, background-color 160ms ease;
}

.ie-mode__item--on {
  border-color: var(--wot-color-theme-primary, #ff4d2e);
  background: var(--app-card, #fff);
}

.ie-mode__item--pressed {
  opacity: 0.85;
}

.ie-mode__title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--app-text, #333);
}

.ie-mode__item--on .ie-mode__title {
  color: var(--wot-color-theme-primary, #ff4d2e);
}

.ie-mode__desc {
  font-size: 22rpx;
  line-height: 1.4;
  color: var(--app-text-2, #666);
}

/* ------------------------------------------------------------ 船舶与靠港选择行 */
.ie-vessel {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 24rpx;
  padding: 24rpx;
  border-radius: 22rpx;
  color: var(--app-text-3, #999);
  background: var(--app-card-2, #f7f8fa);
}

.ie-vessel--pressed {
  background: var(--app-border, #ebeef0);
}

.ie-vessel__icon {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: var(--app-card, #fff);
  color: var(--wot-color-theme-primary, #ff4d2e);
}

.ie-vessel__glyph {
  font-size: 32rpx;
}

.ie-vessel__body {
  flex: 1;
  min-width: 0;
}

.ie-vessel__title-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  min-width: 0;
}

.ie-vessel__title {
  overflow: hidden;
  flex: 0 1 auto;
  font-size: 32rpx;
  font-weight: 600;
  line-height: 1.3;
  color: var(--app-text, #333);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ie-vessel__tag {
  display: flex;
  flex: none;
  align-items: center;
  gap: 6rpx;
  padding: 2rpx 12rpx 2rpx 10rpx;
  border-radius: 8rpx;
  background: var(--app-card, #fff);
  font-size: 22rpx;
  font-weight: 500;
  line-height: 1.7;
  color: var(--app-text-2, #666);
}

.ie-vessel__dot {
  width: 8rpx;
  height: 8rpx;
  border-radius: 50%;
  background: #f59e0b;
}

.ie-vessel__tag--ready .ie-vessel__dot {
  background: #16a34a;
}

.ie-vessel__sub {
  display: block;
  margin-top: 6rpx;
  font-size: 26rpx;
  line-height: 1.45;
  color: var(--app-text-2, #666);
}

.ie-vessel__meta {
  display: flex;
  align-items: center;
  gap: 6rpx;
  margin-top: 6rpx;
  font-size: 24rpx;
  line-height: 1.4;
  color: var(--app-text-2, #666);
}

.ie-vessel__meta-icon {
  flex: none;
  font-size: 24rpx;
}

.ie-vessel__chevron {
  flex: none;
  font-size: 32rpx;
}

.ie-vessel-hint {
  display: flex;
  align-items: flex-start;
  gap: 8rpx;
  margin-top: 16rpx;
  padding: 0 4rpx;
  color: var(--app-text-2, #666);
}

.ie-vessel-hint__icon {
  flex: none;
  margin-top: 3rpx;
  font-size: 24rpx;
}

.ie-vessel-hint__text {
  flex: 1;
  font-size: 24rpx;
  line-height: 1.5;
  color: var(--app-text-2, #666);
}

/* -------------------------------------------------------------------- 备注 */
.ie-remark {
  margin-top: 28rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid var(--app-border, #ebeef0);
}

.ie-remark__head {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.ie-remark__label {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--app-text, #333);
}

.ie-remark__optional {
  font-size: 24rpx;
  color: var(--app-text-2, #666);
}

.ie-remark__count {
  margin-left: auto;
  font-size: 24rpx;
  font-variant-numeric: tabular-nums;
  color: var(--app-text-2, #666);
}

.ie-remark__field {
  display: flex;
  align-items: center;
  gap: 12rpx;
  box-sizing: border-box;
  height: 88rpx;
  margin-top: 16rpx;
  padding: 0 24rpx;
  border: 1rpx solid transparent;
  border-radius: 20rpx;
  color: var(--app-text-3, #999);
  background: var(--app-card-2, #f7f8fa);
  transition: border-color 160ms ease, background-color 160ms ease;
}

.ie-remark__field--focus {
  border-color: var(--wot-color-theme-primary, #ff4d2e);
  background: var(--app-card, #fff);
}

.ie-remark__icon {
  flex: none;
  font-size: 28rpx;
}

.ie-remark__input {
  flex: 1;
  min-width: 0;
  height: 100%;
  font-size: 28rpx;
  color: var(--app-text, #333);
}

.ie-remark__placeholder {
  color: var(--app-text-3, #999);
  font-size: 28rpx;
}

.ie-remark__clear {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
}

.ie-remark__clear--pressed {
  background: var(--app-border, #ebeef0);
}

.ie-remark__clear-icon {
  font-size: 28rpx;
}

/* ------------------------------------------------------------------ 导入说明 */
.ie-notice {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 20rpx;
  padding: 16rpx 20rpx;
  border-radius: 16rpx;
  color: var(--app-text-2, #666);
  background: var(--app-card-2, #f7f8fa);
}

.ie-notice__icon {
  flex: none;
  font-size: 28rpx;
}

.ie-notice__text {
  flex: 1;
  font-size: 26rpx;
  line-height: 1.45;
  color: var(--app-text-2, #666);
}

.ie-guide-step {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  margin-top: 24rpx;
}

.ie-guide-step__num {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 40rpx;
  height: 40rpx;
  margin-top: 2rpx;
  border-radius: 50%;
  background: var(--app-card-2, #f7f8fa);
  font-size: 22rpx;
  font-weight: 600;
  color: var(--app-text-2, #666);
}

.ie-guide-step__body {
  flex: 1;
  min-width: 0;
}

.ie-guide-step__title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  line-height: 1.4;
  color: var(--app-text, #333);
}

.ie-guide-step__desc {
  display: block;
  margin-top: 4rpx;
  font-size: 26rpx;
  line-height: 1.55;
  color: var(--app-text-2, #666);
}

/* ---------------------------------------------------------------- 次级出口 */
.ie-all-link {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
  box-sizing: border-box;
  min-height: 44px;
  margin-top: 16rpx;
  font-size: 26rpx;
  color: var(--app-text-2, #666);
}

.ie-all-link--pressed {
  opacity: 0.6;
}

/* 继续上次导入是恢复路径，用品牌色与普通出口区分 */
.ie-all-link--resume {
  font-weight: 600;
  color: var(--wot-color-theme-primary, #ff4d2e);
}

.ie-all-link__icon {
  font-size: 24rpx;
}

/* ---------------------------------------------------------------- 吸底 CTA */
.ie-footer-spacer {
  height: calc(150rpx + env(safe-area-inset-bottom));
}

.ie-footer {
  position: fixed;
  right: 0;
  // H5 的 --window-bottom 为 TabBar 高度；本页是分包页无 TabBar，取 0 兜底
  bottom: var(--window-bottom, 0px);
  left: 0;
  z-index: 10;
  box-sizing: border-box;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: var(--app-card, #fff);
  box-shadow: 0 -2rpx 12rpx rgb(0 0 0 / 4%);
}

.ie-footer__primary {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  height: 88rpx;
  min-height: 44px;
  color: #fff;
  border-radius: 999rpx;
  background: linear-gradient(135deg, var(--wot-color-theme-secondary, #ff8a00), var(--wot-color-theme-primary, #ff4d2e));
  box-shadow: 0 6rpx 18rpx rgb(15 23 42 / 18%);
  transition: opacity 160ms ease;
}

.ie-footer__primary--pressed {
  opacity: 0.88;
}

.ie-footer__primary--disabled {
  opacity: 0.6;
}

.ie-footer__icon {
  font-size: 30rpx;
}

// 渐变浅端上的白字只有 ~2.2:1：给文案补一层极轻的深色投影，提升浅端可读性，
// 不改变全站主 CTA 的渐变色（购物车/详情/列表同一套）
.ie-footer__label {
  font-size: 30rpx;
  font-weight: 600;
  color: #fff;
  text-shadow: 0 1rpx 2rpx rgb(0 0 0 / 16%);
}

/* ---------------------------------------------------------------- 收货地址弹层 */
.ie-sheet {
  padding: 32rpx 24rpx calc(24rpx + env(safe-area-inset-bottom));
}

.ie-sheet__title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--app-text, #333);
}

.ie-sheet__state {
  padding: 48rpx 24rpx;
  font-size: 26rpx;
  line-height: 1.5;
  text-align: center;
  color: var(--app-text-2, #666);
}

.ie-sheet__state--empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
}

.ie-sheet__state-text {
  max-width: 520rpx;
}

.ie-sheet__list {
  max-height: 400rpx;
  margin-top: 24rpx;
}

.ie-sheet__addr {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx;
  border: 1rpx solid var(--app-border, #ebeef0);
  border-radius: 20rpx;
  background: var(--app-card-2, #f7f8fa);
}

.ie-sheet__addr + .ie-sheet__addr {
  margin-top: 16rpx;
}

.ie-sheet__addr--on {
  border-color: var(--wot-color-theme-primary, #ff4d2e);
  background: var(--app-card, #fff);
}

.ie-sheet__addr--pressed {
  opacity: 0.85;
}

.ie-sheet__addr-body {
  flex: 1;
  min-width: 0;
}

.ie-sheet__addr-line {
  display: flex;
  align-items: center;
  gap: 12rpx;
  min-width: 0;
}

.ie-sheet__addr-name {
  flex: none;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--app-text, #333);
}

.ie-sheet__addr-phone {
  overflow: hidden;
  font-size: 24rpx;
  color: var(--app-text-2, #666);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ie-sheet__addr-default {
  flex: none;
  font-size: 20rpx;
  color: var(--wot-color-theme-primary, #ff4d2e);
}

.ie-sheet__addr-detail {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  line-height: 1.45;
  color: var(--app-text-2, #666);
}

.ie-sheet__addr-check {
  flex: none;
  font-size: 32rpx;
  color: var(--app-border, #ebeef0);
}

.ie-sheet__addr-check--on {
  color: var(--wot-color-theme-primary, #ff4d2e);
}

.ie-sheet__add {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 88rpx;
  margin-top: 16rpx;
  border: 1rpx dashed var(--app-border, #ebeef0);
  border-radius: 20rpx;
  font-size: 26rpx;
  color: var(--wot-color-theme-primary, #ff4d2e);
}

.ie-sheet__add--pressed {
  opacity: 0.7;
}

/* -------------------------------------------------------------------- 骨架屏 */
.ie-loading {
  padding: 20rpx 24rpx 0;
}

.ie-skeleton--steps {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 4rpx 8rpx 0;
}

.ie-skeleton--card {
  margin-top: 28rpx;
  padding: 28rpx;
  border: 1rpx solid var(--app-border, #ebeef0);
  border-radius: 28rpx;
  background: var(--app-card, #fff);
}

.ie-skeleton__block {
  border-radius: 10rpx;
  background: linear-gradient(90deg, var(--app-skeleton) 25%, var(--app-skeleton-band) 37%, var(--app-skeleton) 63%);
  background-size: 600rpx 100%;
  animation: ie-shimmer 1.4s ease-in-out infinite;
}

@keyframes ie-shimmer {
  0% {
    background-position: -300rpx 0;
  }

  100% {
    background-position: 300rpx 0;
  }
}

.ie-skeleton__block--dot {
  flex: none;
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
}

.ie-skeleton__block--line {
  width: 140rpx;
  height: 24rpx;
}

.ie-skeleton__head {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.ie-skeleton__block--icon {
  flex: none;
  width: 72rpx;
  height: 72rpx;
  border-radius: 22rpx;
}

.ie-skeleton__head-lines {
  flex: 1;
}

.ie-skeleton__block--title {
  width: 42%;
  height: 30rpx;
}

.ie-skeleton__block--sub {
  width: 78%;
  height: 24rpx;
  margin-top: 12rpx;
}

.ie-skeleton__block--select {
  height: 128rpx;
  margin-top: 24rpx;
  border-radius: 22rpx;
}

.ie-skeleton__block--field {
  height: 88rpx;
  margin-top: 20rpx;
  border-radius: 20rpx;
}

@media (prefers-reduced-motion: reduce) {
  .ie-skeleton__block {
    animation: none;
  }
}

/* -------------------------------------------------------------------- 空态 */
.ie-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 160rpx 64rpx 0;
  text-align: center;
}

.ie-empty__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 120rpx;
  height: 120rpx;
  border-radius: 36rpx;
  background: var(--app-card, #fff);
  color: var(--app-text-3, #999);
  box-shadow: 0 2rpx 12rpx rgb(23 32 51 / 5%);
}

.ie-empty__glyph {
  font-size: 56rpx;
}

.ie-empty__title {
  margin-top: 28rpx;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--app-text, #333);
}

.ie-empty__desc {
  margin-top: 12rpx;
  font-size: 26rpx;
  line-height: 1.6;
  color: var(--app-text-2, #666);
}
</style>
