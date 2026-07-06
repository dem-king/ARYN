<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElImage,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { delObj, getPage } from '#/api/promotion/points-goods';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const Form = defineAsyncComponent(() => import('./form.vue'));

const queryRef = ref<FormInstance>();

const state = reactive({
  queryParams: {
    name: '',
    type: '',
    status: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    desc: 'create_time',
  },
  tableData: [] as any[],
});
const showSearch = ref(true);
const loading = ref(false);
const formRef = ref();

const typeOptions = [
  { label: '实物商品', value: 'goods' },
  { label: '优惠券', value: 'coupon' },
  { label: '赠品', value: 'gift' },
];

const statusOptions = [
  { label: '已上架', value: '0' },
  { label: '已下架', value: '1' },
];

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

const resetQuery = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  formEl.resetFields();
};

const handleAdd = () => {
  formRef.value.initForm();
};

const handleEdit = (row: any) => {
  formRef.value.initForm(row);
};

const handleDelete = (id: string) => {
  ElMessageBox.confirm('此操作将删除该积分商品，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    delObj(id)
      .then(() => {
        ElMessage.success('删除成功');
        initPage();
      })
      .catch(() => {});
  });
};

const typeLabel = (type: string) => {
  const found = typeOptions.find((item) => item.value === type);
  return found ? found.label : type;
};

const statusTagType = (status: string) => {
  if (status === '0') return 'success';
  if (status === '1') return 'info';
  return 'info';
};

const statusLabel = (status: string) => {
  const found = statusOptions.find((item) => item.value === status);
  return found ? found.label : status;
};

initPage();
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索 -->
      <ElForm
        :model="state.queryParams"
        ref="queryRef"
        :inline="true"
        v-show="showSearch"
      >
        <ElFormItem label="商品名称" prop="name">
          <ElInput
            v-model="state.queryParams.name"
            clearable
            style="width: 200px"
            placeholder="请输入商品名称"
          />
        </ElFormItem>
        <ElFormItem label="商品类型" prop="type">
          <ElSelect
            v-model="state.queryParams.type"
            clearable
            style="width: 200px"
            placeholder="请选择商品类型"
          >
            <ElOption
              v-for="item in typeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="状态" prop="status">
          <ElSelect
            v-model="state.queryParams.status"
            clearable
            style="width: 200px"
            placeholder="请选择状态"
          >
            <ElOption
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage" :icon="Search">
            搜索
          </ElButton>
          <ElButton @click="resetQuery(queryRef)" :icon="Refresh">
            重置
          </ElButton>
        </ElFormItem>
      </ElForm>
      <Form ref="formRef" @init-page="initPage" />
      <!-- 工具栏 -->
      <div class="hx-table-toolbar">
        <div>
          <ElButton
            type="primary"
            @click="handleAdd"
            v-access:code="'promotion:pointsgoods:add'"
            :icon="Plus"
          >
            新增
          </ElButton>
        </div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>
      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn
          prop="name"
          label="商品名称"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn prop="cover" label="封面" align="center" width="80">
          <template #default="scope">
            <ElImage
              v-if="scope.row.cover"
              :src="scope.row.cover"
              style="width: 40px; height: 40px"
              fit="cover"
              :preview-src-list="[scope.row.cover]"
              :preview-teleported="true"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="type" label="商品类型" align="center" width="100">
          <template #default="scope">
            {{ typeLabel(scope.row.type) }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="pointsPrice"
          label="积分价格"
          align="center"
          width="100"
        />
        <ElTableColumn prop="stock" label="库存" align="center" width="80" />
        <ElTableColumn
          prop="limitPerUser"
          label="限购"
          align="center"
          width="80"
        >
          <template #default="scope">
            {{ scope.row.limitPerUser > 0 ? scope.row.limitPerUser : '不限' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="活动时间" align="center" width="180">
          <template #default="scope">
            {{ scope.row.startTime }}<br />
            {{ scope.row.endTime }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="status" label="状态" align="center" width="80">
          <template #default="scope">
            <ElTag :type="statusTagType(scope.row.status)">
              {{ statusLabel(scope.row.status) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" width="180" />
        <ElTableColumn label="操作" width="200" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              @click="handleEdit(scope.row)"
              v-access:code="'promotion:pointsgoods:edit'"
              :icon="Edit"
            >
              修改
            </ElButton>
            <ElButton
              link
              type="danger"
              @click="handleDelete(scope.row.id)"
              v-access:code="'promotion:pointsgoods:del'"
              :icon="Delete"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <!-- 分页 -->
      <Pagination
        :total="state.page.total"
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        @change="initPage"
      />
    </div>
  </div>
</template>
