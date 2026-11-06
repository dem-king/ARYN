<script lang="ts" setup>
import type {
  DistributionCouponPackRecord,
  DistributionCouponPackSavePayload,
  DistributionCouponPackStatus,
} from '#/api/promotion/distribution-coupon-pack';

import { defineAsyncComponent, reactive, ref } from 'vue';

import { formatDateTime } from '@vben/utils';

import { Delete, Edit, Plus, Sort } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getPage as getCouponInfoPage } from '#/api/promotion/coupon-info';
import {
  addObj,
  delObj,
  editObj,
  getById,
  getPage,
  updateStatus,
} from '#/api/promotion/distribution-coupon-pack';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';
import { useDict } from '#/utils/dict';
import { formatMoney } from '#/utils/util';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);

/** 券模板下拉项（coupon-info 精简字段） */
interface CouponTemplateOption {
  amount?: number;
  couponName?: string;
  couponType?: string;
  id: string;
  threshold?: number;
}

interface PackItemForm {
  couponInfoId: string;
  quantity: number;
}

const { distribution_coupon_pack_status } = useDict(
  'distribution_coupon_pack_status',
);

const loading = ref(false);
const showSearch = ref(true);
const templateOptions = ref<CouponTemplateOption[]>([]);
const state = reactive({
  queryParams: {
    packName: '',
    status: '' as '' | DistributionCouponPackStatus,
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    desc: 'create_time',
  },
  tableData: [] as DistributionCouponPackRecord[],
});

const formatTime = (value?: string) => (value ? formatDateTime(value) : '—');
const templateLabel = (item: CouponTemplateOption) => {
  const face = Number(item.amount ?? 0);
  const threshold = Number(item.threshold ?? 0);
  const faceText =
    item.couponType === '2'
      ? `${Number(face) || 0} 折`
      : `¥${formatMoney(face)}`;
  return `${item.couponName ?? item.id}（${faceText}${
    threshold > 0 ? `，满¥${formatMoney(threshold)}可用` : '，无门槛'
  }）`;
};
const packCouponText = (row: DistributionCouponPackRecord) =>
  (row.couponInfos ?? [])
    .map((item) => `${item.name ?? item.couponInfoId}×${item.quantity ?? 1}`)
    .join('、') || '—';

const loadTemplateOptions = async () => {
  if (templateOptions.value.length > 0) {
    return;
  }
  try {
    const response = await getCouponInfoPage({ current: 1, size: 200 });
    templateOptions.value = (response?.records ?? []).map((item: any) => ({
      id: item.id,
      couponName: item.couponName,
      couponType: item.couponType,
      amount: item.amount,
      threshold: item.threshold,
    }));
  } catch {
    templateOptions.value = [];
  }
};

const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    desc: state.page.desc,
  };
  await getPage(Object.assign(params, state.queryParams))
    .then((response) => {
      state.tableData = response.records;
      state.page.total = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};

// ---------------------------------------------------------------------------
// 新增/编辑券包
// ---------------------------------------------------------------------------
const formRef = ref();
const formState = reactive({
  visible: false,
  submitting: false,
  unlimitedStock: false,
  items: [] as PackItemForm[],
  form: {
    id: undefined,
    packName: '',
    faceAmount: 0,
    thresholdAmount: 0,
    validDays: 30,
    requiredCommission: 0,
    stock: 100,
    status: '0',
    sort: 0,
    remark: '',
  } as Omit<DistributionCouponPackSavePayload, 'items'>,
});

const openAdd = async () => {
  formState.form = {
    id: undefined,
    packName: '',
    faceAmount: 0,
    thresholdAmount: 0,
    validDays: 30,
    requiredCommission: 0,
    stock: 100,
    status: '0',
    sort: 0,
    remark: '',
  };
  formState.items = [{ couponInfoId: '', quantity: 1 }];
  formState.unlimitedStock = false;
  formState.visible = true;
  formRef.value?.clearValidate();
  await loadTemplateOptions();
};

const openEdit = async (row: DistributionCouponPackRecord) => {
  formState.items = [];
  formState.visible = true;
  formRef.value?.clearValidate();
  await loadTemplateOptions();
  let target = row;
  try {
    const detail = await getById(row.id);
    if (detail) {
      target = detail;
    }
  } catch {
    // 详情失败时保留列表行数据
  }
  formState.form = {
    id: target.id,
    packName: target.packName,
    faceAmount: Number(target.faceAmount ?? 0),
    thresholdAmount: Number(target.thresholdAmount ?? 0),
    validDays: target.validDays ?? 30,
    requiredCommission: Number(target.requiredCommission ?? 0),
    stock: target.stock ?? null,
    status: target.status ?? '0',
    sort: target.sort ?? 0,
    remark: target.remark ?? '',
  };
  formState.unlimitedStock =
    target.stock === null || target.stock === undefined;
  formState.items = (target.couponInfos ?? []).map((item) => ({
    couponInfoId: item.couponInfoId,
    quantity: item.quantity ?? 1,
  }));
  if (formState.items.length === 0) {
    formState.items = [{ couponInfoId: '', quantity: 1 }];
  }
};

const addItem = () => {
  formState.items.push({ couponInfoId: '', quantity: 1 });
};

const removeItem = (index: number) => {
  formState.items.splice(index, 1);
  if (formState.items.length === 0) {
    formState.items.push({ couponInfoId: '', quantity: 1 });
  }
};

const submitForm = async () => {
  await formRef.value?.validate();
  const items = formState.items.filter((item) => item.couponInfoId);
  if (items.length === 0) {
    ElMessage.error('请至少选择一张优惠券模板');
    return;
  }
  const duplicated = items.some(
    (item, index) =>
      items.findIndex((target) => target.couponInfoId === item.couponInfoId) !==
      index,
  );
  if (duplicated) {
    ElMessage.error('同一优惠券模板只能配置一次');
    return;
  }
  formState.submitting = true;
  try {
    const payload: DistributionCouponPackSavePayload = {
      ...formState.form,
      stock: formState.unlimitedStock ? null : formState.form.stock,
      items: items.map((item) => ({
        couponInfoId: item.couponInfoId,
        quantity: Number(item.quantity) || 1,
      })),
    };
    if (formState.form.id) {
      await editObj(payload);
      ElMessage.success('修改成功');
    } else {
      await addObj(payload);
      ElMessage.success('新增成功');
    }
    formState.visible = false;
    initPage();
  } finally {
    formState.submitting = false;
  }
};

/** 上下架切换 */
const toggleStatus = (row: DistributionCouponPackRecord) => {
  const nextStatus: DistributionCouponPackStatus =
    row.status === '0' ? '1' : '0';
  ElMessageBox.confirm(
    nextStatus === '0'
      ? `确认上架券包「${row.packName}」？`
      : `确认下架券包「${row.packName}」？下架后小程序不可兑换。`,
    '提示',
    {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    },
  ).then(() => {
    updateStatus({ id: row.id, status: nextStatus })
      .then(() => {
        ElMessage.success(nextStatus === '0' ? '已上架' : '已下架');
        initPage();
      })
      .catch(() => {});
  });
};

const del = (row: DistributionCouponPackRecord) => {
  ElMessageBox.confirm(
    `确认删除券包「${row.packName}」？已被兑换的券包仅逻辑删除，历史记录保留。`,
    '提示',
    {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    },
  ).then(() => {
    delObj(row.id)
      .then(() => {
        ElMessage.success('删除成功');
        initPage();
      })
      .catch(() => {});
  });
};

initPage();

useKeepAliveRefresh(initPage);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索 -->
      <PageFilterPanel
        :model="state.queryParams"
        :visible="showSearch"
        @reset="initPage"
        @search="initPage"
      >
        <ElFormItem label="券包名称" prop="packName">
          <ElInput
            v-model="state.queryParams.packName"
            clearable
            placeholder="请输入券包名称"
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="状态" prop="status">
          <ElSelect
            v-model="state.queryParams.status"
            clearable
            placeholder="请选择状态"
          >
            <ElOption
              v-for="item in distribution_coupon_pack_status"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
      </PageFilterPanel>

      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="个券包">
        <template #left>
          <ElButton
            v-access:code="'promotion:distributioncouponpack:add'"
            :icon="Plus"
            type="primary"
            @click="openAdd"
          >
            新增券包
          </ElButton>
        </template>
        <template #right>
          <RightToolbar
            :search-btn="true"
            :refresh-btn="true"
            @search="showSearch = !showSearch"
            @refresh="initPage"
          />
        </template>
      </PageToolbar>

      <!-- 列表 -->
      <ElTable
        v-loading="loading"
        class="hx-data-table"
        :data="state.tableData"
        row-key="id"
        border
        empty-text="暂无券包"
      >
        <ElTableColumn
          prop="packName"
          label="券包名称"
          min-width="150"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn label="面额" width="110" align="center">
          <template #default="scope">
            <span class="hx-money">
              ¥{{ formatMoney(scope.row.faceAmount) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="使用门槛" width="110" align="center">
          <template #default="scope">
            {{
              Number(scope.row.thresholdAmount) > 0
                ? `满¥${formatMoney(scope.row.thresholdAmount)}`
                : '无门槛'
            }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="validDays"
          label="有效期"
          width="90"
          align="center"
        >
          <template #default="scope">
            {{ scope.row.validDays ? `${scope.row.validDays} 天` : '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="所需佣金" width="120" align="center">
          <template #default="scope">
            <span class="hx-money">
              ¥{{ formatMoney(scope.row.requiredCommission) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="库存" width="90" align="center">
          <template #default="scope">
            {{
              scope.row.stock === null || scope.row.stock === undefined
                ? '不限'
                : scope.row.stock
            }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="关联券模板" min-width="200" align="center">
          <template #default="scope">
            <span class="template-cell">
              {{ packCouponText(scope.row as DistributionCouponPackRecord) }}
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="sort" label="排序" width="80" align="center" />
        <ElTableColumn prop="status" label="状态" width="90" align="center">
          <template #default="scope">
            <DictTag
              :options="distribution_coupon_pack_status"
              :value="scope.row.status"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn label="创建时间" width="170" align="center">
          <template #default="scope">
            {{ formatTime(scope.row.createTime) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="230" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              v-access:code="'promotion:distributioncouponpack:edit'"
              :icon="Edit"
              link
              type="primary"
              @click="openEdit(scope.row as DistributionCouponPackRecord)"
            >
              编辑
            </ElButton>
            <ElButton
              v-access:code="'promotion:distributioncouponpack:edit'"
              :icon="Sort"
              link
              type="primary"
              @click="toggleStatus(scope.row as DistributionCouponPackRecord)"
            >
              {{ scope.row.status === '0' ? '下架' : '上架' }}
            </ElButton>
            <ElButton
              v-access:code="'promotion:distributioncouponpack:del'"
              :icon="Delete"
              link
              type="danger"
              @click="del(scope.row as DistributionCouponPackRecord)"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>

      <!-- 分页 -->
      <Pagination
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        :total="state.page.total"
        @change="initPage"
      />

      <!-- 新增/编辑券包 -->
      <ElDialog
        v-model="formState.visible"
        :title="formState.form.id ? '编辑券包' : '新增券包'"
        width="720px"
      >
        <ElForm
          ref="formRef"
          :model="formState.form"
          :rules="{
            packName: [
              { required: true, message: '请填写券包名称', trigger: 'blur' },
            ],
            faceAmount: [
              { required: true, message: '请填写面额', trigger: 'change' },
            ],
            requiredCommission: [
              { required: true, message: '请填写所需佣金', trigger: 'change' },
            ],
          }"
          label-width="110px"
        >
          <ElFormItem label="券包名称" prop="packName">
            <ElInput
              v-model="formState.form.packName"
              :maxlength="64"
              placeholder="请输入券包名称"
              show-word-limit
            />
          </ElFormItem>
          <ElFormItem label="面额" prop="faceAmount">
            <ElInputNumber
              v-model="formState.form.faceAmount"
              :min="0"
              :precision="2"
              :step="10"
            />
            <span class="form-tip is-inline">元，展示给分销员的券面额</span>
          </ElFormItem>
          <ElFormItem label="使用门槛">
            <ElInputNumber
              v-model="formState.form.thresholdAmount"
              :min="0"
              :precision="2"
              :step="10"
            />
            <span class="form-tip is-inline">元，0 表示无门槛</span>
          </ElFormItem>
          <ElFormItem label="有效期">
            <ElInputNumber
              v-model="formState.form.validDays"
              :max="3650"
              :min="1"
              :precision="0"
            />
            <span class="form-tip is-inline">天，发放后有效天数</span>
          </ElFormItem>
          <ElFormItem label="所需佣金" prop="requiredCommission">
            <ElInputNumber
              v-model="formState.form.requiredCommission"
              :min="0"
              :precision="2"
              :step="1"
            />
            <span class="form-tip is-inline">
              元，兑换时扣减分销员的可用佣金
            </span>
          </ElFormItem>
          <ElFormItem label="库存">
            <ElSwitch v-model="formState.unlimitedStock" />
            <span class="form-tip is-inline">不限库存</span>
            <ElInputNumber
              v-if="!formState.unlimitedStock"
              v-model="formState.form.stock as number"
              :min="0"
              :precision="0"
              style="margin-left: 12px"
            />
            <span v-if="!formState.unlimitedStock" class="form-tip is-inline">
              个
            </span>
          </ElFormItem>
          <ElFormItem label="上架状态">
            <ElSwitch
              :model-value="formState.form.status === '0'"
              @update:model-value="
                (value) => (formState.form.status = value === true ? '0' : '1')
              "
            />
          </ElFormItem>
          <ElFormItem label="排序">
            <ElInputNumber
              v-model="formState.form.sort"
              :min="0"
              :precision="0"
            />
            <span class="form-tip is-inline">越小越靠前</span>
          </ElFormItem>
          <ElFormItem label="备注">
            <ElInput
              v-model="formState.form.remark"
              :maxlength="255"
              placeholder="请输入备注"
              show-word-limit
              type="textarea"
            />
          </ElFormItem>
          <ElFormItem label="关联券模板" required>
            <div class="pack-items">
              <div
                v-for="(item, index) in formState.items"
                :key="index"
                class="pack-items__row"
              >
                <ElSelect
                  v-model="item.couponInfoId"
                  filterable
                  placeholder="选择优惠券模板"
                  class="pack-items__select"
                >
                  <ElOption
                    v-for="template in templateOptions"
                    :key="template.id"
                    :label="templateLabel(template)"
                    :value="template.id"
                  />
                </ElSelect>
                <ElInputNumber
                  v-model="item.quantity"
                  :min="1"
                  :precision="0"
                  class="pack-items__quantity"
                />
                <ElButton
                  :icon="Delete"
                  link
                  type="danger"
                  @click="removeItem(index)"
                >
                  删除
                </ElButton>
              </div>
              <ElButton :icon="Plus" link type="primary" @click="addItem">
                添加优惠券
              </ElButton>
            </div>
          </ElFormItem>
        </ElForm>
        <template #footer>
          <div class="hx-form-footer">
            <ElButton @click="formState.visible = false">取消</ElButton>
            <ElButton
              :loading="formState.submitting"
              type="primary"
              @click="submitForm"
            >
              确定
            </ElButton>
          </div>
        </template>
      </ElDialog>
    </div>
  </div>
</template>

<style scoped>
.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.form-tip.is-inline {
  margin-left: 10px;
}

.template-cell {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.pack-items {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.pack-items__row {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
}

.pack-items__select {
  flex: 1;
}

.pack-items__quantity {
  width: 120px;
}
</style>
