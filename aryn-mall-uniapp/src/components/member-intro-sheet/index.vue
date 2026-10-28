<script setup lang="ts">
/**
 * 会员介绍底部弹层。
 *
 * 触发点：首页多 Tab 商品流「会员专区」的会员权益条右侧「了解会员」。
 * 原行为是整页跳会员中心；改为先弹层讲清「会员是什么 / 能得什么 / 怎么升级」，
 * 用户想深挖再点主按钮进会员中心（等级权益、积分、储值都在那边）。
 *
 * 展示口径全部收敛在 `member-intro.ts`（纯函数）：不在模板里写死等级名/门槛/折数，
 * 避免租户改等级配置后文案过期。组件只负责渲染与交互，不做会员接口请求
 * （会员态/等级名由宿主已有的会员权益条数据透传，接口失败时按游客态介绍）。
 *
 * 层级：wd-popup 默认 z-index=10，会被首页 H5 原生 tabBar(998) 盖住，这里显式提到
 * 1100（高于加购确认弹层 1020，低于全局 loading/toast）。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  MEMBER_INTRO_BENEFITS,
  MEMBER_INTRO_CTA_TEXT_KEY,
  MEMBER_INTRO_DISMISS_TEXT_KEY,
  MEMBER_INTRO_PRICE_NOTE_KEY,
  MEMBER_INTRO_STEPS,
  MEMBER_INTRO_SUBTITLE_KEY,
  MEMBER_INTRO_TITLE_KEY,
  resolveMemberIntroView,
} from './member-intro'

const props = withDefaults(defineProps<{
  /** 是否展示（v-model） */
  modelValue?: boolean
  /** 是否已是有等级的会员；默认按游客态介绍 */
  isMember?: boolean
  /** 当前等级名（租户可配，仅会员态展示） */
  levelName?: string
}>(), {
  isMember: false,
  levelName: '',
  modelValue: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  /** 点了主按钮「查看会员中心」；导航由宿主决定，弹层不关心路由拼装 */
  (e: 'member-center'): void
}>()

const { t } = useI18n()

const benefits = MEMBER_INTRO_BENEFITS
const steps = MEMBER_INTRO_STEPS
/**
 * 权益图标类名放在 .vue 内而不是 member-intro.ts：
 * UnoCSS 默认内容扫描不含裸 `.ts`，图标类写进纯模块会导致样式产物里没有这个类，
 * 运行时表现为「图标整块空白」（卡片文案正常）。
 */
const BENEFIT_ICONS: Record<string, string> = {
  coupon: 'i-carbon:ticket',
  points: 'i-carbon:increase-level',
  price: 'i-carbon:tag',
  privilege: 'i-carbon:star',
}
const view = computed(() => resolveMemberIntroView({
  isMember: props.isMember,
  levelName: props.levelName,
}))

function close() {
  emit('update:modelValue', false)
}

/** 遮罩点击/下滑关闭时同步外部 v-model，避免宿主状态与弹层显示不一致 */
function onPopupUpdate(value: boolean) {
  if (!value)
    close()
}

function onConfirm() {
  close()
  emit('member-center')
}
</script>

<template>
  <wd-popup
    :model-value="modelValue"
    position="bottom"
    :z-index="1100"
    :safe-area-inset-bottom="true"
    custom-style="border-radius: 28rpx 28rpx 0 0; overflow: hidden;"
    @update:model-value="onPopupUpdate"
  >
    <view class="member-intro">
      <view class="member-intro__handle" />
      <view class="member-intro__close" hover-class="member-intro__close--pressed" @click="close">
        <text class="i-carbon:close member-intro__close-glyph" />
      </view>

      <!-- 身份区：金色徽章 + 标题 + 状态行 -->
      <view class="member-intro__hero">
        <view class="member-intro__medal">
          <text class="i-carbon:star-filled member-intro__medal-glyph" />
        </view>
        <view class="member-intro__hero-text">
          <view class="member-intro__title">
            {{ t(MEMBER_INTRO_TITLE_KEY) }}
          </view>
          <view class="member-intro__subtitle">
            {{ t(MEMBER_INTRO_SUBTITLE_KEY) }}
          </view>
        </view>
      </view>

      <view class="member-intro__status" :class="{ 'member-intro__status--member': view.state === 'member' }">
        <text class="member-intro__status-dot" />
        <text class="member-intro__status-label">
          {{ view.statusText }}
        </text>
        <text class="member-intro__status-hint">
          {{ view.statusHint }}
        </text>
      </view>

      <!-- 权益四宫格：只做确定性描述，不写折数/门槛（租户可配） -->
      <view class="member-intro__section-title">
        {{ t('promotion.memberIntro.sectionBenefits') }}
      </view>
      <view class="member-intro__benefits">
        <view
          v-for="benefit in benefits"
          :key="benefit.key"
          class="member-intro__benefit"
        >
          <view class="member-intro__benefit-icon">
            <text :class="BENEFIT_ICONS[benefit.key]" class="member-intro__benefit-glyph" />
          </view>
          <view class="member-intro__benefit-body">
            <view class="member-intro__benefit-title">
              {{ t(benefit.titleKey) }}
            </view>
            <view class="member-intro__benefit-desc">
              {{ t(benefit.descKey) }}
            </view>
          </view>
        </view>
      </view>

      <!-- 成长路径：登录 → 消费累计 → 自动评级 -->
      <view class="member-intro__section-title">
        {{ t('promotion.memberIntro.sectionGrowth') }}
      </view>
      <view class="member-intro__steps">
        <view class="member-intro__steps-line" />
        <view
          v-for="(step, index) in steps"
          :key="step.key"
          class="member-intro__step"
        >
          <view class="member-intro__step-index">
            {{ index + 1 }}
          </view>
          <view class="member-intro__step-title">
            {{ t(step.titleKey) }}
          </view>
          <view class="member-intro__step-desc">
            {{ t(step.descKey) }}
          </view>
        </view>
      </view>

      <view class="member-intro__note">
        <view class="member-intro__note-icon">
          <text class="i-carbon:information member-intro__note-icon-glyph" />
        </view>
        <text class="member-intro__note-text">
          {{ t(MEMBER_INTRO_PRICE_NOTE_KEY) }}
        </text>
      </view>

      <view class="member-intro__cta" hover-class="member-intro__cta--pressed" @click="onConfirm">
        {{ view.ctaText || t(MEMBER_INTRO_CTA_TEXT_KEY) }}
      </view>
      <view class="member-intro__dismiss" hover-class="member-intro__dismiss--pressed" @click="close">
        {{ t(MEMBER_INTRO_DISMISS_TEXT_KEY) }}
      </view>
    </view>
  </wd-popup>
</template>

<style lang="scss" scoped>
/* 文字色阶沿用会员中心/个人中心：标题 #1f2329 / 正文 #4e5969 / 辅助 #6b7280
   （辅助色不用 #909399：11~12px 小字在浅底上对比度不足 4.5:1） */
$mis-title: #1f2329;
$mis-text: #4e5969;
/* 会员身份色：与「会员价」角标、权益条同族的暖金，不跟随租户主色（主色只用于 CTA） */
$mis-gold: #c8a05a;
$mis-gold-deep: #8a5a1c;
$mis-gold-soft: #a06a1f;
$mis-gold-bg: #fff6e8;
$mis-gold-line: #f0e1c4;
$mis-hairline: #f0f1f3;
$mis-primary: var(--theme-color-primary, var(--wot-color-theme-primary, #ff2237));

.member-intro {
  position: relative;
  box-sizing: border-box;
  padding: 0 32rpx 8rpx;
  background: #fff;
}

.member-intro__handle {
  width: 64rpx;
  height: 8rpx;
  margin: 16rpx auto 0;
  border-radius: 999rpx;
  background: #e5e7eb;
}

/* 关闭按钮固定在右上角：不占 header 排版，长标题也能完整显示 */
.member-intro__close {
  position: absolute;
  top: 24rpx;
  right: 24rpx;
  /* 图标类 color:inherit：颜色给父容器 */
  color: #9ca3af;
  display: flex;
  width: 56rpx;
  height: 56rpx;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #f5f6f8;
  z-index: 1;
}

.member-intro__close--pressed {
  background: #eceef1;
}

/* 视觉 56rpx、热区 88rpx：移动端可点区域不低于 44px */
.member-intro__close::after {
  position: absolute;
  top: -16rpx;
  right: -16rpx;
  bottom: -16rpx;
  left: -16rpx;
  content: '';
}


.member-intro__close-glyph {
  font-size: 28rpx;
}

.member-intro__hero {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx 0 8rpx;
}

.member-intro__medal {
  display: flex;
  flex: none;
  /* 图标类 color:inherit：颜色给父容器 */
  color: $mis-gold-soft;
  width: 92rpx;
  height: 92rpx;
  align-items: center;
  justify-content: center;
  border: 2rpx solid #e8c98a;
  border-radius: 50%;
  /* 不用 filter/blur：小程序端支持不可靠，纯渐变就能做出徽章质感 */
  background: linear-gradient(135deg, #fff3da 0%, #f4d9a6 100%);
  box-shadow: 0 6rpx 16rpx rgb(200 160 90 / 22%);
}

.member-intro__medal-glyph {
  font-size: 44rpx;
}

.member-intro__hero-text {
  min-width: 0;
  flex: 1;
}

.member-intro__title {
  color: $mis-title;
  font-size: 40rpx;
  font-weight: 700;
  line-height: 1.3;
}

.member-intro__subtitle {
  margin-top: 6rpx;
  color: $mis-gold-deep;
  font-size: 24rpx;
  line-height: 1.4;
}

/* 状态行：游客态暖金、会员态压深一档，区分「还未入会」与「已享有」 */
.member-intro__status {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6rpx 12rpx;
  margin-top: 16rpx;
  padding: 16rpx 20rpx;
  border: 1rpx solid $mis-gold-line;
  border-radius: 16rpx;
  background: $mis-gold-bg;
}

.member-intro__status--member {
  background: linear-gradient(90deg, #fff6e8, #ffe9cf);
}

.member-intro__status-dot {
  flex: none;
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: $mis-gold;
}

.member-intro__status-label {
  color: $mis-gold-deep;
  font-size: 24rpx;
  font-weight: 600;
  line-height: 1.4;
}

.member-intro__status-hint {
  color: #7a5a24;
  font-size: 24rpx;
  line-height: 1.4;
}

.member-intro__section-title {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin: 28rpx 0 16rpx;
  color: $mis-title;
  font-size: 28rpx;
  font-weight: 600;
  line-height: 1.4;
}

/* 标题前的小金条：分区更清楚，又不用整块底色抢视觉 */
.member-intro__section-title::before {
  display: block;
  width: 6rpx;
  height: 24rpx;
  border-radius: 999rpx;
  background: $mis-gold;
  content: '';
}

.member-intro__benefits {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16rpx;
}

.member-intro__benefit {
  display: flex;
  align-items: center;
  gap: 16rpx;
  box-sizing: border-box;
  padding: 18rpx 16rpx;
  border-radius: 16rpx;
  background: #f8f9fb;
}

.member-intro__benefit-icon {
  display: flex;
  flex: none;
  /* 图标类 color:inherit：颜色给父容器 */
  color: $mis-gold-soft;
  width: 64rpx;
  height: 64rpx;
  align-items: center;
  justify-content: center;
  border-radius: 16rpx;
  background: linear-gradient(135deg, #fff3da 0%, #ffe9cf 100%);
}

.member-intro__benefit-glyph {
  font-size: 34rpx;
}

.member-intro__benefit-body {
  min-width: 0;
}

.member-intro__benefit-title {
  color: $mis-title;
  font-size: 26rpx;
  font-weight: 600;
  line-height: 1.4;
}

.member-intro__benefit-desc {
  margin-top: 2rpx;
  color: #6b7280;
  font-size: 24rpx;
  line-height: 1.4;
}

.member-intro__steps {
  position: relative;
  display: flex;
  align-items: flex-start;
}

/* 横向连线：从第一个圆点圆心到最后一个圆点圆心，两端各留半格 */
.member-intro__steps-line {
  position: absolute;
  top: 20rpx;
  right: 16.67%;
  left: 16.67%;
  height: 2rpx;
  background: $mis-gold-line;
}

.member-intro__step {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  min-width: 0;
  text-align: center;
}

.member-intro__step-index {
  display: flex;
  width: 40rpx;
  height: 40rpx;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: $mis-gold;
  color: #fff;
  font-size: 22rpx;
  font-weight: 600;
  line-height: 1;
}

.member-intro__step-title {
  margin-top: 10rpx;
  color: $mis-title;
  font-size: 24rpx;
  font-weight: 600;
  line-height: 1.4;
}

.member-intro__step-desc {
  margin-top: 2rpx;
  color: #6b7280;
  font-size: 24rpx;
  line-height: 1.4;
}

.member-intro__note {
  display: flex;
  align-items: flex-start;
  gap: 10rpx;
  margin-top: 24rpx;
  padding: 16rpx 20rpx;
  border-radius: 12rpx;
  background: #f7f8fa;
}

.member-intro__note-icon {
  display: flex;
  flex: none;
  margin-top: 2rpx;
  /* 图标类 color:inherit：颜色给父容器 */
  color: $mis-gold-soft;
}

.member-intro__note-icon-glyph {
  font-size: 26rpx;
}

.member-intro__note-text {
  color: $mis-text;
  font-size: 24rpx;
  line-height: 1.5;
}

/* 主按钮用租户主题色：与全站主 CTA 一致，会员身份色只用于信息层 */
.member-intro__cta {
  display: flex;
  height: 88rpx;
  align-items: center;
  justify-content: center;
  margin-top: 28rpx;
  border-radius: 999rpx;
  background: $mis-primary;
  color: #fff;
  font-size: 30rpx;
  font-weight: 600;
}

.member-intro__cta--pressed {
  opacity: 0.86;
}

.member-intro__dismiss {
  display: flex;
  height: 88rpx;
  align-items: center;
  justify-content: center;
  color: #6b7280;
  font-size: 26rpx;
}

.member-intro__dismiss--pressed {
  color: $mis-text;
}
</style>
