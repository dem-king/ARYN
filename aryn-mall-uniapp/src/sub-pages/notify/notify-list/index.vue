<script setup lang="ts">
import { nextTick, reactive, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getPage, getUnreadCount, markAsRead, markAllAsRead } from '@/api/notify/notifyMessage'

definePage({
  name: 'notify-list',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '消息通知',
  },
})

interface State {
  queryParams: {
    notifyType: number | ''
  }
  tabCurrent: number
  notifyList: Array<any>
  unreadMap: Record<number, number>
}

const tabs = [
  { name: '全部', value: 0 },
  { name: '订单', value: 1 },
  { name: '支付', value: 2 },
  { name: '物流', value: 3 },
  { name: '营销', value: 4 },
  { name: '系统', value: 5 },
]

const pagingRef = ref()
const router = useRouter()

const state = reactive<State>({
  queryParams: {
    notifyType: '',
  },
  tabCurrent: 0,
  notifyList: [],
  unreadMap: {},
})

onLoad(() => {
  nextTick(() => {
    pagingRef.value?.reload()
  })
  loadUnread()
})

onShow(() => {
  loadUnread()
})

// 监听 WebSocket 新消息
uni.$on('notify:new', () => {
  pagingRef.value?.reload()
  loadUnread()
})
uni.$on('notify:unread-refresh', () => {
  loadUnread()
})

async function queryList(pageNo: number, pageSize: number) {
  try {
    const response = await getPage({
      current: pageNo,
      size: pageSize,
      notifyType: state.queryParams.notifyType || undefined,
    })
    pagingRef.value?.complete(response.records || [])
  }
  catch (e) {
    pagingRef.value?.complete(false)
  }
}

async function loadUnread() {
  try {
    const res = await getUnreadCount()
    state.unreadMap = res?.counts || {}
  }
  catch (e) {
    console.warn('加载未读数失败', e)
  }
}

function changeTab({ index }: { index: number, name: string }) {
  state.tabCurrent = index
  state.queryParams.notifyType = tabs[index].value as number | ''
  pagingRef.value?.reload()
}

async function handleClick(msg: any) {
  // 标记已读
  if (msg.readStatus === '0') {
    try {
      await markAsRead(msg.id)
      msg.readStatus = '1'
      loadUnread()
    }
    catch (e) {
      console.warn('标记已读失败', e)
    }
  }
  // 跳转
  if (msg.jumpType === 1 && msg.bizId) {
    router.push({ name: 'order-detail', params: { id: msg.bizId } })
  }
  else if (msg.jumpType === 4 && msg.jumpUrl) {
    router.push({ path: msg.jumpUrl })
  }
}

async function handleReadAll() {
  try {
    await markAllAsRead(
      state.queryParams.notifyType ? Number(state.queryParams.notifyType) : undefined,
    )
    pagingRef.value?.reload()
    loadUnread()
    uni.showToast({ title: '已全部标为已读', icon: 'success' })
  }
  catch (e) {
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}

function formatTime(t: string): string {
  if (!t) return ''
  // 简单格式化：截取到分钟
  return t.replace('T', ' ').substring(0, 16)
}

function notifyTypeIcon(type: number): string {
  const icons: Record<number, string> = {
    1: 'i-carbon:package',
    2: 'i-carbon:wallet',
    3: 'i-carbon:delivery-truck',
    4: 'i-carbon:tag',
    5: 'i-carbon:settings',
    6: 'i-carbon:chat',
  }
  return icons[type] || 'i-carbon:notification'
}
</script>

<template>
  <z-paging
    ref="pagingRef"
    v-model="state.notifyList"
    :auto="false"
    @query="queryList"
  >
    <template #top>
      <hr-navbar title="消息通知" />
      <wd-tabs
        v-model="state.tabCurrent"
        slidable="always"
        swipeable
        @change="changeTab"
      >
        <block v-for="(item, index) in tabs" :key="index">
          <wd-tab :title="item.name" />
        </block>
      </wd-tabs>
    </template>

    <view class="px-20rpx">
      <view
        v-for="(msg, index) in state.notifyList"
        :key="index"
        class="my-20rpx rounded-xl bg-white p-20rpx"
        :class="{ 'border-l-4 border-red-500': msg.readStatus === '0' }"
        @click="handleClick(msg)"
      >
        <view class="flex items-start">
          <view class="mr-20rpx flex h-60rpx w-60rpx flex-none items-center justify-center rounded-full bg-gray-100">
            <view :class="notifyTypeIcon(msg.notifyType)" class="text-30rpx" />
          </view>
          <view class="flex-1 overflow-hidden">
            <view class="flex items-center justify-between">
              <view class="flex-1 truncate text-15px font-medium">
                {{ msg.title }}
              </view>
              <view v-if="msg.readStatus === '0'" class="ml-10rpx h-14rpx w-14rpx flex-none rounded-full bg-red-500" />
            </view>
            <view class="pt-5rpx text-13px text-gray leading-relaxed">
              {{ msg.content }}
            </view>
            <view class="pt-10rpx text-12px text-gray">
              {{ formatTime(msg.createTime) }}
            </view>
          </view>
        </view>
      </view>
    </view>

    <template #bottom>
      <view v-if="state.notifyList.length" class="p-20rpx">
        <wd-button block plain @click="handleReadAll">
          全部已读
        </wd-button>
      </view>
    </template>
  </z-paging>
</template>
