<script lang="ts" setup>
import type { PointsExchange } from '#/api/user/points-config';

import { onMounted, reactive, ref } from 'vue';

import { Delete, Plus } from '@element-plus/icons-vue';
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
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getPage as getCouponPage } from '#/api/promotion/coupon-info';
import {
  delExchange,
  listExchange,
  saveExchange,
} from '#/api/user/points-config';

const loading = ref(false);
const saving = ref(false);
const tableData = ref<PointsExchange[]>([]);
const couponOptions = ref<any[]>([]);

const dialogVisible = ref(false);
const formRef = ref();
// 本地表单字段非空化（status/iconUrl 允许 null，但 v-model 绑定不接受 null）
const form = reactive({
  id: '',
  title: '',
  iconUrl: '',
  pointsCost: 100,
  couponId: '',
  perUserLimit: 1,
  status: '0',
  sortOrder: 0,
});

const rules = reactive({
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  pointsCost: [{ required: true, message: '请输入所需积分', trigger: 'blur' }],
  couponId: [
    { required: true, message: '请选择优惠券模板', trigger: 'change' },
  ],
});

const loadCouponOptions = async (selectedId?: string) => {
  try {
    const res: any = await getCouponPage({
      current: 1,
      size: 200,
      status: '0',
    });
    couponOptions.value = res?.records ?? [];
  } catch {
    couponOptions.value = [];
  }
  if (
    selectedId &&
    !couponOptions.value.some((item: any) => item.id === selectedId)
  ) {
    couponOptions.value.unshift({
      id: selectedId,
      couponName: `已选模板 ${selectedId}`,
      remainNum: null,
    });
  }
};

const initList = async () => {
  loading.value = true;
  try {
    tableData.value = ((await listExchange()) ?? []) as PointsExchange[];
  } finally {
    loading.value = false;
  }
};

const openAdd = () => {
  Object.assign(form, {
    id: '',
    title: '',
    iconUrl: '',
    pointsCost: 100,
    couponId: '',
    perUserLimit: 1,
    status: '0',
    sortOrder: 0,
  });
  loadCouponOptions();
  dialogVisible.value = true;
};

const openEdit = (row: any) => {
  Object.assign(form, {
    id: row.id ?? '',
    title: row.title,
    iconUrl: row.iconUrl ?? '',
    pointsCost: row.pointsCost,
    couponId: row.couponId,
    perUserLimit: row.perUserLimit ?? 0,
    status: row.status ?? '0',
    sortOrder: row.sortOrder ?? 0,
  });
  loadCouponOptions(row.couponId);
  dialogVisible.value = true;
};

const handleSubmit = async () => {
  await formRef.value.validate();
  saving.value = true;
  try {
    await saveExchange({ ...form } as PointsExchange);
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    await initList();
  } finally {
    saving.value = false;
  }
};

const handleDelete = (row: any) => {
  ElMessageBox.confirm(
    `删除后「${row.title}」将从小程序积分兑换下架，已兑换记录不受影响，是否继续?`,
    '提示',
    { confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning' },
  )
    .then(() => {
      delExchange(row.id as string)
        .then(() => {
          ElMessage.success('删除成功');
          initList();
        })
        .catch(() => {});
    })
    .catch(() => {});
};

onMounted(() => {
  initList();
});
</script>
<template>
  <div>
    <!-- 工具栏 -->
    <div class="mb-2 flex items-center justify-between">
      <div style="font-size: 13px; color: #6b7a8f">
        积分兑换优惠券：兑换 = 扣积分 +
        发券到账户（每人限兑可控，券可定向品类与门槛）
      </div>
      <ElButton
        type="primary"
        v-access:code="'user:pointsconfig:edit'"
        @click="openAdd"
        :icon="Plus"
      >
        新增兑换项
      </ElButton>
    </div>
    <!-- 列表 -->
    <ElTable
      v-loading="loading"
      class="hx-data-table"
      :data="tableData"
      border
      empty-text="暂无兑换项，小程序积分页将隐藏兑换区块"
    >
      <ElTableColumn prop="sortOrder" label="排序" width="70" align="center" />
      <ElTableColumn prop="title" label="标题" min-width="140" />
      <ElTableColumn label="所需积分" width="100" align="center">
        <template #default="scope"> {{ scope.row.pointsCost }} 分 </template>
      </ElTableColumn>
      <ElTableColumn label="优惠券模板" min-width="180">
        <template #default="scope">
          {{ scope.row.couponId }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="每人限兑" width="100" align="center">
        <template #default="scope">
          {{
            scope.row.perUserLimit && scope.row.perUserLimit > 0
              ? `${scope.row.perUserLimit} 次`
              : '不限'
          }}
        </template>
      </ElTableColumn>
      <ElTableColumn label="状态" width="80" align="center">
        <template #default="scope">
          <ElTag v-if="scope.row.status === '0'" type="success">启用</ElTag>
          <ElTag v-else type="info">下架</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn label="操作" width="150" align="center">
        <template #default="scope">
          <ElButton
            link
            type="primary"
            v-access:code="'user:pointsconfig:edit'"
            @click="openEdit(scope.row)"
          >
            编辑
          </ElButton>
          <ElButton
            link
            type="danger"
            v-access:code="'user:pointsconfig:del'"
            @click="handleDelete(scope.row)"
            :icon="Delete"
          >
            删除
          </ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <!-- 新增/编辑弹窗 -->
    <ElDialog
      v-model="dialogVisible"
      :title="form.id ? '编辑兑换项' : '新增兑换项'"
      width="520px"
    >
      <ElForm ref="formRef" :model="form" :rules="rules" label-width="100px">
        <ElFormItem label="标题" prop="title">
          <ElInput v-model="form.title" placeholder="如：5 元无门槛券" />
        </ElFormItem>
        <ElFormItem label="所需积分" prop="pointsCost">
          <ElInputNumber v-model="form.pointsCost" :min="1" :step="100" />
        </ElFormItem>
        <ElFormItem label="优惠券模板" prop="couponId">
          <ElSelect
            v-model="form.couponId"
            filterable
            placeholder="请选择优惠券模板（仅启用模板）"
            style="width: 100%"
          >
            <ElOption
              v-for="coupon in couponOptions"
              :key="coupon.id"
              :label="`${coupon.couponName}（剩余 ${coupon.remainNum ?? 0}）`"
              :value="coupon.id"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="每人限兑">
          <ElInputNumber v-model="form.perUserLimit" :min="0" :step="1" />
          <div class="form-tip">0 表示不限次数</div>
        </ElFormItem>
        <ElFormItem label="状态">
          <ElRadioGroup v-model="form.status">
            <ElRadio value="0">启用</ElRadio>
            <ElRadio value="1">下架</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="排序号">
          <ElInputNumber v-model="form.sortOrder" :min="0" :step="1" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="saving" @click="handleSubmit">
          保存
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.form-tip {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.4;
  color: #909399;
}
</style>
