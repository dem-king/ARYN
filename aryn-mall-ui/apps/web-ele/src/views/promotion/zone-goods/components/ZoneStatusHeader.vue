<script lang="ts" setup>
import type { ZoneKey } from '../logic';

import type { ZoneSyncZone } from '#/views/product/zone-batch/types';

import { computed, nextTick, ref } from 'vue';

import { InfoFilled, QuestionFilled, Setting } from '@element-plus/icons-vue';
import { ElButton, ElIcon, ElPopover, ElTooltip } from 'element-plus';

import {
  describeDefaultDiscount,
  formatTime,
  resolveActivityStatusMeta,
  resolveSyncMeta,
  resolveZoneEnabledMeta,
  ZONE_DESCRIPTION,
  ZONE_LABEL,
} from '../logic';

/**
 * 「专区商品管理」页顶部概览。
 *
 * 把原先平铺的一行「标签：值」重组为「专区切换器 + 状态指标带」：
 * 1. 专区切换是页面的一级上下文，用分段控件表达当前所在专区；
 * 2. 专区状态 / 默认折扣率 / 系统活动 / 已同步商品 / 最近同步 按指标卡展示，
 *    运营扫一眼就能判断「价格是否在生效」；
 * 3. 完整价格口径收进「规则说明」气泡，底部保留一行「标签存在≠价格生效」
 *    常驻提示，不再用整条 Alert 长期占用列表首屏。
 */
interface ZoneSettingLike {
  defaultDiscountRate?: null | number | string;
  enabled?: boolean | string;
}

const props = withDefaults(
  defineProps<{
    /** 当前专区（member / clearance） */
    activeTab: string;
    /** 同步状态加载中 */
    loading?: boolean;
    /** 触发重算请求中 */
    retrying?: boolean;
    /** 专区设置（默认折扣率 / 开关兜底） */
    setting?: null | ZoneSettingLike;
    /** 当前专区的同步状态 */
    syncZone?: null | ZoneSyncZone;
  }>(),
  {
    loading: false,
    retrying: false,
    setting: null,
    syncZone: null,
  },
);

const emit = defineEmits<{
  change: [zone: ZoneKey];
  retry: [];
  settings: [];
}>();

const ZONE_ORDER: ZoneKey[] = ['member', 'clearance'];

/** 分段按钮 ref，方向键切换后用于转移焦点 */
const switchRef = ref<HTMLButtonElement[]>([]);

const zoneKey = computed<ZoneKey>(() =>
  props.activeTab === 'clearance' ? 'clearance' : 'member',
);
const zoneDescription = computed(() => ZONE_DESCRIPTION[zoneKey.value]);

const enabledMeta = computed(() =>
  resolveZoneEnabledMeta(props.syncZone?.enabled, props.setting?.enabled),
);
const discountText = computed(() =>
  describeDefaultDiscount(props.setting?.defaultDiscountRate),
);
const activityMeta = computed(() =>
  resolveActivityStatusMeta(props.syncZone?.activityStatus),
);
const syncMeta = computed(() =>
  resolveSyncMeta(props.syncZone?.lastSyncStatus),
);
const syncTime = computed(() => formatTime(props.syncZone?.lastSyncTime));
const syncFailed = computed(
  () => (props.syncZone?.lastSyncStatus ?? '').toUpperCase() === 'FAILED',
);

function setSwitchRef(el: unknown, index: number) {
  if (el instanceof HTMLButtonElement) {
    switchRef.value[index] = el;
  }
}

function handleChange(zone: ZoneKey) {
  if (zone === props.activeTab) {
    return;
  }
  emit('change', zone);
}

/** 分段控件按单选组惯例支持左右方向键切换 */
function handleSwitchKeydown(event: KeyboardEvent) {
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') {
    return;
  }
  event.preventDefault();
  const currentIndex = ZONE_ORDER.indexOf(zoneKey.value);
  const offset = event.key === 'ArrowRight' ? 1 : -1;
  const next =
    ZONE_ORDER[(currentIndex + offset + ZONE_ORDER.length) % ZONE_ORDER.length];
  if (!next) {
    return;
  }
  emit('change', next);
  // 切换后把焦点移到新的分段，键盘用户不会停在已失活的选项上
  void nextTick(() => {
    const buttons = switchRef.value;
    const index = ZONE_ORDER.indexOf(next);
    buttons?.[index]?.focus();
  });
}
</script>

<template>
  <section class="zone-hero" aria-label="专区概览">
    <div class="zone-hero__head">
      <div
        class="zone-switch"
        role="radiogroup"
        aria-label="专区切换"
        @keydown="handleSwitchKeydown"
      >
        <button
          v-for="(zone, index) in ZONE_ORDER"
          :key="zone"
          :ref="(el) => setSwitchRef(el, index)"
          class="zone-switch__item"
          :class="{ 'is-active': activeTab === zone }"
          type="button"
          role="radio"
          :aria-checked="activeTab === zone"
          :tabindex="activeTab === zone ? 0 : -1"
          @click="handleChange(zone)"
        >
          {{ ZONE_LABEL[zone] }}
        </button>
      </div>

      <div class="zone-hero__actions">
        <ElPopover
          placement="bottom-end"
          :width="360"
          trigger="click"
          popper-class="zone-rule-popover"
        >
          <template #reference>
            <ElButton link :icon="InfoFilled">规则说明</ElButton>
          </template>
          <div class="zone-rule">
            <p class="zone-rule__desc">{{ zoneDescription }}</p>
            <p class="zone-rule__warn">
              专区价由后端按规则实时计算：价格倒挂、出清到期、商品下架时 C
              端不会展示专区价。
            </p>
          </div>
        </ElPopover>
        <ElButton :icon="Setting" @click="emit('settings')">专区设置</ElButton>
      </div>
    </div>

    <div v-loading="loading" class="zone-metrics">
      <div class="zone-metric">
        <span class="zone-metric__label">专区状态</span>
        <span class="zone-metric__value">
          <span
            class="zone-dot"
            :class="`zone-dot--${enabledMeta.tone}`"
            aria-hidden="true"
          ></span>
          {{ enabledMeta.label }}
        </span>
      </div>

      <div class="zone-metric">
        <span class="zone-metric__label">
          默认折扣率
          <ElTooltip
            content="商品未配置专区规则时，按该折扣率计算专区价"
            placement="top"
            :trigger="['hover', 'focus']"
          >
            <ElIcon
              class="zone-metric__tip"
              tabindex="0"
              aria-label="默认折扣率说明：商品未配置专区规则时，按该折扣率计算专区价"
            >
              <QuestionFilled />
            </ElIcon>
          </ElTooltip>
        </span>
        <span
          class="zone-metric__value"
          :class="{ 'is-muted': discountText === '未设置' }"
        >
          {{ discountText }}
        </span>
      </div>

      <div class="zone-metric">
        <span class="zone-metric__label">系统活动</span>
        <span class="zone-metric__value">
          <span
            class="zone-dot"
            :class="`zone-dot--${activityMeta.type}`"
            aria-hidden="true"
          ></span>
          {{ activityMeta.label }}
        </span>
      </div>

      <div class="zone-metric">
        <span class="zone-metric__label">
          已同步 SKU
          <ElTooltip
            content="实际参与专区价的规格数（SKU），与专区商品 SPU 数不是同一口径"
            placement="top"
            :trigger="['hover', 'focus']"
          >
            <ElIcon
              class="zone-metric__tip"
              tabindex="0"
              aria-label="已同步 SKU 说明：实际参与专区价的规格数，与专区商品 SPU 数不是同一口径"
            >
              <QuestionFilled />
            </ElIcon>
          </ElTooltip>
        </span>
        <span class="zone-metric__value">
          {{ syncZone?.rulesSkuCount ?? '—' }}
          <span class="zone-metric__unit">个</span>
        </span>
      </div>

      <div class="zone-metric zone-metric--sync">
        <span class="zone-metric__label">最近同步</span>
        <span class="zone-metric__value">
          <span
            class="zone-dot"
            :class="`zone-dot--${syncMeta.type}`"
            aria-hidden="true"
          ></span>
          <ElTooltip
            :content="syncZone?.lastError || syncMeta.tip"
            placement="top"
            :trigger="['hover', 'focus']"
          >
            <span class="zone-metric__sync" tabindex="0">
              {{ syncMeta.label }}
            </span>
          </ElTooltip>
          <span class="zone-metric__time">{{ syncTime }}</span>
          <ElButton
            v-if="syncFailed || !syncZone"
            class="zone-metric__retry"
            link
            type="primary"
            :loading="retrying"
            @click="emit('retry')"
          >
            重试
          </ElButton>
        </span>
      </div>
    </div>

    <p class="zone-hero__hint">
      <ElIcon class="zone-hero__hint-icon" aria-hidden="true">
        <InfoFilled />
      </ElIcon>
      专区价由规则实时计算；「标签存在」≠「价格生效」，请以列表「生效状态」列为准。
    </p>
  </section>
</template>

<style scoped lang="scss">
@media (width <= 900px) {
  .zone-metric {
    flex: 1 1 calc(50% - 1px);

    &:nth-child(odd) {
      padding-left: 0;
      border-left: 0;
    }
  }
}

.zone-hero {
  flex: none;
  padding: 14px 18px 12px;
  margin-bottom: 12px;
  background: color-mix(
    in srgb,
    var(--el-color-primary) 4%,
    var(--el-bg-color)
  );
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}

.zone-hero__head {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.zone-hero__actions {
  display: flex;
  gap: 4px;
  align-items: center;

  :deep(.el-button.is-link) {
    color: var(--el-text-color-regular);
  }

  :deep(.el-button.is-link:hover) {
    color: var(--el-color-primary);
  }
}

/* 分段控件：灰色轨道 + 白色高亮活动段，比下划线 Tab 更能表达「切换上下文」 */
.zone-switch {
  display: inline-flex;
  gap: 2px;
  padding: 3px;
  background: color-mix(in srgb, var(--el-text-color-primary) 6%, transparent);
  border-radius: 10px;
}

.zone-switch__item {
  padding: 7px 20px;
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-regular);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 8px;
  transition:
    background-color 200ms ease,
    color 200ms ease,
    box-shadow 200ms ease;

  &:hover {
    color: var(--el-color-primary);
  }

  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 1px;
  }

  &.is-active {
    color: var(--el-color-primary);
    background: var(--el-bg-color);
    box-shadow:
      0 1px 2px rgb(0 0 0 / 6%),
      0 1px 3px rgb(0 0 0 / 10%);
  }
}

.zone-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 0;
  padding-top: 14px;
  margin-top: 12px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.zone-metric {
  display: flex;
  flex: 1 1 0;
  flex-direction: column;
  gap: 4px;
  min-width: 138px;
  padding: 0 18px;

  &:not(:first-child) {
    border-left: 1px solid var(--el-border-color-lighter);
  }
}

.zone-metric__label {
  display: flex;
  gap: 4px;
  align-items: center;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.zone-metric__tip {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  cursor: help;

  &:hover {
    color: var(--el-color-primary);
  }
}

.zone-metric__value {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);

  &.is-muted {
    font-weight: 500;
    color: var(--el-text-color-secondary);
  }
}

.zone-metric__unit,
.zone-metric__time {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.zone-metric--sync {
  flex: 1.4 1 0;
}

.zone-metric__sync {
  cursor: help;
  border-bottom: 1px dashed var(--el-border-color);

  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 1px;
    border-bottom-color: transparent;
  }
}

.zone-metric__retry {
  font-weight: 500;
}

/* 状态点：颜色 + 文字双通道，不依赖颜色单独传达语义 */
.zone-dot {
  width: 6px;
  height: 6px;
  background: var(--el-text-color-placeholder);
  border-radius: 50%;

  &--success {
    background: var(--el-color-success);
    box-shadow: 0 0 0 3px
      color-mix(in srgb, var(--el-color-success) 16%, transparent);
  }

  &--danger {
    background: var(--el-color-danger);
    box-shadow: 0 0 0 3px
      color-mix(in srgb, var(--el-color-danger) 16%, transparent);
  }

  &--warning {
    background: var(--el-color-warning);
    box-shadow: 0 0 0 3px
      color-mix(in srgb, var(--el-color-warning) 16%, transparent);
  }

  &--info {
    background: var(--el-color-info);
  }

  &--unknown {
    background: var(--el-text-color-placeholder);
  }
}

.zone-hero__hint {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  padding-top: 10px;
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
  border-top: 1px dashed var(--el-border-color-lighter);
}

.zone-hero__hint-icon {
  margin-top: 2px;
  color: var(--el-color-primary);
}

.zone-rule {
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: 13px;
  line-height: 1.65;
  color: var(--el-text-color-regular);
}

.zone-rule__desc {
  margin: 0;
}

.zone-rule__warn {
  padding: 8px 10px;
  margin: 0;
  color: var(--el-color-warning-dark-2);
  background: var(--el-color-warning-light-9);
  border-radius: 6px;
}

/* 暗色下 track 用浅色叠加、active 用更亮的表面色，保持「选中项更亮」的层级 */
:global(html.dark .zone-switch) {
  background: color-mix(in srgb, var(--el-text-color-primary) 8%, transparent);
}

:global(html.dark .zone-switch__item.is-active) {
  /* 暗色下选中项用浅色叠加提亮，避免出现「选中比未选中更暗」的层级反转 */
  background: color-mix(
    in srgb,
    var(--el-text-color-primary) 18%,
    var(--el-bg-color)
  );
  box-shadow:
    0 1px 2px rgb(0 0 0 / 40%),
    0 1px 3px rgb(0 0 0 / 50%);
}

:global(html.dark .zone-hero) {
  background: color-mix(
    in srgb,
    var(--el-color-primary) 10%,
    var(--el-bg-color)
  );
  border-color: var(--el-border-color);
}
</style>
