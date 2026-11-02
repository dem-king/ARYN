<script setup lang="ts">
import { computed } from 'vue'
import { onLoad, onPageScroll, onShow } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { useUserLogout } from '@/composables/useUserLogout'

const { handlePageScroll } = useFrostedNavbar()

definePage({
  name: 'user-setting-index',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '个人信息',
  },
})

const router = useRouter()
const { t } = useI18n()
const { localeLabel, chooseLanguage } = useLocaleSwitch()
const userStore = useUserStore()
const { confirmLogout } = useUserLogout()
const maskedPhone = computed(() => {
  const p = userStore.getUserPhone
  if (!p)
    return t('userSetting.notBound')
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
function toLoginDevices() {
  router.push({ name: 'user-login-devices' })
}
onLoad(() => {})
onShow(() => {
  userStore.refreshPointsInfo()
})

// 顶栏磨砂玻璃：字面量注册滚动（小程序按页面源码生成滚动位标记，包进 composable 会静默失效）
onPageScroll(handlePageScroll)
</script>

<template>
  <view>
    <hr-navbar :title="t('userSetting.title')" />

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
              {{ userStore.getUserNickname || t('userSetting.nicknameNotSet') }}
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
                {{ t('userSetting.memberCenter') }}
              </text>
            </view>
          </template>
          <view class="text-14px text-gray-400">
            {{ t('userSetting.pointsBalance', { points: userStore.getPoint, balance: userStore.getBalance }) }}
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
                {{ t('userSetting.phone') }}
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
                {{ t('userSetting.changePassword') }}
              </text>
            </view>
          </template>
          <wd-text :text="t('userSetting.tapToChange')" size="13px" color="#909090" />
        </wd-cell>
        <wd-cell is-link @click="toAddressList">
          <template #title>
            <view class="flex items-center">
              <wd-icon name="location" size="24px" color="var(--wot-color-theme)" />
              <text class="ml-10rpx">
                {{ t('userSetting.addressManagement') }}
              </text>
            </view>
          </template>
          <wd-text :text="t('userSetting.setNow')" size="13px" color="#909090" />
        </wd-cell>
        <wd-cell is-link @click="chooseLanguage">
          <template #title>
            <view class="flex items-center">
              <wd-icon name="translate-bold" size="24px" color="var(--wot-color-theme)" />
              <text class="ml-10rpx">
                {{ t('common.language') }}
              </text>
            </view>
          </template>
          <view class="text-14px">
            {{ localeLabel }}
          </view>
        </wd-cell>
        <!-- 三期：登录设备管理（当前会话 + 下线其它会话；当前设备请从底部退出登录） -->
        <wd-cell is-link @click="toLoginDevices">
          <template #title>
            <view class="flex items-center">
              <wd-icon name="computer" size="24px" color="var(--wot-color-theme)" />
              <text class="ml-10rpx">
                {{ t('loginDevices.entry') }}
              </text>
            </view>
          </template>
        </wd-cell>
      </wd-cell-group>
    </view>
    <view class="m-4">
      <wd-button custom-class="bg-secondary! border-secondary!" block @click="confirmLogout">
        {{ t('userSetting.logout') }}
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
