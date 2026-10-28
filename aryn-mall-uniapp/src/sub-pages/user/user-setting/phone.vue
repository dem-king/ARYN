<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { editPhone } from '@/sub-pages/api/user/user'
import { sendSmsCode } from '@/api/upms/sms'

definePage({
  name: 'user-setting-phone',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '修改手机号',
  },
})

const router = useRouter()
const { t } = useI18n()
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
    show(t('accountSettings.phone.invalidPhone'))
    return
  }
  if (countdown.value > 0)
    return
  globalLoading.loading(t('accountSettings.phone.sending'))
  sendSmsCode('update', phone)
    .then(() => {
      showSuccess(t('accountSettings.phone.codeSent'))
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
      globalLoading.loading(t('accountSettings.phone.submitting'))
      editPhone({ phone: state.form.newPhone, code: state.form.code })
        .then(() => {
          showSuccess(t('accountSettings.phone.success'))
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
    <hr-navbar :title="t('accountSettings.phone.navTitle')" />
    <wd-form ref="formRef" :model="state.form">
      <wd-cell-group border>
        <wd-cell :title="t('accountSettings.phone.current')">
          <view class="text-14px">
            {{ state.form.oldPhone || t('accountSettings.phone.notBound') }}
          </view>
        </wd-cell>
        <wd-input
          v-model="state.form.newPhone"
          :label="t('accountSettings.phone.newPhone')"
          label-width="100px"
          type="number"
          prop="newPhone"
          marker-side="after"
          :placeholder="t('accountSettings.phone.newPhonePlaceholder')"
          :rules="[{ required: true, message: t('accountSettings.phone.newPhoneRequired') }]"
        />
        <wd-input
          v-model="state.form.code"
          :label="t('accountSettings.phone.code')"
          label-width="100px"
          prop="code"
          marker-side="after"
          :placeholder="t('accountSettings.phone.codePlaceholder')"
          :rules="[{ required: true, message: t('accountSettings.phone.codeRequired') }]"
        >
          <template #suffix>
            <wd-button size="small" :disabled="countdown > 0" @click="sendCode">
              {{ countdown > 0
                ? t('accountSettings.phone.resend', { count: countdown })
                : t('accountSettings.phone.sendCode') }}
            </wd-button>
          </template>
        </wd-input>
        <wd-cell />
      </wd-cell-group>
      <wd-button type="primary" block @click="submit">
        {{ t('accountSettings.phone.submit') }}
      </wd-button>
    </wd-form>
  </view>
</template>

<style lang="scss" scoped>
</style>
