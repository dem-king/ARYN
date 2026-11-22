<script lang="ts" setup>
import { computed, reactive, ref } from 'vue';

import { Plus, Search } from '@element-plus/icons-vue';
import {
  ElAlert,
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
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getBuyerOrgPage } from '#/api/order/buyer-org';
import { getDisplayByIds } from '#/api/product/goods-sku';
import { getPage as getGoodsPage } from '#/api/product/goods-spu';
import {
  addPricePolicy,
  changePricePolicyStatus,
  deletePricePolicy,
  getPricePolicies,
  getPricePolicyDetail,
  getPricePolicyDryRun,
  updatePricePolicy,
} from '#/api/promotion/price-policy';
import { getList as getMemberTagList } from '#/api/user/member-tag';
import { getVesselPage } from '#/api/vessel/vessel';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';

import {
  fmtPromoTime as fmtTime,
  PROMO_STATUS,
  PROMO_STATUS_TEXT as STATUS_TEXT,
  PROMO_STATUS_TYPE as STATUS_TYPE,
} from '../shared/promo-status';

/**
 * 价格政策（协议价）：按采购主体/船舶/客群标签圈定专属合同价。
 *
 * 口径提示（与后端一致）：
 * - 生效中的政策锁定成交基价，阶梯价/会员价/档期特供均不可覆盖，不可叠券积分；
 * - 同 SKU 多政策命中：优先级取大、同级取更低价；协议价 ≥ 售价视为倒挂不生效；
 * - 生效中的政策不可修改，需先停用。
 */

interface PolicyItem {
  minQty?: number;
  policyPrice?: null | number;
  policyRate?: null | number;
  /** 售价（仅展示，供协议价倒挂参考；保存时剥离，不落库） */
  salesPrice?: null | number | string;
  skuId: string;
  skuName?: string;
  spuId: string;
}

const APPLY_TYPE_TEXT: Record<string, string> = {
  1: '采购主体',
  2: '船舶',
  3: '客群标签',
};

const state = reactive({
  keyword: '',
  status: '',
  tableData: [] as any[],
});
const loading = ref(false);
const drawer = ref(false);
const saving = ref(false);
const editingId = ref<null | string>(null);

const form = reactive({
  applyType: '1',
  applyValue: '',
  endTime: '',
  policyName: '',
  priceMode: '1',
  priority: 0,
  remark: '',
  startTime: '',
});
const items = ref<PolicyItem[]>([]);

const range = ref<[] | [string, string]>([]);

/**
 * 适用对象下拉：applyType=1 采购主体(buyer-org) / 2 船舶(vessel) / 3 客群标签(member-tag)。
 * 三个列表接口均已存在，按 ID 多选回填，替代此前手填 ID。
 * form.applyValue 仍存英文逗号分隔字符串（与后端一致），下拉用数组视图读写；
 * 候选只取前 200 条，未覆盖的 ID 仍可经 allow-create 手动补入，不形成硬回归。
 */
const applyOptions = ref<Array<{ label: string; value: string }>>([]);
const applyLoading = ref(false);
const applyValueList = computed<string[]>({
  get: () =>
    form.applyValue ? form.applyValue.split(',').filter(Boolean) : [],
  set: (values) => {
    form.applyValue = values.join(',');
  },
});

const loadApplyOptions = async () => {
  applyLoading.value = true;
  try {
    switch (form.applyType) {
      case '1': {
        const res: any = await getBuyerOrgPage({ current: 1, size: 200 });
        applyOptions.value = (res.records ?? []).map((org: any) => ({
          label: org.orgName,
          value: org.id,
        }));

        break;
      }
      case '2': {
        const res: any = await getVesselPage({ current: 1, size: 200 });
        applyOptions.value = (res.records ?? []).map((vessel: any) => ({
          label: vessel.vesselName,
          value: vessel.id,
        }));

        break;
      }
      case '3': {
        const res: any = await getMemberTagList();
        const list = Array.isArray(res) ? res : (res?.records ?? []);
        applyOptions.value = list.map((tag: any) => ({
          label: tag.tagName,
          value: tag.id,
        }));

        break;
      }
      default: {
        applyOptions.value = [];
      }
    }
  } catch {
    // 列表加载失败降级为空选项，仍可手动输入 ID（allow-create）
    applyOptions.value = [];
  } finally {
    applyLoading.value = false;
  }
};

/** 切换适用对象类型：清空已选（跨类型 ID 不通用）并重新拉取候选 */
const onApplyTypeChange = () => {
  form.applyValue = '';
  loadApplyOptions();
};

/** 按 skuId 批量补齐商品名/规格/售价（明细只落 skuId，编辑回显与批量导入都用它） */
const enrichItemsDisplay = async (rows: PolicyItem[]) => {
  const ids = [...new Set(rows.map((row) => row.skuId).filter(Boolean))];
  if (ids.length === 0) return;
  try {
    const displays = (await getDisplayByIds(ids)) ?? [];
    const map = new Map(displays.map((d) => [d.skuId, d]));
    for (const row of rows) {
      const display = map.get(row.skuId);
      if (!display) continue;
      row.skuName =
        `${display.spuName ?? ''} ${display.skuName ?? ''}`.trim() || row.skuId;
      row.spuId = display.spuId ?? row.spuId;
      row.salesPrice = display.salesPrice ?? null;
    }
  } catch {
    // 商品服务不可用：保留 skuId 兜底显示，不阻断配置
  }
};

const load = async () => {
  loading.value = true;
  try {
    state.tableData = (await getPricePolicies({
      keyword: state.keyword || undefined,
      status: state.status || undefined,
    })) as any[];
  } finally {
    loading.value = false;
  }
};

const resetForm = () => {
  editingId.value = null;
  Object.assign(form, {
    applyType: '1',
    applyValue: '',
    endTime: '',
    policyName: '',
    priceMode: '1',
    priority: 0,
    remark: '',
    startTime: '',
  });
  items.value = [];
  range.value = [];
};

const openCreate = () => {
  resetForm();
  loadApplyOptions();
  drawer.value = true;
};

const openEdit = async (row: any) => {
  resetForm();
  editingId.value = row.id;
  // 先用列表行回填表单，弹窗即时可见；明细以详情接口为准（列表行不含 SKU 明细）
  Object.assign(form, {
    applyType: row.applyType,
    applyValue: row.applyValue,
    endTime: row.endTime,
    policyName: row.policyName,
    priceMode: row.priceMode,
    priority: row.priority ?? 0,
    remark: row.remark ?? '',
    startTime: row.startTime,
  });
  range.value =
    row.startTime && row.endTime ? [row.startTime, row.endTime] : [];
  loadApplyOptions();
  drawer.value = true;
  try {
    const detail: any = await getPricePolicyDetail(row.id);
    const policy = detail?.policy ?? row;
    Object.assign(form, {
      applyType: policy.applyType,
      applyValue: policy.applyValue,
      endTime: policy.endTime,
      policyName: policy.policyName,
      priceMode: policy.priceMode,
      priority: policy.priority ?? 0,
      remark: policy.remark ?? '',
      startTime: policy.startTime,
    });
    range.value =
      policy.startTime && policy.endTime
        ? [policy.startTime, policy.endTime]
        : [];
    const detailItems: any[] = detail?.items ?? [];
    items.value = detailItems.map((item) => ({
      minQty: item.minQty ?? 1,
      policyPrice: item.policyPrice ?? null,
      policyRate: item.policyRate ?? null,
      salesPrice: null,
      skuId: item.skuId,
      skuName: item.skuId,
      spuId: item.spuId ?? '',
    }));
    // 明细只落 skuId，按 ID 批量补商品名/售价后再展示
    await enrichItemsDisplay(items.value);
  } catch {
    ElMessage.error('SKU 明细加载失败，请重试');
  }
};

// 商品选择：SPU 列表勾选后按行内 goodsSkus 展开 SKU 明细
const picker = ref(false);
const pickerLoading = ref(false);
const pickerRows = ref<any[]>([]);
const pickerKeyword = ref('');
const pickerSelected = ref<any[]>([]);

const loadPicker = async () => {
  pickerLoading.value = true;
  try {
    const response: any = await getGoodsPage({
      current: 1,
      keyword: pickerKeyword.value || undefined,
      size: 20,
    });
    pickerRows.value = response.records || [];
  } finally {
    pickerLoading.value = false;
  }
};

const openPicker = () => {
  picker.value = true;
  pickerKeyword.value = '';
  pickerSelected.value = [];
  loadPicker();
};

const confirmPicker = () => {
  const exist = new Set(items.value.map((item) => item.skuId));
  for (const spu of pickerSelected.value) {
    for (const sku of spu.goodsSkus || []) {
      if (exist.has(sku.id)) {
        continue;
      }
      exist.add(sku.id);
      items.value.push({
        minQty: 1,
        policyPrice: null,
        policyRate: null,
        salesPrice: sku.salesPrice ?? null,
        skuId: sku.id,
        skuName: `${spu.name} ${sku.specsInfo || ''}`.trim(),
        spuId: spu.id,
      });
    }
  }
  picker.value = false;
};

/**
 * SKU 明细批量导入：粘贴 SKU ID（换行/逗号/空格分隔），按 ID 批量取商品名与售价
 * 回填明细行；查不到的 ID 明确列出。复用商品域 byIds 接口，不改后端。
 */
const importVisible = ref(false);
const importText = ref('');
const importLoading = ref(false);
const importMissing = ref<string[]>([]);

const openImport = () => {
  importText.value = '';
  importMissing.value = [];
  importVisible.value = true;
};

const confirmImport = async () => {
  const ids = [
    ...new Set(
      importText.value
        .split(/[\s,，]+/)
        .map((id) => id.trim())
        .filter(Boolean),
    ),
  ];
  if (ids.length === 0) {
    ElMessage.warning('请粘贴至少一个 SKU ID');
    return;
  }
  importLoading.value = true;
  try {
    const displays = (await getDisplayByIds(ids)) ?? [];
    const found = new Map(displays.map((d) => [d.skuId, d]));
    const exist = new Set(items.value.map((item) => item.skuId));
    let added = 0;
    for (const id of ids) {
      const display = found.get(id);
      if (!display || exist.has(id)) continue;
      exist.add(id);
      items.value.push({
        minQty: 1,
        policyPrice: null,
        policyRate: null,
        salesPrice: display.salesPrice ?? null,
        skuId: id,
        skuName:
          `${display.spuName ?? ''} ${display.skuName ?? ''}`.trim() || id,
        spuId: display.spuId ?? '',
      });
      added += 1;
    }
    importMissing.value = ids.filter((id) => !found.has(id));
    if (added > 0) ElMessage.success(`已导入 ${added} 个 SKU 明细`);
    if (importMissing.value.length > 0) {
      ElMessage.warning(`未找到 ${importMissing.value.length} 个 SKU ID`);
    } else {
      importVisible.value = false;
    }
  } finally {
    importLoading.value = false;
  }
};

const save = async () => {
  if (!form.policyName) {
    ElMessage.warning('请填写政策名称');
    return;
  }
  if (!form.applyValue) {
    ElMessage.warning('请选择适用对象');
    return;
  }
  if (!range.value || range.value.length !== 2) {
    ElMessage.warning('请选择生效时间窗');
    return;
  }
  if (items.value.length === 0) {
    ElMessage.warning('请至少配置一个 SKU 明细');
    return;
  }
  for (const item of items.value) {
    if (form.priceMode === '1' && !item.policyPrice) {
      ElMessage.warning(`SKU ${item.skuId} 缺少协议固定价`);
      return;
    }
    if (form.priceMode === '2' && !item.policyRate) {
      ElMessage.warning(`SKU ${item.skuId} 缺少协议折扣率`);
      return;
    }
  }
  saving.value = true;
  try {
    const payload = {
      // 只提交后端 PricePolicyItem 字段；skuName/salesPrice 仅前端展示，剥离不落库
      items: items.value.map((item) => ({
        minQty: item.minQty,
        policyPrice: item.policyPrice,
        policyRate: item.policyRate,
        skuId: item.skuId,
        spuId: item.spuId,
      })),
      policy: {
        ...form,
        endTime: range.value[1],
        startTime: range.value[0],
      },
    };
    if (editingId.value) {
      await updatePricePolicy(editingId.value, payload);
      ElMessage.success('已保存（草稿/停用态）');
    } else {
      await addPricePolicy(payload);
      ElMessage.success('已创建（草稿，发布后生效）');
    }
    drawer.value = false;
    load();
  } finally {
    saving.value = false;
  }
};

const publish = async (row: any) => {
  await changePricePolicyStatus(row.id, PROMO_STATUS.ACTIVE);
  ElMessage.success('已发布生效');
  load();
};

/**
 * 发布前 dry-run：同 SKU 多政策冲突 / 协议价倒挂 / 成本击穿预检。
 * 结论只解释「按当前配置哪个政策会赢」，不阻断发布；COST_RISK 运营自行决策。
 */
const VERDICT_TYPE: Record<string, 'danger' | 'info' | 'primary' | 'warning'> =
  {
    OK: 'info',
    INVERTED_SALES: 'danger',
    SHADOWED: 'warning',
    SHADOWS_OTHER: 'primary',
    COST_RISK: 'warning',
  };
const dryRunVisible = ref(false);
const dryRunLoading = ref(false);
const dryRunRow = ref<any>(null);
const dryRunItems = ref<any[]>([]);

const openDryRun = async (row: any) => {
  dryRunRow.value = row;
  dryRunVisible.value = true;
  dryRunLoading.value = true;
  try {
    const detail: any = await getPricePolicyDryRun(row.id);
    dryRunItems.value = detail?.items || [];
  } finally {
    dryRunLoading.value = false;
  }
};

const pause = async (row: any) => {
  await changePricePolicyStatus(row.id, PROMO_STATUS.DISABLED);
  ElMessage.success('已停用');
  load();
};

const handleDelete = (row: any) => {
  ElMessageBox.confirm(
    `删除价格政策「${row.policyName}」及其明细，是否继续？`,
    '提示',
    {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    },
  ).then(async () => {
    await deletePricePolicy(row.id);
    ElMessage.success('删除成功');
    load();
  });
};

load();
useKeepAliveRefresh(load);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <PageToolbar total-label="">
        <template #left>
          <ElButton
            type="primary"
            v-access:code="'promotion:pricepolicy:save'"
            :icon="Plus"
            @click="openCreate"
          >
            新建政策
          </ElButton>
        </template>
        <template #right>
          <div class="flex items-center gap-2">
            <ElInput
              v-model="state.keyword"
              placeholder="政策名称"
              clearable
              style="width: 180px"
              @keyup.enter="load"
            />
            <ElSelect
              v-model="state.status"
              placeholder="状态"
              clearable
              style="width: 120px"
            >
              <ElOption label="草稿" :value="PROMO_STATUS.DRAFT" />
              <ElOption label="生效中" :value="PROMO_STATUS.ACTIVE" />
              <ElOption label="已停用" :value="PROMO_STATUS.DISABLED" />
            </ElSelect>
            <ElButton :icon="Search" @click="load"> 查询 </ElButton>
          </div>
        </template>
      </PageToolbar>

      <ElTable
        v-loading="loading"
        class="hx-data-table"
        :data="state.tableData"
        row-key="id"
        border
        empty-text="暂无价格政策"
      >
        <ElTableColumn prop="policyName" label="政策名称" min-width="160" />
        <ElTableColumn label="适用对象" width="180">
          <template #default="scope">
            <ElTag size="small">
              {{ APPLY_TYPE_TEXT[scope.row.applyType] || '-' }}
            </ElTag>
            <span class="ml-1 text-xs text-gray-500">
              {{ String(scope.row.applyValue || '').split(',').length }} 个对象
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="价格模式" width="100">
          <template #default="scope">
            {{ scope.row.priceMode === '1' ? '固定协议价' : '折扣率' }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="priority" label="优先级" width="80" />
        <ElTableColumn label="生效时间窗" width="240">
          <template #default="scope">
            {{ fmtTime(scope.row.startTime) }} ~
            {{ fmtTime(scope.row.endTime) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="90">
          <template #default="scope">
            <ElTag :type="STATUS_TYPE[scope.row.status] || 'info'">
              {{ STATUS_TEXT[scope.row.status] || '-' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="250" align="center">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'promotion:pricepolicy:page'"
              @click="openDryRun(scope.row)"
            >
              预检
            </ElButton>
            <ElButton
              v-if="scope.row.status !== PROMO_STATUS.ACTIVE"
              link
              type="primary"
              v-access:code="'promotion:pricepolicy:publish'"
              @click="publish(scope.row)"
            >
              发布
            </ElButton>
            <ElButton
              v-if="scope.row.status === PROMO_STATUS.ACTIVE"
              link
              type="warning"
              v-access:code="'promotion:pricepolicy:publish'"
              @click="pause(scope.row)"
            >
              停用
            </ElButton>
            <ElButton
              v-if="scope.row.status !== PROMO_STATUS.ACTIVE"
              link
              type="primary"
              v-access:code="'promotion:pricepolicy:save'"
              @click="openEdit(scope.row)"
            >
              编辑
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'promotion:pricepolicy:del'"
              @click="handleDelete(scope.row)"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>

      <ElDialog
        v-model="drawer"
        :title="editingId ? '编辑价格政策' : '新建价格政策'"
        width="860px"
        destroy-on-close
      >
        <ElForm label-width="92px">
          <div class="grid grid-cols-2 gap-x-4">
            <ElFormItem label="政策名称">
              <ElInput
                v-model="form.policyName"
                placeholder="如：XX工厂年度协议价"
              />
            </ElFormItem>
            <ElFormItem label="优先级">
              <ElInputNumber v-model="form.priority" :min="0" :max="999" />
              <span class="ml-2 text-xs text-gray-400"
                >同SKU多政策命中取大，同级取低价</span
              >
            </ElFormItem>
            <ElFormItem label="适用对象">
              <ElSelect
                v-model="form.applyType"
                style="width: 130px"
                @change="onApplyTypeChange"
              >
                <ElOption label="采购主体" value="1" />
                <ElOption label="船舶" value="2" />
                <ElOption label="客群标签" value="3" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label-width="0">
              <ElSelect
                v-model="applyValueList"
                multiple
                filterable
                allow-create
                default-first-option
                clearable
                :loading="applyLoading"
                placeholder="选择适用对象（可多选，未列出的可直接输入ID）"
                style="width: 100%"
              >
                <ElOption
                  v-for="option in applyOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="价格模式">
              <ElSelect v-model="form.priceMode">
                <ElOption label="固定协议价" value="1" />
                <ElOption label="折扣率（对售价）" value="2" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="生效时间窗">
              <ElDatePicker
                v-model="range"
                type="datetimerange"
                value-format="YYYY-MM-DD HH:mm:ss"
              />
            </ElFormItem>
          </div>
          <ElFormItem label="备注">
            <ElInput v-model="form.remark" type="textarea" :rows="2" />
          </ElFormItem>

          <ElFormItem label="SKU 明细">
            <div class="w-full">
              <div class="flex items-center gap-2">
                <ElButton size="small" type="primary" @click="openPicker">
                  从商品库添加
                </ElButton>
                <ElButton size="small" @click="openImport"> 批量导入 </ElButton>
              </div>
              <ElTable
                :data="items"
                size="small"
                border
                class="mt-2"
                empty-text="暂无明细"
              >
                <ElTableColumn
                  prop="skuName"
                  label="商品/规格"
                  min-width="180"
                />
                <ElTableColumn prop="skuId" label="SKU ID" width="200" />
                <ElTableColumn label="售价" width="110">
                  <template #default="scope">
                    {{ scope.row.salesPrice ?? '-' }}
                  </template>
                </ElTableColumn>
                <ElTableColumn label="协议价/折扣率" width="220">
                  <template #default="scope">
                    <ElInputNumber
                      v-if="form.priceMode === '1'"
                      v-model="scope.row.policyPrice"
                      :min="0.01"
                      :precision="2"
                      size="small"
                    />
                    <ElInputNumber
                      v-else
                      v-model="scope.row.policyRate"
                      :min="0.0001"
                      :max="1"
                      :precision="4"
                      :step="0.05"
                      size="small"
                    />
                  </template>
                </ElTableColumn>
                <ElTableColumn label="起订量提示" width="140">
                  <template #default="scope">
                    <ElInputNumber
                      v-model="scope.row.minQty"
                      :min="1"
                      :precision="0"
                      size="small"
                    />
                  </template>
                </ElTableColumn>
                <ElTableColumn label="操作" width="70" align="center">
                  <template #default="scope">
                    <ElButton
                      link
                      type="danger"
                      size="small"
                      @click="items.splice(scope.$index, 1)"
                    >
                      移除
                    </ElButton>
                  </template>
                </ElTableColumn>
              </ElTable>
            </div>
          </ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="drawer = false"> 取消 </ElButton>
          <ElButton type="primary" :loading="saving" @click="save">
            保存
          </ElButton>
        </template>
      </ElDialog>

      <ElDialog v-model="picker" title="从商品库选择（展开 SKU）" width="720px">
        <div class="mb-2 flex items-center gap-2">
          <ElInput
            v-model="pickerKeyword"
            placeholder="商品名称/编码"
            clearable
            style="width: 240px"
            @keyup.enter="loadPicker"
          />
          <ElButton type="primary" @click="loadPicker"> 查询 </ElButton>
        </div>
        <ElTable
          v-loading="pickerLoading"
          :data="pickerRows"
          height="420"
          @selection-change="(rows: any[]) => (pickerSelected = rows)"
        >
          <ElTableColumn type="selection" width="42" />
          <ElTableColumn prop="name" label="商品" min-width="220" />
          <ElTableColumn label="SKU 数" width="80">
            <template #default="scope">
              {{ (scope.row.goodsSkus || []).length }}
            </template>
          </ElTableColumn>
          <ElTableColumn prop="salesPrice" label="售价" width="100" />
        </ElTable>
        <template #footer>
          <ElButton @click="picker = false"> 取消 </ElButton>
          <ElButton type="primary" @click="confirmPicker">
            添加所选 SKU
          </ElButton>
        </template>
      </ElDialog>

      <ElDialog v-model="importVisible" title="批量导入 SKU" width="560px">
        <ElAlert type="info" :closable="false" show-icon class="mb-2">
          粘贴 SKU ID，支持换行、逗号或空格分隔；导入后自动带出商品名称与售价，
          未匹配到的 ID 会在下方列出。
        </ElAlert>
        <ElInput
          v-model="importText"
          type="textarea"
          :rows="6"
          placeholder="例如：&#10;1001&#10;1002,1003 1004"
        />
        <ElAlert
          v-if="importMissing.length > 0"
          type="warning"
          :closable="false"
          show-icon
          class="mt-2"
        >
          以下 SKU ID 未找到：{{ importMissing.join('、') }}
        </ElAlert>
        <template #footer>
          <ElButton @click="importVisible = false"> 取消 </ElButton>
          <ElButton
            type="primary"
            :loading="importLoading"
            @click="confirmImport"
          >
            导入
          </ElButton>
        </template>
      </ElDialog>

      <ElDialog
        v-model="dryRunVisible"
        :title="`发布预检：${dryRunRow?.policyName ?? ''}`"
        width="760px"
        destroy-on-close
      >
        <ElAlert type="info" :closable="false" show-icon>
          预检只解释「按当前配置哪个政策会赢」（与成交引擎同口径），不阻断发布；
          INVERTED_SALES=协议价不低于售价（引擎不落价）、SHADOWED=会被更高优先级政策
          压住、SHADOWS_OTHER=会压住其他政策、COST_RISK=低于成本价（自行决策）。
        </ElAlert>
        <ElTable
          v-loading="dryRunLoading"
          :data="dryRunItems"
          size="small"
          border
          class="mt-3"
          empty-text="该政策没有 SKU 明细"
        >
          <ElTableColumn prop="skuId" label="SKU" width="200" />
          <ElTableColumn prop="spuName" label="商品" min-width="160" />
          <ElTableColumn prop="salesPrice" label="当前售价" width="90" />
          <ElTableColumn prop="policyPrice" label="本政策价" width="90" />
          <ElTableColumn prop="costPrice" label="成本价" width="90" />
          <ElTableColumn label="结论" min-width="220">
            <template #default="scope">
              <ElTag
                v-for="v in String(scope.row.verdict || '').split(',')"
                :key="v"
                class="mr-1"
                size="small"
                :type="VERDICT_TYPE[v] || 'info'"
              >
                {{ v }}
              </ElTag>
              <span
                v-if="scope.row.counterpartyPolicyName"
                class="text-xs text-gray-500"
              >
                对手：{{ scope.row.counterpartyPolicyName }}
              </span>
            </template>
          </ElTableColumn>
        </ElTable>
        <template #footer>
          <ElButton @click="dryRunVisible = false"> 关闭 </ElButton>
        </template>
      </ElDialog>
    </div>
  </div>
</template>
