<script setup lang="ts">
/**
 * 登录设备（活跃会话管理，三期契约 §2.1/§2.2）。
 *
 * 能力：
 *  - 当前设备卡片（系统/浏览器、地点、登录时间、「当前设备」标记）；
 *  - 其他会话列表 + 「下线」（二次确认 → DELETE → 刷新列表）；当前设备不可下线
 *    （服务端也会拒绝，当前设备请走底部「退出登录」）；
 *  - 单点策略说明；
 *  - 底部「退出登录」复用 user-setting 的同一份退出逻辑（useUserLogout）。
 *
 * 数据/决策在 `useLoginDevices` + `presentation.ts`（node 环境可单测），
 * 页面只做渲染与 toast；接口路径由请求层自动做 boot 改写，页面不拼 /boot。
 */
import type { LoginSessionDevice } from '@/api/session-devices'
import { onPageScroll, onShow } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { useUserLogout } from '@/composables/useUserLogout'
import {
  canRemoveLoginDevice,
  resolveDeviceLoginTimeText,
  resolveDeviceSystemText,
} from './presentation'
import { useLoginDevices } from './useLoginDevices'

const { handlePageScroll } = useFrostedNavbar()

definePage({
  name: 'user-login-devices',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '登录设备',
  },
})

const { t } = useI18n()
const { confirm } = useGlobalMessage()
const { show: showToast, success: showSuccess } = useGlobalToast()
const { confirmLogout } = useUserLogout()
const { view, loading, loaded, loadError, removingId, load, remove } = useLoginDevices()

onShow(() => {
  void refresh()
})

async function refresh() {
  const ok = await load()
  if (!ok)
    showToast(t('loginDevices.loadFailed'))
}

function systemText(device: LoginSessionDevice | null) {
  return resolveDeviceSystemText(device) || t('loginDevices.unknownDevice')
}

function loginTimeText(device: LoginSessionDevice | null) {
  return resolveDeviceLoginTimeText(device)
}

/** 下线：二次确认后调 DELETE；当前设备/脏数据不发起请求（服务端同样禁止）。 */
function onKick(device: LoginSessionDevice) {
  if (!canRemoveLoginDevice(device)) {
    showToast(t('loginDevices.currentDeviceCannotKick'))
    return
  }
  confirm({
    title: t('loginDevices.kickTitle'),
    msg: t('loginDevices.kickConfirm'),
    closeOnClickModal: false,
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel'),
    success: (res) => {
      if (res.action !== 'confirm')
        return
      void kick(device.id)
    },
  })
}

async function kick(id: string) {
  const result = await remove(id)
  if (result === 'removed') {
    showSuccess(t('loginDevices.kickSuccess'))
    return
  }
  if (result === 'blocked-current')
    showToast(t('loginDevices.currentDeviceCannotKick'))
  else
    showToast(t('loginDevices.kickFailed'))
}

// 顶栏磨砂玻璃：字面量注册滚动（小程序按页面源码生成滚动位标记，包进 composable 会静默失效）
onPageScroll(handlePageScroll)
</script>

<template>
  <view class="min-h-screen pb-40rpx">
    <hr-navbar :title="t('loginDevices.title')" />

    <!-- 首次加载：轻量 loading（不阻塞导航栏） -->
    <view v-if="loading && !loaded" class="m-2 flex items-center justify-center rounded-16rpx bg-white p-4">
      <wd-loading />
      <text class="ml-2 text-24rpx text-gray-400">
        {{ t('common.loading') }}
      </text>
    </view>

    <!-- 首次加载失败：错误态 + 重试（不阻塞页面，可退出登录） -->
    <view v-else-if="!loaded && loadError" class="m-2 rounded-16rpx bg-white p-4 text-center">
      <text class="text-26rpx text-gray-500">
        {{ t('loginDevices.loadFailed') }}
      </text>
      <view class="mt-20rpx">
        <wd-button size="small" plain @click="refresh">
          {{ t('loginDevices.retry') }}
        </wd-button>
      </view>
    </view>

    <!-- 空态 -->
    <view v-else-if="view.isEmpty" class="m-2 rounded-16rpx bg-white p-6 text-center">
      <text class="i-carbon:devices text-60rpx text-gray-300" />
      <view class="mt-2 text-26rpx text-gray-500">
        {{ t('loginDevices.empty') }}
      </view>
      <view class="mt-1 text-22rpx text-gray-400">
        {{ t('loginDevices.emptyHint') }}
      </view>
    </view>

    <template v-else>
      <!-- 当前设备 -->
      <view v-if="view.current" class="m-2 rounded-16rpx bg-white p-3">
        <view class="flex items-center justify-between">
          <text class="text-30rpx font-bold">
            {{ t('loginDevices.currentDevice') }}
          </text>
          <wd-tag type="primary" plain>
            {{ t('loginDevices.currentTag') }}
          </wd-tag>
        </view>
        <view class="mt-2 text-26rpx text-gray-700">
          {{ systemText(view.current) }}
        </view>
        <view v-if="view.current.location" class="mt-1 text-24rpx text-gray-500">
          {{ t('loginDevices.location', { location: view.current.location }) }}
        </view>
        <view v-if="loginTimeText(view.current)" class="mt-1 text-24rpx text-gray-500">
          {{ t('loginDevices.loginTime', { time: loginTimeText(view.current) }) }}
        </view>
        <view class="mt-2 text-22rpx text-gray-400">
          {{ t('loginDevices.currentDeviceCannotKick') }}
        </view>
      </view>

      <!-- 其他会话 -->
      <view class="m-2 rounded-16rpx bg-white p-3">
        <view class="text-28rpx font-bold">
          {{ t('loginDevices.otherDevices') }}
        </view>
        <view v-if="view.others.length === 0" class="py-30rpx text-center text-24rpx text-gray-400">
          {{ t('loginDevices.noOthers') }}
        </view>
        <view
          v-for="device in view.others"
          :key="device.id"
          class="mt-2 flex items-center justify-between border-t border-[#f0f0f0] pt-2"
        >
          <view class="flex-1 pr-2">
            <view class="text-26rpx text-gray-700">
              {{ systemText(device) }}
            </view>
            <view v-if="device.location" class="mt-1 text-24rpx text-gray-500">
              {{ t('loginDevices.location', { location: device.location }) }}
            </view>
            <view v-if="loginTimeText(device)" class="mt-1 text-24rpx text-gray-500">
              {{ t('loginDevices.loginTime', { time: loginTimeText(device) }) }}
            </view>
          </view>
          <wd-button
            size="small"
            plain
            :loading="removingId === device.id"
            @click="onKick(device)"
          >
            {{ t('loginDevices.kick') }}
          </wd-button>
        </view>
      </view>

      <!-- 单点策略说明：页面必须如实说明当前策略（契约红线） -->
      <view class="mx-2 mt-1 text-22rpx leading-5 text-gray-400">
        {{ t('loginDevices.policyTip') }}
      </view>

      <!-- 退出登录：与个人设置页同一份逻辑（服务端登出 + 清态 + 回首页） -->
      <view class="m-4">
        <wd-button custom-class="bg-secondary! border-secondary!" block @click="confirmLogout">
          {{ t('userSetting.logout') }}
        </wd-button>
      </view>
    </template>
  </view>
</template>
