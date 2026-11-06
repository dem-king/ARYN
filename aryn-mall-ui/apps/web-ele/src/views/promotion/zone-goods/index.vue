<script lang="ts" setup>
import type { TableInstance } from 'element-plus';

import type { ZoneKey } from './logic';

import type {
  ZoneAction,
  ZoneBatchDoneEvent,
  ZoneGoodsRow,
  ZoneSyncStatus,
  ZoneSyncZone,
} from '#/views/product/zone-batch/types';

import { computed, nextTick, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import {
  Box,
  Picture,
  Plus,
  QuestionFilled,
  Refresh,
  Search,
} from '@element-plus/icons-vue';
import { useMediaQuery } from '@vueuse/core';
import {
  ElAlert,
  ElButton,
  ElCascader,
  ElDrawer,
  ElIcon,
  ElImage,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTooltip,
} from 'element-plus';

import { getList as getBrandList } from '#/api/product/goods-brand';
import { getPage as getCategoryTree } from '#/api/product/goods-category';
import {
  getZoneIds,
  getZonePage,
  getZoneSyncStatus,
  triggerZoneSync,
} from '#/api/product/goods-zone';
import { getZoneSetting } from '#/api/promotion/zone-setting';
import Pagination from '#/components/pagination/index.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';
import { useDict } from '#/utils/dict';
import ZoneBatchDialog from '#/views/product/zone-batch/components/ZoneBatchDialog.vue';
import ZoneSkuEditor from '#/views/product/zone-batch/components/ZoneSkuEditor.vue';

import BatchRecordDrawer from './components/BatchRecordDrawer.vue';
import GoodsPickerDialog from './components/GoodsPickerDialog.vue';
import ZoneActionBar from './components/ZoneActionBar.vue';
import ZoneStatusHeader from './components/ZoneStatusHeader.vue';
import {
  buildRemoveConfirmText,
  buildRuleChips,
  buildZoneIdsPayload,
  buildZonePageQuery,
  dedupeIds,
  describeEffectiveDiscount,
  describeSelectAllResult,
  describeSelection,
  formatPrice,
  formatRate,
  hasSkuOverride,
  hasZoneRule,
  mergeFilterSelection,
  resolveBatchDoneTone,
  resolveSelectAllLimit,
  resolveStateMeta,
  summarizeBatchDone,
  toNumber,
  ZONE_LABEL,
} from './logic';

/**
 * 「专区商品管理」页。
 *
 * 以专区为主体管理商品集合，页面按「专区概览 → 筛选 → 列表」三层组织：
 * - 顶部 ZoneStatusHeader：专区切换器 + 专区状态/默认折扣/活动/同步指标，
 *   规则口径收进「规则说明」气泡，不再用整条 Alert 占列表首屏；
 * - 筛选与工具栏分离：筛选只负责查询条件，添加商品/批次记录常驻工具栏，
 *   批量动作按需出现在 ZoneBulkBar（含跨页选择与全选筛选结果）；
 * - 列表把「售价/原价」「生效价」「当前规则」「SKU 精调」「生效状态」重组为
 *   价格、规则、状态三列，价格列直接呈现 C 端生效价与实际折扣。
 *
 * 批量操作复用 #/views/product/zone-batch 共享弹窗（预检→执行→结果面板），
 * 行内精调复用共享 ZoneSkuEditor，批次记录支持明细与撤销。
 *
 * 路由 /promotion/zone-goods 由动态菜单驱动（菜单 component: promotion/zone-goods/index）。
 */

// ---------------------------------------------------------------------------
// 专区 tab 与状态
// ---------------------------------------------------------------------------

const router = useRouter();

const activeTab = ref<string>('member');
const zoneType = computed<ZoneKey>(() =>
  activeTab.value === 'clearance' ? 'clearance' : 'member',
);

interface ZoneSettingRow {
  costFloorMultiplier?: null | number | string;
  defaultDiscountRate?: null | number | string;
  enabled?: boolean | string;
  sortMode?: string;
}

const syncStatus = ref<null | ZoneSyncStatus>(null);
const syncLoading = ref(false);
const triggerLoading = ref(false);
const zoneSettings = ref<Record<ZoneKey, ZoneSettingRow>>({
  clearance: {},
  member: {},
});

const currentSyncZone = computed<null | ZoneSyncZone>(
  () =>
    syncStatus.value?.zones?.find((item) => item.zone === zoneType.value) ??
    null,
);
const currentSetting = computed<ZoneSettingRow>(
  () => zoneSettings.value[zoneType.value] ?? {},
);
const syncFailed = computed(
  () =>
    (currentSyncZone.value?.lastSyncStatus ?? '').toUpperCase() === 'FAILED',
);

// ---------------------------------------------------------------------------
// 列表 / 筛选 / 分页
// ---------------------------------------------------------------------------

const tableRef = ref<TableInstance>();
const loading = ref(false);
const tableData = ref<ZoneGoodsRow[]>([]);
const page = reactive({ current: 1, size: 10, total: 0 });
const filter = reactive({
  brandId: '',
  categorySecondId: '',
  name: '',
  status: '',
});
const categoryTree = ref<Record<string, any>[]>([]);
const brandList = ref<Record<string, any>[]>([]);
const { goods_status } = useDict('goods_status');
/**
 * 窄屏（≤1400px）隐藏「类目 / 品牌」次要列：保证价格、规则、生效状态
 * 三个决策列在 1280 笔记本上无需横向滚动即可看到。
 */
const compactColumns = useMediaQuery('(max-width: 1400px)');

const categoryProps = {
  children: 'children',
  emitPath: false,
  label: 'name',
  value: 'id',
};

async function loadCategories() {
  try {
    categoryTree.value = ((await getCategoryTree()) ?? []) as Record<
      string,
      any
    >[];
  } catch {
    categoryTree.value = [];
  }
}

async function loadBrands() {
  try {
    brandList.value = ((await getBrandList()) ?? []) as Record<string, any>[];
  } catch {
    brandList.value = [];
  }
}

async function initPage() {
  loading.value = true;
  try {
    const response = await getZonePage(
      buildZonePageQuery(zoneType.value, filter, page),
    );
    tableData.value = (response?.records ?? []) as ZoneGoodsRow[];
    page.total = Number(response?.total ?? 0);
    await syncPageSelectionWithSelectAll();
  } catch {
    tableData.value = [];
    page.total = 0;
  } finally {
    loading.value = false;
  }
}

async function loadSyncStatus() {
  syncLoading.value = true;
  try {
    syncStatus.value = await getZoneSyncStatus();
  } catch {
    // 后端同步状态接口未就绪时不阻断页面主流程，仅隐藏状态区
    syncStatus.value = null;
  } finally {
    syncLoading.value = false;
  }
}

async function loadZoneSettings() {
  const [member, clearance] = await Promise.all([
    getZoneSetting('member').catch(() => null),
    getZoneSetting('clearance').catch(() => null),
  ]);
  zoneSettings.value = {
    member: (member ?? {}) as ZoneSettingRow,
    clearance: (clearance ?? {}) as ZoneSettingRow,
  };
}

async function refreshAll() {
  // 列表、同步状态、专区设置一起刷新：别处改了开关/默认折扣后回到本页也能看到
  await Promise.all([initPage(), loadSyncStatus(), loadZoneSettings()]);
}

function handleZoneChange(zone: ZoneKey) {
  if (zone === zoneType.value) {
    return;
  }
  activeTab.value = zone;
  page.current = 1;
  clearSelection(true);
  // 立刻清空旧专区行：loading 期间若继续渲染旧行，会被按新专区的规则口径解读
  tableData.value = [];
  page.total = 0;
  void refreshAll();
}

/** 搜索：条件变化回到第一页并清空跨页选择，避免跨筛选误操作 */
function handleSearch() {
  page.current = 1;
  clearSelection(true);
  void initPage();
}

/** 重置：清空查询条件后立即查询（不再依赖表单组件的 resetFields） */
function handleReset() {
  filter.name = '';
  filter.categorySecondId = '';
  filter.brandId = '';
  filter.status = '';
  page.current = 1;
  clearSelection(true);
  void initPage();
}

// ---------------------------------------------------------------------------
// 选择：跨页保留 + 全选筛选结果
// ---------------------------------------------------------------------------

const selectedRows = ref<ZoneGoodsRow[]>([]);
const selectAllState = reactive({
  active: false,
  ids: [] as string[],
  loading: false,
  total: 0,
  truncated: false,
});
/** 程序化改选表格时屏蔽 selection-change，避免误退出「全选筛选结果」模式 */
let syncingSelection = false;

const effectiveIds = computed<string[]>(() =>
  selectAllState.active
    ? selectAllState.ids
    : dedupeIds(selectedRows.value.map((row) => row.id)),
);
const selectedCount = computed(() => effectiveIds.value.length);
const selectionText = computed(() =>
  describeSelection(
    selectedCount.value,
    selectAllState.active ? 'filter' : 'manual',
    selectAllState.total || page.total,
  ),
);

function resetSelectAll() {
  selectAllState.active = false;
  selectAllState.ids = [];
  selectAllState.total = 0;
  selectAllState.truncated = false;
}

function clearSelection(silent = false) {
  const hadSelection = selectedCount.value > 0 || selectAllState.active;
  syncingSelection = true;
  try {
    tableRef.value?.clearSelection();
  } finally {
    syncingSelection = false;
  }
  selectedRows.value = [];
  resetSelectAll();
  if (!silent && hadSelection) {
    ElMessage.info('已清空跨页选择');
  }
}

function handleSelectionChange(selection: ZoneGoodsRow[]) {
  if (syncingSelection) {
    return;
  }
  // 「全选筛选结果」模式下，当前页勾选变化只增删对应 id，不静默退化成
  // 「仅已渲染行选择」——否则用户取消一行时批量作用范围会从 48 件骤降到当前页。
  if (selectAllState.active) {
    const nextIds = mergeFilterSelection(
      selectAllState.ids,
      tableData.value.map((row) => String(row.id)),
      selection.map((row) => String(row.id)),
    );
    if (nextIds.length === 0) {
      resetSelectAll();
    } else {
      selectAllState.ids = nextIds;
    }
  }
  selectedRows.value = selection;
}

/**
 * 全选模式下翻页/刷新后，把当前页落在筛选结果集里的行补上勾选状态。
 * 选择语义以 selectAllState.ids 为准，表格勾选只做视觉同步。
 */
async function syncPageSelectionWithSelectAll() {
  if (!selectAllState.active) {
    return;
  }
  await nextTick();
  const idSet = new Set(selectAllState.ids);
  syncingSelection = true;
  try {
    for (const row of tableData.value) {
      if (idSet.has(String(row.id))) {
        tableRef.value?.toggleRowSelection(row, true);
      }
    }
  } finally {
    syncingSelection = false;
  }
}

/**
 * 全选筛选结果：后端按固定排序返回筛选结果全集（契约 §2.5，默认 500 / 上限 1000）。
 * 超过上限时提示缩小筛选分批操作，不静默漏选。
 */
async function handleSelectAll() {
  if (page.total <= 0) {
    ElMessage.warning('当前筛选没有可选商品');
    return;
  }
  selectAllState.loading = true;
  try {
    const limit = resolveSelectAllLimit(page.total);
    const response = await getZoneIds(
      buildZoneIdsPayload(zoneType.value, filter, limit),
    );
    const ids = dedupeIds(response?.ids ?? []);
    if (ids.length === 0) {
      ElMessage.warning('筛选结果为空，无可选商品');
      return;
    }
    const idSet = new Set(ids);
    syncingSelection = true;
    try {
      tableRef.value?.clearSelection();
      for (const row of tableData.value) {
        if (idSet.has(String(row.id))) {
          tableRef.value?.toggleRowSelection(row, true);
        }
      }
    } finally {
      syncingSelection = false;
    }
    selectAllState.active = true;
    selectAllState.ids = ids;
    selectAllState.total = Number(response?.total ?? page.total);
    selectAllState.truncated = response?.truncated === true;
    const message = describeSelectAllResult(
      selectAllState.total,
      ids.length,
      selectAllState.truncated,
    );
    if (selectAllState.total > ids.length || selectAllState.truncated) {
      ElMessage.warning(message);
    } else {
      ElMessage.success(message);
    }
  } catch {
    // 请求层已提示错误，这里保证 loading 复位即可
  } finally {
    selectAllState.loading = false;
  }
}

// ---------------------------------------------------------------------------
// 批量操作：添加商品 / 设置规则 / 移出专区（复用共享 ZoneBatchDialog）
// ---------------------------------------------------------------------------

const pickerVisible = ref(false);
const batchVisible = ref(false);
const batchMode = ref<'remove' | 'standard'>('standard');
/** 打开来源：add=添加商品，rules=批量设置规则，remove=移出专区 */
const batchIntent = ref<'add' | 'remove' | 'rules'>('rules');
const batchSpuIds = ref<string[]>([]);
/**
 * 添加商品流程打开弹窗时预选「加入/更新规则」（契约 §3.4），
 * 批量设置规则/移出流程保持默认（不修改 / 由 remove 模式决定）。
 */
const batchDefaultAction = computed<undefined | ZoneAction>(() =>
  batchIntent.value === 'add' ? 'ADD' : undefined,
);
const batchDialogTitle = computed(() => {
  const zoneLabel = ZONE_LABEL[zoneType.value];
  if (batchIntent.value === 'remove') {
    return `移出「${zoneLabel}」`;
  }
  if (batchIntent.value === 'add') {
    return `添加商品到「${zoneLabel}」并设置规则`;
  }
  return `批量设置「${zoneLabel}」规则`;
});

function openBatchDialog(
  spuIds: string[],
  mode: 'remove' | 'standard',
  intent: 'add' | 'remove' | 'rules' = 'rules',
) {
  const ids = dedupeIds(spuIds);
  if (ids.length === 0) {
    ElMessage.warning('请选择商品');
    return;
  }
  batchSpuIds.value = ids;
  batchMode.value = mode;
  batchIntent.value = intent;
  batchVisible.value = true;
}

function openAddGoods() {
  pickerVisible.value = true;
}

/** 添加商品：picker 选完商品后进入共享弹窗（动作默认加入/更新规则，预检后确认） */
function handlePickerConfirm(ids: string[]) {
  pickerVisible.value = false;
  openBatchDialog(ids, 'standard', 'add');
}

function openBatchRules() {
  if (selectedCount.value === 0) {
    ElMessage.warning('请先选择商品');
    return;
  }
  openBatchDialog(effectiveIds.value, 'standard', 'rules');
}

async function openBatchRemove() {
  if (selectedCount.value === 0) {
    ElMessage.warning('请先选择商品');
    return;
  }
  const ids = effectiveIds.value;
  try {
    await ElMessageBox.confirm(
      buildRemoveConfirmText(zoneType.value, ids.length),
      '批量移出专区',
      {
        cancelButtonText: '取消',
        confirmButtonText: '继续移出',
        type: 'warning',
      },
    );
  } catch {
    return;
  }
  openBatchDialog(ids, 'remove', 'remove');
}

/** 行内移出：复用共享弹窗的 remove 模式，先预检影响再确认 */
async function handleRowRemove(row: Record<string, any> | ZoneGoodsRow) {
  try {
    await ElMessageBox.confirm(
      buildRemoveConfirmText(zoneType.value, 1),
      '移出专区',
      {
        cancelButtonText: '取消',
        confirmButtonText: '继续移出',
        type: 'warning',
      },
    );
  } catch {
    return;
  }
  openBatchDialog([String(row.id)], 'remove', 'remove');
}

function handleBatchDone(payload: ZoneBatchDoneEvent) {
  batchVisible.value = false;
  ElMessage({
    type: resolveBatchDoneTone(payload),
    message: summarizeBatchDone(payload),
    duration: 4000,
  });
  // 操作后清空选择，避免对已变更数据二次误操作
  clearSelection(true);
  void refreshAll();
}

// ---------------------------------------------------------------------------
// 行内 SKU 精调抽屉（复用共享 ZoneSkuEditor）
// ---------------------------------------------------------------------------

const skuDrawerVisible = ref(false);
const skuSpuId = ref('');
const skuSpuName = ref('');
const skuDrawerTitle = computed(() =>
  skuSpuName.value ? `专区规则精调 - ${skuSpuName.value}` : '专区规则精调',
);

function openSkuEditor(row: Record<string, any> | ZoneGoodsRow) {
  skuSpuId.value = String(row.id ?? '');
  skuSpuName.value = String(row.name ?? '');
  skuDrawerVisible.value = true;
}

function handleSkuSaved() {
  void refreshAll();
}

// ---------------------------------------------------------------------------
// 批次记录 / 同步重试
// ---------------------------------------------------------------------------

const recordDrawerVisible = ref(false);

function openRecordDrawer() {
  recordDrawerVisible.value = true;
}

function handleRecordChanged() {
  void refreshAll();
}

async function retrySync() {
  triggerLoading.value = true;
  try {
    syncStatus.value = await triggerZoneSync();
    ElMessage.success('已触发专区价格重算，请稍后刷新确认同步状态');
    await initPage();
  } catch {
    // 请求层已提示错误；这里保证 loading 复位且页面可继续重试
  } finally {
    triggerLoading.value = false;
  }
}

function goZoneSetting() {
  void router.push('/promotion/zone-setting');
}

function goBatchRecords() {
  openRecordDrawer();
}

// ---------------------------------------------------------------------------
// 列表展示派生（纯函数集中放在 logic.ts，这里只做模板适配）
// ---------------------------------------------------------------------------

/** 生效状态徽标（模板内多次读取，集中到一处避免重复三目） */
function stateMeta(row: Record<string, any>) {
  return resolveStateMeta(row.state, row.stateLabel);
}

/** 生效价相对售价的实际折扣角标 */
function discountOf(row: Record<string, any>) {
  return describeEffectiveDiscount(row.effectivePrice, row.salesPrice);
}

/**
 * 规则标记：未单独配规则但「生效中」时，价格来自专区默认折扣率，
 * 直接把实际口径写成「默认折扣 9折」，避免运营误判为「什么都没配」。
 */
function ruleChips(row: Record<string, any>) {
  const defaultRate = toNumber(currentSetting.value.defaultDiscountRate);
  const inheritDefault =
    !hasZoneRule(zoneType.value, row) &&
    String(row.state ?? '').toUpperCase() === 'NORMAL' &&
    defaultRate !== null;
  if (inheritDefault) {
    return [{ label: `默认折扣 ${formatRate(defaultRate)}` }];
  }
  return buildRuleChips(zoneType.value, row);
}

/** 是否存在有效筛选条件（空状态下的「清空筛选」只在有筛选时才有意义） */
const hasActiveFilter = computed(
  () =>
    !!filter.name ||
    !!filter.categorySecondId ||
    !!filter.brandId ||
    !!filter.status,
);

/** 窄屏隐藏「类目 / 品牌」列时，把该信息折叠进商品副标题行 */
function categoryBrandText(row: Record<string, any>) {
  const parts = [row.categoryName, row.brandName].filter(Boolean);
  return parts.length > 0 ? parts.join(' · ') : '未分类 · 无品牌';
}

// ---------------------------------------------------------------------------
// 初始化 / keepAlive 刷新
// ---------------------------------------------------------------------------

onMounted(() => {
  void loadCategories();
  void loadBrands();
  void loadZoneSettings();
  void loadSyncStatus();
  void initPage();
});

useKeepAliveRefresh(() => {
  void refreshAll();
});
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view zone-page">
      <!-- 专区概览：切换器 + 状态指标 + 规则说明 -->
      <ZoneStatusHeader
        :active-tab="activeTab"
        :loading="syncLoading"
        :retrying="triggerLoading"
        :setting="currentSetting"
        :sync-zone="currentSyncZone"
        @change="handleZoneChange"
        @retry="retrySync"
        @settings="goZoneSetting"
      />

      <ElAlert
        v-if="syncFailed && currentSyncZone?.lastError"
        class="zone-alert"
        type="error"
        :closable="false"
        show-icon
      >
        <template #title>
          最近一次价格同步失败：{{ currentSyncZone.lastError }}
        </template>
        <ElButton
          link
          type="primary"
          :loading="triggerLoading"
          @click="retrySync"
        >
          立即重试同步
        </ElButton>
      </ElAlert>

      <!-- 筛选 -->
      <section class="zone-filters" role="search" aria-label="商品筛选">
        <ElInput
          v-model="filter.name"
          class="zone-filters__search"
          aria-label="商品名称"
          clearable
          placeholder="搜索商品名称"
          :prefix-icon="Search"
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        />
        <ElCascader
          v-model="filter.categorySecondId"
          class="zone-filters__cascader"
          :options="categoryTree"
          :props="categoryProps"
          aria-label="商品类目"
          clearable
          filterable
          placeholder="商品类目"
          @change="handleSearch"
        />
        <ElSelect
          v-model="filter.brandId"
          class="zone-filters__select"
          aria-label="商品品牌"
          clearable
          filterable
          placeholder="商品品牌"
          @change="handleSearch"
        >
          <ElOption
            v-for="brand in brandList"
            :key="brand.id"
            :label="brand.name"
            :value="brand.id"
          />
        </ElSelect>
        <ElSelect
          v-model="filter.status"
          class="zone-filters__select zone-filters__select--status"
          aria-label="商品状态"
          clearable
          placeholder="商品状态"
          @change="handleSearch"
        >
          <ElOption
            v-for="item in goods_status"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
        <div class="zone-filters__actions">
          <ElButton type="primary" :icon="Search" @click="handleSearch">
            查询
          </ElButton>
          <ElButton :icon="Refresh" @click="handleReset">重置</ElButton>
        </div>
      </section>

      <!-- 操作条：普通态为添加商品/批次记录，选择态原地切换为批量操作 -->
      <ZoneActionBar
        :selection-text="selectionText"
        :filter-total="page.total"
        :select-all-loading="selectAllState.loading"
        :selected-count="selectedCount"
        :show-select-all="selectedCount < page.total"
        @add="openAddGoods"
        @clear="clearSelection(false)"
        @records="goBatchRecords"
        @refresh="refreshAll"
        @remove="openBatchRemove"
        @rules="openBatchRules"
        @select-all="handleSelectAll"
      />

      <!-- 列表 -->
      <ElTable
        ref="tableRef"
        v-loading="loading"
        class="hx-data-table zone-table"
        :data="tableData"
        row-key="id"
        empty-text="暂无专区商品"
        @selection-change="handleSelectionChange"
      >
        <ElTableColumn
          type="selection"
          width="46"
          align="center"
          reserve-selection
        />

        <ElTableColumn label="商品信息" min-width="270">
          <template #default="{ row }">
            <div class="goods-cell">
              <ElImage
                class="goods-cell__img"
                :src="row.spuUrls?.[0]"
                :preview-src-list="row.spuUrls"
                :hide-on-click-modal="true"
                fit="cover"
                :preview-teleported="true"
              >
                <template #error>
                  <div class="goods-cell__fallback">
                    <ElIcon><Picture /></ElIcon>
                  </div>
                </template>
              </ElImage>
              <div class="goods-cell__main">
                <div class="goods-cell__title-row">
                  <span class="goods-cell__title" :title="row.name">
                    {{ row.name || '—' }}
                  </span>
                  <ElTag
                    v-if="row.status !== '1'"
                    size="small"
                    type="info"
                    effect="plain"
                  >
                    已下架
                  </ElTag>
                  <span
                    v-if="row.stock !== null && row.stock !== undefined"
                    class="goods-cell__stock"
                  >
                    库存 {{ row.stock }}
                  </span>
                </div>
                <div
                  v-if="row.subTitle || compactColumns"
                  class="goods-cell__meta"
                >
                  <span
                    v-if="row.subTitle"
                    class="goods-cell__sub"
                    :title="row.subTitle"
                  >
                    {{ row.subTitle }}
                  </span>
                  <span v-if="compactColumns" class="goods-cell__cate">
                    {{ categoryBrandText(row) }}
                  </span>
                </div>
              </div>
            </div>
          </template>
        </ElTableColumn>

        <ElTableColumn v-if="!compactColumns" label="类目 / 品牌" width="150">
          <template #default="{ row }">
            <div class="cate-cell">
              <span class="cate-cell__main">
                {{ row.categoryName || '未分类' }}
              </span>
              <span class="cate-cell__sub">
                {{ row.brandName || '无品牌' }}
              </span>
            </div>
          </template>
        </ElTableColumn>

        <ElTableColumn width="190">
          <template #header>
            <span class="zone-th">
              生效价
              <ElTooltip
                content="生效价为 C 端实际展示的专区价；售价 / 原价为商品基础价格"
                placement="top"
                :trigger="['hover', 'focus']"
              >
                <ElIcon
                  class="zone-th__tip"
                  tabindex="0"
                  aria-label="价格口径说明：生效价为 C 端实际展示的专区价，售价与原价为商品基础价格"
                >
                  <QuestionFilled />
                </ElIcon>
              </ElTooltip>
            </span>
          </template>
          <template #default="{ row }">
            <div class="price-cell">
              <div class="price-cell__main">
                <span
                  class="price-cell__now"
                  :class="{ 'is-empty': row.effectivePrice == null }"
                  :title="
                    hasSkuOverride(row)
                      ? '含 SKU 精调：部分规格按精调价格展示'
                      : undefined
                  "
                >
                  {{ formatPrice(row.effectivePrice) }}
                </span>
                <span v-if="hasSkuOverride(row)" class="price-cell__flag">
                  含精调
                </span>
                <span v-if="discountOf(row)" class="price-cell__off">
                  {{ discountOf(row) }}
                </span>
              </div>
              <div class="price-cell__meta">
                <span>售价 {{ formatPrice(row.salesPrice) }}</span>
                <span v-if="row.originalPrice" class="price-cell__origin">
                  原价 {{ formatPrice(row.originalPrice) }}
                </span>
              </div>
            </div>
          </template>
        </ElTableColumn>

        <ElTableColumn min-width="225">
          <template #header>
            <span class="zone-th">
              专区规则
              <ElTooltip
                content="规则按 SPU 配置；SKU 精调会按规格覆盖 SPU 规则"
                placement="top"
                :trigger="['hover', 'focus']"
              >
                <ElIcon
                  class="zone-th__tip"
                  tabindex="0"
                  aria-label="专区规则说明：规则按 SPU 配置，SKU 精调会按规格覆盖 SPU 规则"
                >
                  <QuestionFilled />
                </ElIcon>
              </ElTooltip>
            </span>
          </template>
          <template #default="{ row }">
            <div class="rule-cell">
              <span
                v-for="(chip, index) in ruleChips(row)"
                :key="index"
                class="rule-chip"
                :class="chip.tone ? `rule-chip--${chip.tone}` : ''"
                :title="chip.hint"
              >
                {{ chip.label }}
              </span>
              <button
                v-if="hasSkuOverride(row)"
                type="button"
                class="rule-chip rule-chip--action"
                @click="openSkuEditor(row)"
              >
                SKU 精调 {{ row.skuOverrideCount }}
              </button>
            </div>
          </template>
        </ElTableColumn>

        <ElTableColumn width="120" align="center">
          <template #header>
            <span class="zone-th">
              生效状态
              <ElTooltip
                content="状态优先级：已下架 > 已到期 > 未配规则 > 倒挂不生效 > 生效中"
                placement="top"
                :trigger="['hover', 'focus']"
              >
                <ElIcon
                  class="zone-th__tip"
                  tabindex="0"
                  aria-label="生效状态说明：状态优先级为已下架、已到期、未配规则、倒挂不生效、生效中"
                >
                  <QuestionFilled />
                </ElIcon>
              </ElTooltip>
            </span>
          </template>
          <template #default="{ row }">
            <ElTooltip :content="stateMeta(row).tip" placement="top">
              <span
                class="state-pill"
                :class="`state-pill--${stateMeta(row).type}`"
              >
                <span class="state-pill__dot" aria-hidden="true"></span>
                {{ stateMeta(row).label }}
              </span>
            </ElTooltip>
          </template>
        </ElTableColumn>

        <ElTableColumn label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <ElButton
                v-access:code="'product:goodsspu:edit'"
                link
                type="primary"
                @click="openSkuEditor(row)"
              >
                调整规则
              </ElButton>
              <ElButton
                v-access:code="'product:goodsspu:edit'"
                link
                type="danger"
                @click="handleRowRemove(row)"
              >
                移出
              </ElButton>
            </div>
          </template>
        </ElTableColumn>

        <template #empty>
          <div class="zone-empty">
            <div class="zone-empty__icon" aria-hidden="true">
              <ElIcon><Box /></ElIcon>
            </div>
            <p class="zone-empty__title">当前条件下暂无专区商品</p>
            <p class="zone-empty__desc">
              调整筛选条件，或把商品添加到专区后再设置规则。
            </p>
            <div class="zone-empty__actions">
              <ElButton
                v-access:code="'product:goodsspu:edit'"
                type="primary"
                :icon="Plus"
                @click="openAddGoods"
              >
                添加商品
              </ElButton>
              <ElButton v-if="hasActiveFilter" @click="handleReset">
                清空筛选
              </ElButton>
            </div>
          </div>
        </template>
      </ElTable>

      <Pagination
        v-model:current="page.current"
        v-model:size="page.size"
        :total="page.total"
        @change="initPage"
      />

      <!-- 添加商品选择器 -->
      <GoodsPickerDialog
        v-model:visible="pickerVisible"
        :zone-type="zoneType"
        @confirm="handlePickerConfirm"
      />

      <!-- 批量操作共享弹窗：预检 → 执行 → 结果面板 -->
      <ZoneBatchDialog
        v-model:visible="batchVisible"
        :spu-ids="batchSpuIds"
        :zone-scope="zoneType"
        :mode="batchMode"
        :default-action="batchDefaultAction"
        :title="batchDialogTitle"
        @done="handleBatchDone"
      />

      <!-- 行内 SKU 精调（共享组件，独立保存） -->
      <ElDrawer
        v-model="skuDrawerVisible"
        :title="skuDrawerTitle"
        size="72%"
        :close-on-click-modal="false"
        destroy-on-close
      >
        <ZoneSkuEditor
          v-if="skuSpuId"
          :key="skuSpuId"
          :spu-id="skuSpuId"
          @saved="handleSkuSaved"
        />
      </ElDrawer>

      <!-- 批次记录抽屉 -->
      <BatchRecordDrawer
        v-model:visible="recordDrawerVisible"
        @changed="handleRecordChanged"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
/* 窄屏：筛选控件铺满，避免右侧按钮被挤到下一行悬空 */
@media (width <= 1280px) {
  .zone-filters__search,
  .zone-filters__cascader {
    width: 210px;
  }

  .zone-filters__select {
    width: 160px;
  }

  .zone-filters__select--status {
    width: 140px;
  }
}

.zone-page {
  --zone-gap: 12px;
}

.zone-alert {
  flex: none;
  margin-bottom: var(--zone-gap);
  border-radius: 10px;
}

/* ---------------------------------------------------------------------- */

/* 筛选                                                                   */

/* ---------------------------------------------------------------------- */

.zone-filters {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 14px 16px;
  margin-bottom: var(--zone-gap);
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}

.zone-filters__search {
  width: 260px;
}

.zone-filters__cascader {
  width: 220px;
}

.zone-filters__select {
  width: 180px;
}

.zone-filters__select--status {
  width: 150px;
}

.zone-filters__actions {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-left: auto;

  :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}

/* ---------------------------------------------------------------------- */

/* 表格                                                                   */

/* ---------------------------------------------------------------------- */

.zone-table {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;

  :deep(.el-table__inner-wrapper::before) {
    display: none;
  }

  :deep(th.el-table__cell) {
    height: 46px;
    padding: 0;
    font-weight: 600;
    color: var(--el-text-color-secondary);
  }

  :deep(td.el-table__cell) {
    padding: 8px 0;
  }

  :deep(.el-table__row:last-child > td.el-table__cell) {
    border-bottom: 0;
  }

  /* 操作列固定右侧：默认背景会透出滚动内容，补一层卡片底色 */
  :deep(.el-table-fixed-column--right) {
    background: var(--el-bg-color);
  }

  :deep(.el-table__row:hover > .el-table-fixed-column--right) {
    background: var(--el-color-primary-light-9);
  }
}

.zone-th {
  display: inline-flex;
  gap: 4px;
  align-items: center;
}

.zone-th__tip {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  cursor: help;

  &:hover {
    color: var(--el-color-primary);
  }
}

/* 商品单元格 */
.goods-cell {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
}

.goods-cell__img {
  flex: none;
  width: 48px;
  height: 48px;
  overflow: hidden;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.goods-cell__fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 18px;
  color: var(--el-text-color-placeholder);
}

.goods-cell__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.goods-cell__title-row {
  display: flex;
  gap: 6px;
  align-items: center;
  min-width: 0;
}

.goods-cell__title {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.4;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.goods-cell__meta {
  display: flex;
  flex-wrap: nowrap;
  gap: 8px;
  align-items: center;
  min-width: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.goods-cell__sub {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 窄屏隐藏「类目 / 品牌」列后，把该信息折叠到副标题行，信息不丢失 */
.goods-cell__cate {
  flex: none;
  padding-left: 8px;
  margin-left: auto;
  border-left: 1px solid var(--el-border-color-lighter);
}

.goods-cell__stock {
  flex: none;
  margin-left: auto;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-secondary);
}

/* 类目 / 品牌 */
.cate-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 13px;
}

.cate-cell__main {
  color: var(--el-text-color-regular);
}

.cate-cell__sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 价格 */
.price-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.price-cell__main {
  display: flex;
  gap: 6px;
  align-items: baseline;
}

.price-cell__now {
  font-size: 16px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--el-color-danger);

  &.is-empty {
    font-size: 14px;
    font-weight: 400;
    color: var(--el-text-color-placeholder);
  }
}

.price-cell__off {
  padding: 0 5px;
  font-size: 11px;
  line-height: 16px;

  /* 语义色与正文色混合，小字号下仍满足 4.5:1 对比度 */
  color: color-mix(
    in srgb,
    var(--el-color-danger) 55%,
    var(--el-text-color-primary)
  );
  background: var(--el-color-danger-light-9);
  border-radius: 4px;
}

.price-cell__flag {
  padding: 0 5px;
  font-size: 11px;
  line-height: 16px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color);
  border-radius: 4px;
}

.price-cell__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-regular);
}

.price-cell__origin {
  text-decoration: line-through;
}

/* 规则 chip */
.rule-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.rule-chip {
  padding: 1px 8px;
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-regular);
  white-space: nowrap;
  background: var(--el-fill-color-light);
  border: 1px solid transparent;
  border-radius: 6px;

  &--warning {
    color: color-mix(
      in srgb,
      var(--el-color-warning) 45%,
      var(--el-text-color-primary)
    );
    background: var(--el-color-warning-light-9);
    border-color: var(--el-color-warning-light-7);
  }

  &--danger {
    color: color-mix(
      in srgb,
      var(--el-color-danger) 55%,
      var(--el-text-color-primary)
    );
    background: var(--el-color-danger-light-9);
    border-color: var(--el-color-danger-light-7);
  }

  &--action {
    font-family: inherit;
    color: var(--el-color-primary);
    cursor: pointer;
    background: var(--el-color-primary-light-9);
    border-color: var(--el-color-primary-light-7);
    transition:
      background-color 160ms ease,
      border-color 160ms ease;

    &:hover {
      background: var(--el-color-primary-light-8);
      border-color: var(--el-color-primary-light-5);
    }

    &:focus-visible {
      outline: 2px solid var(--el-color-primary);
      outline-offset: 1px;
    }
  }
}

/* 生效状态 pill：色点 + 文字，避免只靠颜色传达状态 */
.state-pill {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  padding: 1px 9px;
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
  border: 1px solid transparent;
  border-radius: 999px;

  .state-pill__dot {
    width: 5px;
    height: 5px;
    background: currentcolor;
    border-radius: 50%;
  }

  &--success {
    color: color-mix(
      in srgb,
      var(--el-color-success) 42%,
      var(--el-text-color-primary)
    );
    background: var(--el-color-success-light-9);
    border-color: var(--el-color-success-light-7);
  }

  &--warning {
    color: color-mix(
      in srgb,
      var(--el-color-warning) 40%,
      var(--el-text-color-primary)
    );
    background: var(--el-color-warning-light-9);
    border-color: var(--el-color-warning-light-7);
  }

  &--danger {
    color: color-mix(
      in srgb,
      var(--el-color-danger) 50%,
      var(--el-text-color-primary)
    );
    background: var(--el-color-danger-light-9);
    border-color: var(--el-color-danger-light-7);
  }

  &--info {
    color: var(--el-text-color-regular);
    background: var(--el-fill-color-light);
    border-color: var(--el-border-color-lighter);
  }
}

/* 行操作 */
.row-actions {
  display: flex;
  gap: 4px;
  align-items: center;
  justify-content: center;

  :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}

/* 空状态 */
.zone-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 36px 16px 40px;
}

.zone-empty__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  margin-bottom: 12px;
  font-size: 24px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 14px;
}

.zone-empty__title {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.zone-empty__desc {
  margin: 0 0 16px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.zone-empty__actions {
  display: flex;
  gap: 8px;
  align-items: center;

  :deep(.el-button + .el-button) {
    margin-left: 0;
  }
}
</style>
