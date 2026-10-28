<script setup lang="ts">
import type { GoodsAttrTagIconKey, GoodsAttrTagPayload } from '@/api/product/attr-tags'
import type { GoodsAttrTagView } from './goods-attr-tags'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { OVERLAY_Z_INDEX } from '@/utils/overlay'
import { isAllowedInternalPagePath, normalizeGoodsAttrTags } from './goods-attr-tags'

interface Props {
  /**
   * 后端 `GoodsSpu.attrTags` 原样传入。
   * 空数组 / 字段缺失 / 旧数据脏值时整块不渲染（后端未部署时页面与现状一致）。
   */
  attrTags?: GoodsAttrTagPayload[] | null
}

const props = defineProps<Props>()
const { t } = useI18n()

/**
 * 预置图标 key → unocss iconify 类。
 *
 * 项目图标集合以 carbon 为主（全仓 100+ 处 `i-carbon:*`），且后端 8 个预置 key
 * 与 carbon 同名图标完全对齐，因此不引入新图标集。类名必须是完整字面量
 * （不能运行时拼接），UnoCSS 才能静态扫描并生成对应 wxss。
 */
const ICON_CLASS_MAP: Record<GoodsAttrTagIconKey, string> = {
  'location': 'i-carbon:location',
  'snowflake': 'i-carbon:snowflake',
  'trophy': 'i-carbon:trophy',
  'package': 'i-carbon:package',
  'security': 'i-carbon:security',
  'time': 'i-carbon:time',
  'shopping-bag': 'i-carbon:shopping-bag',
  'star-filled': 'i-carbon:star-filled',
}

/** 归一化只做一次：模板消费的是已收敛的视图模型 */
const tags = computed(() => normalizeGoodsAttrTags(props.attrTags))

/** 预先拼好图标类，避免模板里对同一张卡反复查表 */
const displayTags = computed(() => tags.value.map(tag => ({
  ...tag,
  iconClass: tag.iconKey ? ICON_CLASS_MAP[tag.iconKey] : '',
})))

const popupVisible = ref(false)
const popupTitle = ref('')
const popupContent = ref('')

function isActionable(tag: GoodsAttrTagView) {
  return tag.linkType !== 'none'
}

function openPopup(tag: GoodsAttrTagView) {
  popupTitle.value = tag.popupTitle || tag.value
  popupContent.value = tag.popupContent
  popupVisible.value = true
}

function openPage(tag: GoodsAttrTagView) {
  // 归一化已过滤非法路径；这里再判一次，防止未来接口口径变化把任意 URL 带进 navigateTo
  if (!isAllowedInternalPagePath(tag.linkUrl)) {
    return
  }
  uni.navigateTo({
    url: tag.linkUrl,
    fail: () => {
      // 页面栈满/目标页未注册时不静默失败，给用户一个可理解的反馈
      uni.showToast({ title: t('product.pageUnavailable'), icon: 'none' })
    },
  })
}

function onTagClick(tag: GoodsAttrTagView) {
  if (tag.linkType === 'popup') {
    openPopup(tag)
    return
  }
  if (tag.linkType === 'page') {
    openPage(tag)
    return
  }
  // linkType=none：纯展示，不响应点击（卡片也不展示 chevron）
}
</script>

<template>
  <view v-if="displayTags.length > 0" class="attr-tags">
    <!--
      横滑卡条：卡宽 176rpx + 12rpx 间距，标题卡内容区一屏露出 3 张以上，
      第四张被裁切即「还有更多」的天然提示；不显示滚动条避免首屏噪音。
    -->
    <scroll-view
      class="attr-tags__scroll"
      scroll-x
      :show-scrollbar="false"
      :enable-flex="true"
    >
      <view class="attr-tags__track">
        <view
          v-for="tag in displayTags"
          :key="tag.key"
          class="attr-tag dark:bg-[var(--wot-dark-background4)]"
          :class="`attr-tag--${tag.tone}`"
          :hover-class="isActionable(tag) ? 'attr-tag--pressed' : 'none'"
          @click="onTagClick(tag)"
        >
          <view class="attr-tag__main">
            <image
              v-if="tag.iconUrl"
              class="attr-tag__img"
              :src="tag.iconUrl"
              mode="aspectFit"
            />
            <text
              v-else-if="tag.iconClass"
              class="attr-tag__icon dark:text-[var(--wot-dark-color)]"
              :class="tag.iconClass"
            />
            <text class="attr-tag__value">{{ tag.value }}</text>
            <!-- 只有可点击的卡才提示「点得动」 -->
            <view v-if="isActionable(tag)" class="attr-tag__chevron">
              <wd-icon name="arrow-right" :size="10" color="var(--attr-tag-icon, #9aa0aa)" />
            </view>
          </view>
          <text class="attr-tag__label">{{ tag.label }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- 属性说明弹层：标题缺省取属性值，正文只做展示 -->
    <wd-popup
      v-model="popupVisible"
      position="bottom"
      :safe-area-inset-bottom="true"
      :z-index="OVERLAY_Z_INDEX.sheet"
      custom-style="border-radius: 24rpx 24rpx 0 0; overflow: hidden;"
    >
      <view class="attr-tag-popup">
        <view class="attr-tag-popup__head">
          <text class="attr-tag-popup__title dark:text-[var(--wot-dark-color)]">
            {{ popupTitle }}
          </text>
          <view class="attr-tag-popup__close" @click="popupVisible = false">
            <wd-icon name="close" :size="16" color="var(--wot-cell-arrow-color, #969799)" />
          </view>
        </view>
        <view class="attr-tag-popup__body">
          <text class="attr-tag-popup__content dark:text-[var(--wot-dark-color3)]">
            {{ popupContent }}
          </text>
        </view>
      </view>
    </wd-popup>
  </view>
</template>

<style lang="scss" scoped>
.attr-tags {
  margin-top: 16rpx;

  &__scroll {
    width: 100%;
    white-space: nowrap;
  }

  &__track {
    display: flex;
    gap: 12rpx;
  }
}

/**
 * 单张属性卡：第一行图标 + 属性值（可点击时带 chevron），第二行小号属性名。
 * 底色按 tone 给低饱和浅色（绿/蓝/灰/橙），不抢标题与价格的注意力。
 */
.attr-tag {
  display: flex;
  width: 176rpx;
  box-sizing: border-box;
  flex: 0 0 176rpx;
  flex-direction: column;
  padding: 12rpx 14rpx;
  border-radius: 14rpx;
  background: var(--attr-tag-bg, #f5f6f8);
  transition: opacity 120ms ease, transform 120ms ease;

  &--green {
    --attr-tag-bg: #eef9f2;
    --attr-tag-value: #0f7a4a;
    --attr-tag-icon: #16a05f;
  }

  &--blue {
    --attr-tag-bg: #eef4ff;
    --attr-tag-value: #2458c9;
    --attr-tag-icon: #3b82f6;
  }

  &--gray {
    --attr-tag-bg: #f5f6f8;
    --attr-tag-value: #4b5563;
    --attr-tag-icon: #6b7280;
  }

  &--orange {
    --attr-tag-bg: #fff5e9;
    --attr-tag-value: #b45309;
    --attr-tag-icon: #ea8a1f;
  }

  &--pressed {
    opacity: 0.72;
    transform: scale(0.97);
  }

  &__main {
    display: flex;
    min-width: 0;
    align-items: center;
    gap: 6rpx;
  }

  &__icon {
    width: 26rpx;
    height: 26rpx;
    flex: none;
    color: var(--attr-tag-icon, #6b7280);
    font-size: 26rpx;
  }

  &__img {
    width: 26rpx;
    height: 26rpx;
    flex: none;
    border-radius: 4rpx;
  }

  &__value {
    overflow: hidden;
    min-width: 0;
    flex: 1;
    color: var(--attr-tag-value, #3a3f47);
    font-size: 24rpx;
    font-weight: 600;
    line-height: 1.3;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__chevron {
    display: flex;
    flex: none;
    align-items: center;
  }

  &__label {
    margin-top: 4rpx;
    overflow: hidden;
    color: #8a8f99;
    font-size: 20rpx;
    line-height: 1.3;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.attr-tag-popup {
  padding: 32rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));

  &__head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 24rpx;
  }

  &__title {
    min-width: 0;
    flex: 1;
    color: #1f2329;
    font-size: 32rpx;
    font-weight: 700;
    line-height: 1.4;
    word-break: break-word;
  }

  &__close {
    display: flex;
    width: 48rpx;
    height: 48rpx;
    flex: none;
    align-items: center;
    justify-content: center;
  }

  &__body {
    margin-top: 20rpx;
  }

  &__content {
    color: #5f6673;
    font-size: 26rpx;
    line-height: 1.7;
    white-space: pre-wrap;
    word-break: break-word;
  }
}
</style>
