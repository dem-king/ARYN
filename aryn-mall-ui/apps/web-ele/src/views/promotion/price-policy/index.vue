<script lang="ts" setup>
import { reactive, ref } from 'vue';

import { Plus, Search } from '@element-plus/icons-vue';
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
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getPage as getGoodsPage } from '#/api/product/goods-spu';
import {
  addPricePolicy,
  changePricePolicyStatus,
  deletePricePolicy,
  getPricePolicies,
  getPricePolicyDryRun,
  updatePricePolicy,
} from '#/api/promotion/price-policy';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';

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
  skuId: string;
  skuName?: string;
  spuId: string;
}

const APPLY_TYPE_TEXT: Record<string, string> = {
  1: '采购主体',
  2: '船舶',
  3: '客群标签',
};
const STATUS_TYPE: Record<string, 'danger' | 'info' | 'success'> = {
  0: 'info',
  1: 'success',
  2: 'danger',
};
const STATUS_TEXT: Record<string, string> = {
  0: '草稿',
  1: '生效中',
  2: '已停用',
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
  drawer.value = true;
};

const openEdit = async (row: any) => {
  resetForm();
  editingId.value = row.id;
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
  drawer.value = true;
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
        skuId: sku.id,
        skuName: `${spu.name} ${sku.specsInfo || ''}`.trim(),
        spuId: spu.id,
      });
    }
  }
  picker.value = false;
};

const save = async () => {
  if (!form.policyName) {
    ElMessage.warning('请填写政策名称');
    return;
  }
  if (!form.applyValue) {
    ElMessage.warning('请填写适用对象ID（多个用英文逗号分隔）');
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
      items: items.value,
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
  await changePricePolicyStatus(row.id, '1');
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
  await changePricePolicyStatus(row.id, '2');
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

const fmtTime = (value?: string) =>
  value ? value.replace('T', ' ').slice(0, 16) : '-';

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
              <ElOption label="草稿" value="0" />
              <ElOption label="生效中" value="1" />
              <ElOption label="已停用" value="2" />
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
              v-if="scope.row.status !== '1'"
              link
              type="primary"
              v-access:code="'promotion:pricepolicy:publish'"
              @click="publish(scope.row)"
            >
              发布
            </ElButton>
            <ElButton
              v-if="scope.row.status === '1'"
              link
              type="warning"
              v-access:code="'promotion:pricepolicy:publish'"
              @click="pause(scope.row)"
            >
              停用
            </ElButton>
            <ElButton
              v-if="scope.row.status !== '1'"
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
              <ElSelect v-model="form.applyType" style="width: 130px">
                <ElOption label="采购主体" value="1" />
                <ElOption label="船舶" value="2" />
                <ElOption label="客群标签" value="3" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label-width="0">
              <ElInput
                v-model="form.applyValue"
                :placeholder="
                  form.applyType === '2'
                    ? '船舶ID，多个英文逗号分隔'
                    : '对象ID，多个英文逗号分隔'
                "
              />
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
                value-format="YYYY-MM-DDTHH:mm:ss"
              />
            </ElFormItem>
          </div>
          <ElFormItem label="备注">
            <ElInput v-model="form.remark" type="textarea" :rows="2" />
          </ElFormItem>

          <ElFormItem label="SKU 明细">
            <div class="w-full">
              <ElButton size="small" type="primary" @click="openPicker">
                从商品库添加
              </ElButton>
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
