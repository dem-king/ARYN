<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

definePage({
  name: 'seckill-rule',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '限时秒杀活动规则',
  },
})

const { t } = useI18n()

/** 规则模块：标题 + 条目；条目里的加粗片段用 **...** 标记，渲染时拆成两段 */
const ruleSections = computed(() => [
  {
    title: t('promotion.seckillRule.time.title'),
    items: [
      t('promotion.seckillRule.time.item1'),
      t('promotion.seckillRule.time.item2'),
      t('promotion.seckillRule.time.item3'),
    ],
  },
  {
    title: t('promotion.seckillRule.condition.title'),
    items: [
      t('promotion.seckillRule.condition.item1'),
      t('promotion.seckillRule.condition.item2'),
      t('promotion.seckillRule.condition.item3'),
    ],
  },
  {
    title: t('promotion.seckillRule.flow.title'),
    items: [
      t('promotion.seckillRule.flow.item1'),
      t('promotion.seckillRule.flow.item2'),
      t('promotion.seckillRule.flow.item3'),
      t('promotion.seckillRule.flow.item4'),
    ],
    ordered: true,
  },
  {
    title: t('promotion.seckillRule.stock.title'),
    items: [
      t('promotion.seckillRule.stock.item1'),
      t('promotion.seckillRule.stock.item2'),
      t('promotion.seckillRule.stock.item3'),
    ],
  },
  {
    title: t('promotion.seckillRule.order.title'),
    items: [
      t('promotion.seckillRule.order.item1'),
      t('promotion.seckillRule.order.item2'),
      t('promotion.seckillRule.order.item3'),
      t('promotion.seckillRule.order.item4'),
    ],
  },
  {
    title: t('promotion.seckillRule.other.title'),
    items: [
      t('promotion.seckillRule.other.item1'),
      t('promotion.seckillRule.other.item2'),
      t('promotion.seckillRule.other.item3'),
    ],
  },
])

/** 把「**加粗**」拆成片段，模板逐段渲染（每个条目渲染时算一次，避免模板里反复正则） */
function toSegments(item: string) {
  const match = item.match(/^(.*?)\*\*(.+?)\*\*(.*)$/)
  if (!match)
    return [{ strong: false, text: item }]
  return [
    { strong: false, text: match[1] ?? '' },
    { strong: true, text: match[2] ?? '' },
    { strong: false, text: match[3] ?? '' },
  ].filter(segment => segment.text.length > 0)
}
</script>

<template>
  <view class="rule-page">
    <hr-navbar :title="t('promotion.seckillRule.title')" :bordered="false" />

    <scroll-view scroll-y class="rule-scroll">
      <view class="rule-body">
        <view v-for="section in ruleSections" :key="section.title" class="rule-section">
          <view class="rule-title">
            {{ section.title }}
          </view>
          <view
            v-for="(item, index) in section.items"
            :key="item"
            class="rule-item"
          >
            <text class="rule-dot">
              {{ section.ordered ? `${index + 1}.` : '•' }}
            </text>
            <text class="rule-text">
              <text
                v-for="(segment, segIndex) in toSegments(item)"
                :key="`${index}-${segIndex}`"
                :class="{ 'rule-strong': segment.strong }"
              >
                {{ segment.text }}
              </text>
            </text>
          </view>
        </view>

        <text class="rule-foot">
          {{ t('promotion.seckillRule.footer') }}
        </text>
      </view>
    </scroll-view>
  </view>
</template>

<style lang="scss" scoped>
.rule-page {
  display: flex;
  height: 100vh;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
}

.rule-scroll {
  min-height: 0;
  flex: 1;
}

.rule-body {
  padding: 8rpx 32rpx calc(48rpx + env(safe-area-inset-bottom));
}

.rule-section {
  margin-top: 40rpx;
}

.rule-title {
  color: #1f2329;
  font-size: 30rpx;
  font-weight: 700;
  line-height: 1.5;
}

.rule-item {
  display: flex;
  margin-top: 16rpx;
  gap: 12rpx;
}

/* 小红点做视觉锚点（有序条目直接用序号） */
.rule-dot {
  flex: 0 0 auto;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 26rpx;
  font-weight: 700;
  line-height: 1.8;
}

.rule-text {
  flex: 1;
  color: #333;
  font-size: 26rpx;
  line-height: 1.8;
}

.rule-strong {
  color: #1f2329;
  font-weight: 700;
}

.rule-foot {
  display: block;
  padding-top: 56rpx;
  color: #b6b9c0;
  font-size: 20rpx;
  text-align: center;
}
</style>
