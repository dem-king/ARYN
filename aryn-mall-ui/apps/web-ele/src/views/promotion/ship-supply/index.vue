<script lang="ts" setup>
import { defineAsyncComponent, onMounted, reactive, ref } from 'vue';

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
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import {
  addActivity,
  deleteActivity,
  getActivityPage,
  pauseActivity,
  publishActivity,
  updateActivity,
} from '#/api/promotion/ship-activity';

const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
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
  endTime: '',
  priority: 0,
  purchaseScene: '',
  scopeType: '1',
  scopeValue: '',
  startTime: '',
});
const ladders = ref<Array<{ minQty: number; unitPrice: number }>>([
  { minQty: 10, unitPrice: 0 },
]);
const tiers = ref<Array<{ discountAmount: number; minAmount: number }>>([
  { discountAmount: 0, minAmount: 0 },
]);

const typeLabel: Record<string, string> = {
  '4': '阶梯价',
  '7': '整船优惠',
};
const statusLabel: Record<string, string> = {
  '1': '草稿',
  '2': '已发布',
  '3': '已暂停',
  '4': '已结束',
};
const scopeLabel: Record<string, string> = {
  '1': '全场',
  '2': '指定SKU',
  '4': '指定场景',
  '5': '指定船舶',
  '6': '指定靠港',
  '7': '指定港口',
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
  form.activityName = row?.activityName ?? '';
  form.activityType = row?.activityType ?? '4';
  form.scopeType = row?.scopeType ?? '1';
  form.scopeValue = row?.scopeValue ?? '';
  form.purchaseScene = row?.purchaseScene ?? '';
  form.priority = row?.priority ?? 0;
  form.startTime = row?.startTime ?? '';
  form.endTime = row?.endTime ?? '';
  try {
    const rules = row ? JSON.parse(row.rules) : {};
    ladders.value = rules.ladders ?? [{ minQty: 10, unitPrice: 0 }];
    tiers.value = rules.tiers ?? [{ discountAmount: 0, minAmount: 0 }];
  } catch {
    ladders.value = [{ minQty: 10, unitPrice: 0 }];
    tiers.value = [{ discountAmount: 0, minAmount: 0 }];
  }
  dialogVisible.value = true;
};

const buildPayload = () => {
  const rules =
    form.activityType === '4'
      ? { ladders: ladders.value }
      : { tiers: tiers.value };
  return {
    ...form,
    rules: JSON.stringify(rules),
  };
};

const handleSave = () => {
  if (!form.activityName || !form.startTime || !form.endTime) {
    ElMessage.warning('请完善活动名称与时间');
    return;
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

const handlePublish = (row: any) => {
  publishActivity(row.id)
    .then(() => {
      ElMessage.success('已发布');
      initPage();
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

const addLadder = () => {
  ladders.value.push({ minQty: 1, unitPrice: 0 });
};
const addTier = () => {
  tiers.value.push({ discountAmount: 0, minAmount: 0 });
};

onMounted(initPage);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElForm :inline="true" :model="state.query">
        <ElFormItem label="活动类型">
          <ElSelect
            v-model="state.query.activityType"
            clearable
            style="width: 130px"
          >
            <ElOption
              v-for="(label, value) in typeLabel"
              :key="value"
              :label="label"
              :value="value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSelect v-model="state.query.status" clearable style="width: 120px">
            <ElOption
              v-for="(label, value) in statusLabel"
              :key="value"
              :label="label"
              :value="value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage">搜索</ElButton>
          <ElButton
            type="success"
            v-access:code="'promotion:shipactivity:save'"
            @click="openDialog()"
          >
            新建活动
          </ElButton>
        </ElFormItem>
      </ElForm>

      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="activityName" label="活动名称" min-width="160" />
        <ElTableColumn label="类型" width="100">
          <template #default="scope">
            {{ typeLabel[scope.row.activityType] ?? scope.row.activityType }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="范围" width="100">
          <template #default="scope">
            {{ scopeLabel[scope.row.scopeType] ?? scope.row.scopeType }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="startTime" label="开始" width="170" />
        <ElTableColumn prop="endTime" label="结束" width="170" />
        <ElTableColumn label="状态" width="90">
          <template #default="scope">
            {{ statusLabel[scope.row.status] ?? scope.row.status }}
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
        <ElForm label-width="110px">
          <ElFormItem label="活动名称" required>
            <ElInput v-model="form.activityName" />
          </ElFormItem>
          <ElFormItem label="活动类型">
            <ElRadioGroup v-model="form.activityType" :disabled="!!editingId">
              <ElRadioButton value="4">船供阶梯价</ElRadioButton>
              <ElRadioButton value="7">船供整船优惠</ElRadioButton>
            </ElRadioGroup>
          </ElFormItem>
          <ElFormItem label="适用范围">
            <ElSelect
              v-model="form.scopeType"
              :disabled="!!editingId"
              style="width: 130px"
            >
              <ElOption
                v-for="(label, value) in scopeLabel"
                :key="value"
                :label="label"
                :value="value"
              />
            </ElSelect>
            <ElInput
              v-if="form.scopeType !== '1'"
              v-model="form.scopeValue"
              class="ml-10px"
              :placeholder="
                form.scopeType === '4'
                  ? '场景：1 或 2'
                  : form.scopeType === '7'
                    ? '港口编码，如 CNSHA'
                    : 'ID 逗号分隔'
              "
              style="width: 260px"
            />
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
          <ElFormItem label="开始时间" required>
            <ElInput
              v-model="form.startTime"
              placeholder="2026-09-20 00:00:00"
            />
          </ElFormItem>
          <ElFormItem label="结束时间" required>
            <ElInput v-model="form.endTime" placeholder="2026-10-20 23:59:59" />
          </ElFormItem>

          <template v-if="form.activityType === '4'">
            <ElFormItem
              v-for="(ladder, index) in ladders"
              :key="index"
              :label="`档位 ${index + 1}`"
            >
              <span>满</span>
              <ElInputNumber
                v-model="ladder.minQty"
                :min="1"
                controls-position="right"
                style="width: 110px; margin: 0 8px"
              />
              <span>件，单价</span>
              <ElInputNumber
                v-model="ladder.unitPrice"
                :min="0"
                :precision="2"
                controls-position="right"
                style="width: 120px; margin: 0 8px"
              />
              <ElButton link type="primary" @click="addLadder"> 加档 </ElButton>
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
                controls-position="right"
                style="width: 130px; margin: 0 8px"
              />
              <span>元，减</span>
              <ElInputNumber
                v-model="tier.discountAmount"
                :min="0"
                :precision="2"
                controls-position="right"
                style="width: 120px; margin: 0 8px"
              />
              <ElButton link type="primary" @click="addTier">加档</ElButton>
            </ElFormItem>
          </template>
        </ElForm>
        <template #footer>
          <ElButton @click="dialogVisible = false">取消</ElButton>
          <ElButton type="primary" @click="handleSave">保存</ElButton>
        </template>
      </ElDialog>
    </div>
  </div>
</template>
