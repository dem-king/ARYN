<script setup lang="ts">
import { onMounted, ref } from 'vue'

interface Props {
  title?: string
  bordered?: boolean
  leftArrow?: boolean
}
withDefaults(defineProps<Props>(), {
  title: '',
  bordered: true,
  leftArrow: true,
})

const isCanBack = ref(true)
const router = useRouter()

onMounted(() => {
  const pages = getCurrentPages()
  /**
   * 判断是否能返回
   */
  if (pages.length <= 1 || pages[pages.length - 1].route === 'pages/login/index') {
    isCanBack.value = false
  }
  else {
    isCanBack.value = true
  }
})

function handleLeftClick() {
  if (isCanBack.value) {
    router.back()
  }
  else {
    router.pushTab({
      name: 'home',
    })
  }
}
</script>

<template>
  <wd-navbar placeholder safe-area-inset-top fixed :bordered="bordered" :title="title">
    <template #left>
      <wd-icon
        v-if="leftArrow"
        :name="isCanBack ? 'arrow-left' : 'home1'"
        size="22px"
        @click="handleLeftClick"
      />
    </template>
  </wd-navbar>
</template>
