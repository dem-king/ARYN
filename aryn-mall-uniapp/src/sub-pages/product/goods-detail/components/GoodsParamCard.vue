<script setup lang="ts">
import type { GoodsParamRow } from '@/utils/goods-params'
import { computed } from 'vue'
import { useI18n } from '@/sub-pages/locales'

const props = withDefaults(defineProps<{
  rows: GoodsParamRow[]
  showProfile?: boolean
}>(), {
  showProfile: false,
})
const emit = defineEmits<{ openProfile: [] }>()
const { t } = useI18n()

/**
 * 主页面只解释“档案里有什么”，不再重复首屏标签里的规格、产地、储存值。
 * 没有深层食品档案时，退化为参数类目摘要；完整值统一在底部档案弹层查看。
 */
const summary = computed(() => {
  if (props.showProfile)
    return t('product.profile.summary')
  const labels = props.rows
    .filter(row => !row.selectable)
    .slice(0, 3)
    .map(row => row.label ?? t(`product.params.${row.key}`))
    .filter(Boolean)
  return labels.join(' · ') || t('product.params.title')
})
</script>

<template>
  <view
    class="archive-entry"
    hover-class="archive-entry--pressed"
    role="button"
    :aria-label="t('product.profile.entry')"
    @click="emit('openProfile')"
  >
    <view class="archive-entry__icon-wrap">
      <text class="archive-entry__icon i-carbon:document" />
    </view>
    <view class="archive-entry__content">
      <text class="archive-entry__title">{{ t('product.profile.entry') }}</text>
      <text class="archive-entry__summary">{{ summary }}</text>
    </view>
    <wd-icon name="arrow-right" size="16px" color="var(--app-text-3)" />
  </view>
</template>

<style scoped>
.archive-entry {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 68px;
  padding: 14px 16px;
  background: var(--app-card);
  transition: background 120ms ease, opacity 120ms ease;
}

.archive-entry--pressed {
  background: var(--app-card-2);
  opacity: 0.82;
}

.archive-entry__icon-wrap {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: #f4f1ec;
  color: #76685c;
}

.archive-entry__icon {
  width: 19px;
  height: 19px;
  font-size: 19px;
}

.archive-entry__content {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}

.archive-entry__title {
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.35;
}

.archive-entry__summary {
  overflow: hidden;
  color: var(--app-text-3);
  font-size: 12px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.wot-theme-dark .archive-entry__icon-wrap {
  background: #302d29;
  color: #d4c4b4;
}
</style>
