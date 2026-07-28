<script setup lang="ts">
import type { DeliveryArea } from '#/api/order/delivery-area';

import { reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  createDeliveryArea,
  getDeliveryAreas,
  removeDeliveryArea,
  updateDeliveryArea,
} from '#/api/order/delivery-area';

const loading = ref(false);
const saving = ref(false);
const visible = ref(false);
const records = ref<DeliveryArea[]>([]);
const form = reactive<DeliveryArea>({ enabled: '1', scopeLevel: 'DISTRICT' });
const load = async () => {
  loading.value = true;
  try {
    records.value = (await getDeliveryAreas()) || [];
  } finally {
    loading.value = false;
  }
};
const open = (row?: DeliveryArea | Record<string, any>) => {
  Object.assign(
    form,
    {
      areaCode: '',
      cityCode: '',
      cityName: '',
      districtCode: '',
      districtName: '',
      enabled: '1',
      id: undefined,
      provinceCode: '',
      provinceName: '',
      scopeLevel: 'DISTRICT',
    },
    row || {},
  );
  visible.value = true;
};
const save = async () => {
  const selectedCode =
    form.scopeLevel === 'CITY' ? form.cityCode : form.districtCode;
  if (!selectedCode) {
    ElMessage.warning('请填写配送范围编码');
    return;
  }
  form.areaCode = selectedCode;
  saving.value = true;
  try {
    await (form.id
      ? updateDeliveryArea(form.id, form)
      : createDeliveryArea(form));
    ElMessage.success('保存成功');
    visible.value = false;
    await load();
  } finally {
    saving.value = false;
  }
};
const remove = async (row: any) => {
  await ElMessageBox.confirm(
    '删除后该地区将不能选择商城配送，确认继续？',
    '删除配送范围',
    { type: 'warning' },
  );
  await removeDeliveryArea(row.id!);
  ElMessage.success('删除成功');
  await load();
};
load();
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view area-page">
      <div class="heading">
        <div>
          <h2>商城配送范围</h2>
          <p>区县规则优先于城市规则；停用规则保留配置但不参与结算判断。</p>
        </div>
        <div>
          <ElButton :icon="Refresh" @click="load">刷新</ElButton
          ><ElButton
            type="primary"
            :icon="Plus"
            v-access:code="'order:delivery:area'"
            @click="open()"
          >
            新增范围
          </ElButton>
        </div>
      </div>
      <ElTable v-loading="loading" :data="records" row-key="id">
        <ElTableColumn label="层级" width="100">
          <template #default="{ row }">
            <ElTag>
              {{ row.scopeLevel === 'CITY' ? '城市' : '区县' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="省份" prop="provinceName" min-width="130" />
        <ElTableColumn label="城市" prop="cityName" min-width="130" />
        <ElTableColumn label="区县" min-width="150">
          <template #default="{ row }">
            {{ row.districtName || '全市' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="范围编码" prop="areaCode" min-width="140" />
        <ElTableColumn label="状态" width="100">
          <template #default="{ row }">
            <ElTag :type="row.enabled === '1' ? 'success' : 'info'">
              {{ row.enabled === '1' ? '启用' : '停用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <ElButton
              link
              type="primary"
              :icon="Edit"
              v-access:code="'order:delivery:area'"
              @click="open(row)"
            >
              编辑
            </ElButton>
            <ElButton
              link
              type="danger"
              :icon="Delete"
              v-access:code="'order:delivery:area'"
              @click="remove(row)"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <ElDialog
        v-model="visible"
        :title="form.id ? '编辑配送范围' : '新增配送范围'"
        width="560px"
      >
        <ElForm label-position="top" :model="form">
          <ElFormItem label="规则层级">
            <ElSelect v-model="form.scopeLevel" class="w-full">
              <ElOption label="区县" value="DISTRICT" /><ElOption
                label="城市"
                value="CITY"
              />
            </ElSelect>
          </ElFormItem>
          <div class="form-grid">
            <ElFormItem label="省份名称">
              <ElInput v-model="form.provinceName" />
            </ElFormItem>
            <ElFormItem label="省份编码">
              <ElInput v-model="form.provinceCode" />
            </ElFormItem>
            <ElFormItem label="城市名称">
              <ElInput v-model="form.cityName" />
            </ElFormItem>
            <ElFormItem label="城市编码" required>
              <ElInput v-model="form.cityCode" />
            </ElFormItem>
            <ElFormItem v-if="form.scopeLevel === 'DISTRICT'" label="区县名称">
              <ElInput v-model="form.districtName" />
            </ElFormItem>
            <ElFormItem
              v-if="form.scopeLevel === 'DISTRICT'"
              label="区县编码"
              required
            >
              <ElInput v-model="form.districtCode" />
            </ElFormItem>
          </div>
          <ElFormItem label="启用">
            <ElSwitch
              v-model="form.enabled"
              active-value="1"
              inactive-value="0"
            />
          </ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="visible = false">取消</ElButton
          ><ElButton type="primary" :loading="saving" @click="save">
            保存
          </ElButton>
        </template>
      </ElDialog>
    </div>
  </div>
</template>

<style scoped lang="scss">
.area-page {
  gap: 20px;
}

.heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.heading h2 {
  margin: 0;
  font-size: 20px;
}

.heading p {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
</style>
