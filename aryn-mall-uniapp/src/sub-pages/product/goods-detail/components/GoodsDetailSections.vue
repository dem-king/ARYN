<script setup lang="ts">
import type { GoodsDetailSection } from '@/utils/goods-profile'
import { computed, ref } from 'vue'

const props = defineProps<{ sections?: GoodsDetailSection[] | null }>()
const emit = defineEmits<{ resize: [] }>()
const visibleSections = computed(() => props.sections ?? [])
const expanded = ref<Record<string, boolean>>({})

function sectionKey(section: GoodsDetailSection, index: number) {
  return `${index}-${section.title}`
}

/** 有图段落直接展开；纯文字段落默认只露标题，避免通用说明再次形成文字墙。 */
function isOpen(section: GoodsDetailSection, index: number) {
  return Boolean(section.imageUrl) || !section.title || expanded.value[sectionKey(section, index)]
}

function toggle(section: GoodsDetailSection, index: number) {
  if (section.imageUrl || !section.title || !section.content)
    return
  const key = sectionKey(section, index)
  expanded.value[key] = !expanded.value[key]
  emit('resize')
}
</script>

<template>
  <view v-if="visibleSections.length" class="detail-stories">
    <view
      v-for="(section, index) in visibleSections"
      :key="sectionKey(section, index)"
      class="detail-story"
      :class="{ 'detail-story--media': section.imageUrl, 'detail-story--text': !section.imageUrl }"
    >
      <!-- 有图时先建立视觉吸引，再读标题与说明；不再先摆一段文字。 -->
      <image
        v-if="section.imageUrl"
        class="detail-story__image"
        :src="section.imageUrl"
        mode="widthFix"
        @load="emit('resize')"
      />

      <view
        class="detail-story__heading"
        :class="{ 'detail-story__heading--action': !section.imageUrl && section.content }"
        :hover-class="!section.imageUrl && section.content ? 'detail-story__heading--pressed' : 'none'"
        @click="toggle(section, index)"
      >
        <text v-if="section.title" class="detail-story__title">{{ section.title }}</text>
        <wd-icon
          v-if="!section.imageUrl && section.title && section.content"
          :name="isOpen(section, index) ? 'arrow-down' : 'arrow-right'"
          size="14px"
          color="var(--app-text-3)"
        />
      </view>
      <text
        v-if="section.content && isOpen(section, index)"
        class="detail-story__text"
        :class="{ 'detail-story__text--media': section.imageUrl }"
      >
        {{ section.content }}
      </text>
    </view>
  </view>
</template>

<style scoped>
.detail-stories {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.detail-story {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.detail-story--media {
  overflow: hidden;
  border-radius: 18px;
  background: var(--app-card-2);
}

.detail-story--text {
  padding: 2px 0;
  border-bottom: 1px solid var(--app-border);
}

.detail-story--text:last-child {
  border-bottom: 0;
}

.detail-story__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 42px;
}

.detail-story--media .detail-story__heading {
  min-height: 0;
  padding: 14px 14px 0;
}

.detail-story__heading--pressed {
  opacity: 0.62;
}

.detail-story__title {
  min-width: 0;
  flex: 1;
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.detail-story__text {
  padding: 4px 0 12px;
  color: var(--app-text-2);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.detail-story__text--media {
  padding: 6px 14px 14px;
}

.detail-story__image {
  display: block;
  width: 100%;
  min-height: 160px;
  background: #f3eee7;
}
</style>
