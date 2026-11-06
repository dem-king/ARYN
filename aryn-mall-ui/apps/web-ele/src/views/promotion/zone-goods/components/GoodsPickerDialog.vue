<script lang="ts" setup>
import type { TableInstance } from 'element-plus';

import type { ZoneKey } from '../logic';

import { computed, nextTick, reactive, ref, watch } from 'vue';

import { Check, Refresh } from '@element-plus/icons-vue';
import {
  ElAlert,
  ElButton,
  ElCascader,
  ElDialog,
  ElFormItem,
  ElImage,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getList as getBrandList } from '#/api/product/goods-brand';
import { getPage as getCategoryTree } from '#/api/product/goods-category';
import { getPage as getGoodsPage } from '#/api/product/goods-spu';
import { getZoneIds } from '#/api/product/goods-zone';
import Pagination from '#/components/pagination/index.vue';

import {
  buildZoneIdsPayload,
  dedupeIds,
  describeSelectAllResult,
  formatMoney,
  resolvePickerZoneType,
  resolveSelectAllLimit,
  ZONE_LABEL,
} from '../logic';

/**
 * 添加商品：候选商品选择器。
 *
 * 只负责「选商品」，不负责设规则——确认后由父页面打开共享 ZoneBatchDialog
 * （zoneScope=当前专区，默认 ADD），规则与预检统一由共享弹窗完成。
 *
 * 候选口径按契约 §2.5：`zoneType=not_member | not_clearance`（未加入当前专区）。
 */
const props = withDefaults(
  defineProps<{
    visible: boolean;
    /** 当前管理的专区 */
    zoneType: ZoneKey;
  }>(),
  {},
);

const emit = defineEmits<{
  confirm: [ids: string[]];
  'update:visible': [visible: boolean];
}>();

interface CandidateRow {
  brandName?: string;
  categoryName?: string;
  id: string;
  name: string;
  originalPrice?: null | number | string;
  salesPrice?: null | number | string;
  spuUrls?: string[];
  status?: string;
  subTitle?: string;
}

const tableRef = ref<TableInstance>();
const loading = ref(false);
const rows = ref<CandidateRow[]>([]);
const page = reactive({ current: 1, size: 10, total: 0 });
const filter = reactive({
  name: '',
  categorySecondId: '',
  brandId: '',
  status: '',
});
const categoryTree = ref<Record<string, any>[]>([]);
const brandList = ref<Record<string, any>[]>([]);
const categoryLoadFailed = ref(false);
const brandLoadFailed = ref(false);

const categoryProps = {
  children: 'children',
  emitPath: false,
  label: 'name',
  value: 'id',
};

const selectedRows = ref<CandidateRow[]>([]);
/** 全选筛选结果：手动勾选与筛选结果集是两种选择模式 */
const selectAllState = reactive({
  active: false,
  ids: [] as string[],
  total: 0,
  truncated: false,
  loading: false,
});
/** 程序化改选表格时屏蔽 selection-change，避免误退出全选模式 */
let syncingSelection = false;

const candidateZoneType = computed(() => resolvePickerZoneType(props.zoneType));

const effectiveIds = computed<string[]>(() => {
  if (selectAllState.active) {
    return selectAllState.ids;
  }
  return dedupeIds(selectedRows.value.map((row) => row.id));
});

const selectedCount = computed(() => effectiveIds.value.length);

const statusFilterOptions = [
  { label: '已上架', value: '1' },
  { label: '已下架', value: '0' },
];

/** 候选范围提示，明确「只列未加入本专区的商品」 */
const candidateTip = computed(
  () =>
    `候选仅展示未加入「${ZONE_LABEL[props.zoneType]}」的商品；可跨页勾选或全选筛选结果，确认后在「批量设置规则」弹窗中统一设置规则。`,
);

async function loadCategories() {
  if (categoryTree.value.length > 0 || categoryLoadFailed.value) {
    return;
  }
  try {
    categoryTree.value = ((await getCategoryTree()) ?? []) as Record<
      string,
      any
    >[];
  } catch {
    categoryLoadFailed.value = true;
  }
}

async function loadBrands() {
  if (brandList.value.length > 0 || brandLoadFailed.value) {
    return;
  }
  try {
    brandList.value = ((await getBrandList()) ?? []) as Record<string, any>[];
  } catch {
    brandLoadFailed.value = true;
  }
}

/** 候选分页：复用商品分页接口 + not_member / not_clearance 口径 */
async function loadPage() {
  loading.value = true;
  try {
    const response = await getGoodsPage({
      current: page.current,
      size: page.size,
      zoneType: candidateZoneType.value,
      name: filter.name.trim() || undefined,
      categorySecondId: filter.categorySecondId || undefined,
      brandId: filter.brandId || undefined,
      status: filter.status || undefined,
    });
    rows.value = (response?.records ?? []) as CandidateRow[];
    page.total = Number(response?.total ?? 0);
    await syncPageSelectionWithSelectAll();
  } catch {
    rows.value = [];
    page.total = 0;
  } finally {
    loading.value = false;
  }
}

/** 全选模式下翻页后补齐当前页勾选状态（选择语义以 selectAllState.ids 为准） */
async function syncPageSelectionWithSelectAll() {
  if (!selectAllState.active) {
    return;
  }
  await nextTick();
  const idSet = new Set(selectAllState.ids);
  syncingSelection = true;
  try {
    for (const row of rows.value) {
      if (idSet.has(String(row.id))) {
        tableRef.value?.toggleRowSelection(row, true);
      }
    }
  } finally {
    syncingSelection = false;
  }
}

function resetSelectAll() {
  selectAllState.active = false;
  selectAllState.ids = [];
  selectAllState.total = 0;
  selectAllState.truncated = false;
}

function clearSelectionSilently() {
  syncingSelection = true;
  try {
    tableRef.value?.clearSelection();
  } finally {
    syncingSelection = false;
  }
  selectedRows.value = [];
  resetSelectAll();
}

function handleSelectionChange(selection: CandidateRow[]) {
  if (syncingSelection) {
    return;
  }
  // 用户手动改选即退出「全选筛选结果」模式，避免两种选择语义叠加
  if (selectAllState.active) {
    resetSelectAll();
  }
  selectedRows.value = selection;
}

/** 搜索：筛选条件变化后回到第一页，并清空选择（避免跨筛选误操作） */
function handleSearch() {
  page.current = 1;
  clearSelectionSilently();
  void loadPage();
}

function handleReset() {
  filter.name = '';
  filter.categorySecondId = '';
  filter.brandId = '';
  filter.status = '';
  handleSearch();
}

/**
 * 全选筛选结果：调 getZoneIds 拿筛选结果全集（默认 500、上限 1000），
 * 超过 1000 件提示缩小筛选 / 分批；分页勾选与筛选结果集互不干扰。
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
      buildZoneIdsPayload(candidateZoneType.value, filter, limit),
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
      for (const row of rows.value) {
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

function handleClearSelection() {
  clearSelectionSilently();
}

function handleSubmit() {
  if (selectedCount.value === 0) {
    ElMessage.warning('请选择要添加的商品');
    return;
  }
  emit('confirm', [...effectiveIds.value]);
  emit('update:visible', false);
}

function handleClose() {
  emit('update:visible', false);
}

/** ElDialog 只在关闭后抛 update:modelValue(false)，这里显式忽略打开事件 */
function handleVisibleChange(value: boolean) {
  if (!value) {
    handleClose();
  }
}

function resetAndLoad() {
  filter.name = '';
  filter.categorySecondId = '';
  filter.brandId = '';
  filter.status = '';
  page.current = 1;
  page.total = 0;
  selectedRows.value = [];
  resetSelectAll();
  clearSelectionSilently();
  void loadCategories();
  void loadBrands();
  void loadPage();
}

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      resetAndLoad();
    }
  },
);

watch(
  () => props.zoneType,
  () => {
    if (props.visible) {
      resetAndLoad();
    }
  },
);
</script>

<template>
  <ElDialog
    :model-value="visible"
    class="goods-picker-dialog"
    :title="`添加商品 - ${ZONE_LABEL[zoneType]}`"
    width="960px"
    top="6vh"
    :close-on-click-modal="false"
    append-to-body
    @update:model-value="handleVisibleChange"
  >
    <ElAlert
      type="info"
      :closable="false"
      show-icon
      class="mb10"
      :title="candidateTip"
    />
    <ElFormItem label-width="80px" class="picker-filter">
      <ElInput
        v-model="filter.name"
        class="picker-filter__input"
        clearable
        placeholder="请输入商品名称"
        @clear="handleSearch"
        @keyup.enter="handleSearch"
      />
      <ElCascader
        v-model="filter.categorySecondId"
        class="picker-filter__cascader"
        :options="categoryTree"
        :props="categoryProps"
        clearable
        filterable
        placeholder="全部类目"
        @change="handleSearch"
      />
      <ElSelect
        v-model="filter.brandId"
        class="picker-filter__select"
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
      <ElSelect
        v-model="filter.status"
        class="picker-filter__select"
        clearable
        placeholder="全部状态"
        @change="handleSearch"
      >
        <ElOption
          v-for="option in statusFilterOptions"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </ElSelect>
      <ElButton type="primary" @click="handleSearch">搜索</ElButton>
      <ElButton :icon="Refresh" @click="handleReset">重置</ElButton>
    </ElFormItem>

    <div class="picker-selection">
      <span class="picker-selection__text">
        {{ selectedCount > 0 ? `已选 ${selectedCount} 件` : '未选择商品' }}
      </span>
      <ElButton
        v-if="selectedCount > 0"
        link
        type="primary"
        @click="handleClearSelection"
      >
        清空选择
      </ElButton>
      <ElButton
        v-if="!selectAllState.active"
        link
        type="primary"
        :icon="Check"
        :loading="selectAllState.loading"
        @click="handleSelectAll"
      >
        全选筛选结果（{{ page.total }} 件）
      </ElButton>
    </div>

    <ElTable
      ref="tableRef"
      v-loading="loading"
      :data="rows"
      row-key="id"
      border
      empty-text="暂无候选商品"
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
          <div class="picker-goods">
            <ElImage
              class="picker-goods__image"
              :src="row.spuUrls?.[0]"
              fit="cover"
            />
            <div class="picker-goods__meta">
              <span class="line-clamp-2">{{ row.name }}</span>
              <span v-if="row.subTitle" class="picker-goods__sub line-clamp-1">
                {{ row.subTitle }}
              </span>
            </div>
          </div>
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="categoryName"
        label="类目"
        width="140"
        align="center"
      />
      <ElTableColumn prop="brandName" label="品牌" width="120" align="center" />
      <ElTableColumn label="售价 / 原价" width="150" align="center">
        <template #default="{ row }">
          <span class="picker-price">¥{{ formatMoney(row.salesPrice) }}</span>
          <span class="picker-origin-price">
            ¥{{ formatMoney(row.originalPrice) }}
          </span>
        </template>
      </ElTableColumn>
      <ElTableColumn label="上架状态" width="100" align="center">
        <template #default="{ row }">
          <ElTag v-if="row.status === '1'" type="success" size="small">
            已上架
          </ElTag>
          <ElTag v-else type="info" size="small">已下架</ElTag>
        </template>
      </ElTableColumn>
    </ElTable>

    <Pagination
      v-model:current="page.current"
      v-model:size="page.size"
      :total="page.total"
      @change="loadPage"
    />

    <template #footer>
      <div class="picker-footer">
        <span class="picker-footer__count">
          已选 <b>{{ selectedCount }}</b> 件
        </span>
        <div>
          <ElButton @click="handleClose">取消</ElButton>
          <ElButton
            type="primary"
            :disabled="selectedCount === 0"
            @click="handleSubmit"
          >
            下一步：设置规则（{{ selectedCount }} 件）
          </ElButton>
        </div>
      </div>
    </template>
  </ElDialog>
</template>

<style scoped lang="scss">
.picker-filter {
  gap: 12px;
  margin-bottom: 12px;

  :deep(.el-form-item__content) {
    gap: 12px;
  }
}

.picker-filter__input {
  width: 200px;
}

.picker-filter__cascader {
  width: 220px;
}

.picker-filter__select {
  width: 140px;
}

.picker-selection {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.picker-selection__text {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.picker-goods {
  display: flex;
  gap: 10px;
  align-items: center;
}

.picker-goods__image {
  flex: none;
  width: 48px;
  height: 48px;
  border-radius: 6px;
}

.picker-goods__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.picker-goods__sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.picker-price {
  font-weight: 600;
  color: var(--el-color-danger);
}

.picker-origin-price {
  margin-left: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-decoration: line-through;
}

.picker-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.picker-footer__count {
  font-size: 13px;
  color: var(--el-text-color-secondary);

  b {
    color: var(--el-color-primary);
  }
}
</style>

<style lang="scss">
/* ElDialog 通过 append-to-body 挂载到 body，内部结构不受组件 scope 约束，
   因此这里用非 scoped 样式固定弹窗尺寸：
   - 弹窗最高不超过视口，头/脚常驻，只有内容区滚动；
   - 底部「取消 / 下一步」在任何屏幕高度下都可见可点。 */
.goods-picker-dialog {
  display: flex;
  flex-direction: column;
  max-height: calc(93vh - 60px);
  overflow: hidden;

  .el-dialog__header {
    flex: none;
  }

  .el-dialog__body {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    scrollbar-gutter: stable;
    overscroll-behavior: contain;
  }

  .el-dialog__footer {
    flex: none;
  }
}
</style>
