<script setup lang="ts" name="selectGoods">
import type { TableInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElButton,
  ElCol,
  ElDialog,
  ElImage,
  ElMessage,
  ElMessageBox,
  ElRow,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getWarehousePage as getPage } from '#/api/product/goods-spu';

const props = defineProps({
  limitNum: {
    type: Number,
    default: 1,
  },
});
const emit = defineEmits(['currentRow']);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const tableRef = ref<TableInstance>();
const state = reactive<any>({
  dialog: false,
  loading: false,
  page: {
    total: 0, // 总页数
    currentPage: 1, // 当前页数
    pageSize: 20, // 每页显示多少条
  },
  queryParams: {
    groupId: '',
  },
  tableData: [],
  currentList: [], // 选中的商品
  maxHeight: window.screen.availHeight - 500,
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
    status: '1',
    verifyStatus: '1',
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
/**
 * 选择商品
 */
const handleCurrentChange = (val: any) => {
  if (val && val.length > props.limitNum) {
    tableRef.value!.clearSelection();
    state.currentList = [];
    return ElMessage.error(`最多只能选择【${props.limitNum}】件商品`);
  }
  state.currentList = val;
};
const onSubmit = () => {
  if (state.currentList.length <= 0) {
    return ElMessage.error('请选择商品');
  }
  if (state.currentList.length > props.limitNum) {
    return ElMessage.error(`最多只能选择【${props.limitNum}】件商品`);
  }
  const message = `已选择 【${state.currentList[0].name}】等${
    state.currentList.length
  }件商品, 是否继续?`;
  ElMessageBox.confirm(message, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    emit('currentRow', state.currentList);
    state.dialog = false;
  });
};
defineExpose({
  initPage,
});
</script>
<template>
  <div>
    <ElDialog v-model="state.dialog" width="80%" title="商品选择器">
      <ElTable
        ref="tableRef"
        v-loading="state.loading"
        :data="state.tableData"
        highlight-current-row
        @selection-change="handleCurrentChange"
        :height="state.maxHeight"
      >
        <ElTableColumn type="selection" width="55" />
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
          <template #default="scope">
            <span style="color: red">￥{{ scope.row.salesPrice }}</span>
          </template>
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
            <ElTag v-if="scope.row.status === '1'" type="success">
              已上架
            </ElTag>
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
      <template #footer>
        <span class="dialog-footer">
          <ElButton @click="state.dialog = false">关闭</ElButton>
          <ElButton type="primary" @click="onSubmit"> 确 认 </ElButton>
        </span>
      </template>
    </ElDialog>
  </div>
</template>
