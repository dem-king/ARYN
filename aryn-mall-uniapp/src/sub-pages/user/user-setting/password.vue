<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { editPassword } from '@/sub-pages/api/user/user'

definePage({
  name: 'user-setting-password',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '修改密码',
  },
})

const router = useRouter()
const { t } = useI18n()
const formRef = ref()
const globalLoading = useGlobalLoading()
const { warning, success } = useGlobalToast()

const state = reactive<{ form: { currentPassword: string, password: string, confirmPassword: string } }>(
  {
    form: { currentPassword: '', password: '', confirmPassword: '' },
  },
)

function submit() {
  formRef.value
    .validate()
    .then(({ valid }: any) => {
      if (!valid)
        return
      if (state.form.password !== state.form.confirmPassword) {
        warning(t('accountSettings.password.mismatch'))
        return
      }
      globalLoading.loading(t('accountSettings.password.submitting'))
      editPassword(state.form).then(() => {
        success(t('accountSettings.password.success'))
        setTimeout(() => {
          const pages = getCurrentPages()
          if (pages.length > 1) {
            router.back()
          }
          else {
            router.pushTab({ name: 'home' })
          }
        }, 1000)
      }).finally(() => {
        globalLoading.close()
      })
    })
}
</script>

<template>
  <view class="bg-white p-2">
    <hr-navbar :title="t('accountSettings.password.navTitle')" />
    <wd-form ref="formRef" :model="state.form">
      <wd-cell-group border>
        <wd-input
          v-model="state.form.currentPassword"
          :label="t('accountSettings.password.current')"
          label-width="100px"
          show-password
          prop="currentPassword"
          clearable
          :placeholder="t('accountSettings.password.currentPlaceholder')"
          :rules="[{ required: true, message: t('accountSettings.password.currentRequired') }]"
        />
        <wd-input
          v-model="state.form.password"
          :label="t('accountSettings.password.newPassword')"
          label-width="100px"
          show-password
          prop="password"
          clearable
          :placeholder="t('accountSettings.password.newPlaceholder')"
          :rules="[{ required: true, message: t('accountSettings.password.newRequired') }]"
        />
        <wd-input
          v-model="state.form.confirmPassword"
          :label="t('accountSettings.password.confirm')"
          label-width="100px"
          show-password
          prop="confirmPassword"
          clearable
          :placeholder="t('accountSettings.password.confirmPlaceholder')"
          :rules="[{ required: true, message: t('accountSettings.password.confirmRequired') }]"
        />
        <wd-cell />
      </wd-cell-group>
      <wd-button type="primary" block @click="submit">
        {{ t('accountSettings.password.submit') }}
      </wd-button>
    </wd-form>
  </view>
</template>

<style lang="scss" scoped>
</style>
