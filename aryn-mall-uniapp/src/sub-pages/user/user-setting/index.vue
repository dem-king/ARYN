<script setup lang="ts">
import { computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'

definePage({
  name: 'user-setting-index',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '个人信息',
  },
})

const router = useRouter()
const userStore = useUserStore()
const authStore = useAuthStore()
const globalLoading = useGlobalLoading()
const { success: showSuccess } = useGlobalToast()
const { confirm } = useGlobalMessage()
const maskedPhone = computed(() => {
  const p = userStore.getUserPhone
  if (!p)
    return '未绑定'
  return p.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
})

function toUserDetail() {
  router.push({ name: 'user-setting' })
}
function toAddressList() {
  router.push({ name: 'address-list' })
}
function updatePassword() {
  router.push({ name: 'user-setting-password' })
}
function updatePhone() {
  router.push({ name: 'user-setting-phone' })
}
function toMemberCenter() {
  router.push({ name: 'member-center' })
}

function logout() {
  confirm({
    title: '退出系统',
    msg: '确认退出系统？',
    closeOnClickModal: false,
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    success: (res) => {
      if (res.action === 'confirm') {
        globalLoading.loading('退出中...')
        authStore.userLogout().then(() => {
          showSuccess('退出成功')
          router.replaceAll({ name: 'home' })
        })
      }
    },
  })
}
onLoad(() => {})
onShow(() => {
  userStore.refreshPointsInfo()
})
</script>

<template>
  <view>
    <hr-navbar title="个人信息" />

    <!-- 顶部个人卡片 -->
    <view class="m-2 rounded-xl bg-white p-4" @click="toUserDetail">
      <view class="flex items-center">
        <image
          :src="userStore.getUserAvatar || '/static/images/default-avatar.png'"
          class="h-80rpx w-80rpx rounded-full"
          mode="aspectFill"
        />
        <view class="ml-20rpx flex-1">
          <view class="flex items-center">
            <view class="text-16px">
              {{ userStore.getUserNickname || '未设置昵称' }}
            </view>
            <view v-if="userStore.getLevelName" class="level-tag ml-2">
              {{ userStore.getLevelName }}
            </view>
          </view>
        </view>
        <wd-icon name="arrow-right" size="20px" color="#c0c0c0" />
      </view>
    </view>

    <!-- 会员信息 -->
    <view class="m-2 rounded-xl bg-white p-2">
      <wd-cell-group border>
        <wd-cell is-link @click="toMemberCenter">
          <template #title>
            <view class="flex items-center">
              <wd-icon name="vip-1" size="24px" color="var(--wot-color-theme)" />
              <text class="ml-10rpx">
                会员中心
              </text>
            </view>
          </template>
          <view class="text-14px text-gray-400">
            积分{{ userStore.getPoint }} | 余额{{ userStore.getBalance }}
          </view>
        </wd-cell>
      </wd-cell-group>
    </view>

    <!-- 信息与设置列表 -->
    <view class="m-2 rounded-xl bg-white p-2">
      <wd-cell-group border>
        <wd-cell is-link @click="updatePhone">
          <template #title>
            <view class="flex items-center">
              <wd-icon name="mobile" size="24px" color="var(--wot-color-theme)" />
              <text class="ml-10rpx">
                手机号码
              </text>
            </view>
          </template>
          <view class="text-14px">
            {{ maskedPhone }}
          </view>
        </wd-cell>
        <wd-cell is-link @click="updatePassword">
          <template #title>
            <view class="flex items-center">
              <wd-icon name="lock-on" size="24px" color="var(--wot-color-theme)" />
              <text class="ml-10rpx">
                修改密码
              </text>
            </view>
          </template>
          <wd-text text="点击修改" size="13px" color="#909090" />
        </wd-cell>
        <wd-cell is-link @click="toAddressList">
          <template #title>
            <view class="flex items-center">
              <wd-icon name="location" size="24px" color="var(--wot-color-theme)" />
              <text class="ml-10rpx">
                地址管理
              </text>
            </view>
          </template>
          <wd-text text="立即设置" size="13px" color="#909090" />
        </wd-cell>
      </wd-cell-group>
    </view>
    <view class="m-4">
      <wd-button custom-class="bg-secondary! border-secondary!" block @click="logout">
        退出登录
      </wd-button>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.rounded-xl {
  border-radius: 16rpx;
}

.level-tag {
  display: inline-block;
  border: 1px solid #667eea;
  border-radius: 50rpx;
  background: linear-gradient(135deg, #667eea, #764ba2);
  padding: 2rpx 12rpx;
  font-size: 20rpx;
  color: #fff;
}
</style>
