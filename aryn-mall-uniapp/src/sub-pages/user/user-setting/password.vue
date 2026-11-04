<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onPageScroll, onUnload } from '@dcloudio/uni-app'
import { useI18n } from 'vue-i18n'
import { editPassword } from '@/sub-pages/api/user/user'
import { useFrostedNavbar } from '@/composables/useFrostedNavbar'
import { getTopPage, isPageAlive } from '@/utils/navigation-lock'

const { handlePageScroll } = useFrostedNavbar()

definePage({
  name: 'user-setting-password',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '修改密码',
  },
})

const router = useRouter()
/** setup 时的页面实例 + 延时跳转句柄：卸载后不许再对已销毁页面发路由 */
const pageAtSetup = getTopPage()
let navTimer: ReturnType<typeof setTimeout> | null = null
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
        if (navTimer) {
          clearTimeout(navTimer)
        }
        navTimer = setTimeout(() => {
          navTimer = null
          // 成功提示展示期间用户可能已自行返回：只对仍是栈顶的页面发路由
          if (!isPageAlive(pageAtSetup)) {
            return
          }
          if (getCurrentPages().length > 1) {
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

onUnload(() => {
  if (navTimer) {
    clearTimeout(navTimer)
    navTimer = null
  }
})

// 顶栏磨砂玻璃：字面量注册滚动（小程序按页面源码生成滚动位标记，包进 composable 会静默失效）
onPageScroll(handlePageScroll)
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
