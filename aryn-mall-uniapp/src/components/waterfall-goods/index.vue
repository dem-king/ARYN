<script setup lang="ts">
import type { ListZonePrice } from '@/utils/zone-price'
import { computed, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { getRecommendPage } from '@/api/product/spu'
import { shouldShowSubtitle } from '@/components/diy/subtitle-display'
import PriceText from '@/components/price-text/index.vue'
import QuickCartButton from '@/components/quick-cart-button/index.vue'
import { useDiyPagedLoadMore } from '@/composables/useDiyPagedLoadMore'
import { useResponsiveColumns } from '@/composables/useResponsiveColumns'
import { useZonePrices } from '@/composables/useZonePrices'
import { imageAspectPadding } from '@/utils/image-ratio'
import { splitIntoColumns } from '@/utils/masonry'
import { socialProofIcon, splitSocialProofText, zonePriceBadgeText } from '@/utils/zone-price'

const props = defineProps<{
  /** 商详：当前商品，既作为分类上下文也默认排除 */
  spuId?: string
  /** 购物车/订单：提供分类上下文的商品（取前 20 个） */
  sourceSpuIds?: string[]
  /** 购物车/订单：需要排除的商品（取前 50 个） */
  excludeSpuIds?: string[]
}>()

const emit = defineEmits<{
  /** 快捷加购成功：宿主页据此刷新车内列表/从推荐流移除已加商品 */
  (e: 'added', spuId: string): void
}>()

// 定义变量
const state = reactive<any>({
  list: [],
})

// 交替瀑布流：手机 2 列、宽屏 3 列；按索引交替入列，翻页追加不重排已有卡片
const columns = useResponsiveColumns(600, 2, 3)
const goodsColumns = computed(() => splitIntoColumns<any>(state.list, columns.value))

const router = useRouter()
const { t } = useI18n()
const pagingRef = ref()
/** 推荐位的专区价（会员价/捡漏价）：先渲染、后补价，失败静默回落原价 */
const zone = useZonePrices()

/**
 * 推荐流无感续页（与装修商品楼层共用同一套机制）。
 *
 * z-paging 在 use-page-scroll 模式下不自己监听页面滚动，缺省会停在「默认态」
 * 渲染可点的「点击加载更多」。这里复用 `useDiyPagedLoadMore`：宿主页把页面滚动
 * 活动广播出来（`useDecorationInfiniteScroll`），本组件实测自身底边距视口底
 * ≤ 400px 就提前调 `pageReachBottom()`；每次查询结束再自愈重测，直到没有更多。
 * 默认态文案在模板里置空，加载中/没有更多/加载失败文案保持不变。
 *
 * 上下文串行化（2026-11-05 修复「购物车『为你推荐』只出第 1 页、不能再往下加载」）：
 * 购物车的推荐上下文（sourceSpuIds / excludeSpuIds）是挂载后才异步到齐的，props
 * 变化会 reload 重取第 1 页。若此时 z-paging 的首次查询还没结束，直接 reload 会让
 * 它并发发出两次 query，两次 complete 争抢 z-paging 内部唯一的 complete promise
 * 槽位：先调用的 complete 的 resolve 被后一次顶掉，`await complete()` 永不返回，
 * `useDiyPagedLoadMore` 的 queryInFlight 因此永久 >0，之后所有滚动广播都被丢弃
 * ——表现为列表停在第一页且没有任何可点文案。这里把 reload 串行化到查询空闲之后。
 */
const COMPLETE_FALLBACK_TIMEOUT_MS = 800

const { runQuery, isQuerying } = useDiyPagedLoadMore(pagingRef, '.waterfall-goods', {
  // z-paging 首次 reload 在 nextTick 触发；推荐位若正好在首屏（如空购物车），
  // 挂载即测距会先发出 pageNo=2 再被 reload 重置。改由首次查询成功后的自愈补齐。
  preloadOnMount: false,
  onQuerySettled: handleQuerySettled,
})

/**
 * 推荐上下文版本：props（spuId / sourceSpuIds / excludeSpuIds）变化会 reload 重取第 1 页。
 * 旧上下文的在途请求回来时不能再交给 z-paging —— 它没有请求序号/取消机制，两次 page1
 * 的结果都会被 append（购物车、支付结果页的推荐上下文就是挂载后才异步到齐的）。
 */
let contextEpoch = 0

/** 上下文签名：props 的稳定字符串形式，用于判断当前列表/在途查询是否已是最新上下文 */
function contextSignature() {
  return [props.spuId ?? '', props.sourceSpuIds?.join(',') ?? '', props.excludeSpuIds?.join(',') ?? ''].join('|')
}

/** 最近一次启动的查询所用的上下文签名 */
let lastQuerySignature = ''
/** 首查是否已启动：启动前 z-paging 的初始 reload 会读到最新 props，无需再补一次 reload */
let firstQueryStarted = false
/** reload 已调用、但其 query 还没启动（防止两次上下文变化叠出两次 reload） */
let reloadPendingQuery = false
/** 上下文已变化、但当前查询还没结束：等 settle 后再按新上下文重取第 1 页 */
let pendingContextReload = false

/**
 * z-paging 的 complete 兜底等待。
 *
 * `complete()` 返回的 Promise 存在 z-paging 内部的单槽位里，并发 complete 会顶掉
 * 先前的 resolve 导致 await 永不返回（数据其实已经落地，只是 Promise 丢了）。
 * 串行化之后理论上不会再并发 complete，这里保留超时兜底：即使同类竞态再次发生，
 * 也只让本次查询晚一点结束，不会把 queryInFlight 永久卡死、静默停掉续页。
 * 必须等到数据生效再让自愈链路测距，否则量到的是旧布局。
 */
async function awaitPagingComplete(records: any[]) {
  const settled = pagingRef.value?.complete(records)
  if (!settled || typeof settled.then !== 'function')
    return
  // 超时后迟到的 reject 不能变成未处理拒绝
  settled.catch?.(() => {})
  await Promise.race([
    settled,
    new Promise<void>(resolve => setTimeout(resolve, COMPLETE_FALLBACK_TIMEOUT_MS)),
  ])
}

async function queryList(pageNo: number, pageSize: number) {
  const epoch = contextEpoch
  // 查询一启动就锁定上下文：此后 props 再变只会挂起，等本次查询 settle 后再 reload
  firstQueryStarted = true
  reloadPendingQuery = false
  lastQuerySignature = contextSignature()
  await runQuery(async () => {
    const params: Record<string, any> = {
      current: pageNo,
      size: pageSize,
    }
    if (props.spuId)
      params.spuId = props.spuId
    if (props.sourceSpuIds?.length)
      params.sourceSpuIds = props.sourceSpuIds.join(',')
    if (props.excludeSpuIds?.length)
      params.excludeIds = props.excludeSpuIds.join(',')
    // 推荐接口：同分类优先、排除已选商品；无上下文时后端按浏览足迹或全店排序兜底
    const response = await getRecommendPage(params)
    if (epoch !== contextEpoch)
      return
    const records = response?.records ?? []
    // 先按普通售价渲染本页，会员价/捡漏价异步补拉（缓存 60s、失败静默）
    void zone.loadForSpuIds(records.map((item: any) => item?.id))
    // 后端推荐接口 page.setSearchCount(false)（GoodsSpuServiceImpl#recommendPage），不下发真实
    // total，注释明确「前端只按返回条数判断是否还有下一页」——所以用 complete 让 z-paging 按
    // 本页条数推断；不能用 completeByTotal：total=0 会被判成「没有更多」，第一页后不再加载。
    await awaitPagingComplete(records)
  }, () => epoch === contextEpoch)
}

/** 按最新上下文重取第 1 页 */
function reloadForContext() {
  if (!pagingRef.value) {
    // z-paging 还没挂载：它的初始 reload 会按最新 props 取数，等查完再判断是否还差一次
    pendingContextReload = true
    return
  }
  contextEpoch++
  reloadPendingQuery = true
  // reload 的 promise 在请求失败时会 reject（callNetworkReject 默认 true），加 catch 避免未处理拒绝
  pagingRef.value.reload()?.catch?.(() => {})
}

/** 上下文变化：空闲则立刻重取；查询在途 / 首查未启动 / reload 的 query 还没发出则挂起 */
function requestContextReload() {
  if (contextSignature() === lastQuerySignature) {
    // 当前列表或已启动的查询本身就是这份上下文：不需要重复请求
    pendingContextReload = false
    return
  }
  if (!firstQueryStarted || isQuerying() || reloadPendingQuery) {
    pendingContextReload = true
    return
  }
  pendingContextReload = false
  reloadForContext()
}

/** 查询完全空闲后的挂起重取：本次查询已读到新上下文则直接作废，不重复请求 */
function handleQuerySettled() {
  reloadPendingQuery = false
  if (pendingContextReload)
    requestContextReload()
}

// 页面数据异步到齐或切换商品时，按新的推荐上下文重新取数（串行化见上）
watch(
  () => contextSignature(),
  () => requestContextReload(),
)

// 跳转商品详情
function toDetail(id: string) {
  router.push({
    name: 'goods-detail',
    params: {
      id,
    },
  })
}

/**
 * 推荐卡专区价展示：主价/划线/角标/未达标引导统一由共享层判定。
 *
 * 渲染期读取共享层 revision，价格晚一帧回来时卡片自动重算；未命中时
 * 主价=售价、划线仍按「原价 > 售价」口径，与改造前一致。
 */
function cardPrice(item: any): ListZonePrice {
  return zone.cardPriceOf(item)
}

/** 社证数字强调：服务端成文拆「前缀+数字+后缀」，数字加粗（纯展示，口径仍在服务端） */
function proofParts(item: any) {
  return splitSocialProofText(cardPrice(item).socialProofText)
}

/** 金额展示：去掉无意义的末尾 0，避免浮点尾差 */
function formatAmount(value: unknown): string {
  const amount = Number(value)
  if (!Number.isFinite(amount))
    return ''
  return String(Number(amount.toFixed(2)))
}

// 推荐卡快捷加购成功：透传宿主页（购物车页要据此立即刷新本页列表与推荐上下文）
function handleAdded(spuId: string) {
  emit('added', spuId)
}
</script>

<template>
  <view class="waterfall-goods">
    <!--
      use-page-scroll：复用宿主页页面滚动，由 useDiyPagedLoadMore 测自身底边位置
      提前续页（无感浏览，不出现「点击加载更多」）。
      refresher-enabled=false：宿主页各自的下拉刷新/页面刷新已覆盖，避免双重下拉。
      loading-more-default-text 置空：默认状态不渲染任何可点击文案。
    -->
    <z-paging
      ref="pagingRef"
      v-model="state.list"
      use-page-scroll
      :refresher-enabled="false"
      :loading-more-loading-text="t('common.loading')"
      :loading-more-no-more-text="t('catalog.noMore')"
      :loading-more-fail-text="t('catalog.loadMoreFailed')"
      loading-more-default-text=""
      :empty-view-text="t('catalog.noRecommendations')"
      :empty-view-reload-text="t('catalog.reload')"
      @query="queryList"
    >
      <!--
        waterfall-body / waterfall-grid / waterfall-card 三个语义 class 只为底部
        MP-WEIXIN 宽屏段提供挂载点（折叠态没有任何样式规则命中它们，规则只加在样式表末尾）。
        卡片仍保留全部 Uno 工具类：手机端版式一行都不改。
      -->
      <view class="waterfall-body p-2">
        <view class="waterfall-grid">
          <view
            v-for="(column, columnIndex) in goodsColumns"
            :key="columnIndex"
            class="waterfall-column"
          >
            <view v-for="item in column" :key="item.id" class="waterfall-card mb-20rpx box-border rounded-xl shadow-sm bg-white dark:bg-[var(--wot-dark-background2)]" @click="toDetail(item.id)">
              <view
                class="waterfall-pic-box rounded-lg"
                :style="{ paddingTop: imageAspectPadding(item.picWidth, item.picHeight) }"
              >
                <image :src="item.spuUrls[0]" class="waterfall-pic" mode="aspectFit" />
              </view>
              <view class="p-10rpx">
                <view class="px-10rpx">
                  <wd-text size="14px" :lines="2" :text="item.name" custom-class="waterfall-title" />
                  <view
                    v-if="shouldShowSubtitle(item.name, item.subTitle)"
                    class="goods-subtitle"
                  >
                    {{ item.subTitle }}
                  </view>
                </view>
                <!--
                  价格与快捷加购同一行：推荐卡原先只有「点整卡跳商详」一个动作，
                  用户看到想买的商品必须先进详情页才能加购。加购按钮复用
                  `quick-cart-button`（单规格按起订量直加、多规格唤起 SKU 弹层、
                  未登录先跳登录，加购去向由全局 App.ku 的确认弹层统一处理），
                  其根节点已 `@tap.stop`，点加购不会同时触发外层卡片的跳商详。
                  售罄（stock <= 0）不渲染按钮，与装修商品流 `diy-goods-*` 同口径。
                -->
                <!--
                  营销标签行：新人首单价 + 热销社证同一行并排、nowrap 不折行，
                  新人=红橙渐变星芒（资格类 chip 不占价格行），社证=浅桃底数字加粗。
                -->
                <view
                  v-if="cardPrice(item).badgeKind === 'newbie' || cardPrice(item).socialProofText"
                  class="tag-row"
                >
                  <view v-if="cardPrice(item).badgeKind === 'newbie'" class="tag-chip tag-chip--newbie">
                    <text class="tag-chip-spark" />
                    <text class="tag-chip-text">
                      {{ zonePriceBadgeText('newbie') }}
                    </text>
                  </view>
                  <view v-if="cardPrice(item).socialProofText" class="tag-chip tag-chip--proof">
                    <wd-icon :name="socialProofIcon(cardPrice(item).socialProofKind)" size="18rpx" />
                    <text class="tag-chip-before">
                      {{ proofParts(item).before }}
                    </text>
                    <text v-if="proofParts(item).number" class="tag-chip-num">
                      {{ proofParts(item).number }}
                    </text>
                    <text class="tag-chip-after">
                      {{ proofParts(item).after }}
                    </text>
                  </view>
                </view>
                <view class="flex items-end justify-between pt-10rpx">
                  <view class="min-w-0 flex flex-1 flex-wrap items-baseline overflow-hidden">
                    <text
                      v-if="cardPrice(item).badgeKind && cardPrice(item).badgeKind !== 'newbie'"
                      class="zone-badge"
                      :class="`zone-badge--${cardPrice(item).badgeKind}`"
                    >
                      {{ zonePriceBadgeText(cardPrice(item).badgeKind) }}
                    </text>
                    <price-text class="waterfall-price" :value="cardPrice(item).mainPrice" symbol="￥" />
                    <!-- 近N天最低价：主价旁描边小标（固定红系不随主题，见需求文档视觉规范） -->
                    <text v-if="cardPrice(item).priceTagText" class="price-tag">
                      <text class="price-tag-icon" />
                      {{ cardPrice(item).priceTagText }}
                    </text>
                    <text v-if="cardPrice(item).strikePrice !== null" class="price-original">
                      ￥{{ formatAmount(cardPrice(item).strikePrice) }}
                    </text>
                    <text v-if="cardPrice(item).guideText" class="price-guide">
                      {{ cardPrice(item).guideText }}
                    </text>
                  </view>
                  <view v-if="item.stock > 0" class="flex flex-shrink-0 pl-8rpx">
                    <quick-cart-button
                      :spu-id="item.id"
                      :zone-prices="zone.skuZonePricesOf(item.id)"
                      size="48rpx"
                      @added="handleAdded"
                    />
                  </view>
                </view>
              </view>
            </view>
          </view>
          </view>
        </view>
    </z-paging>
  </view>
</template>

<style lang="scss" scoped>
@import '@/styles/goods-tag.scss';

.goods-subtitle {
  margin-top: 4rpx;
  overflow: hidden;
  color: var(--app-text-3);
  font-size: 22rpx;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 主价：整数大、小数小（price-text 组件内用 em 跟随本字号） */
.waterfall-price {
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 28rpx;
  font-weight: 600;
}

.price-original {
  margin-left: 8rpx;
  font-size: 20rpx;
  color: var(--app-text-3);
  text-decoration: line-through;
}

/* 近N天最低价贴纸：浅红实底+600 字重（固定红系不随主题，令牌见 goods-tag.scss） */
.price-tag {
  display: inline-flex;
  align-items: center;
  gap: 2rpx;
  flex-shrink: 0;
  margin-left: 8rpx;
  max-width: 100%;
  overflow: hidden;
  padding: 0 8rpx;
  border-radius: 6rpx;
  font-size: 20rpx;
  line-height: 28rpx;
  white-space: nowrap;
  @include goods-tag-low;
}

.price-tag-icon {
  width: 16rpx;
  height: 12rpx;
  flex-shrink: 0;
  @include goods-tag-low-icon;
}

/* 身份价角标：与列表卡同一套颜色语义（会员金 / 捡漏红）；新人价带星芒图标 */
.zone-badge {
  display: inline-flex;
  align-items: center;
  gap: 2rpx;
  margin-right: 8rpx;
  padding: 2rpx 8rpx;
  border-radius: 6rpx;
  color: #fff;
  font-size: 18rpx;
  font-weight: 500;
  line-height: 1.4;

  &--member {
    background: linear-gradient(135deg, #b8860b 0%, #e0a82e 100%);
  }

  &--clearance {
    @include goods-tag-clearance;
  }

  /* 秒杀价：主题红渐变实底白字——限时活动，视觉强于身份价 */
  &--seckill {
    background: linear-gradient(135deg, var(--wot-color-theme-primary, #ff2237) 0%, #ff6b45 100%);
  }
}

/* 未达标引导：独占一行、弱化颜色 */
.price-guide {
  flex-basis: 100%;
  margin-top: 2rpx;
  color: #b45309;
  font-size: 20rpx;
  line-height: 1.4;
}

/*
 * 营销标签行：新人首单价 + 热销社证同一行并排、nowrap 不折行
 * （2026-11-08 视觉增强 + 并排口径，令牌见 goods-tag.scss）。
 * 【icon 尺寸硬约束】勿用 px 定小尺寸：9px 固定字号下线条糊成一团，
 * 20rpx 与文字同高跟随缩放才可辨；icon 颜色随 currentColor 走暖橙。
 */
.tag-row {
  /* 左右 10rpx 与商品名块（px-10rpx）左对齐 */
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 4rpx;
  overflow: hidden;
  margin: 8rpx 10rpx 0;
  min-width: 0;
}

.tag-chip {
  display: inline-flex;
  align-items: center;
  flex: none;
  max-width: 100%;
  overflow: hidden;
  font-size: 18rpx;
  line-height: 1.5;
  white-space: nowrap;
}

.tag-chip--newbie {
  @include goods-tag-newbie;
  gap: 2rpx;
  padding: 2rpx 6rpx;
  border-radius: 6rpx;
}

.tag-chip-spark {
  width: 16rpx;
  height: 16rpx;
  flex: none;
  @include goods-tag-newbie-icon;
}

.tag-chip-text {
  white-space: nowrap;
}

/* 社证 chip 可收缩：与新人芯片并排时优先保留新人 chip，空间不足裁切社证 */
.tag-chip--proof {
  @include goods-tag-hot;
  gap: 2rpx;
  flex: 0 1 auto;
  padding: 2rpx 8rpx;
}

.tag-chip-before {
  flex: none;
  white-space: nowrap;
}

.tag-chip-num {
  @include goods-tag-hot-num;
  white-space: nowrap;
}

.tag-chip-after {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* 交替瀑布流：手机 2 列、宽屏 3 列（列数由 useResponsiveColumns 控制），卡片按内容高度错落 */
.waterfall-grid {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
}

.waterfall-column {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

/* 图片区按主图真实宽高锁占位（后端回填，缺失按 1:1），加载前后卡片高度不变 */
.waterfall-pic-box {
  position: relative;
  width: 100%;
  overflow: hidden;
  background: #f5f5f5;
}

.waterfall-pic {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

/**
 * 折叠屏 / 平板展开态（仅微信小程序，断点 600px）。
 *
 * 推荐流被商详推荐位、个人中心、购物车、支付结果、订单详情共用，宽屏统一 3 列是预期行为
 * （与商品列表页同参数：标题 13px、价格 15px、列间距用折痕安全区 --wide-gutter 24px）。
 * 段内禁用 rpx/upx：rpx 以「窗口宽 = 750rpx」为基准，展开态所有尺寸等比放大 ≈1.8 倍，
 * 正是本次要修的根因；这里只用 px 与 App.vue 的 --wide-* token（引用带兜底值）。
 * 折叠态（<600px）不命中本段，H5/App 被条件编译整段剔除 → 手机端像素级零变化。
 *
 * 宽度账（A8，700px 窗口）：外层左右各 12px 内边距 → 网格 676px；
 * 卡宽 =（676 − 2×24 列间距）/ 3 ≈ 209px，3 列并排恰好铺满、不换行也不溢出。
 *
 * 图片区已改为按主图真实宽高锁占位（缺失 1:1），宽屏下卡片宽度仍由 3 列均分，
 * 图片高度随比例自适应，不再固定 h-160px。
 */
/* #ifdef MP-WEIXIN */
@media (min-width: 600px) {
  /* 外层 p-2 是模板里的 UnoCSS 工具类：它和本文件 scoped 规则同权、且注入顺序在 scoped
     样式之后，同权后写者胜——必须用「祖先 + 本类」提高优先级才压得住 */
  .waterfall-goods .waterfall-body {
    width: 100%;
    box-sizing: border-box;
    padding: 12px;
  }

  /* 3 列由 useResponsiveColumns 控制，这里只放宽屏列间距 */
  .waterfall-goods .waterfall-grid {
    gap: var(--wide-gutter, 24px);
  }

  .waterfall-goods .waterfall-grid .waterfall-card {
    min-width: 0;
    margin-bottom: 12px;
    border-radius: var(--wide-radius, 12px);
  }

  /* wd-text 的 font-size 由模板 size 行内样式（标题 14px）决定，内联样式只能 !important 覆盖 */
  .waterfall-goods .waterfall-title {
    font-size: var(--wide-card-font, 13px) !important;
    line-height: 1.4;
  }

  /* 价格同样走 wd-text，统一 !important 口径，避免将来补 size 属性时静默失效 */
  .waterfall-goods .waterfall-price {
    font-size: var(--wide-price-font, 15px) !important;
  }

  /* 卡片内辅助文案仍是 rpx（展开会放大），按平板密度改 px */
  .waterfall-goods .goods-subtitle {
    margin-top: 3px;
    font-size: var(--wide-meta-font, 12px);
  }

  .waterfall-goods .price-original {
    margin-left: 6px;
    font-size: 11px;
  }

  .waterfall-goods .zone-badge {
    margin-right: 6px;
    padding: 1px 4px;
    border-radius: 3px;
    font-size: 10px;
  }

  .waterfall-goods .price-tag {
    margin-left: 6px;
    padding: 0 4px;
    border-radius: 3px;
    font-size: 10px;
    line-height: 14px;
  }

  .waterfall-goods .price-tag-icon {
    width: 8px;
    height: 6px;
  }

  /* 营销标签行宽屏密度：rpx 展开态放大 ≈1.8 倍，改 px */
  .waterfall-goods .tag-row {
    gap: 6px;
    margin: 4px 5px 0;
  }

  .waterfall-goods .tag-chip--newbie {
    gap: 2px;
    padding: 1px 6px;
    border-radius: 3px;
    font-size: 10px;
  }

  .waterfall-goods .tag-chip-spark {
    width: 8px;
    height: 8px;
  }

  .waterfall-goods .tag-chip--proof {
    gap: 2px;
    padding: 1px 6px;
    font-size: 10px;
  }

  .waterfall-goods .tag-chip--proof :deep(.wd-icon) {
    font-size: 10px !important;
  }

  .waterfall-goods .price-guide {
    font-size: 10px;
  }
}
/* #endif */
</style>
