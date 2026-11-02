/**
 * 统一「退出登录」动作：二次确认 → 调登出接口 → 清本地态 → 回首页。
 *
 * 抽出来的原因：个人设置页与新增的「登录设备」页都需要同一条退出链路
 * （userLogout 内已含服务端登出 + clearAuthData），各自复制一份容易出现
 * 「一处加了确认文案、另一处忘了」的漂移。
 */
export function useUserLogout() {
  const router = useRouter()
  const authStore = useAuthStore()
  const globalLoading = useGlobalLoading()
  const { success: showSuccess } = useGlobalToast()
  const { confirm } = useGlobalMessage()
  const { t } = useI18n()

  function confirmLogout() {
    confirm({
      title: t('userSetting.logoutTitle'),
      msg: t('userSetting.logoutMessage'),
      closeOnClickModal: false,
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      success: (res) => {
        if (res.action !== 'confirm')
          return
        globalLoading.loading(t('userSetting.loggingOut'))
        authStore.userLogout().then(() => {
          showSuccess(t('userSetting.logoutSuccess'))
          router.replaceAll({ name: 'home' })
        })
      },
    })
  }

  return { confirmLogout }
}
