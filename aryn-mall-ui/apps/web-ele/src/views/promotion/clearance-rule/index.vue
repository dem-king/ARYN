<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  addClearanceRule,
  deleteClearanceRule,
  getClearanceRules,
  updateClearanceRule,
} from '#/api/promotion/clearance-rule';

const loading = ref(false);
const rules = ref<any[]>([]);
const dialogVisible = ref(false);
const saving = ref(false);
const editingId = ref('');
const form = reactive<any>({
  ruleName: '',
  hoursBeforeExpire: 24,
  discountRate: 0.8,
  enabled: true,
  sortOrder: 0,
  remark: '',
});

async function load() {
  loading.value = true;
  try {
    rules.value = (await getClearanceRules()) || [];
  } finally {
    loading.value = false;
  }
}

function openDialog(row?: any) {
  editingId.value = row?.id || '';
  Object.assign(form, {
    ruleName: row?.ruleName || '',
    hoursBeforeExpire: row?.hoursBeforeExpire ?? 24,
    discountRate: row?.discountRate ? Number(row.discountRate) : 0.8,
    enabled: row ? row.enabled === '1' : true,
    sortOrder: row?.sortOrder ?? 0,
    remark: row?.remark || '',
  });
  dialogVisible.value = true;
}

async function submit() {
  if (!form.ruleName) {
    return ElMessage.warning('请输入规则名称');
  }
  if (!form.hoursBeforeExpire || form.hoursBeforeExpire <= 0) {
    return ElMessage.warning('距到期小时数必须大于 0');
  }
  if (!form.discountRate || form.discountRate <= 0 || form.discountRate >= 1) {
    return ElMessage.warning('折扣率必须在 (0, 1) 区间内');
  }
  saving.value = true;
  try {
    const payload = {
      ruleName: form.ruleName,
      hoursBeforeExpire: form.hoursBeforeExpire,
      discountRate: form.discountRate,
      enabled: form.enabled ? '1' : '0',
      sortOrder: form.sortOrder,
      remark: form.remark,
    };
    await (editingId.value
      ? updateClearanceRule(editingId.value, payload)
      : addClearanceRule(payload));
    ElMessage.success('规则已保存，出清价已重新同步');
    dialogVisible.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}

async function remove(row: any) {
  await ElMessageBox.confirm(
    `确认删除规则「${row.ruleName}」？删除后该时间档不再叠加折扣。`,
    '提示',
    { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' },
  );
  await deleteClearanceRule(row.id);
  ElMessage.success('已删除');
  await load();
}

onMounted(load);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <div class="mb10 hint">
        出清价先按商品规则算出基础价，再按「距到期小时数」命中最深一档折扣率打折；
        同一时刻只应用一档，不连乘。保存后立即重算系统出清活动。
      </div>
      <div class="mb10">
        <ElButton
          type="primary"
          v-access:code="'promotion:shipactivity:save'"
          @click="openDialog()"
        >
          新增规则
        </ElButton>
      </div>
      <ElTable
        v-loading="loading"
        :data="rules"
        class="hx-data-table"
        row-key="id"
        border
        empty-text="暂无出清折扣规则"
      >
        <ElTableColumn prop="ruleName" label="规则名称" min-width="180" />
        <ElTableColumn label="距到期小时" width="130" align="center">
          <template #default="scope">
            ≤ {{ scope.row.hoursBeforeExpire }} 小时
          </template>
        </ElTableColumn>
        <ElTableColumn label="再打折" width="120" align="center">
          <template #default="scope">
            {{ (Number(scope.row.discountRate) * 10).toFixed(1) }} 折
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="100" align="center">
          <template #default="scope">
            <ElTag :type="scope.row.enabled === '1' ? 'success' : 'info'">
              {{ scope.row.enabled === '1' ? '启用' : '停用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="sortOrder"
          label="排序"
          width="90"
          align="center"
        />
        <ElTableColumn prop="remark" label="备注" min-width="180" />
        <ElTableColumn label="操作" width="160" align="center">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'promotion:shipactivity:save'"
              @click="openDialog(scope.row)"
            >
              修改
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'promotion:shipactivity:save'"
              @click="remove(scope.row)"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </div>

    <ElDialog
      v-model="dialogVisible"
      :title="editingId ? '修改出清折扣规则' : '新增出清折扣规则'"
      width="560px"
      :close-on-click-modal="false"
    >
      <ElForm :model="form" label-width="120px">
        <ElFormItem label="规则名称" required>
          <ElInput
            v-model="form.ruleName"
            maxlength="64"
            placeholder="如：临期 48 小时 8 折"
          />
        </ElFormItem>
        <ElFormItem label="距到期小时" required>
          <ElInputNumber
            v-model="form.hoursBeforeExpire"
            :min="1"
            :precision="0"
            controls-position="right"
          />
          <span class="ml10 hint">剩余时间 ≤ 该值时命中</span>
        </ElFormItem>
        <ElFormItem label="再打折率" required>
          <ElInputNumber
            v-model="form.discountRate"
            :min="0.0001"
            :max="0.9999"
            :step="0.01"
            :precision="4"
            controls-position="right"
          />
          <span class="ml10 hint">如 0.8 = 8 折（在基础出清价上再打折）</span>
        </ElFormItem>
        <ElFormItem label="启用">
          <ElSwitch v-model="form.enabled" />
        </ElFormItem>
        <ElFormItem label="排序">
          <ElInputNumber
            v-model="form.sortOrder"
            :precision="0"
            controls-position="right"
          />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput
            v-model="form.remark"
            type="textarea"
            :rows="2"
            maxlength="255"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="submit">
          保存
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ml10 {
  margin-left: 10px;
}
</style>
