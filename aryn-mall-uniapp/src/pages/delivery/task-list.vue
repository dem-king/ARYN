<script setup lang="ts">
import { reactive, ref } from 'vue'
import { getMyTaskPage, getTaskStatusName, getTaskStatusColor } from '@/api/delivery'
import type { DeliveryTask, DeliveryTaskStatus } from '@/api/delivery'

definePage({
  name: 'delivery-task-list',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '我的任务',
  },
})

interface TabItem {
  name: string
  value: string
}

interface State {
  tabCurrent: number
  taskList: DeliveryTask[]
  queryParams: {
    status: string
    keyword: string
  }
}

const pagingRef = ref()
const router = useRouter()
const globalLoading = useGlobalLoading()

const tabList = ref<TabItem[]>([
  { name: '全部', value: '' },
  { name: '待派单', value: '1' },
  { name: '待取货', value: '2' },
  { name: '配货中', value: '3' },
  { name: '待送达', value: '4' },
  { name: '已送达', value: '5' },
  { name: '已签收', value: '6' },
  { name: '已取消', value: '7' },
])

const state = reactive<State>({
  tabCurrent: 0,
  taskList: [],
  queryParams: {
    status: '',
    keyword: '',
  },
})

/** 分页查询 */
async function queryList(pageNo: number, pageSize: number) {
  globalLoading.loading('加载中...')
  try {
    const response = await getMyTaskPage({
      current: pageNo,
      size: pageSize,
      status: state.queryParams.status,
      keyword: state.queryParams.keyword,
    })
    pagingRef.value?.complete((response as any)?.records ?? [])
  }
  finally {
    globalLoading.close()
  }
}

/** 切换Tab */
function changeTab({ index }: { index: number }) {
  state.tabCurrent = index
  state.queryParams.status = tabList.value[index]?.value ?? ''
  pagingRef.value?.reload()
}

/** 跳转到任务详情 */
function toTaskDetail(taskId: string) {
  router.push({
    name: 'delivery-task-detail',
    params: { id: taskId },
  })
}

/** 跳转回工作台 */
function toWorkbench() {
  router.replaceAll({ name: 'delivery-index' })
}

/** 跳转到个人中心 */
function toProfile() {
  router.push({ name: 'delivery-profile' })
}
</script>

<template>
  <z-paging
    ref="pagingRef"
    v-model="state.taskList"
    :auto="false"
    @query="queryList"
  >
    <template #top>
      <hr-navbar title="我的任务" />
      <wd-tabs
        v-model="state.tabCurrent"
        slidable="always"
        swipeable
        @change="changeTab"
      >
        <block v-for="(item, index) in tabList" :key="index">
          <wd-tab :title="item.name" />
        </block>
      </wd-tabs>
    </template>

    <view class="px-20rpx">
      <view
        v-for="(task, index) in state.taskList"
        :key="index"
        class="my-20rpx rounded-20rpx bg-white p-30rpx"
        @click="toTaskDetail(task.id)"
      >
        <!-- 任务头部 -->
        <view class="flex items-center justify-between pb-20rpx">
          <text class="text-26rpx text-gray-500">
            {{ task.taskNo }}
          </text>
          <text
            class="rounded-8rpx px-16rpx py-4rpx text-24rpx text-white"
            :style="{ backgroundColor: getTaskStatusColor(task.status as DeliveryTaskStatus) }"
          >
            {{ getTaskStatusName(task.status as DeliveryTaskStatus) }}
          </text>
        </view>

        <!-- 订单号 -->
        <view class="flex items-center pb-10rpx">
          <text class="i-carbon:shopping-cart mr-10rpx text-28rpx text-gray-400" />
          <text class="text-26rpx text-gray-600">
            订单号：
          </text>
          <text class="text-26rpx">
            {{ task.orderNo }}
          </text>
        </view>

        <!-- 收货人 -->
        <view class="flex items-center pb-10rpx">
          <text class="i-carbon:user mr-10rpx text-28rpx text-gray-400" />
          <text class="text-26rpx text-gray-600">
            收货人：
          </text>
          <text class="text-26rpx">
            {{ task.recipientName }}
          </text>
        </view>

        <!-- 收货地址 -->
        <view class="flex items-start pb-10rpx">
          <text class="i-carbon:location mr-10rpx mt-4rpx text-28rpx flex-none text-gray-400" />
          <text class="text-26rpx text-gray-600">
            收货地址：
          </text>
          <text class="flex-1 text-26rpx">
            {{ task.recipientAddress }}
          </text>
        </view>

        <!-- 底部箭头 -->
        <view class="mt-10rpx flex items-center justify-end border-t border-gray-100 pt-20rpx">
          <text class="text-24rpx text-primary">
            查看详情
          </text>
          <text class="i-carbon:chevron-right ml-4rpx text-24rpx text-primary" />
        </view>
      </view>
    </view>
  </z-paging>

  <!-- 底部Tab -->
  <view class="delivery-tabbar fixed bottom-0 left-0 right-0 flex border-t border-gray-200 bg-white">
    <view class="tabbar-item" @click="toWorkbench">
      <text class="i-carbon:dashboard text-40rpx text-gray-500" />
      <text class="mt-4rpx text-22rpx text-gray-500">
        工作台
      </text>
    </view>
    <view class="tabbar-item">
      <text class="i-carbon:list text-40rpx text-primary" />
      <text class="mt-4rpx text-22rpx text-primary">
        我的任务
      </text>
    </view>
    <view class="tabbar-item" @click="toProfile">
      <text class="i-carbon:user text-40rpx text-gray-500" />
      <text class="mt-4rpx text-22rpx text-gray-500">
        个人中心
      </text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.delivery-tabbar {
  padding-bottom: env(safe-area-inset-bottom, 0);
}

.tabbar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 16rpx 0;
}
</style>