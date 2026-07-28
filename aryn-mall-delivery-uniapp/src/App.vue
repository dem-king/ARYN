<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { useAuthStore } from '@/store/auth'

onLaunch(() => {
  const authenticated = useAuthStore().restore()
  uni.reLaunch({ url: authenticated ? '/pages/workbench/index' : '/pages/login/index' })
})
</script>

<style lang="scss">
page {
  min-height: 100%;
  color: #17233d;
  background: #f3f6fa;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}

button::after {
  border: 0;
}
</style>
