<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { editPhone } from '@/api/user/user'
import { sendSmsCode } from '@/api/upms/sms'

definePage({
  name: 'user-setting-phone',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '修改手机号',
  },
})

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const globalLoading = useGlobalLoading()
const { success: showSuccess, show } = useGlobalToast()

const state = reactive<{ form: { oldPhone: string, newPhone: string, code: string } }>({
  form: { oldPhone: '', newPhone: '', code: '' },
})

const countdown = ref(0)
let timer: any = null

onLoad(() => {
  state.form.oldPhone = userStore.getUserPhone || ''
})
onUnload(() => {
  if (timer)
    clearInterval(timer)
})

function startCountdown() {
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

function sendCode() {
  const phone = state.form.newPhone
  if (!/^1\d{10}$/.test(phone)) {
    show('请输入正确的手机号')
    return
  }
  if (countdown.value > 0)
    return
  globalLoading.loading('发送中...')
  sendSmsCode('update', phone)
    .then(() => {
      showSuccess('验证码已发送')
      startCountdown()
    })
    .finally(() => {
      globalLoading.close()
    })
}

function submit() {
  formRef.value
    .validate()
    .then(({ valid }: any) => {
      if (!valid)
        return
      globalLoading.loading('提交中...')
      editPhone({ phone: state.form.newPhone, code: state.form.code })
        .then(() => {
          showSuccess('修改成功')
          userStore.fetchUserInfo?.()
          setTimeout(() => {
            const pages = getCurrentPages()
            if (pages.length > 1) {
              router.back()
            }
            else {
              router.pushTab({ name: 'home' })
            }
          }, 1000)
        })
        .finally(() => {
          globalLoading.close()
        })
    })
}
</script>

<template>
  <view class="bg-white p-2">
    <hr-navbar title="修改手机号" />
    <wd-form ref="formRef" :model="state.form">
      <wd-cell-group border>
        <wd-cell title="原手机号">
          <view class="text-14px">
            {{ state.form.oldPhone || '未绑定' }}
          </view>
        </wd-cell>
        <wd-input
          v-model="state.form.newPhone"
          label="新手机号"
          label-width="100px"
          type="number"
          prop="newPhone"
          marker-side="after"
          placeholder="请输入新手机号"
          :rules="[{ required: true, message: '请填写新手机号' }]"
        />
        <wd-input
          v-model="state.form.code"
          label="验证码"
          label-width="100px"
          prop="code"
          marker-side="after"
          placeholder="请输入短信验证码"
          :rules="[{ required: true, message: '请填写验证码' }]"
        >
          <template #suffix>
            <wd-button size="small" :disabled="countdown > 0" @click="sendCode">
              {{ countdown > 0 ? `${countdown}s后重发` : '发送验证码' }}
            </wd-button>
          </template>
        </wd-input>
        <wd-cell />
      </wd-cell-group>
      <wd-button type="primary" block @click="submit">
        确认提交
      </wd-button>
    </wd-form>
  </view>
</template>

<style lang="scss" scoped>
</style>
