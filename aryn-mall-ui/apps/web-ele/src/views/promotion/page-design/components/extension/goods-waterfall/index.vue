<script setup lang="ts">
import ExtensionPreview from '../extension-preview.vue';

const props = defineProps<{ showData: Record<string, unknown> }>();
</script>

<template>
  <ExtensionPreview :show-data="props.showData" type="goods-waterfall" />
</template>
