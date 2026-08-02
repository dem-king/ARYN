<script lang="ts" setup name="deliveryArea">
import {
  ElButton,
  ElCard,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElPagination,
  ElTable,
  ElTableColumn,
  ElTag,
  ElDialog,
  ElSwitch,
} from 'element-plus';

import { onMounted, reactive, ref } from 'vue';

import {
  createDeliveryArea,
  deleteDeliveryArea,
  getDeliveryAreaPage,
  updateDeliveryArea,
} from '#/api/delivery/area';

const loading = ref(false);
const tableData = ref<any[]>([]);
const total = ref(0);
const dialogVisible = ref(false);
const dialogTitle = ref('新增配送范围');
const submitting = ref(false);

const queryParams = reactive({
  current: 1,
  size: 10,
});

const form = reactive({
  id: undefined as string | undefined,
  provinceCode: '',
  provinceName: '',
  cityCode: '',
  cityName: '',
  areaCode: '',
  areaName: '',
  enabled: '1',
});

const statusMap: Record<string, { label: string; type: any }> = {
  '1': { label: '启用', type: 'success' },
  '0': { label: '禁用', type: 'info' },
};

const loadData = () => {
  loading.value = true;
  getDeliveryAreaPage(queryParams)
    .then((res: any) => {
      tableData.value = res?.records ?? [];
      total.value = res?.total ?? 0;
    })
    .finally(() => {
      loading.value = false;
    });
};

const handleAdd = () => {
  dialogTitle.value = '新增配送范围';
  Object.assign(form, {
    id: undefined,
    provinceCode: '',
    provinceName: '',
    cityCode: '',
    cityName: '',
    areaCode: '',
    areaName: '',
    enabled: '1',
  });
  dialogVisible.value = true;
};

const handleEdit = (row: any) => {
  dialogTitle.value = '编辑配送范围';
  Object.assign(form, row);
  dialogVisible.value = true;
};

const handleDelete = (row: any) => {
  ElMessageBox.confirm('确认删除该配送范围？', '提示', {
    type: 'warning',
  }).then(() => {
    deleteDeliveryArea(row.id).then(() => {
      ElMessage.success('删除成功');
      loadData();
    });
  });
};

const handleSubmit = () => {
  if (!form.provinceName) {
    ElMessage.warning('请至少填写省名称');
    return;
  }
  submitting.value = true;
  const action = form.id ? updateDeliveryArea(form) : createDeliveryArea(form);
  action
    .then(() => {
      ElMessage.success('保存成功');
      dialogVisible.value = false;
      loadData();
    })
    .finally(() => {
      submitting.value = false;
    });
};

const handlePageChange = (page: number) => {
  queryParams.current = page;
  loadData();
};

onMounted(() => {
  loadData();
});
</script>
<template>
  <div class="delivery-area-root">
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElCard v-loading="loading">
        <template #header>
          <div class="card-header">
            <span>配送范围管理</span>
            <ElButton type="primary" @click="handleAdd">新增</ElButton>
          </div>
        </template>
        <ElTable :data="tableData" border style="width: 100%">
          <ElTableColumn label="省" prop="provinceName" />
          <ElTableColumn label="市" prop="cityName" />
          <ElTableColumn label="区县" prop="areaName" />
          <ElTableColumn label="状态" width="100">
            <template #default="{ row }">
              <ElTag :type="statusMap[row.enabled]?.type || 'info'">
                {{ statusMap[row.enabled]?.label || '未知' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="创建时间" prop="createTime" width="180" />
          <ElTableColumn label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <ElButton type="primary" link @click="handleEdit(row)">编辑</ElButton>
              <ElButton type="danger" link @click="handleDelete(row)">删除</ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
        <ElPagination
          :current-page="queryParams.current"
          :page-size="queryParams.size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="handlePageChange"
        />
      </ElCard>
    </div>
  </div>

  <ElDialog v-model="dialogVisible" :title="dialogTitle" width="500px">
    <ElForm :model="form" label-width="100px">
      <ElFormItem label="省编码">
        <ElInput v-model="form.provinceCode" placeholder="省编码" />
      </ElFormItem>
      <ElFormItem label="省名称">
        <ElInput v-model="form.provinceName" placeholder="省名称" />
      </ElFormItem>
      <ElFormItem label="市编码">
        <ElInput v-model="form.cityCode" placeholder="市编码" />
      </ElFormItem>
      <ElFormItem label="市名称">
        <ElInput v-model="form.cityName" placeholder="市名称" />
      </ElFormItem>
      <ElFormItem label="区县编码">
        <ElInput v-model="form.areaCode" placeholder="区县编码" />
      </ElFormItem>
      <ElFormItem label="区县名称">
        <ElInput v-model="form.areaName" placeholder="区县名称" />
      </ElFormItem>
      <ElFormItem label="启用状态">
        <ElSwitch
          v-model="form.enabled"
          active-value="1"
          inactive-value="0"
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton @click="dialogVisible = false">取消</ElButton>
      <ElButton type="primary" :loading="submitting" @click="handleSubmit">保存</ElButton>
    </template>
  </ElDialog>
  </div>
</template>
<style lang="scss" scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>