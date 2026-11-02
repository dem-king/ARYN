<script setup lang="ts">
import type { GroupBuyActivity } from '@/api/promotion/groupBuyActivity'
import { onLoad } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getByIds } from '@/api/product/spu'
import { getActivityPage } from '@/api/promotion/groupBuyActivity'
import { normalizeRetailGoods } from '@/components/diy/retail-normalizers'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'

const { handlePageScroll } = useFrostedNavbar()

definePage({
  name: 'group-buy-list',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '拼团活动',
  },
})

const { t } = useI18n()
const pagingRef = ref()
const state = reactive<{ list: GroupBuyActivity[] }>({
  list: [],
})
const globalLoading = useGlobalLoading()

/**
 * spuId → 商品主图 / 子标题。
 *
 * 拼团活动 VO 不带图片与商品字段，列表卡又必须出货架图与子标题；这里按
 * 「本页 spuId 去重后一次 getByIds」批量取回（单请求），而不是每张卡各发
 * 一次请求，避免 N+1。取数失败只影响图片与子标题（走占位/隐藏），不影响列表本身。
 */
const imageMap = ref<Record<string, string>>({})
const subTitleMap = ref<Record<string, string>>({})

onLoad(async () => {
  nextTick(() => {
    pagingRef.value?.reload()
  })
})

async function queryList(pageNo: number, pageSize: number) {
  globalLoading.loading(t('common.loading'))
  try {
    const response = await getActivityPage({
      current: pageNo,
      size: pageSize,
      desc: 'create_time',
    })
    pagingRef.value?.complete(response.records)
    await loadGoodsInfo(response.records)
  }
  finally {
    globalLoading.close()
  }
}

/** 批量补商品主图与子标题（一次请求覆盖本页所有 spuId） */
async function loadGoodsInfo(records: GroupBuyActivity[]) {
  const pendingIds = Array.from(
    new Set(records.map(item => item.spuId).filter((id): id is string => Boolean(id))),
  ).filter(id => !imageMap.value[id])
  if (pendingIds.length === 0)
    return

  try {
    // getByIds 的真实响应是 { id, name, spuUrls[] }（没有 picUrl/spuName），
    // 复用装修楼层的归一化：firstImage 已兼容 spuUrls[0]，避免图片永远取空。
    const goodsList = normalizeRetailGoods(await getByIds(pendingIds))
    const nextImages = { ...imageMap.value }
    const nextSubTitles = { ...subTitleMap.value }
    for (const goods of goodsList) {
      if (!goods.id)
        continue
      nextImages[goods.id] = goods.imageUrl || ''
      nextSubTitles[goods.id] = goods.subTitle || ''
    }
    imageMap.value = nextImages
    subTitleMap.value = nextSubTitles
  }
  catch {
    // 取数失败不影响列表：卡片走缺图占位，子标题整行隐藏
  }
}

function imageOf(activity: GroupBuyActivity) {
  return activity.spuId ? imageMap.value[activity.spuId] : ''
}

function subTitleOf(activity: GroupBuyActivity) {
  return activity.spuId ? subTitleMap.value[activity.spuId] : ''
}

function goDetail(activity: GroupBuyActivity) {
  if (activity.id) {
    uni.navigateTo({
      url: `/sub-pages/promotion/group-buy/group-buy-detail/index?id=${activity.id}`,
    })
  }
}
</script>

<template>
  <!-- 收尾文案与会场页统一（z-paging 默认是「没有更多了」） -->
  <hr-navbar :title="t('promotion.groupBuy.list.title')" />
  <z-paging
    ref="pagingRef"
    v-model="state.list"
    :auto="false"
    :loading-more-no-more-text="t('promotion.common.noMore')"
    @query="queryList"
    @scroll="handlePageScroll"
  >
    <!-- 顶栏覆盖层等高占位：列表整屏滚动，首屏内容从顶栏下沿开始 -->
    <hr-navbar-space />

    <view class="group-buy-list">
      <group-buy-card
        v-for="item in state.list"
        :key="item.id"
        :activity="item"
        :image-url="imageOf(item)"
        :sub-title="subTitleOf(item)"
        @click="goDetail"
      />
    </view>
  </z-paging>
</template>

<style lang="scss" scoped>
.group-buy-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 24rpx;
}
</style>
