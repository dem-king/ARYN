<script setup lang="ts">
import type { FormInstance } from 'wot-design-uni/components/wd-form/types'
import { onLoad, onPageScroll } from '@dcloudio/uni-app'
import { computed, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { getById, saveOrUpdateAddress } from '@/api/user/address'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { getTopPage, isPageAlive } from '@/utils/navigation-lock'
// @ts-expect-error: 省市区数据为纯 js 数据文件，无类型声明
import regionData from '@/sub-pages/components/region-picker/region-picker.js'
// @ts-expect-error: region-picker type declaration issue
import RegionPicker from '@/sub-pages/components/region-picker/region-picker.vue'

const { handlePageScroll } = useFrostedNavbar()
const router = useRouter()
/** setup 时的页面实例：保存请求在途时用户可能已返回，回来就不要再弹栈 */
const pageAtSetup = getTopPage()

definePage({
  name: 'address-form',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '收货地址',
  },
})
interface State {
  form: {
    id: string
    recipientName: string
    telephone: string
    postalCode: string
    isDefault: string
    provinceName: string
    cityName: string
    areaName: string
    provinceCode: string
    cityCode: string
    areaCode: string
    detailAddress: string
  }
}
interface RegionNode {
  name: string
  code: string
  children?: RegionNode[]
}
const formRef = ref<FormInstance>()
const { t } = useI18n()
const state = reactive<State>({
  form: {
    id: '',
    recipientName: '',
    telephone: '',
    postalCode: '',
    isDefault: '0',
    provinceName: '',
    cityName: '',
    areaName: '',
    provinceCode: '',
    cityCode: '',
    areaCode: '',
    detailAddress: '',
  },
})
const submitting = ref(false)
const region = ref()
const globalLoading = useGlobalLoading()

const regionText = computed(() => {
  const { provinceName, cityName, areaName } = state.form
  // 直辖市微信返回省市同名，展示时去重
  return [provinceName, cityName && cityName !== provinceName ? cityName : '', areaName]
    .filter(Boolean)
    .join(' ')
})

onLoad(async (options) => {
  if (options?.id) {
    getDetail(options?.id)
  }
})

function regionChange(e: any) {
  state.form.provinceCode = e.detail.code[0]
  state.form.cityCode = e.detail.code[1]
  state.form.areaCode = e.detail.code[2]
  state.form.provinceName = e.detail.value[0]
  state.form.cityName = e.detail.value[1]
  state.form.areaName = e.detail.value[2]
}

/**
 * 微信地址只有名称没有行政区划代码，按名称回查省市区库补齐 codes。
 * 直辖市数据源市级为「市辖区」，微信返回与省同名，需回退匹配。
 */
function resolveRegionCodes(provinceName: string, cityName: string, areaName: string) {
  const provinces = regionData as RegionNode[]
  const province = provinces.find(item => item.name === provinceName)
  const city = province?.children?.find(item => item.name === cityName)
    ?? (cityName === provinceName ? province?.children?.find(item => item.name === '市辖区') : undefined)
  const district = city?.children?.find(item => item.name === areaName)
  if (!province || !city || !district) {
    return false
  }
  state.form.provinceCode = province.code
  state.form.cityCode = city.code
  state.form.areaCode = district.code
  state.form.provinceName = provinceName
  state.form.cityName = cityName
  state.form.areaName = areaName
  return true
}

function chooseAddress() {
  uni.authorize({
    scope: 'scope.address',
    success() {
      uni.chooseAddress({
        success: (res) => {
          state.form.recipientName = res.userName
          state.form.telephone = res.telNumber
          state.form.postalCode = res.postalCode
          state.form.detailAddress = res.detailInfo
          const regionResolved = resolveRegionCodes(res.provinceName, res.cityName, res.countyName)
          if (regionResolved) {
            useGlobalToast().success(t('accountSettings.addressForm.imported'))
          }
          else {
            // 名称对不上省市区库时保持地区为空，交给用户手动选择
            useGlobalToast().warning(t('accountSettings.addressForm.regionRequired'))
          }
        },
      })
    },
  })
}

async function getDetail(id: string) {
  globalLoading.loading(t('common.loading'))

  try {
    const response = await getById(id)
    Object.assign(state.form, response)
  }
  finally {
    globalLoading.close()
  }
}

function submit() {
  if (submitting.value) {
    return
  }
  formRef.value?.validate()
    .then(({ valid }) => {
      if (!valid) {
        return
      }
      // 验证手机号是否正确
      if (!/^1[3-9]\d{9}$/.test(state.form.telephone)) {
        return useGlobalToast().warning(t('accountSettings.addressForm.invalidPhone'))
      }
      if (!state.form.provinceCode || !state.form.cityCode || !state.form.areaCode) {
        return useGlobalToast().warning(t('accountSettings.addressForm.regionRequired'))
      }
      submitting.value = true
      globalLoading.loading(t('accountSettings.addressForm.submitting'))
      saveOrUpdateAddress(state.form)
        .then(() => {
          globalLoading.close()
          // 保存期间用户已自行返回/被顶替时不发路由（对已销毁页面路由会触发 routeDone 报错）
          if (!isPageAlive(pageAtSetup)) {
            return
          }
          router.back()
        })
        .catch(() => {
          globalLoading.close()
          submitting.value = false
        })
    })
}

// 顶栏磨砂玻璃：字面量注册滚动（小程序按页面源码生成滚动位标记，包进 composable 会静默失效）
onPageScroll(handlePageScroll)
</script>

<template>
  <hr-navbar :title="t('accountSettings.address.navTitle')" />
  <view class="page">
    <!-- #ifdef MP-WEIXIN -->
    <view
      v-if="!state.form.id"
      class="wechat-entry"
      hover-class="hv-dim"
      :hover-stay-time="80"
      @tap="chooseAddress"
    >
      <view class="wechat-entry__badge">
        <wd-icon name="chat" size="44rpx" color="#ffffff" />
      </view>
      <view class="wechat-entry__body">
        <view class="wechat-entry__title">
          {{ t('accountSettings.addressForm.importWechat') }}
        </view>
        <view class="wechat-entry__hint">
          {{ t('accountSettings.addressForm.importWechatHint') }}
        </view>
      </view>
      <wd-icon name="arrow-right" size="32rpx" color="#c8c9cc" />
    </view>
    <!-- #endif -->

    <wd-form ref="formRef" :model="state.form">
      <view class="form-card">
        <view class="form-row">
          <wd-input
            v-model="state.form.recipientName"
            :label="t('accountSettings.addressForm.name')"
            label-width="100px"
            prop="recipientName"
            :placeholder="t('accountSettings.addressForm.namePlaceholder')"
            clearable
            no-border
            :rules="[{ required: true, message: t('accountSettings.addressForm.nameRequired') }]"
          />
        </view>
        <view class="form-row form-row--divider">
          <wd-input
            v-model="state.form.telephone"
            :label="t('accountSettings.addressForm.phone')"
            label-width="100px"
            prop="telephone"
            type="number"
            :maxlength="11"
            :placeholder="t('accountSettings.addressForm.phonePlaceholder')"
            clearable
            no-border
            :rules="[{ required: true, message: t('accountSettings.addressForm.phoneRequired') }]"
          />
        </view>
        <view class="form-row form-row--divider">
          <region-picker :value="region" @change="regionChange">
            <view
              class="region-row"
              hover-class="hv-dim"
              :hover-stay-time="80"
            >
              <view class="region-row__label">
                <text class="region-row__required">
                  *
                </text>
                <text>{{ t('accountSettings.addressForm.region') }}</text>
              </view>
              <view class="region-row__value" :class="{ 'is-placeholder': !regionText }">
                <text class="region-row__text">
                  {{ regionText || t('accountSettings.addressForm.regionPlaceholder') }}
                </text>
                <wd-icon name="arrow-right" size="32rpx" color="#c8c9cc" custom-class="region-row__arrow" />
              </view>
            </view>
          </region-picker>
        </view>
        <view class="form-row form-row--divider">
          <wd-textarea
            v-model="state.form.detailAddress"
            :label="t('accountSettings.addressForm.detail')"
            label-width="100px"
            prop="detailAddress"
            :placeholder="t('accountSettings.addressForm.detailPlaceholder')"
            auto-height
            no-border
            :rules="[{ required: true, message: t('accountSettings.addressForm.detailRequired') }]"
          />
        </view>
        <view class="form-row form-row--divider">
          <wd-input
            v-model="state.form.postalCode"
            :label="t('accountSettings.addressForm.postalCode')"
            label-width="100px"
            prop="postalCode"
            type="number"
            :placeholder="t('accountSettings.addressForm.postalCodePlaceholder')"
            clearable
            no-border
          />
        </view>
        <view class="form-row form-row--divider switch-row">
          <view class="switch-row__text">
            <text class="switch-row__label">
              {{ t('accountSettings.addressForm.defaultTitle') }}
            </text>
            <text class="switch-row__hint">
              {{ t('accountSettings.addressForm.defaultHint') }}
            </text>
          </view>
          <wd-switch v-model="state.form.isDefault" active-value="1" inactive-value="0" />
        </view>
      </view>
    </wd-form>
  </view>

  <view class="footer-bar">
    <wd-button block custom-class="submit-btn" :loading="submitting" @click="submit">
      {{ t('accountSettings.addressForm.submit') }}
    </wd-button>
  </view>
</template>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  padding-bottom: calc(env(safe-area-inset-bottom) + 200rpx);
  background: var(--app-bg);
}

.hv-dim {
  opacity: 0.75;
}

.wechat-entry {
  display: flex;
  align-items: center;
  margin: 24rpx 24rpx 0;
  padding: 24rpx;
  border-radius: 24rpx;
  background: var(--app-card);
}

.wechat-entry__badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80rpx;
  height: 80rpx;
  margin-right: 20rpx;
  border-radius: 50%;
  background: #07c160;
  flex-shrink: 0;
}

.wechat-entry__body {
  flex: 1;
  min-width: 0;
}

.wechat-entry__title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--app-text);
}

.wechat-entry__hint {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: var(--app-text-3);
}

.form-card {
  margin: 24rpx;
  border-radius: 24rpx;
  background: var(--app-card);
  overflow: hidden;
}

.form-row--divider {
  position: relative;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    right: 0;
    left: 15px;
    height: 1rpx;
    background: var(--app-border);
  }
}

.region-row {
  display: flex;
  align-items: center;
  padding: 10px 15px;
}

.region-row__label {
  display: flex;
  align-items: center;
  width: 100px;
  margin-right: 15px;
  font-size: 14px;
  color: var(--app-text);
  flex-shrink: 0;
}

.region-row__required {
  margin-right: 2px;
  color: var(--wot-color-danger, #fa4350);
}

.region-row__value {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 1;
  min-width: 0;
  font-size: 14px;
  color: var(--app-text);

  &.is-placeholder {
    color: var(--app-text-3);
  }
}

.region-row__text {
  flex: 1;
  min-width: 0;
  padding-right: 12rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.region-row__arrow {
  flex-shrink: 0;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 15px;
}

.switch-row__text {
  display: flex;
  flex-direction: column;
}

.switch-row__label {
  font-size: 14px;
  color: var(--app-text);
}

.switch-row__hint {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: var(--app-text-3);
}

.footer-bar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 100;
  padding: 16rpx 24rpx;
  padding-bottom: calc(env(safe-area-inset-bottom) + 16rpx);
  background: var(--app-card);
  border-top: 1rpx solid var(--app-border);
}

.submit-btn {
  height: 88rpx !important;
  font-size: 32rpx !important;
}
</style>
