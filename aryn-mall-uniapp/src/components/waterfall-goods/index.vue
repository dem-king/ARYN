<script setup lang="ts">
import type { ListZonePrice } from '@/utils/zone-price'
import { reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { getRecommendPage } from '@/api/product/spu'
import { shouldShowSubtitle } from '@/components/diy/subtitle-display'
import QuickCartButton from '@/components/quick-cart-button/index.vue'
import { useDiyPagedLoadMore } from '@/composables/useDiyPagedLoadMore'
import { useZonePrices } from '@/composables/useZonePrices'
import { zonePriceBadgeText } from '@/utils/zone-price'

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
      <view class="p-2 waterfall-body">
        <view class="flex flex-wrap justify-between waterfall-grid">
          <view v-for="(item, index) in state.list" :key="index" class="mb-20rpx box-border w-[calc(50%-10rpx)] rounded-xl bg-white shadow-sm dark:bg-[var(--wot-dark-background2)] waterfall-card" @click="toDetail(item.id)">
            <image :src="item.spuUrls[0]" class="block h-160px w-full rounded-lg" />
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
              <view class="flex items-end justify-between pt-10rpx">
                <view class="min-w-0 flex flex-1 flex-wrap items-baseline overflow-hidden">
                  <text
                    v-if="cardPrice(item).badgeKind"
                    class="zone-badge"
                    :class="`zone-badge--${cardPrice(item).badgeKind}`"
                  >
                    {{ zonePriceBadgeText(cardPrice(item).badgeKind) }}
                  </text>
                  <wd-text mode="price" :text="cardPrice(item).mainPrice" prefix="￥" color="red" custom-class="waterfall-price" />
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
    </z-paging>
  </view>
</template>

<style lang="scss" scoped>
.goods-subtitle {
  margin-top: 4rpx;
  overflow: hidden;
  color: var(--app-text-3);
  font-size: 22rpx;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price-original {
  margin-left: 8rpx;
  font-size: 20rpx;
  color: var(--app-text-3);
  text-decoration: line-through;
}

/* 身份价角标：与列表卡同一套颜色语义（会员金 / 捡漏红） */
.zone-badge {
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
    background: linear-gradient(135deg, #fa2c19 0%, #ff6b45 100%);
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
 * 卡片图片 h-160px 本来就是 px（不随 rpx 放大），保持现状不改比例——改成 1:1 会同时
 * 动到手机端观感，与「折叠态零变化」冲突。
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

  /* justify-between 同理是工具类：3 列定宽后要改回左对齐，否则最后一行的少量卡片会被拉开 */
  .waterfall-goods .waterfall-grid {
    gap: var(--wide-gutter, 24px);
    justify-content: flex-start;
  }

  /* 卡宽原由模板工具类 w-[calc(50%-10rpx)] 控制，这里用「祖先 + 双类」把优先级提到更高，
     不依赖 !important 即可压过工具类；宽度按「容器宽 − 两条列间距」三等分，
     以后改列数只动这一个数字 */
  .waterfall-goods .waterfall-grid .waterfall-card {
    width: calc((100% - 2 * var(--wide-gutter, 24px)) / 3);
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

  .waterfall-goods .price-guide {
    font-size: 10px;
  }
}
/* #endif */
</style>
