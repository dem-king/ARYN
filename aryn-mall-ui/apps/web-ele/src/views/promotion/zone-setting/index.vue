<script lang="ts" setup>
import type { Component } from 'vue';

import type {
  ZoneFormModel,
  ZoneKey,
  ZoneSettingLike,
  ZoneSyncMeta,
} from './logic';

import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@vben/access';

import {
  ArrowRight,
  InfoFilled,
  PriceTag,
  User,
  WarningFilled,
} from '@element-plus/icons-vue';
import {
  ElButton,
  ElIcon,
  ElInputNumber,
  ElMessage,
  ElSegmented,
  ElSwitch,
  ElTooltip,
} from 'element-plus';

import { getZoneSyncStatus, triggerZoneSync } from '#/api/product/goods-zone';
import { getZoneSetting, saveZoneSetting } from '#/api/promotion/zone-setting';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';

import {
  buildZoneSettingPayload,
  createEmptyZoneForm,
  describeCostFloorChip,
  describeDiscountChip,
  isZoneFormDirty,
  normalizeZoneForm,
  resolveZoneSyncMeta,
  SORT_MODE_OPTIONS,
  ZONE_KEYS,
} from './logic';

/**
 * 「专区设置」页。
 *
 * 设计目标：把原来「两张上下堆叠、字段全是灰提示」的长表单，改成运营能一眼看懂
 * 两个专区当前状态的设置面板：
 * - 页头只保留「本页管什么」的一句话 + 去页面设计的唯一入口，不再用整条 Alert 占位；
 * - 两个专区并排成卡片，卡片头 = 身份 + 状态 + 开关，卡身体 = 价格规则，卡脚 = 同步状态 + 保存；
 * - 每个价格字段右侧固定一枚即时口径 chip（9 折 / 成本上浮 5%），并区分「未设置」；
 * - 关闭专区时用行内提示说明 C 端影响（替代原来常驻的长句提示）；
 * - 有改动才可保存，并显示「未保存 / 撤销」，避免误以为改了就已生效。
 *
 * 路由 /promotion/zone-setting 由动态菜单驱动（菜单 component: promotion/zone-setting/index）。
 */

// ---------------------------------------------------------------------------
// 静态文案：专区身份、字段说明。文案在这里集中，模板只负责排版
// ---------------------------------------------------------------------------

interface ZoneCardCopy {
  icon: Component;
  /** 关闭后的 C 端影响，仅在关闭态展示，避免常驻长句干扰 */
  offNotice: string;
  subtitle: string;
  title: string;
}

const ZONE_CARDS: Record<ZoneKey, ZoneCardCopy> = {
  member: {
    icon: User,
    title: '会员专区',
    subtitle:
      '会员价 = 售价 × min(商品折扣率, 等级折扣率)，并受成本兜底下限约束。',
    offNotice:
      '关闭后 C 端不再展示会员专区货盘，也不再计算会员价；商品标签保留，重新开启后自动恢复。',
  },
  clearance: {
    icon: PriceTag,
    title: '低价捡漏专区',
    subtitle:
      '出清价按「原价 × 出清折扣率」计算；到期、下架或规则倒挂时不生效。',
    offNotice:
      '关闭后 C 端不再展示出清货盘，也不再计算出清价；商品标签保留，重新开启后自动恢复。',
  },
};

interface ZoneFieldCopy {
  costFloorHint: string;
  costFloorLabel: string;
  costFloorPlaceholder: string;
  discountHint: string;
  discountLabel: string;
  discountPlaceholder: string;
  /** 折扣率未设置时的 chip 文案（两个专区含义不同，必须分开写） */
  emptyDiscountChip: string;
  skuTip: string;
  sortHint: string;
  sortLabel: string;
}

const ZONE_FIELDS: Record<ZoneKey, ZoneFieldCopy> = {
  member: {
    discountLabel: '默认折扣率',
    discountPlaceholder: '如 0.9 表示 9 折',
    emptyDiscountChip: '仅用等级折扣',
    discountHint:
      '商品未单独配置会员折扣率时使用；会员价仍与等级折扣率取优，不叠加。',
    costFloorLabel: '成本兜底倍率',
    costFloorPlaceholder: '如 1.05 表示成本上浮 5%',
    costFloorHint:
      '会员价下限 = 成本价 × 倍率；商品已配置专属下限时以商品为准；空 = 不启用。',
    sortLabel: '',
    sortHint: '',
    skuTip:
      '会员专区系统活动内实际参与会员价的 SKU 数；可在「专区商品管理」中调整。',
  },
  clearance: {
    discountLabel: '默认折扣率',
    discountPlaceholder: '如 0.8 表示 8 折',
    emptyDiscountChip: '不参与出清',
    discountHint:
      '商品未配置出清价/折扣率时使用（按原价折算）；空 = 未配置规则的商品不参与出清。',
    costFloorLabel: '',
    costFloorPlaceholder: '',
    costFloorHint: '',
    sortLabel: '专区排序',
    sortHint:
      '作用于 C 端所有出清列表（包括装修 Tab）；装修 Tab 里的排序选项不生效。',
    skuTip:
      '低价捡漏系统活动内实际参与出清价的 SKU 数；可在「专区商品管理」中调整。',
  },
};

// ---------------------------------------------------------------------------
// 页面状态
// ---------------------------------------------------------------------------

const router = useRouter();
const { hasAccessByCodes } = useAccess();
/** 只有保存权限才可编辑；否则页面退化为只读视图，避免「能改但存不了」 */
const canSave = computed(() =>
  hasAccessByCodes(['promotion:shipactivity:save']),
);

/** 可编辑表单：数字字段 null 表示未设置 */
const form = reactive<Record<ZoneKey, ZoneFormModel>>({
  member: createEmptyZoneForm(),
  clearance: createEmptyZoneForm(),
});

/** 加载快照：保存 / 撤销的基准，也是「是否有未保存改动」的判断依据 */
const baseline = reactive<Record<ZoneKey, ZoneFormModel>>({
  member: createEmptyZoneForm(),
  clearance: createEmptyZoneForm(),
});

const syncMeta = reactive<Record<ZoneKey, ZoneSyncMeta>>({
  member: resolveZoneSyncMeta(null),
  clearance: resolveZoneSyncMeta(null),
});

/** 系统活动内实际生效的 SKU 数（取不到时为 null，整块指标隐藏） */
const skuCount = reactive<Record<ZoneKey, null | number>>({
  member: null,
  clearance: null,
});

const loading = reactive<Record<ZoneKey, boolean>>({
  member: false,
  clearance: false,
});
const saving = reactive<Record<ZoneKey, boolean>>({
  member: false,
  clearance: false,
});
const loadError = reactive<Record<ZoneKey, string>>({
  member: '',
  clearance: '',
});
/** 同步是按租户全量重算，两个卡片共用一次「重试同步」进行中状态 */
const retrying = ref(false);
/** 首次数据回来前不渲染同步/规模信息，避免「未同步 —」闪一下 */
const loaded = reactive<Record<ZoneKey, boolean>>({
  member: false,
  clearance: false,
});

const dirty = computed<Record<ZoneKey, boolean>>(() => ({
  member: isZoneFormDirty(form.member, baseline.member),
  clearance: isZoneFormDirty(form.clearance, baseline.clearance),
}));

const hasDirty = computed(() => ZONE_KEYS.some((zone) => dirty.value[zone]));

// ---------------------------------------------------------------------------
// 数据加载
// ---------------------------------------------------------------------------

async function loadZone(zone: ZoneKey) {
  loading[zone] = true;
  loadError[zone] = '';
  try {
    const data = (await getZoneSetting(
      zone,
    )) as null | Partial<ZoneSettingLike>;
    form[zone] = normalizeZoneForm(data);
    baseline[zone] = { ...form[zone] };
    syncMeta[zone] = resolveZoneSyncMeta(data);
    loaded[zone] = true;
  } catch {
    loadError[zone] = '专区设置加载失败，请检查网络或稍后重试。';
  } finally {
    loading[zone] = false;
  }
}

/**
 * 系统活动 SKU 数：属于「锦上添花」的信息，接口失败时整块指标隐藏，
 * 不影响设置本身的读写（错误提示仍由请求层统一弹出，这里只做数据降级）。
 */
async function loadZoneStats() {
  try {
    const data = await getZoneSyncStatus();
    ZONE_KEYS.forEach((zone) => {
      const item = data?.zones?.find((row) => row.zone === zone);
      skuCount[zone] = item?.rulesSkuCount ?? null;
    });
  } catch {
    ZONE_KEYS.forEach((zone) => {
      skuCount[zone] = null;
    });
  }
}

function reloadZones() {
  ZONE_KEYS.forEach((zone) => {
    void loadZone(zone);
  });
  void loadZoneStats();
}

/** keepAlive 返回页面时刷新；有未保存改动则保留现场，不能悄悄覆盖用户输入 */
useKeepAliveRefresh(() => {
  if (!hasDirty.value) {
    reloadZones();
  }
});

// ---------------------------------------------------------------------------
// 表单动作
// ---------------------------------------------------------------------------

function resetZone(zone: ZoneKey) {
  form[zone] = { ...baseline[zone] };
}

async function saveZone(zone: ZoneKey) {
  if (saving[zone] || !dirty.value[zone]) {
    return;
  }
  saving[zone] = true;
  try {
    await saveZoneSetting(zone, buildZoneSettingPayload(form[zone]));
    await loadZone(zone);
    void loadZoneStats();
    if (loadError[zone]) {
      ElMessage.warning('设置已保存，但刷新最新状态失败，请手动重新加载');
      return;
    }
    const tone = syncMeta[zone].tone;
    if (tone === 'danger') {
      ElMessage.warning('设置已保存，但价格同步失败，可点击「重试同步」再试');
    } else if (tone === 'success') {
      ElMessage.success('专区设置已保存，C 端价格已同步');
    } else {
      ElMessage.success('专区设置已保存');
    }
  } catch {
    // 失败提示由请求层统一弹出；此处保留未保存状态，用户可直接重试
  } finally {
    saving[zone] = false;
  }
}

/**
 * 保存后后端会立即重算并同步 C 端价格；若上次同步失败，允许不改任何字段直接重试。
 * 同步接口是按租户全量重算，所以两个专区一起刷新，避免界面只更新一半。
 */
async function retrySync() {
  if (retrying.value) {
    return;
  }
  retrying.value = true;
  try {
    await triggerZoneSync();
    await Promise.all(ZONE_KEYS.map((key) => loadZone(key)));
    void loadZoneStats();
    const stillFailed = ZONE_KEYS.some(
      (key) => syncMeta[key].tone !== 'success',
    );
    if (stillFailed) {
      ElMessage.warning('同步仍未成功，请检查专区配置后重试');
    } else {
      ElMessage.success('专区价格已重新同步');
    }
  } catch {
    // 失败提示由请求层统一弹出
  } finally {
    retrying.value = false;
  }
}

/**
 * 专区在 C 端的展示位置、Tab 标题与公告由装修承担：
 * 本页只配置「规则与价格」，跳转到页面设计列表，避免运营在两个页面之间找不到入口。
 */
function goPageDesign() {
  router.push('/promotion/page-design');
}

// ---------------------------------------------------------------------------
// 派生展示文案
// ---------------------------------------------------------------------------

function discountChip(zone: ZoneKey) {
  return describeDiscountChip(
    form[zone].defaultDiscountRate,
    ZONE_FIELDS[zone].emptyDiscountChip,
  );
}

function costFloorChip(zone: ZoneKey) {
  return describeCostFloorChip(form[zone].costFloorMultiplier);
}

/** 历史 sortMode（如已停用的 price_asc）不在选项里，分段控件会没有选中项 */
function isSortModeKnown(zone: ZoneKey) {
  return SORT_MODE_OPTIONS.some((item) => item.value === form[zone].sortMode);
}

onMounted(() => {
  reloadZones();
});
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <div class="zone-page">
        <!-- 页头：说明本页边界，唯一入口去页面设计 -->
        <header class="zone-page__head">
          <div class="zone-page__intro">
            <h1 class="zone-page__title">
              专区设置
              <span v-if="!canSave" class="zone-page__readonly">只读</span>
            </h1>
            <p class="zone-page__desc">
              本页只配置专区的规则与价格；C 端展示位置、Tab
              标题与公告请到「页面设计 → 多 Tab 商品流」中维护。
            </p>
          </div>
          <div class="zone-page__actions">
            <ElButton type="primary" plain @click="goPageDesign">
              去页面设计配置展示
              <ElIcon class="zone-page__arrow"><ArrowRight /></ElIcon>
            </ElButton>
          </div>
        </header>

        <div class="zone-grid">
          <article
            v-for="zone in ZONE_KEYS"
            :key="zone"
            class="zone-card"
            :class="`zone-card--${zone}`"
          >
            <!-- 卡片头：专区身份 + 状态 + 开关 -->
            <header class="zone-card__head">
              <div class="zone-card__identity">
                <span class="zone-card__icon">
                  <ElIcon><component :is="ZONE_CARDS[zone].icon" /></ElIcon>
                </span>
                <div class="zone-card__titles">
                  <h2 class="zone-card__title">
                    {{ ZONE_CARDS[zone].title }}
                  </h2>
                  <p class="zone-card__subtitle">
                    {{ ZONE_CARDS[zone].subtitle }}
                  </p>
                </div>
              </div>
              <div class="zone-card__toggle">
                <span
                  class="zone-state"
                  :class="form[zone].enabled ? 'is-on' : 'is-off'"
                >
                  <i class="zone-state__dot"></i>
                  {{ form[zone].enabled ? '已开启' : '已关闭' }}
                </span>
                <ElSwitch
                  v-model="form[zone].enabled"
                  :aria-label="`${ZONE_CARDS[zone].title}开关`"
                  :disabled="
                    !canSave ||
                    Boolean(loadError[zone]) ||
                    loading[zone] ||
                    saving[zone]
                  "
                />
              </div>
            </header>

            <!-- 卡片身：价格规则字段 -->
            <div v-loading="loading[zone]" class="zone-card__body">
              <div v-if="loadError[zone]" class="zone-error" role="alert">
                <ElIcon class="zone-error__icon"><WarningFilled /></ElIcon>
                <span>{{ loadError[zone] }}</span>
                <ElButton link type="primary" @click="loadZone(zone)">
                  重新加载
                </ElButton>
              </div>

              <template v-else>
                <div v-if="!form[zone].enabled" class="zone-callout">
                  <ElIcon class="zone-callout__icon"><InfoFilled /></ElIcon>
                  <span>{{ ZONE_CARDS[zone].offNotice }}</span>
                </div>

                <div class="zone-field">
                  <div class="zone-field__head">
                    <label
                      class="zone-field__label"
                      :for="`${zone}-discount-rate`"
                    >
                      {{ ZONE_FIELDS[zone].discountLabel }}
                    </label>
                    <span
                      class="zone-chip"
                      :class="{ 'is-empty': discountChip(zone).empty }"
                    >
                      {{ discountChip(zone).text }}
                    </span>
                  </div>
                  <ElInputNumber
                    :id="`${zone}-discount-rate`"
                    v-model="form[zone].defaultDiscountRate"
                    :min="0.0001"
                    :max="0.9999"
                    :step="0.01"
                    :precision="4"
                    :placeholder="ZONE_FIELDS[zone].discountPlaceholder"
                    :aria-label="`${ZONE_CARDS[zone].title}默认折扣率`"
                    :disabled="!canSave || loading[zone] || saving[zone]"
                    controls-position="right"
                    class="zone-field__number"
                  />
                  <p class="zone-field__hint">
                    {{ ZONE_FIELDS[zone].discountHint }}
                  </p>
                </div>

                <div v-if="zone === 'member'" class="zone-field">
                  <div class="zone-field__head">
                    <label
                      class="zone-field__label"
                      :for="`${zone}-cost-floor`"
                    >
                      {{ ZONE_FIELDS[zone].costFloorLabel }}
                    </label>
                    <span
                      class="zone-chip"
                      :class="{ 'is-empty': costFloorChip(zone).empty }"
                    >
                      {{ costFloorChip(zone).text }}
                    </span>
                  </div>
                  <ElInputNumber
                    :id="`${zone}-cost-floor`"
                    v-model="form[zone].costFloorMultiplier"
                    :min="0.01"
                    :max="10"
                    :step="0.05"
                    :precision="2"
                    :placeholder="ZONE_FIELDS[zone].costFloorPlaceholder"
                    :aria-label="`${ZONE_CARDS[zone].title}成本兜底倍率`"
                    :disabled="!canSave || loading[zone] || saving[zone]"
                    controls-position="right"
                    class="zone-field__number"
                  />
                  <p class="zone-field__hint">
                    {{ ZONE_FIELDS[zone].costFloorHint }}
                  </p>
                </div>

                <div v-if="zone === 'clearance'" class="zone-field">
                  <span class="zone-field__label">
                    {{ ZONE_FIELDS[zone].sortLabel }}
                  </span>
                  <ElSegmented
                    v-model="form[zone].sortMode"
                    :options="SORT_MODE_OPTIONS"
                    class="zone-field__segmented"
                    :aria-label="`${ZONE_CARDS[zone].title} C 端排序`"
                    :disabled="!canSave || loading[zone] || saving[zone]"
                  />
                  <p class="zone-field__hint">
                    {{ ZONE_FIELDS[zone].sortHint }}
                  </p>
                  <p v-if="!isSortModeKnown(zone)" class="zone-field__hint">
                    当前排序值「{{
                      form[zone].sortMode
                    }}」已不在可选范围，重新选择后生效。
                  </p>
                </div>
              </template>
            </div>

            <!-- 卡片脚：同步状态 + SKU 规模 + 保存 -->
            <footer v-if="!loadError[zone]" class="zone-card__foot">
              <div v-if="loaded[zone]" class="zone-card__meta">
                <ElTooltip
                  :content="syncMeta[zone].tip"
                  placement="top-start"
                  :show-after="120"
                >
                  <span
                    class="zone-sync"
                    :tabindex="syncMeta[zone].tone === 'danger' ? 0 : undefined"
                    :aria-label="
                      syncMeta[zone].tone === 'danger'
                        ? `同步失败：${syncMeta[zone].error || syncMeta[zone].tip}`
                        : undefined
                    "
                  >
                    <i
                      class="zone-sync__dot"
                      :class="`is-${syncMeta[zone].tone}`"
                    ></i>
                    <span>C 端价格{{ syncMeta[zone].label }}</span>
                    <span
                      v-if="
                        syncMeta[zone].tone !== 'info' &&
                        syncMeta[zone].time !== '—'
                      "
                      class="zone-sync__time"
                    >
                      {{ syncMeta[zone].time }}
                    </span>
                  </span>
                </ElTooltip>
                <ElTooltip
                  v-if="skuCount[zone] !== null"
                  :content="ZONE_FIELDS[zone].skuTip"
                  placement="top-start"
                  :show-after="120"
                >
                  <span class="zone-metric">
                    {{
                      skuCount[zone] === 0
                        ? '暂无生效 SKU'
                        : `${skuCount[zone]} 个 SKU 生效`
                    }}
                  </span>
                </ElTooltip>
                <ElButton
                  v-if="syncMeta[zone].tone === 'danger'"
                  class="zone-sync-retry"
                  link
                  type="primary"
                  :loading="retrying"
                  :disabled="retrying"
                  @click="retrySync"
                >
                  重试同步
                </ElButton>
              </div>
              <div class="zone-card__actions">
                <span v-if="dirty[zone]" class="zone-dirty">
                  <i></i>未保存
                </span>
                <ElButton
                  v-if="dirty[zone]"
                  text
                  :disabled="saving[zone]"
                  @click="resetZone(zone)"
                >
                  撤销
                </ElButton>
                <ElButton
                  v-access:code="'promotion:shipactivity:save'"
                  :type="dirty[zone] ? 'primary' : 'default'"
                  :disabled="!dirty[zone] || loading[zone]"
                  :loading="saving[zone]"
                  @click="saveZone(zone)"
                >
                  保存
                </ElButton>
              </div>
            </footer>
          </article>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.zone-page {
  /* 12px 辅助文字的统一色：比 --el-text-color-secondary 略深，保证浅色下也够 4.5:1 */
  --zone-text-muted: color-mix(
    in srgb,
    var(--el-text-color-regular) 72%,
    transparent
  );

  /* 暗色下的链接/主色文字：EP 的 --el-color-primary 在深底上只有 ~2-3.7:1，模板里统一用这个提亮色 */
  --zone-link-color: var(--el-color-primary);

  width: 100%;
  max-width: 1440px;
  padding: 8px 14px 28px;
  margin: 0 auto;
}

/* ---------------------------------------------------------------- 页头 */

.zone-page__head {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 6px 2px 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.zone-page__intro {
  flex: 1 1 380px;
  min-width: 0;
}

.zone-page__title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  line-height: 28px;
  color: var(--el-text-color-primary);
  letter-spacing: -0.2px;
}

.zone-page__readonly {
  display: inline-block;
  padding: 0 8px;
  margin-left: 8px;
  font-size: 11px;
  font-weight: 500;
  line-height: 18px;
  vertical-align: 2px;
  color: var(--zone-text-muted);
  background: var(--el-fill-color-light);
  border-radius: 6px;
}

.zone-page__desc {
  max-width: 760px;
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 20px;
  color: var(--zone-text-muted);
}

.zone-page__actions {
  display: flex;
  flex: none;
  gap: 10px;
  align-items: center;
}

.zone-page__arrow {
  margin-left: 4px;
}

/* 暗色主题下 EP 的 primary 文字偏暗（对比度 ~2-3.7:1），主色换用 dark-2 提亮 */
html.dark .zone-page {
  --zone-link-color: color-mix(in srgb, var(--el-color-primary) 55%, #fff 45%);
}

html.dark .zone-page__actions .el-button--primary.is-plain {
  color: var(--zone-link-color);
  border-color: color-mix(in srgb, var(--zone-link-color) 45%, transparent);
}

html.dark .zone-page__actions .el-button--primary.is-plain:hover {
  color: var(--zone-link-color);
  background: var(--el-color-primary-light-9);
  border-color: var(--zone-link-color);
}

/* ---------------------------------------------------------------- 卡片网格 */

.zone-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(430px, 1fr));
  gap: 16px;
  align-items: stretch;
  margin-top: 20px;
}

.zone-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 14px;
  box-shadow: 0 1px 2px rgb(0 0 0 / 4%);
  transition:
    border-color 200ms ease,
    box-shadow 200ms ease;
}

.zone-card:hover {
  border-color: var(--el-border-color);
  box-shadow: 0 8px 24px -18px rgb(0 0 0 / 45%);
}

/* ---------------------------------------------------------------- 卡片头 */

.zone-card__head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 18px 20px 16px;
}

.zone-card__identity {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  min-width: 0;
}

.zone-card__icon {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  font-size: 19px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  background: color-mix(in srgb, var(--el-color-primary) 10%, transparent);
  border-radius: 11px;
}

.zone-card--clearance .zone-card__icon {
  color: color-mix(in srgb, var(--el-color-warning) 58%, #000);
  background: var(--el-color-warning-light-9);
  background: color-mix(in srgb, var(--el-color-warning) 16%, transparent);
}

.zone-card__titles {
  min-width: 0;
}

.zone-card__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  line-height: 22px;
  color: var(--el-text-color-primary);
}

.zone-card__subtitle {
  max-width: 34em;
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--zone-text-muted);
}

.zone-card__toggle {
  display: flex;
  flex: none;
  gap: 10px;
  align-items: center;
  padding-top: 2px;
}

.zone-state {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  font-size: 12px;
  color: var(--zone-text-muted);
  white-space: nowrap;
}

.zone-state__dot {
  width: 6px;
  height: 6px;
  background: var(--el-text-color-placeholder);
  border-radius: 50%;
}

.zone-state.is-on .zone-state__dot {
  background: var(--el-color-success);
  box-shadow: 0 0 0 3px
    color-mix(in srgb, var(--el-color-success) 18%, transparent);
}

/* ---------------------------------------------------------------- 卡片身 */

.zone-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 18px;
  padding: 2px 20px 20px;
}

.zone-callout {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.zone-callout__icon {
  flex: none;
  margin-top: 1px;
  color: var(--zone-text-muted);
}

.zone-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.zone-field__head {
  display: flex;
  gap: 8px;
  align-items: center;
}

.zone-field__label {
  font-size: 13px;
  font-weight: 500;
  line-height: 20px;
  color: var(--el-text-color-primary);
}

.zone-chip {
  flex: none;
  padding: 0 8px;
  font-size: 11px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  line-height: 18px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border-radius: 6px;
}

.zone-chip.is-empty {
  color: var(--zone-text-muted);
  background: transparent;
  border: 1px dashed var(--el-border-color);
}

.zone-field__number {
  width: 220px;
}

.zone-field__segmented {
  width: fit-content;
}

.zone-field__hint {
  margin: 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--zone-text-muted);
}

.zone-error {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 12px 14px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  background: var(--el-color-danger-light-9);
  border: 1px solid color-mix(in srgb, var(--el-color-danger) 28%, transparent);
  border-radius: 10px;
}

.zone-error__icon {
  color: var(--el-color-danger);
}

/* ---------------------------------------------------------------- 卡片脚 */

.zone-card__foot {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: var(--el-fill-color-light);
  border-top: 1px solid var(--el-border-color-lighter);
}

.zone-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  align-items: center;
  min-width: 0;
}

.zone-sync {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  font-size: 12px;
  color: var(--zone-text-muted);
  cursor: default;
  outline: none;
  border-radius: 6px;
}

.zone-sync:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

html.dark .zone-sync-retry.el-button.is-link {
  color: var(--zone-link-color);
}

html.dark .zone-sync-retry.el-button.is-link:hover {
  color: var(--zone-link-color);
  opacity: 0.85;
}

.zone-sync__dot {
  flex: none;
  width: 6px;
  height: 6px;
  background: var(--el-text-color-placeholder);
  border-radius: 50%;
}

.zone-sync__dot.is-success {
  background: var(--el-color-success);
}

.zone-sync__dot.is-danger {
  background: var(--el-color-danger);
}

.zone-sync__dot.is-info {
  background: var(--el-text-color-placeholder);
}

.zone-sync__time {
  font-variant-numeric: tabular-nums;
  color: var(--zone-text-muted);
}

.zone-metric {
  padding-left: 12px;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--zone-text-muted);
  cursor: default;
  border-left: 1px solid var(--el-border-color-lighter);
}

.zone-card__actions {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-left: auto;
}

.zone-dirty {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.zone-dirty i {
  width: 6px;
  height: 6px;
  background: var(--el-color-warning);
  border-radius: 50%;
}

/* 暗色主题：页面底色与卡片底色几乎相同，抬一层表面色 + 提亮描边，卡片才有层次 */
html.dark .zone-card {
  background: color-mix(in srgb, var(--el-bg-color) 92%, #fff 8%);
  border-color: color-mix(in srgb, var(--el-border-color) 72%, #fff 28%);
  box-shadow: none;
}

html.dark .zone-card__foot {
  background: color-mix(in srgb, var(--el-bg-color) 86%, #fff 14%);
}

/* 暗色主题（Vben 用 html.dark）下 warning 提亮，避免图标在深色底上发闷 */
html.dark .zone-card--clearance .zone-card__icon {
  color: var(--el-color-warning);
  background: color-mix(in srgb, var(--el-color-warning) 18%, transparent);
}

/* ---------------------------------------------------------------- 响应式 */

/* 媒体查询必须排在基础规则之后：同为单类选择器时后者会反超前者，
   响应式与 reduced-motion 覆盖会被基础规则吃掉，因此关闭 order/order 检查。 */
/* stylelint-disable order/order -- 响应式/动效覆盖必须排在基础规则之后 */
@media (width <= 960px) {
  .zone-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (width <= 640px) {
  .zone-page {
    padding: 4px 4px 20px;
  }

  .zone-page__head {
    flex-direction: column;
    padding-bottom: 14px;
  }

  /* 纵向排列后 flex-basis 会变成高度，必须清掉 380px 的 basis，否则标题与按钮之间出现大空白 */
  .zone-page__intro {
    flex: 0 0 auto;
  }

  .zone-page__actions {
    width: 100%;
  }

  .zone-page__actions .el-button {
    flex: 1;
  }

  .zone-card__head {
    flex-direction: column;
    padding: 16px 16px 14px;
  }

  .zone-card__toggle {
    justify-content: space-between;
    width: 100%;
    padding-top: 0;
  }

  .zone-card__body {
    padding: 2px 16px 18px;
  }

  .zone-card__foot {
    padding: 12px 16px;
  }

  .zone-field__number {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .zone-card {
    transition: none;
  }
}
/* stylelint-enable order/order */
</style>
