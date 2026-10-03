<script setup lang="ts">
import type { PageDesignType } from '#/api/promotion/page-design';

/**
 * 内嵌页型（商详/分类/个人中心）的原生页面示意骨架。
 *
 * 这三类页面的装修块都只是页面中的一段内容，画布若只画区块本身，
 * 运营看不到它在页面里的位置与**真实宽度**：分类页装修实际嵌在右栏商品流内
 * （左栏二级分类 rail 占 176rpx = 88px，右栏只剩 375 - 88 = 287px），
 * 按画布满宽 375px 做的 banner 到实机整体变窄。
 *
 * 尺寸口径与 C 端一致：`1rpx = 0.5px`（屏宽 750rpx 对应画布 375px），
 * 各处取自 C 端真实样式（分类页 `.category-rail` 176rpx、
 * `.sort-bar` 88rpx、`.icon-strip` 圆 96rpx 等）。
 * 骨架是**示意**：商品流、主图墙这类超长区域压缩展示，只保证位置、
 * 列宽与关键块高一致，不追求逐像素还原。
 *
 * 原生导航栏标题用页面自己的固定文案（各页 C 端写死的 title），
 * 不能用管理端的 `pageName`——那是「分类页装修」这类后台命名，实机不显示。
 */
defineProps<{
  pageType: PageDesignType;
}>();
</script>

<template>
  <!-- 分类页：搜索导航栏 + 一级图标条 + 左栏 rail + 右栏（装修区 + 排序条 + 商品流） -->
  <div v-if="pageType === '3'" class="shell" data-shell="category">
    <div class="cat-navbar">
      <div class="cat-navbar__field">
        <span class="cat-navbar__placeholder">搜索商品</span>
        <span class="cat-navbar__search-btn">搜索</span>
      </div>
      <span class="cat-navbar__cart"></span>
    </div>

    <div class="cat-strip">
      <div
        v-for="index in 6"
        :key="index"
        class="cat-strip__item"
        :class="{ 'cat-strip__item--active': index === 1 }"
      >
        <span class="cat-strip__circle"></span>
        <span class="cat-strip__name">分类{{ index }}</span>
      </div>
    </div>

    <div class="cat-content">
      <div class="cat-rail">
        <div class="cat-rail__heading">
          <span class="cat-rail__title">全部分类</span>
          <span class="cat-rail__caption">5 个分类</span>
        </div>
        <div
          v-for="index in 6"
          :key="index"
          class="cat-rail__item"
          :class="{ 'cat-rail__item--active': index === 1 }"
        >
          二级分类{{ index }}
        </div>
      </div>

      <div class="cat-main">
        <!-- 装修块的真实落点：右栏商品流顶部，随商品流一起滚动 -->
        <div class="shell-slot">
          <slot></slot>
        </div>
        <div class="cat-sort">
          <span class="cat-sort__item cat-sort__item--active">综合推荐</span>
          <span class="cat-sort__item">销量</span>
          <span class="cat-sort__item">价格</span>
          <span class="cat-sort__item">新品</span>
        </div>
        <div class="cat-goods">
          <div v-for="index in 4" :key="index" class="cat-goods__card">
            <div class="cat-goods__pic"></div>
            <span class="cat-goods__line"></span>
            <span class="cat-goods__line cat-goods__line--short"></span>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- 个人中心页：品牌页头 + 资产条 + 装修区 + 订单卡片 -->
  <div v-else-if="pageType === '4'" class="shell" data-shell="usercenter">
    <div class="uc-navbar">个人中心</div>
    <div class="uc-hero">
      <span class="uc-hero__avatar"></span>
      <div class="uc-hero__meta">
        <span class="uc-hero__name">用户名</span>
        <span class="uc-hero__hint">登录后查看权益</span>
      </div>
    </div>
    <div class="uc-assets">
      <div
        v-for="item in ['优惠券', '积分', '余额']"
        :key="item"
        class="uc-assets__item"
      >
        <span class="uc-assets__value">--</span>
        <span class="uc-assets__label">{{ item }}</span>
      </div>
    </div>

    <div class="shell-slot">
      <slot></slot>
    </div>

    <div class="uc-card">
      <div class="uc-card__head">
        <span class="uc-card__title">我的订单</span>
        <span class="uc-card__more">查看全部</span>
      </div>
      <div class="uc-grid">
        <span v-for="index in 5" :key="index" class="uc-grid__icon"></span>
      </div>
    </div>
  </div>

  <!-- 商品详情页：导航栏 + 主图区 + 装修区 + 评价区 -->
  <div v-else-if="pageType === '2'" class="shell" data-shell="detail">
    <div class="uc-navbar">商品详情</div>
    <div class="detail-gallery">
      <!-- 实机主图墙 820rpx（410px）高，这里压缩到 200px，便于同时看到下方装修区 -->
      <span class="shell-hint">商品主图 / 价格 / 规格（示意）</span>
    </div>

    <div class="shell-slot">
      <slot></slot>
    </div>

    <div class="detail-comment">
      <div class="detail-comment__head">商品评价</div>
      <span class="detail-comment__row"></span>
      <span class="detail-comment__row"></span>
    </div>
  </div>
</template>

<style scoped>
.shell {
  background: #f4f5f7;
}

.shell-slot {
  position: relative;
  background: #fff;
}

.shell-hint {
  font-size: 11px;
  color: #9aa1ac;
}

/* ===================== 通用导航栏占位 ===================== */

.uc-navbar {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  height: 64px;
  padding-bottom: 10px;
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  background: var(--wot-color-theme-primary, #ff2237);
}

/* ===================== 分类页 ===================== */

.cat-navbar {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  height: 64px;
  padding: 0 10px 8px;
  background: #fff;
}

.cat-navbar__field {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  height: 30px;
  padding: 0 4px 0 8px;
  background: rgb(255 255 255 / 90%);
  border: 1px solid #e5e7eb;
  border-radius: 9px;
}

.cat-navbar__placeholder {
  font-size: 14px;
  color: #9ca3af;
}

.cat-navbar__search-btn {
  padding: 5px 10px;
  font-size: 13px;
  color: #fff;
  background: var(--wot-color-theme-primary, #ff2237);
  border-radius: 8px;
}

.cat-navbar__cart {
  width: 24px;
  height: 24px;
  background: #d1d5db;
  border-radius: 50%;
}

/* 一级分类图标条：圆 96rpx=48px、文字行 16px、上下内边距 4/18rpx */
.cat-strip {
  display: flex;
  padding: 2px 0 9px;
  overflow: hidden;
  background: #fff;
  border-bottom: 1px solid #f1f2f4;
}

.cat-strip__item {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: center;
  width: 70px;
}

.cat-strip__circle {
  width: 48px;
  height: 48px;
  background: #ececf0;
  border-radius: 50%;
}

.cat-strip__item--active .cat-strip__circle {
  background: #ffe4e6;
  border: 1.5px solid var(--wot-color-theme-primary, #ff2237);
}

.cat-strip__name {
  max-width: 66px;
  margin-top: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11.5px;
  line-height: 16px;
  color: #737985;
  white-space: nowrap;
}

.cat-strip__item--active .cat-strip__name {
  font-weight: 600;
  color: var(--wot-color-theme-primary, #ff2237);
}

.cat-content {
  display: flex;
  min-height: 420px;
}

/* 左栏 rail：176rpx = 88px，与 C 端 .category-rail 同宽 */
.cat-rail {
  flex: none;
  width: 88px;
  padding: 0 4px 10px;
  background: linear-gradient(180deg, #f5f6f8 0%, #f1f2f4 100%);
  border-right: 0.5px solid #eef0f2;
}

.cat-rail__heading {
  display: flex;
  flex-direction: column;
  padding: 14px 7px 10px 14px;
  border-bottom: 0.5px solid rgb(27 39 57 / 6%);
}

.cat-rail__title {
  font-size: 12.5px;
  font-weight: 700;
  line-height: 1.35;
  color: #30343b;
}

.cat-rail__caption {
  margin-top: 3px;
  font-size: 9.5px;
  color: #9a9faa;
}

/* rail 项：min-height 88rpx = 44px */
.cat-rail__item {
  display: flex;
  align-items: center;
  min-height: 44px;
  padding: 8px 6px;
  font-size: 12px;
  line-height: 1.4;
  color: #686d76;
}

.cat-rail__item--active {
  font-weight: 650;
  color: var(--wot-color-theme-primary, #ff2237);
  background: #fff;
  border-radius: 7px;
  box-shadow: 0 3px 9px rgb(31 44 65 / 6%);
}

/* 右栏：375 - 88 = 287px，装修块的真实可用宽度 */
.cat-main {
  flex: 1;
  min-width: 0;
  background: #fff;
}

/* 排序条：88rpx = 44px */
.cat-sort {
  display: flex;
  align-items: center;
  height: 44px;
  padding: 0 4px;
  font-size: 14px;
  color: #666;
  background: #fff;
}

.cat-sort__item {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
}

.cat-sort__item--active {
  font-weight: 600;
  color: var(--wot-color-theme-primary, #ff2237);
}

/* 商品流占位：两列 grid，与 C 端 .goods-list--grid 一致（16rpx 间距） */
.cat-goods {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding: 8px;
}

.cat-goods__card {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cat-goods__pic {
  height: 120px;
  background: #f1f2f4;
  border-radius: 8px;
}

.cat-goods__line {
  height: 10px;
  background: #f1f2f4;
  border-radius: 5px;
}

.cat-goods__line--short {
  width: 60%;
}

/* ===================== 个人中心页 ===================== */

.uc-hero {
  display: flex;
  align-items: center;

  /* 底部 104rpx=52px 留给资产条上移压盖 */
  padding: 12px 10px 52px;
  background: linear-gradient(
    180deg,
    var(--wot-color-theme-primary, #ff2237) 0%,
    var(--wot-color-theme-secondary, #ff6b7a) 100%
  );
}

.uc-hero__avatar {
  flex: none;
  width: 52px;
  height: 52px;
  background: #fff;
  border: 1.5px solid rgb(255 255 255 / 55%);
  border-radius: 50%;
}

.uc-hero__meta {
  display: flex;
  flex-direction: column;
  margin-left: 12px;
}

.uc-hero__name {
  font-size: 18px;
  font-weight: 700;
  color: #fff;
}

.uc-hero__hint {
  margin-top: 5px;
  font-size: 12px;
  color: rgb(255 255 255 / 82%);
}

/* 资产条：-72rpx 上移压盖页头 */
.uc-assets {
  display: flex;
  padding: 14px 0;
  margin: -36px 10px 0;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgb(31 44 65 / 6%);
}

.uc-assets__item {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
}

.uc-assets__value {
  font-size: 20px;
  font-weight: 700;
  color: #1f2329;
}

.uc-assets__label {
  margin-top: 4px;
  font-size: 12px;
  color: #9aa1ac;
}

.uc-card {
  padding: 0 10px 4px;
  margin: 10px 10px 0;
  background: #fff;
  border-radius: 12px;
}

.uc-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 44px;
}

.uc-card__title {
  font-size: 15px;
  font-weight: 700;
  color: #1f2329;
}

.uc-card__more {
  font-size: 12px;
  color: #9aa1ac;
}

.uc-grid {
  display: flex;
  padding-bottom: 10px;
}

.uc-grid__icon {
  width: 20%;
  height: 34px;
  background: #f1f2f4;
  border-radius: 8px;
}

/* ===================== 商品详情页 ===================== */

.detail-gallery {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  height: 200px;
  padding-bottom: 8px;
  background: #ececf0;
}

.detail-comment {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 10px;
  background: #fff;
}

.detail-comment__head {
  font-size: 15px;
  font-weight: 700;
  color: #1f2329;
}

.detail-comment__row {
  height: 32px;
  background: #f5f6f8;
  border-radius: 8px;
}
</style>
