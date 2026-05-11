<script lang="ts" setup>
import type { TableInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElCol,
  ElImage,
  ElRadio,
  ElRow,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getWarehousePage as getPage } from '#/api/product/goods-spu';

defineProps({
  // 双向绑定值，默认为 modelValue，
  modelValue: {
    type: Object,
    default: null,
  },
});
const emit = defineEmits(['update:modelValue']);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const selectedRow = ref<number>();
const tableRef = ref<TableInstance>();
const state = reactive({
  dialog: false,
  loading: false,
  page: {
    total: 0, // 总页数
    currentPage: 1, // 当前页数
    pageSize: 10, // 每页显示多少条
  },
  queryParams: {
    groupId: '',
  },
  tableData: [],
  currentList: [], // 选中的商品
});
/**
 * 商品分页列表
 */
const initPage = () => {
  state.dialog = true;
  state.loading = true;
  getPage({
    current: state.page.currentPage,
    size: state.page.pageSize,
  })
    .then((response) => {
      state.loading = false;
      state.tableData = response.records;
      state.page.total = response.total;
    })
    .catch(() => {
      state.loading = false;
    });
};

function handleRowClick(row: any) {
  // 当点击一行时，设置对应的单选按钮为选中状态
  if (selectedRow.value !== row.id) {
    selectedRow.value = row.id;
    emit('update:modelValue', {
      name: row.name,
      url: `/pages/product/goods-detail/index?id=${row.id}`,
    });
  }
}

function handleRadioChange(row: any) {
  // 单选按钮变化时，更新 selectedRow
  selectedRow.value = row.id;
  emit('update:modelValue', {
    name: row.name,
    url: `/pages/product/goods-detail/index?id=${row.id}`,
  });
}
initPage();
</script>
<template>
  <div>
    <ElTable
      ref="tableRef"
      v-loading="state.loading"
      :data="state.tableData"
      highlight-current-row
      @row-click="handleRowClick"
    >
      <ElTableColumn label="" align="center" width="50">
        <template #default="scope">
          <ElRadio
            v-model="selectedRow"
            :value="scope.row.id"
            @change="handleRadioChange(scope.row)"
          />
        </template>
      </ElTableColumn>
      <ElTableColumn prop="spu" label="商品信息" width="300">
        <template #default="scope">
          <ElRow>
            <ElCol :span="6">
              <ElImage
                style="width: 50px; height: 50px"
                :src="scope.row.spuUrls[0]"
                :preview-src-list="scope.row.spuUrls"
                fit="cover"
                :preview-teleported="true"
              />
            </ElCol>
            <ElCol :span="18" style="float: left">
              <span class="overflow-line-clamp-2">{{ scope.row.name }}</span>
            </ElCol>
          </ElRow>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="categoryName" label="商品类目" align="center" />
      <ElTableColumn
        prop="salesPrice"
        label="价格（元）"
        align="center"
        width="160"
      >
        <template #default="scope"> ￥{{ scope.row.salesPrice }} </template>
      </ElTableColumn>
      <ElTableColumn prop="categoryName" label="规格" align="center">
        <template #default="scope">
          <ElTag v-if="scope.row.enableSpecs === '0'" type="danger">
            单规格
          </ElTag>
          <ElTag v-if="scope.row.enableSpecs === '1'" type="success">
            多规格
          </ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="status" label="商品状态">
        <template #default="scope">
          <ElTag v-if="scope.row.status === '0'" type="danger">已下架</ElTag>
          <ElTag v-if="scope.row.status === '1'" type="success">已上架</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="createTime" label="创建时间" />
    </ElTable>
    <!-- 分页 -->
    <Pagination
      :total="state.page.total"
      v-model:current="state.page.currentPage"
      v-model:size="state.page.pageSize"
      @change="initPage"
    />
  </div>
</template>
<style lang="scss" scoped>
:deep(.el-table .el-radio__label) {
  display: none !important;
}
</style>
