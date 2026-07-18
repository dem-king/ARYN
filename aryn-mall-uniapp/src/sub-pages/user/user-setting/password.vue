<script setup lang="ts">
import { reactive, ref } from 'vue'
import { editPassword } from '@/api/user/user'

definePage({
  name: 'user-setting-password',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '修改密码',
  },
})

const router = useRouter()
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
        warning('两次输入的密码不一致')
        return
      }
      globalLoading.loading('提交中...')
      editPassword(state.form).then(() => {
        success('修改成功')
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
    <hr-navbar title="修改密码" />
    <wd-form ref="formRef" :model="state.form">
      <wd-cell-group border>
        <wd-input
          v-model="state.form.currentPassword"
          label="当前密码"
          label-width="100px"
          show-password
          prop="currentPassword"
          clearable
          placeholder="请输入当前密码"
          :rules="[{ required: true, message: '请填写当前密码' }]"
        />
        <wd-input
          v-model="state.form.password"
          label="新密码"
          label-width="100px"
          show-password
          prop="password"
          clearable
          placeholder="请输入新密码"
          :rules="[{ required: true, message: '请填写新密码' }]"
        />
        <wd-input
          v-model="state.form.confirmPassword"
          label="确认密码"
          label-width="100px"
          show-password
          prop="confirmPassword"
          clearable
          placeholder="请再次输入密码"
          :rules="[{ required: true, message: '请填写确认密码' }]"
        />
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
