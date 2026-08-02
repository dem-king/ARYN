<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Local } from '@/utils/storage'
import { getWechatBindStatus, bindWechatByCode, getWxLoginCode, requestSubscribeMessage } from '@/api/delivery'

definePage({
  name: 'delivery-profile',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '配送员中心',
  },
})

const router = useRouter()
const { show: showToast } = useGlobalToast()

/** 配送员信息 */
const staffInfo = ref<any>({})

/** 微信订阅绑定状态 */
const wechatBound = ref(false)
const subscribing = ref(false)

// 从本地存储获取配送员信息
const storedInfo = Local.get('deliveryStaffInfo')
if (storedInfo) {
  staffInfo.value = storedInfo
}

onMounted(async () => {
  try {
    wechatBound.value = await getWechatBindStatus()
  } catch {
    // 忽略
  }
})

/** 跳转到工作台 */
function toWorkbench() {
  router.replaceAll({ name: 'delivery-index' })
}

/** 跳转到我的任务 */
function toTaskList() {
  router.push({ name: 'delivery-task-list' })
}

/** 绑定微信订阅消息 */
async function handleWechatBind() {
  if (wechatBound.value || subscribing.value) return
  subscribing.value = true
  try {
    const code = await getWxLoginCode()
    if (!code) {
      showToast('请在微信小程序中操作')
      return
    }
    const appId = staffInfo.value?.appId || ''
    if (!appId) {
      showToast('未获取到小程序AppID')
      return
    }
    await bindWechatByCode(appId, code)
    wechatBound.value = true
    showToast('绑定成功')
  } catch (e: any) {
    showToast(e?.message || '绑定失败')
  } finally {
    subscribing.value = false
  }
}

/** 请求订阅消息授权 */
async function handleSubscribeAuth() {
  try {
    const templateIds = staffInfo.value?.subscribeTemplateIds || []
    if (templateIds.length === 0) {
      showToast('暂无订阅模板')
      return
    }
    const res = await requestSubscribeMessage(templateIds)
    const accepted = Object.values(res).filter((v) => v === 'accept').length
    showToast(`已授权 ${accepted} 条订阅消息`)
  } catch {
    showToast('授权失败')
  }
}

/** 退出登录 */
function handleLogout() {
  uni.showModal({
    title: '提示',
    content: '确定要退出登录吗？',
    success: (res) => {
      if (res.confirm) {
        Local.remove('deliveryToken')
        Local.remove('deliveryStaffInfo')
        showToast('已退出登录')
        router.replaceAll({ name: 'delivery-login' })
      }
    },
  })
}
</script>

<template>
  <hr-navbar title="配送员中心" />
  <view class="min-h-screen bg-gray-50 pb-100rpx">
    <!-- 配送员信息卡片 -->
    <view class="m-20rpx rounded-20rpx bg-gradient-to-r from-primary to-primary-light p-30rpx text-white">
      <view class="flex items-center">
        <view class="staff-avatar">
          <text class="i-carbon:user-avatar text-60rpx text-white" />
        </view>
        <view class="ml-30rpx">
          <text class="text-32rpx font-bold">
            {{ staffInfo.staffName || '配送员' }}
          </text>
          <view class="mt-10rpx text-24rpx opacity-80">
            {{ staffInfo.phone || '' }}
          </view>
        </view>
      </view>
    </view>

    <!-- 功能菜单 -->
    <view class="mx-20rpx mb-20rpx rounded-20rpx bg-white p-30rpx">
      <view class="menu-item" @click="toWorkbench">
        <text class="i-carbon:dashboard mr-20rpx text-40rpx text-primary" />
        <text class="flex-1 text-28rpx">
          配送工作台
        </text>
        <text class="i-carbon:chevron-right text-28rpx text-gray-400" />
      </view>
      <view class="menu-item" @click="toTaskList">
        <text class="i-carbon:list mr-20rpx text-40rpx text-primary" />
        <text class="flex-1 text-28rpx">
          我的任务
        </text>
        <text class="i-carbon:chevron-right text-28rpx text-gray-400" />
      </view>
      <view class="menu-item" @click="handleWechatBind">
        <text class="i-carbon:logo-wechat mr-20rpx text-40rpx text-primary" />
        <text class="flex-1 text-28rpx">
          微信订阅绑定
        </text>
        <text v-if="wechatBound" class="text-24rpx text-green-500">
          已绑定
        </text>
        <text v-else class="text-24rpx text-gray-400">
          {{ subscribing ? '绑定中...' : '未绑定' }}
        </text>
      </view>
      <view v-if="wechatBound" class="menu-item" @click="handleSubscribeAuth">
        <text class="i-carbon:notification mr-20rpx text-40rpx text-primary" />
        <text class="flex-1 text-28rpx">
          订阅消息授权
        </text>
        <text class="i-carbon:chevron-right text-28rpx text-gray-400" />
      </view>
    </view>

    <!-- 退出登录 -->
    <view class="mx-20rpx rounded-20rpx bg-white p-30rpx">
      <view class="menu-item" @click="handleLogout">
        <text class="i-carbon:logout mr-20rpx text-40rpx text-red-500" />
        <text class="flex-1 text-28rpx text-red-500">
          退出登录
        </text>
      </view>
    </view>
  </view>

  <!-- 底部Tab -->
  <view class="delivery-tabbar fixed bottom-0 left-0 right-0 flex border-t border-gray-200 bg-white">
    <view class="tabbar-item" @click="toWorkbench">
      <text class="i-carbon:dashboard text-40rpx text-gray-500" />
      <text class="mt-4rpx text-22rpx text-gray-500">
        工作台
      </text>
    </view>
    <view class="tabbar-item" @click="toTaskList">
      <text class="i-carbon:list text-40rpx text-gray-500" />
      <text class="mt-4rpx text-22rpx text-gray-500">
        我的任务
      </text>
    </view>
    <view class="tabbar-item">
      <text class="i-carbon:user text-40rpx text-primary" />
      <text class="mt-4rpx text-22rpx text-primary">
        个人中心
      </text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.staff-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.25);
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 30rpx 0;
  border-bottom: 1px solid #f5f5f5;

  &:last-child {
    border-bottom: none;
  }
}

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