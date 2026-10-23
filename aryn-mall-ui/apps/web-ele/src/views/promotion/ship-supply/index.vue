<script lang="ts" setup>
import { computed, defineAsyncComponent, onMounted, reactive, ref } from 'vue';

import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getById as getSpuById } from '#/api/product/goods-spu';
import {
  addActivity,
  deleteActivity,
  dryRunActivity,
  getActivityPage,
  getPublishConflicts,
  pauseActivity,
  publishActivity,
  updateActivity,
} from '#/api/promotion/ship-activity';
import { getDeclaredVesselCalls, getVesselPage } from '#/api/vessel/vessel';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { formatMoney } from '#/utils/util';

const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const SelectGoods = defineAsyncComponent(
  () => import('#/components/select-goods/index.vue'),
);

const state = reactive({
  page: { total: 0, currentPage: 1, pageSize: 10 },
  query: { activityType: '', status: '' },
  tableData: [] as any[],
});
const loading = ref(false);

const dialogVisible = ref(false);
const editingId = ref('');
const form = reactive<any>({
  activityName: '',
  activityType: '4',
  consumedGiftStock: 0,
  endTime: '',
  giftStock: 0,
  perUserLimit: 0,
  priority: 0,
  purchaseScene: '',
  scopeType: '1',
  scopeValue: '',
  startTime: '',
  totalBudget: 0,
});
const skuLadders = ref<
  Array<{
    ladders: Array<{ minQty: number; unitPrice: number }>;
    salesPrice: number | string;
    skuId: string;
    skuName: string;
    spuName: string;
  }>
>([]);
const tiers = ref<Array<{ discountAmount: number; minAmount: number }>>([
  { discountAmount: 0, minAmount: 0 },
]);
const giftThreshold = ref(0);
const newbieSkus = ref<
  Array<{
    price: number;
    salesPrice: number | string;
    skuId: string;
    skuName: string;
    spuName: string;
  }>
>([]);
const registerWithinDays = ref<null | number>(null);
const gifts = ref<
  Array<{ quantity: number; skuId: string; skuName: string; spuName: string }>
>([]);
const vesselOptions = ref<Array<{ id: string; vesselName: string }>>([]);
const callOptions = ref<any[]>([]);
const portOptions = ref<Array<{ code: string; name: string }>>([]);
const isPublished = ref(false);
const selectMode = ref<'dry' | 'gift' | 'ladder' | 'newbie' | 'scope'>(
  'ladder',
);
const dryVisible = ref(false);
const dryLoading = ref(false);
const dryResult = ref<any>(null);
const dryForm = reactive<any>({
  items: [] as Array<{
    quantity: number;
    salesPrice: number | string;
    skuId: string;
    skuName: string;
    spuName: string;
  }>,
  portCode: '',
  purchaseScene: '2',
  vesselCallId: '',
  vesselId: '',
});

const typeLabel: Record<string, string> = {
  '4': '阶梯价',
  '5': '买赠',
  '7': '整船优惠',
  '9': '新人价',
};
const statusLabel: Record<string, string> = {
  '1': '草稿',
  '2': '已发布',
  '3': '已暂停',
  '4': '已结束',
  '5': '待开始',
};

/** 活动状态标签语义色（标签文案沿用 statusLabel，不改变含义） */
const statusTagType = (
  status: string,
): 'danger' | 'info' | 'primary' | 'success' | 'warning' => {
  if (status === '2') return 'success';
  if (status === '3') return 'warning';
  if (status === '5') return 'primary';
  return 'info';
};
const scopeLabel: Record<string, string> = {
  '1': '全场',
  '2': '指定SKU',
  '5': '指定船舶',
  '6': '指定靠港',
  '7': '指定港口',
  '8': '常购用户',
};

const initPage = async () => {
  loading.value = true;
  try {
    const response = await getActivityPage({
      current: state.page.currentPage,
      size: state.page.pageSize,
      activityType: state.query.activityType,
      status: state.query.status,
    });
    state.tableData = response.records;
    state.page.total = response.total;
  } finally {
    loading.value = false;
  }
};

const openDialog = (row?: any) => {
  editingId.value = row?.id ?? '';
  isPublished.value = row?.status === '2';
  form.activityName = row?.activityName ?? '';
  form.activityType = row?.activityType ?? '4';
  form.scopeType = row?.scopeType ?? '1';
  form.scopeValue = row?.scopeValue ?? '';
  form.purchaseScene = row?.purchaseScene ?? '';
  form.priority = row?.priority ?? 0;
  form.totalBudget = row?.totalBudget ?? 0;
  form.perUserLimit = row?.perUserLimit ?? 0;
  form.giftStock = row?.giftStock ?? 0;
  form.consumedGiftStock = row?.consumedGiftStock ?? 0;
  form.startTime = row?.startTime ?? '';
  form.endTime = row?.endTime ?? '';
  try {
    const rules = row ? JSON.parse(row.rules) : {};
    skuLadders.value = rules.skuLadders ?? [];
    tiers.value = rules.tiers ?? [{ discountAmount: 0, minAmount: 0 }];
    giftThreshold.value = rules.minAmount ?? 0;
    gifts.value = rules.gifts ?? [];
    newbieSkus.value = rules.skus ?? [];
    registerWithinDays.value = rules.registerWithinDays ?? null;
  } catch {
    skuLadders.value = [];
    tiers.value = [{ discountAmount: 0, minAmount: 0 }];
    giftThreshold.value = 0;
    gifts.value = [];
    newbieSkus.value = [];
    registerWithinDays.value = null;
  }
  dryResult.value = null;
  loadVesselData();
  dialogVisible.value = true;
};

const buildPayload = () => {
  let rules: Record<string, any> = { tiers: tiers.value };
  switch (form.activityType) {
    case '4': {
      rules = {
        skuLadders: skuLadders.value.map((row) => ({
          skuId: row.skuId,
          spuName: row.spuName,
          skuName: row.skuName,
          ladders: row.ladders,
        })),
      };

      break;
    }
    case '5': {
      rules = { minAmount: giftThreshold.value, gifts: gifts.value };

      break;
    }
    case '9': {
      rules = {
        limitPerUser: 1,
        registerWithinDays: registerWithinDays.value,
        skus: newbieSkus.value.map((row) => ({
          price: row.price,
          skuId: row.skuId,
          skuName: row.skuName,
          spuName: row.spuName,
        })),
      };

      break;
    }
    // No default
  }
  const payload: Record<string, any> = {
    ...form,
    rules: JSON.stringify(rules),
  };
  if (form.activityType === '4' || form.activityType === '9') {
    payload.scopeType = '2';
    payload.scopeValue = (
      form.activityType === '4'
        ? skuLadders.value.map((row) => row.skuId)
        : newbieSkus.value.map((row) => row.skuId)
    ).join(',');
  }
  return payload;
};

const handleSave = () => {
  if (!form.activityName || !form.startTime || !form.endTime) {
    ElMessage.warning('请完善活动名称与时间');
    return;
  }
  if (form.activityType === '4') {
    if (skuLadders.value.length === 0) {
      ElMessage.warning('请添加活动商品并配置档位');
      return;
    }
    const bad = skuLadders.value.find(
      (row) =>
        row.ladders.length === 0 ||
        row.ladders.some((ladder) => !ladder.minQty || ladder.unitPrice <= 0),
    );
    if (bad) {
      ElMessage.warning(
        `商品【${bad.spuName}】的档位数量与档价需完整且档价大于 0`,
      );
      return;
    }
  }
  if (form.activityType === '5' && gifts.value.length === 0) {
    ElMessage.warning('请添加赠品');
    return;
  }
  if (form.activityType === '9') {
    if (newbieSkus.value.length === 0) {
      ElMessage.warning('请添加新人价商品');
      return;
    }
    const bad = newbieSkus.value.find((row) => !row.price || row.price <= 0);
    if (bad) {
      ElMessage.warning(`商品【${bad.spuName}】的新人价需大于 0`);
      return;
    }
  }
  const request = editingId.value
    ? updateActivity(editingId.value, buildPayload())
    : addActivity(buildPayload());
  request
    .then(() => {
      ElMessage.success('保存成功');
      dialogVisible.value = false;
      initPage();
    })
    .catch(() => {});
};

const doPublish = (row: any, force: boolean) => {
  publishActivity(row.id, force)
    .then(() => {
      ElMessage.success('已发布');
      initPage();
    })
    .catch(() => {});
};
const handlePublish = (row: any) => {
  getPublishConflicts(row.id)
    .then((conflicts: any[]) => {
      if (conflicts.length > 0) {
        const names = conflicts.map((item) => item.activityName).join('、');
        ElMessageBox.confirm(
          `与已发布活动时间重叠：${names}。运行时同类型取最优，确认强制发布？`,
          '发布冲突',
          {
            confirmButtonText: '强制发布',
            cancelButtonText: '取消',
            type: 'warning',
          },
        )
          .then(() => doPublish(row, true))
          .catch(() => {});
      } else {
        doPublish(row, false);
      }
    })
    .catch(() => {});
};

const handlePause = (row: any) => {
  pauseActivity(row.id)
    .then(() => {
      ElMessage.success('已暂停');
      initPage();
    })
    .catch(() => {});
};

const handleDelete = (row: any) => {
  ElMessageBox.confirm('确认删除该活动?', '提示')
    .then(() => deleteActivity(row.id))
    .then(() => {
      ElMessage.success('已删除');
      initPage();
    })
    .catch(() => {});
};

const selectGoodsRef = ref();
const openSelect = (mode: 'dry' | 'gift' | 'ladder' | 'newbie' | 'scope') => {
  selectMode.value = mode;
  selectGoodsRef.value?.initPage();
};
const openSelectGoods = () => openSelect('ladder');

const resolveSkuRows = async (spuList: any[]) => {
  const rows: Array<{
    ladders?: Array<{ minQty: number; unitPrice: number }>;
    quantity?: number;
    salesPrice: number | string;
    skuId: string;
    skuName: string;
    spuName: string;
  }> = [];
  for (const spu of spuList) {
    try {
      const detail: any = await getSpuById(spu.id);
      const skus = detail?.goodsSkus ?? [];
      if (skus.length === 0) {
        ElMessage.warning(`商品【${spu.name}】无可用 SKU，已跳过`);
        continue;
      }
      for (const sku of skus) {
        rows.push({
          skuId: sku.id,
          spuName: spu.name,
          skuName:
            (sku.specsArr ?? []).map((spec: any) => spec.specsName).join(' ') ||
            '单规格',
          salesPrice: sku.salesPrice,
        });
      }
    } catch {
      ElMessage.error(`商品【${spu.name}】加载失败`);
    }
  }
  return rows;
};

const onGoodsSelected = async (spuList: any[]) => {
  const rows = await resolveSkuRows(spuList);
  switch (selectMode.value) {
    case 'gift': {
      for (const row of rows) {
        if (gifts.value.some((gift) => gift.skuId === row.skuId)) continue;
        gifts.value.push({ ...row, quantity: 1 });
      }

      break;
    }
    case 'ladder': {
      for (const row of rows) {
        if (skuLadders.value.some((item) => item.skuId === row.skuId)) continue;
        skuLadders.value.push({
          ...row,
          ladders: [{ minQty: 10, unitPrice: 0 }],
        });
      }

      break;
    }
    case 'newbie': {
      for (const row of rows) {
        if (newbieSkus.value.some((sku) => sku.skuId === row.skuId)) continue;
        newbieSkus.value.push({ ...row, price: 0 });
      }

      break;
    }
    case 'scope': {
      const existing = new Set(
        (form.scopeValue || '').split(',').filter(Boolean),
      );
      rows.forEach((row) => existing.add(row.skuId));
      form.scopeValue = [...existing].join(',');

      break;
    }
    default: {
      for (const row of rows) {
        if (dryForm.items.some((item: any) => item.skuId === row.skuId))
          continue;
        dryForm.items.push({ ...row, quantity: 1 });
      }
    }
  }
};

const scopeSkuCount = () =>
  (form.scopeValue || '').split(',').filter(Boolean).length;

const scopeValueOptions = computed(() => {
  if (form.scopeType === '5') {
    return vesselOptions.value.map((vessel) => ({
      value: vessel.id,
      label: vessel.vesselName,
    }));
  }
  if (form.scopeType === '6') {
    return callOptions.value.map((call) => ({
      value: call.id,
      label: `${call.portName} ${String(call.eta).slice(0, 10)}`,
    }));
  }
  if (form.scopeType === '7') {
    return portOptions.value.map((port) => ({
      value: port.code,
      label: `${port.name}（${port.code}）`,
    }));
  }
  return [];
});

const loadVesselData = async () => {
  if (vesselOptions.value.length > 0) return;
  try {
    const [vessels, calls] = await Promise.all([
      getVesselPage({ current: 1, size: 200 }),
      getDeclaredVesselCalls(),
    ]);
    vesselOptions.value = vessels.records ?? [];
    callOptions.value = calls ?? [];
    const seen = new Set<string>();
    portOptions.value = (calls ?? [])
      .filter(
        (call: any) =>
          call.portCode && !seen.has(call.portCode) && seen.add(call.portCode),
      )
      .map((call: any) => ({ code: call.portCode, name: call.portName }));
  } catch {
    // 下拉数据加载失败时降级为空选项，不阻断活动配置
  }
};

const scopeText = (row: any) => {
  const label = scopeLabel[row.scopeType] ?? row.scopeType;
  if (row.scopeType === '2' || row.scopeType === '8') {
    return `${label}(${(row.scopeValue || '').split(',').filter(Boolean).length})`;
  }
  if (row.scopeType === '5') {
    return (
      vesselOptions.value.find((vessel) => vessel.id === row.scopeValue)
        ?.vesselName ?? label
    );
  }
  if (row.scopeType === '6') {
    const call = callOptions.value.find((item) => item.id === row.scopeValue);
    return call ? `${call.portName} ${String(call.eta).slice(0, 10)}` : label;
  }
  if (row.scopeType === '7') {
    return (
      portOptions.value.find((port) => port.code === row.scopeValue)?.name ??
      label
    );
  }
  return label;
};

const openDry = () => {
  loadVesselData();
  if (dryForm.items.length === 0 && skuLadders.value.length > 0) {
    dryForm.items = skuLadders.value.map((row) => ({
      skuId: row.skuId,
      spuName: row.spuName,
      skuName: row.skuName,
      salesPrice: row.salesPrice,
      quantity: row.ladders[0]?.minQty ?? 1,
    }));
  }
  dryResult.value = null;
  dryVisible.value = true;
};

const runDry = async () => {
  if (dryForm.items.length === 0) {
    ElMessage.warning('请添加试算购物车商品');
    return;
  }
  dryLoading.value = true;
  try {
    dryResult.value = await dryRunActivity({
      purchaseScene: dryForm.purchaseScene,
      vesselId: dryForm.vesselId || null,
      vesselCallId: dryForm.vesselCallId || null,
      portCode: dryForm.portCode || null,
      skuItems: dryForm.items.map((item: any) => ({
        skuId: item.skuId,
        quantity: item.quantity,
        salesPrice: item.salesPrice,
      })),
    });
  } finally {
    dryLoading.value = false;
  }
};

const onDryCallChange = (callId: string) => {
  const call = callOptions.value.find((item) => item.id === callId);
  dryForm.portCode = call?.portCode ?? '';
  dryForm.vesselId = call?.vesselId ?? dryForm.vesselId;
};

const addLadder = (row: (typeof skuLadders.value)[number]) => {
  row.ladders.push({ minQty: 1, unitPrice: 0 });
};
const addTier = () => {
  tiers.value.push({ discountAmount: 0, minAmount: 0 });
};

onMounted(initPage);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索 -->
      <PageFilterPanel
        :model="state.query"
        @reset="initPage"
        @search="initPage"
      >
        <ElFormItem label="活动类型" prop="activityType">
          <ElSelect
            v-model="state.query.activityType"
            clearable
            placeholder="请选择活动类型"
          >
            <ElOption
              v-for="(label, value) in typeLabel"
              :key="value"
              :label="label"
              :value="value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="状态" prop="status">
          <ElSelect
            v-model="state.query.status"
            clearable
            placeholder="请选择状态"
          >
            <ElOption
              v-for="(label, value) in statusLabel"
              :key="value"
              :label="label"
              :value="value"
            />
          </ElSelect>
        </ElFormItem>
      </PageFilterPanel>

      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="个船供活动">
        <template #left>
          <ElButton
            type="primary"
            v-access:code="'promotion:shipactivity:save'"
            @click="openDialog()"
          >
            新建活动
          </ElButton>
        </template>
      </PageToolbar>

      <ElTable
        v-loading="loading"
        :data="state.tableData"
        class="hx-data-table"
        row-key="id"
        border
        empty-text="暂无船供活动"
      >
        <ElTableColumn prop="activityName" label="活动名称" min-width="160" />
        <ElTableColumn label="类型" width="100">
          <template #default="scope">
            {{ typeLabel[scope.row.activityType] ?? scope.row.activityType }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="范围" width="140">
          <template #default="scope">
            {{ scopeText(scope.row) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="额度使用" width="190">
          <template #default="scope">
            <template v-if="scope.row.activityType === '5'">
              <span v-if="Number(scope.row.giftStock) > 0">
                赠品 {{ scope.row.consumedGiftStock ?? 0 }} /
                {{ scope.row.giftStock }} 件
              </span>
              <span v-else
                >赠品不限（已赠
                {{ scope.row.consumedGiftStock ?? 0 }} 件）</span
              >
            </template>
            <template v-else>
              <span v-if="Number(scope.row.totalBudget) > 0" class="hx-money">
                ¥{{ formatMoney(scope.row.consumedBudget) }} / ¥{{
                  formatMoney(scope.row.totalBudget)
                }}
              </span>
              <span v-else class="hx-money"
                >预算不限（已用 ¥{{
                  formatMoney(scope.row.consumedBudget)
                }}）</span
              >
            </template>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="startTime" label="开始" width="170" />
        <ElTableColumn prop="endTime" label="结束" width="170" />
        <ElTableColumn label="状态" width="90">
          <template #default="scope">
            <ElTag :type="statusTagType(scope.row.status)">
              {{ statusLabel[scope.row.status] ?? scope.row.status }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="220">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'promotion:shipactivity:save'"
              @click="openDialog(scope.row)"
            >
              编辑
            </ElButton>
            <ElButton
              link
              type="success"
              v-access:code="'promotion:shipactivity:publish'"
              :disabled="scope.row.status !== '1' && scope.row.status !== '3'"
              @click="handlePublish(scope.row)"
            >
              发布
            </ElButton>
            <ElButton
              link
              type="warning"
              v-access:code="'promotion:shipactivity:publish'"
              :disabled="scope.row.status !== '2'"
              @click="handlePause(scope.row)"
            >
              暂停
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'promotion:shipactivity:save'"
              :disabled="scope.row.status === '2'"
              @click="handleDelete(scope.row)"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <Pagination
        :total="state.page.total"
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        @change="initPage"
      />

      <ElDialog
        v-model="dialogVisible"
        :title="editingId ? '编辑活动' : '新建活动'"
        width="720px"
      >
        <ElForm label-width="auto">
          <ElFormItem label="活动名称" required>
            <ElInput v-model="form.activityName" />
          </ElFormItem>
          <ElFormItem label="活动类型">
            <ElRadioGroup v-model="form.activityType" :disabled="!!editingId">
              <ElRadioButton value="4">船供阶梯价</ElRadioButton>
              <ElRadioButton value="7">船供整船优惠</ElRadioButton>
              <ElRadioButton value="5">买赠</ElRadioButton>
              <ElRadioButton value="9">新人专享价</ElRadioButton>
            </ElRadioGroup>
          </ElFormItem>
          <ElFormItem
            v-if="form.activityType !== '4' && form.activityType !== '9'"
            label="适用范围"
          >
            <ElSelect
              v-model="form.scopeType"
              :disabled="!!editingId"
              style="width: 130px"
              @change="loadVesselData"
            >
              <ElOption
                v-for="(label, value) in scopeLabel"
                :key="value"
                :label="label"
                :value="value"
              />
            </ElSelect>
            <ElSelect
              v-if="
                form.scopeType === '5' ||
                form.scopeType === '6' ||
                form.scopeType === '7'
              "
              v-model="form.scopeValue"
              :disabled="!!editingId || isPublished"
              class="ml-10px"
              clearable
              filterable
              placeholder="请选择"
              style="width: 260px"
            >
              <ElOption
                v-for="option in scopeValueOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </ElSelect>
            <template
              v-else-if="form.scopeType === '2' || form.scopeType === '8'"
            >
              <ElButton
                class="ml-10px"
                :disabled="isPublished"
                @click="openSelect('scope')"
              >
                选择SKU
              </ElButton>
              <span class="ml-8px">已选 {{ scopeSkuCount() }} 个 SKU</span>
            </template>
          </ElFormItem>
          <ElFormItem v-else label="适用范围">
            <span style="color: var(--el-text-color-secondary)">
              指定SKU（由活动商品自动带出，无需手填）
            </span>
          </ElFormItem>
          <ElFormItem label="限定场景">
            <ElSelect
              v-model="form.purchaseScene"
              clearable
              placeholder="不限"
              style="width: 160px"
            >
              <ElOption label="仅个人购买" value="1" />
              <ElOption label="仅船供采购" value="2" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="预算金额">
            <ElInputNumber
              v-model="form.totalBudget"
              :min="0"
              :precision="2"
              controls-position="right"
            />
            <span class="ml-8px"
              >元，0
              为不限；额度用尽后活动自动失效，订单按剩余优惠或原价成交</span
            >
          </ElFormItem>
          <ElFormItem label="每人限享">
            <ElInputNumber
              v-model="form.perUserLimit"
              :min="0"
              controls-position="right"
            />
            <span class="ml-8px">次，0 为不限</span>
          </ElFormItem>
          <ElFormItem v-if="form.activityType === '5'" label="赠品总量">
            <ElInputNumber
              v-model="form.giftStock"
              :min="0"
              controls-position="right"
            />
            <span class="ml-8px">
              件，0 为不限；赠品领完后本单自动不赠，订单继续成交
              <template v-if="editingId"
                >（已赠 {{ form.consumedGiftStock ?? 0 }} 件）</template
              >
            </span>
          </ElFormItem>
          <ElFormItem label="开始时间" required>
            <ElDatePicker
              v-model="form.startTime"
              type="datetime"
              placeholder="选择开始时间"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
            />
          </ElFormItem>
          <ElFormItem label="结束时间" required>
            <ElDatePicker
              v-model="form.endTime"
              type="datetime"
              placeholder="选择结束时间"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
            />
          </ElFormItem>

          <template v-if="form.activityType === '4'">
            <ElFormItem label="活动商品">
              <ElButton
                type="primary"
                :disabled="isPublished"
                @click="openSelectGoods"
              >
                添加商品
              </ElButton>
              <span
                class="ml-10px"
                style="color: var(--el-text-color-secondary)"
              >
                按 SKU 配置满件档位与档价（满件 = 单内同一 SKU
                合计），档价需低于当前售价
              </span>
            </ElFormItem>
            <ElFormItem
              v-for="(row, rowIndex) in skuLadders"
              :key="row.skuId"
              :label="row.spuName"
            >
              <div style="width: 100%">
                <div style="margin-bottom: 6px">
                  <span>{{ row.skuName }}</span>
                  <span
                    class="ml-10px hx-money"
                    style="color: var(--el-text-color-secondary)"
                  >
                    售价 ¥{{ formatMoney(row.salesPrice) }}
                  </span>
                  <ElButton
                    link
                    type="danger"
                    class="ml-10px"
                    :disabled="isPublished"
                    @click="skuLadders.splice(rowIndex, 1)"
                  >
                    移除
                  </ElButton>
                </div>
                <div
                  v-for="(ladder, ladderIndex) in row.ladders"
                  :key="ladderIndex"
                  style="margin-bottom: 6px"
                >
                  <span>档位 {{ ladderIndex + 1 }} 满</span>
                  <ElInputNumber
                    v-model="ladder.minQty"
                    :min="1"
                    :disabled="isPublished"
                    controls-position="right"
                    style="width: 110px; margin: 0 8px"
                  />
                  <span>件，单价</span>
                  <ElInputNumber
                    v-model="ladder.unitPrice"
                    :min="0"
                    :precision="2"
                    :disabled="isPublished"
                    controls-position="right"
                    style="width: 120px; margin: 0 8px"
                  />
                </div>
                <ElButton
                  link
                  type="primary"
                  :disabled="isPublished"
                  @click="addLadder(row)"
                >
                  加档
                </ElButton>
              </div>
            </ElFormItem>
          </template>
          <template v-else-if="form.activityType === '5'">
            <ElFormItem label="消费门槛">
              <ElInputNumber
                v-model="giftThreshold"
                :min="0"
                :precision="2"
                :disabled="isPublished"
                controls-position="right"
              />
              <span class="ml-8px">元</span>
            </ElFormItem>
            <ElFormItem label="赠品">
              <ElButton
                type="primary"
                :disabled="isPublished"
                @click="openSelect('gift')"
              >
                添加赠品
              </ElButton>
              <span
                class="ml-10px"
                style="color: var(--el-text-color-secondary)"
              >
                赠品随单追加 0 元明细，结算按真实库存校验
              </span>
            </ElFormItem>
            <ElFormItem
              v-for="(gift, index) in gifts"
              :key="gift.skuId"
              :label="`赠品 ${index + 1}`"
            >
              <span style="min-width: 220px">
                {{ gift.spuName || gift.skuId }} {{ gift.skuName }}
              </span>
              <ElInputNumber
                v-model="gift.quantity"
                :min="1"
                :disabled="isPublished"
                controls-position="right"
                style="width: 110px; margin: 0 8px"
              />
              <ElButton
                link
                type="danger"
                :disabled="isPublished"
                @click="gifts.splice(index, 1)"
              >
                移除
              </ElButton>
            </ElFormItem>
          </template>
          <template v-else-if="form.activityType === '9'">
            <ElFormItem label="注册时限">
              <ElInputNumber
                v-model="registerWithinDays"
                :min="1"
                :disabled="isPublished"
                controls-position="right"
              />
              <span class="ml-8px">天内注册的新用户可见，清空为不限</span>
            </ElFormItem>
            <ElFormItem label="活动商品">
              <ElButton
                type="primary"
                :disabled="isPublished"
                @click="openSelect('newbie')"
              >
                添加商品
              </ElButton>
              <span
                class="ml-10px"
                style="color: var(--el-text-color-secondary)"
              >
                每人每 SKU 限购 1 件，新人价需低于当前售价
              </span>
            </ElFormItem>
            <ElFormItem
              v-for="(row, rowIndex) in newbieSkus"
              :key="row.skuId"
              :label="row.spuName"
            >
              <span class="hx-money" style="min-width: 220px">
                {{ row.skuName }} 售价 ¥{{ formatMoney(row.salesPrice) }}
              </span>
              <ElInputNumber
                v-model="row.price"
                :min="0"
                :precision="2"
                :disabled="isPublished"
                controls-position="right"
                style="width: 120px; margin: 0 8px"
              />
              <ElButton
                link
                type="danger"
                :disabled="isPublished"
                @click="newbieSkus.splice(rowIndex, 1)"
              >
                移除
              </ElButton>
            </ElFormItem>
          </template>
          <template v-else>
            <ElFormItem
              v-for="(tier, index) in tiers"
              :key="index"
              :label="`档位 ${index + 1}`"
            >
              <span>满</span>
              <ElInputNumber
                v-model="tier.minAmount"
                :min="0"
                :precision="2"
                :disabled="isPublished"
                controls-position="right"
                style="width: 130px; margin: 0 8px"
              />
              <span>元，减</span>
              <ElInputNumber
                v-model="tier.discountAmount"
                :min="0"
                :precision="2"
                :disabled="isPublished"
                controls-position="right"
                style="width: 120px; margin: 0 8px"
              />
              <ElButton
                link
                type="primary"
                :disabled="isPublished"
                @click="addTier"
              >
                加档
              </ElButton>
            </ElFormItem>
          </template>
        </ElForm>
        <template #footer>
          <div class="hx-form-footer">
            <ElButton @click="openDry">试算</ElButton>
            <ElButton @click="dialogVisible = false">取消</ElButton>
            <ElButton type="primary" @click="handleSave">确定</ElButton>
          </div>
        </template>
      </ElDialog>

      <ElDialog v-model="dryVisible" title="命中试算（不落库）" width="640px">
        <ElForm label-width="auto">
          <ElFormItem label="购买场景">
            <ElSelect v-model="dryForm.purchaseScene" style="width: 160px">
              <ElOption label="个人购买" value="1" />
              <ElOption label="船供采购" value="2" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="靠港">
            <ElSelect
              v-model="dryForm.vesselCallId"
              clearable
              filterable
              placeholder="不限"
              style="width: 320px"
              @change="onDryCallChange"
            >
              <ElOption
                v-for="call in callOptions"
                :key="call.id"
                :label="`${call.portName} ${String(call.eta).slice(0, 10)}`"
                :value="call.id"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="购物车">
            <ElButton type="primary" @click="openSelect('dry')">
              添加商品
            </ElButton>
          </ElFormItem>
          <ElFormItem
            v-for="(item, index) in dryForm.items"
            :key="item.skuId"
            :label="item.spuName"
          >
            <span style="min-width: 160px">{{ item.skuName }}</span>
            <ElInputNumber
              v-model="item.quantity"
              :min="1"
              controls-position="right"
              style="width: 110px; margin: 0 8px"
            />
            <ElButton
              link
              type="danger"
              @click="dryForm.items.splice(index, 1)"
            >
              移除
            </ElButton>
          </ElFormItem>
        </ElForm>
        <template v-if="dryResult">
          <div class="hx-money" style="font-weight: bold">
            合计优惠：¥{{ formatMoney(dryResult.totalDiscount ?? 0) }}
          </div>
          <div
            v-for="detail in dryResult.details || []"
            :key="detail.activityId"
            class="hx-money"
            style="margin-top: 4px; color: var(--el-color-warning)"
          >
            {{ detail.activityName }}：优惠 ¥{{
              formatMoney(detail.discountAmount)
            }}
          </div>
          <div
            v-for="override in dryResult.ladderOverrides || []"
            :key="override.skuId"
            class="hx-money"
            style="margin-top: 4px; color: var(--el-color-warning)"
          >
            阶梯价 {{ override.skuId }} → ¥{{ formatMoney(override.unitPrice) }}
          </div>
          <div
            v-for="gift in dryResult.gifts || []"
            :key="gift.skuId"
            style="margin-top: 4px; color: var(--el-color-warning)"
          >
            赠品 {{ gift.skuId }} ×{{ gift.quantity }}
          </div>
          <div
            v-if="
              (dryResult.details || []).length === 0 &&
              (dryResult.gifts || []).length === 0
            "
            style="margin-top: 4px; color: var(--el-text-color-secondary)"
          >
            无命中活动
          </div>
        </template>
        <template #footer>
          <div class="hx-form-footer">
            <ElButton @click="dryVisible = false">取消</ElButton>
            <ElButton :loading="dryLoading" type="primary" @click="runDry">
              确定
            </ElButton>
          </div>
        </template>
      </ElDialog>

      <SelectGoods
        ref="selectGoodsRef"
        :limit-num="50"
        @current-row="onGoodsSelected"
      />
    </div>
  </div>
</template>
