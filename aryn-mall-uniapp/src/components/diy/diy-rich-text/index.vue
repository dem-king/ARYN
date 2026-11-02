<script setup lang="ts">
// @ts-expect-error: mp-html type declaration issue
import mpHtml from 'mp-html/dist/uni-app/components/mp-html/mp-html'
import { useDiyStyle } from '@/composables/useDiyStyle'
import { handleHtmlLinkTap } from '@/utils/html-link'

const props = defineProps({
  showData: {
    type: Object,
    default: () => { },
  },
})
const dynamicStyles = useDiyStyle(computed(() => props.showData.commonStyle))

function onLinkTap(attrs: { href?: string }) {
  handleHtmlLinkTap(attrs.href)
}
</script>

<template>
  <view :style="dynamicStyles">
    <mp-html :content="showData.content" @linktap="onLinkTap" />
  </view>
</template>

<style></style>
