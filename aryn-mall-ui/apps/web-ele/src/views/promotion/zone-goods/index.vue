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
  Check,
  Delete,
  Plus,
  Refresh,
  Setting,
  Tickets,
} from '@element-plus/icons-vue';
import {
  ElAlert,
  ElButton,
  ElCascader,
  ElDrawer,
  ElFormItem,
  ElImage,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
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
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';
import Pagination from '#/components/pagination/index.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';
import { useDict } from '#/utils/dict';
import ZoneBatchDialog from '#/views/product/zone-batch/components/ZoneBatchDialog.vue';
import ZoneSkuEditor from '#/views/product/zone-batch/components/ZoneSkuEditor.vue';

import BatchRecordDrawer from './components/BatchRecordDrawer.vue';
import GoodsPickerDialog from './components/GoodsPickerDialog.vue';
import {
  buildRemoveConfirmText,
  buildRuleSummary,
  buildZoneIdsPayload,
  buildZonePageQuery,
  dedupeIds,
  describeDefaultDiscount,
  describeEffectivePrice,
  describeSelectAllResult,
  describeSelection,
  describeSkuOverride,
  formatTime,
  hasSkuOverride,
  resolveActivityStatusMeta,
  resolveBatchDoneTone,
  resolveSelectAllLimit,
  resolveStateMeta,
  resolveSyncMeta,
  summarizeBatchDone,
  ZONE_DESCRIPTION,
  ZONE_LABEL,
} from './logic';

/**
 * 「专区商品管理」页。
 *
 * 以专区为主体管理商品集合：顶部专区 tab + 专区状态（开关/默认折扣/同步状态），
 * 中部专区商品分页（跨页选择 / 全选筛选结果），
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
const defaultDiscountText = computed(() =>
  describeDefaultDiscount(currentSetting.value.defaultDiscountRate),
);
const enabledTag = computed(() => {
  // 优先用同步状态里的开关；接口不可用时回退专区设置；两者都取不到显示未知
  const syncEnabled = currentSyncZone.value?.enabled;
  if (typeof syncEnabled === 'boolean') {
    return syncEnabled
      ? { text: '已开启', type: 'success' as const }
      : { text: '已关闭', type: 'danger' as const };
  }
  const settingEnabled = currentSetting.value.enabled;
  if (settingEnabled === null || settingEnabled === undefined) {
    return { text: '未知', type: 'info' as const };
  }
  return settingEnabled === '0' || settingEnabled === false
    ? { text: '已关闭', type: 'danger' as const }
    : { text: '已开启', type: 'success' as const };
});
const syncMeta = computed(() =>
  resolveSyncMeta(currentSyncZone.value?.lastSyncStatus),
);
const activityMeta = computed(() =>
  resolveActivityStatusMeta(currentSyncZone.value?.activityStatus),
);
const syncFailed = computed(
  () =>
    (currentSyncZone.value?.lastSyncStatus ?? '').toUpperCase() === 'FAILED',
);
const zoneDescription = computed(() => ZONE_DESCRIPTION[zoneType.value]);

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

function handleTabChange(name: number | string) {
  activeTab.value = String(name);
  page.current = 1;
  clearSelection(true);
  void refreshAll();
}

/** 搜索：条件变化回到第一页并清空跨页选择，避免跨筛选误操作 */
function handleSearch() {
  page.current = 1;
  clearSelection(true);
  void initPage();
}

function handleReset() {
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
const selectAllBanner = computed(() =>
  describeSelectAllResult(
    selectAllState.total,
    selectAllState.ids.length,
    selectAllState.truncated,
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
  // 用户手动勾选/取消即退出「全选筛选结果」，避免两种选择语义叠加
  if (selectAllState.active) {
    resetSelectAll();
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
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 专区 tab + 专区状态 -->
      <div class="zone-panel">
        <ElTabs
          v-model="activeTab"
          class="zone-tabs"
          @tab-change="handleTabChange"
        >
          <ElTabPane :label="ZONE_LABEL.member" name="member" />
          <ElTabPane :label="ZONE_LABEL.clearance" name="clearance" />
        </ElTabs>
        <div v-loading="syncLoading" class="zone-status">
          <div class="zone-status__item">
            <span class="zone-status__label">专区状态</span>
            <ElTag size="small" :type="enabledTag.type">
              {{ enabledTag.text }}
            </ElTag>
          </div>
          <div class="zone-status__item">
            <span class="zone-status__label">默认折扣率</span>
            <b class="zone-status__value">{{ defaultDiscountText }}</b>
            <span class="zone-status__tip">商品未配规则时生效</span>
          </div>
          <div class="zone-status__item">
            <span class="zone-status__label">系统活动</span>
            <ElTag size="small" :type="activityMeta.type">
              {{ activityMeta.label }}
            </ElTag>
          </div>
          <div class="zone-status__item">
            <span class="zone-status__label">已同步商品</span>
            <b class="zone-status__value">
              {{ currentSyncZone?.rulesSkuCount ?? '—' }}
            </b>
            <span class="zone-status__tip">件</span>
          </div>
          <div class="zone-status__item">
            <span class="zone-status__label">最近同步</span>
            <ElTooltip
              :content="currentSyncZone?.lastError || syncMeta.tip"
              placement="top"
            >
              <ElTag size="small" :type="syncMeta.type">
                {{ syncMeta.label }}
              </ElTag>
            </ElTooltip>
            <span class="zone-status__tip">
              {{ formatTime(currentSyncZone?.lastSyncTime) }}
            </span>
            <ElButton
              v-if="syncFailed || !currentSyncZone"
              link
              type="primary"
              :loading="triggerLoading"
              @click="retrySync"
            >
              重试同步
            </ElButton>
          </div>
          <div class="zone-status__item zone-status__item--actions">
            <ElButton link type="primary" @click="goZoneSetting">
              去专区设置 →
            </ElButton>
          </div>
        </div>
      </div>

      <ElAlert
        v-if="syncFailed && currentSyncZone?.lastError"
        type="error"
        :closable="false"
        show-icon
        class="mb10"
        :title="`最近一次价格同步失败：${currentSyncZone.lastError}`"
      />
      <ElAlert type="info" :closable="false" show-icon class="mb10">
        <template #title>{{ zoneDescription }}</template>
        「标签存在」不等于「价格生效」：请关注表格「生效状态」列；缺失规则、倒挂、出清到期、商品下架都会导致
        C 端不展示专区价。
      </ElAlert>

      <!-- 筛选 -->
      <PageFilterPanel
        :model="filter"
        @reset="handleReset"
        @search="handleSearch"
      >
        <ElFormItem label="商品名称" prop="name">
          <ElInput
            v-model="filter.name"
            clearable
            placeholder="请输入商品名称"
            @keyup.enter="handleSearch"
          />
        </ElFormItem>
        <ElFormItem label="商品类目" prop="categorySecondId">
          <ElCascader
            v-model="filter.categorySecondId"
            style="width: 200px"
            :options="categoryTree"
            :props="categoryProps"
            clearable
            filterable
            placeholder="全部类目"
            @change="handleSearch"
          />
        </ElFormItem>
        <ElFormItem label="商品品牌" prop="brandId">
          <ElSelect
            v-model="filter.brandId"
            clearable
            filterable
            placeholder="全部品牌"
            @change="handleSearch"
          >
            <ElOption
              v-for="brand in brandList"
              :key="brand.id"
              :label="brand.name"
              :value="brand.id"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="商品状态" prop="status">
          <ElSelect
            v-model="filter.status"
            clearable
            placeholder="全部状态"
            @change="handleSearch"
          >
            <ElOption
              v-for="item in goods_status"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
      </PageFilterPanel>

      <!-- 工具栏 -->
      <PageToolbar :total="page.total" total-label="件专区商品">
        <template #left>
          <ElButton
            v-access:code="'product:goodsspu:edit'"
            type="primary"
            :icon="Plus"
            @click="openAddGoods"
          >
            添加商品
          </ElButton>
          <ElButton
            v-access:code="'product:goodsspu:edit'"
            :icon="Setting"
            :disabled="selectedCount === 0"
            @click="openBatchRules"
          >
            批量设置规则
          </ElButton>
          <ElButton
            v-access:code="'product:goodsspu:edit'"
            type="danger"
            plain
            :icon="Delete"
            :disabled="selectedCount === 0"
            @click="openBatchRemove"
          >
            批量移出专区
          </ElButton>
          <ElButton :icon="Tickets" @click="goBatchRecords">
            批次记录
          </ElButton>
          <ElButton
            :icon="Check"
            :loading="selectAllState.loading"
            @click="handleSelectAll"
          >
            全选筛选结果
          </ElButton>
        </template>
        <template #right>
          <ElTag v-if="selectedCount > 0" type="primary" effect="plain">
            {{ selectionText }}
          </ElTag>
          <ElButton
            v-if="selectedCount > 0"
            link
            type="primary"
            @click="clearSelection(false)"
          >
            清空选择
          </ElButton>
          <ElButton :icon="Refresh" circle @click="refreshAll" />
        </template>
      </PageToolbar>

      <ElAlert
        v-if="selectAllState.active"
        type="success"
        :closable="false"
        show-icon
        class="mb10"
        :title="selectAllBanner"
      >
        <ElButton link type="primary" @click="clearSelection(false)">
          清空选择
        </ElButton>
      </ElAlert>

      <!-- 列表 -->
      <ElTable
        ref="tableRef"
        v-loading="loading"
        class="hx-data-table"
        :data="tableData"
        row-key="id"
        border
        empty-text="暂无专区商品"
        @selection-change="handleSelectionChange"
      >
        <ElTableColumn
          type="selection"
          width="48"
          align="center"
          reserve-selection
        />
        <ElTableColumn label="商品信息" min-width="300">
          <template #default="{ row }">
            <div class="zone-goods">
              <ElImage
                class="zone-goods__image"
                :src="row.spuUrls?.[0]"
                :preview-src-list="row.spuUrls"
                :hide-on-click-modal="true"
                fit="cover"
                :preview-teleported="true"
              />
              <div class="zone-goods__meta">
                <span class="line-clamp-2">{{ row.name }}</span>
                <span v-if="row.subTitle" class="zone-goods__sub line-clamp-1">
                  {{ row.subTitle }}
                </span>
                <ElTag v-if="row.status !== '1'" size="small" type="info">
                  已下架
                </ElTag>
              </div>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="类目 / 品牌" width="170" align="center">
          <template #default="{ row }">
            <div class="zone-dim">{{ row.categoryName || '—' }}</div>
            <div class="zone-dim zone-dim--light">
              {{ row.brandName || '—' }}
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="售价 / 原价" width="140" align="center">
          <template #default="{ row }">
            <div class="zone-price">
              ¥{{ Number(row.salesPrice ?? 0).toFixed(2) }}
            </div>
            <div class="zone-origin-price">
              ¥{{ Number(row.originalPrice ?? 0).toFixed(2) }}
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="当前规则" min-width="220">
          <template #default="{ row }">
            <div class="zone-rule-tags">
              <ElTag
                v-for="(item, index) in buildRuleSummary(zoneType, row)"
                :key="index"
                size="small"
                type="info"
                class="mr5"
              >
                {{ item }}
              </ElTag>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="SKU 精调" width="130" align="center">
          <template #default="{ row }">
            <ElButton
              link
              :type="hasSkuOverride(row) ? 'warning' : 'info'"
              @click="openSkuEditor(row)"
            >
              {{ describeSkuOverride(row) }}
            </ElButton>
          </template>
        </ElTableColumn>
        <ElTableColumn label="生效状态" width="210" align="center">
          <template #default="{ row }">
            <ElTooltip
              :content="resolveStateMeta(row.state, row.stateLabel).tip"
              placement="top"
            >
              <ElTag
                size="small"
                :type="resolveStateMeta(row.state, row.stateLabel).type"
              >
                {{ resolveStateMeta(row.state, row.stateLabel).label }}
              </ElTag>
            </ElTooltip>
            <div class="zone-effective-price">
              {{ describeEffectivePrice(row) }}
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="170" align="center" fixed="right">
          <template #default="{ row }">
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
              移出专区
            </ElButton>
          </template>
        </ElTableColumn>
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
.zone-panel {
  padding: 0 16px;
  margin-bottom: 14px;
  background: hsl(var(--card));
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}

.zone-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 4px;
  }

  :deep(.el-tabs__nav-wrap::after) {
    height: 1px;
    background-color: var(--el-border-color-lighter);
  }

  :deep(.el-tabs__content) {
    display: none;
  }
}

.zone-status {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 24px;
  align-items: center;
  padding: 8px 0 14px;
}

.zone-status__item {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 13px;
}

.zone-status__item--actions {
  margin-left: auto;
}

.zone-status__label {
  color: var(--el-text-color-secondary);
}

.zone-status__value {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.zone-status__tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.zone-goods {
  display: flex;
  gap: 10px;
  align-items: center;
}

.zone-goods__image {
  flex: none;
  width: 54px;
  height: 54px;
  border-radius: 6px;
}

.zone-goods__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.zone-goods__sub {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.zone-dim {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.zone-dim--light {
  color: var(--el-text-color-secondary);
}

.zone-price {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--el-color-danger);
}

.zone-origin-price {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-decoration: line-through;
}

.zone-rule-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.zone-effective-price {
  margin-top: 4px;
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--el-color-danger);
}
</style>
