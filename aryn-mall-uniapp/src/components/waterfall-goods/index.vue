<script setup lang="ts">
import { reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { getRecommendPage } from '@/api/product/spu'
import { shouldShowOriginalPrice } from '@/components/diy/price-display'
import { shouldShowSubtitle } from '@/components/diy/subtitle-display'
import { useDiyPagedLoadMore } from '@/composables/useDiyPagedLoadMore'

const props = defineProps<{
  /** 商详：当前商品，既作为分类上下文也默认排除 */
  spuId?: string
  /** 购物车/订单：提供分类上下文的商品（取前 20 个） */
  sourceSpuIds?: string[]
  /** 购物车/订单：需要排除的商品（取前 50 个） */
  excludeSpuIds?: string[]
}>()

// 定义变量
const state = reactive<any>({
  list: [],
})

const router = useRouter()
const { t } = useI18n()
const pagingRef = ref()

/**
 * 推荐流无感续页（与装修商品楼层共用同一套机制）。
 *
 * z-paging 在 use-page-scroll 模式下不自己监听页面滚动，缺省会停在「默认态」
 * 渲染可点的「点击加载更多」。这里复用 `useDiyPagedLoadMore`：宿主页把页面滚动
 * 活动广播出来（`useDecorationInfiniteScroll`），本组件实测自身底边距视口底
 * ≤ 400px 就提前调 `pageReachBottom()`；每次查询结束再自愈重测，直到没有更多。
 * 默认态文案在模板里置空，加载中/没有更多/加载失败文案保持不变。
 */
const { runQuery } = useDiyPagedLoadMore(pagingRef, '.waterfall-goods', {
  // z-paging 首次 reload 在 nextTick 触发；推荐位若正好在首屏（如空购物车），
  // 挂载即测距会先发出 pageNo=2 再被 reload 重置。改由首次查询成功后的自愈补齐。
  preloadOnMount: false,
})

/**
 * 推荐上下文版本：props（spuId / sourceSpuIds / excludeSpuIds）变化会 reload 重取第 1 页。
 * 旧上下文的在途请求回来时不能再交给 z-paging —— 它没有请求序号/取消机制，两次 page1
 * 的结果都会被 append（购物车、支付结果页的推荐上下文就是挂载后才异步到齐的）。
 */
let contextEpoch = 0

async function queryList(pageNo: number, pageSize: number) {
  const epoch = contextEpoch
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
    // 后端推荐接口 page.setSearchCount(false)（GoodsSpuServiceImpl#recommendPage），不下发真实
    // total，注释明确「前端只按返回条数判断是否还有下一页」——所以用 complete 让 z-paging 按
    // 本页条数推断；不能用 completeByTotal：total=0 会被判成「没有更多」，第一页后不再加载。
    // 必须 await：自愈重测要在数据生效之后进行，否则量到的是旧布局。
    await pagingRef.value?.complete(response?.records ?? [])
  }, () => epoch === contextEpoch)
}

// 页面数据异步到齐或切换商品时，按新的推荐上下文重新取数
watch(
  () => [props.spuId, props.sourceSpuIds?.join(','), props.excludeSpuIds?.join(',')].join('|'),
  () => {
    contextEpoch++
    // reload 的 promise 在请求失败时会 reject（callNetworkReject 默认 true），加 catch 避免未处理拒绝
    pagingRef.value?.reload()?.catch?.(() => {})
  },
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
      <view class="p-2">
        <view class="flex flex-wrap justify-between">
          <view v-for="(item, index) in state.list" :key="index" class="mb-20rpx box-border w-[calc(50%-10rpx)] rounded-xl bg-white shadow-sm dark:bg-[var(--wot-dark-background2)]" @click="toDetail(item.id)">
            <image :src="item.spuUrls[0]" class="block h-160px w-full rounded-lg" />
            <view class="p-10rpx">
              <view class="px-10rpx">
                <wd-text size="14px" :lines="2" :text="item.name" />
                <view
                  v-if="shouldShowSubtitle(item.name, item.subTitle)"
                  class="goods-subtitle"
                >
                  {{ item.subTitle }}
                </view>
              </view>
              <view class="flex items-baseline pt-10rpx">
                <wd-text mode="price" :text="item.salesPrice" prefix="￥" color="red" />
                <text
                  v-if="shouldShowOriginalPrice(item.salesPrice, item.originalPrice)"
                  class="price-original"
                >
                  ￥{{ item.originalPrice }}
                </text>
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
  color: #909399;
  font-size: 22rpx;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price-original {
  margin-left: 8rpx;
  font-size: 20rpx;
  color: #999;
  text-decoration: line-through;
}
</style>
