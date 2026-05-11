<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getById, saveOrUpdateAddress } from '@/api/user/address'
// @ts-expect-error: region-picker type declaration issue
import RegionPicker from '@/components/region-picker/region-picker.vue'

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
const formRef = ref()
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
const region = ref()
const globalLoading = useGlobalLoading()
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
function chooseAddress() {
  uni.authorize({
    scope: 'scope.address',
    success() {
      uni.chooseAddress({
        success: async (res) => {
          state.form.recipientName = res.userName
          state.form.telephone = res.telNumber
          state.form.postalCode = res.postalCode
          state.form.provinceName = res.provinceName
          state.form.detailAddress = res.detailInfo
          state.form.telephone = res.telNumber
        },
      })
    },
    fail(res) {
      console.log(res)
    },
  })
}

async function getDetail(id: string) {
  globalLoading.loading('加载中...')

  try {
    const response = await getById(id)
    Object.assign(state.form, response)
  }
  finally {
    globalLoading.close()
  }
}

function submit() {
  formRef.value
    .validate()
    .then(({ valid, errors }) => {
      if (valid) {
        // 验证手机号是否正确
        if (!/^1[3-9]\d{9}$/.test(state.form.telephone)) {
          return useGlobalToast().warning('请输入正确的手机号')
        }
        if (!state.form.provinceCode || !state.form.cityCode || !state.form.areaCode) {
          return useGlobalToast().warning('请选择收货地址')
        }
        globalLoading.loading('提交中...')
        saveOrUpdateAddress(state.form).then(() => {
          globalLoading.close()
          uni.navigateBack()
        })
      }
    })
}
</script>

<template>
  <hr-navbar title="收货地址" />
  <view class="bg-white">
    <view class="p-2">
      <wd-form ref="formRef" :model="state.form">
        <wd-cell-group custom-class="group" border>
          <wd-input
            v-model="state.form.recipientName" label="姓名" label-width="100px" prop="recipientName"
            placeholder="请输入姓名" :rules="[{ required: true, message: '请填写姓名' }]"
          />
          <wd-input
            v-model="state.form.telephone" label="联系电话" label-width="100px" prop="telephone"
            placeholder="请输入联系电话" :rules="[{ required: true, message: '请填写联系电话' }]"
          />
          <wd-input
            v-model="state.form.postalCode" label="邮政编码" label-width="100px" prop="postalCode"
            placeholder="请输入邮政编码"
          />
          <wd-cell title="收货地址" title-width="100px" required>
            <view style="text-align: left">
              <region-picker :value="region" @change="regionChange">
                <view :class="state.form.provinceName ? 'text-[#333]' : 'text-[#bfbfbf]'">
                  {{ state.form.provinceName
                    ? `${state.form.provinceName} ${
                      state.form.cityName} ${
                      state.form.areaName}` : '请选择收货地址' }}
                </view>
              </region-picker>
            </view>
          </wd-cell>
          <wd-textarea
            v-model="state.form.detailAddress" label="详细地址" label-width="100px" placeholder="请输入详细地址"
            prop="detailAddress" :rules="[{ required: true, message: '请填写详细地址' }]"
            auto-height
          />
          <wd-cell title="设为默认地址" label="下单时会优先使用该地址" title-width="200px">
            <wd-checkbox v-model="state.form.isDefault" true-value="1" false-value="0" />
          </wd-cell>
        </wd-cell-group>
      </wd-form>
    </view>
    <view class="fixed bottom-0 left-0 right-0 border-t border-t-[rgba(255,255,255,0.33)] bg-white p-20rpx pb-[max(env(safe-area-inset-bottom),16rpx)]">
      <wd-button block type="primary" @click="submit">
        确认
      </wd-button>
      <!-- #ifdef MP-WEIXIN -->
      <wd-button
        v-if="!state.form.id"
        custom-class="mt-2!" block type="success"
        @click="chooseAddress"
      >
        导入微信地址
      </wd-button>
      <!-- #endif -->
    </view>
  </view>
</template>

<style lang="scss" scoped>
</style>
