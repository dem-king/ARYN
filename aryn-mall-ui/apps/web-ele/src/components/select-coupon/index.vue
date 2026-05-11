<script setup lang="ts" name="selectCoupon">
import type { TableInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getPage } from '#/api/promotion/coupon-info';
import { useDict } from '#/utils/dict';

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
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);
const { status } = useDict('status');
const tableRef = ref<TableInstance>();
const state = reactive<any>({
  dialog: false,
  loading: false,
  page: {
    total: 0, // 总页数
    currentPage: 1, // 当前页数
    pageSize: 20, // 每页显示多少条
  },
  queryParams: {},
  tableData: [],
  currentList: [], // 选中的商品
  maxHeight: window.screen.availHeight - 500,
});
/**
 * 素材分页列表
 */
const initPage = () => {
  state.dialog = true;
  state.loading = true;
  getPage(
    Object.assign(
      {
        current: state.page.currentPage,
        size: state.page.pageSize,
      },
      state.queryParams,
    ),
  )
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
 * 选择优惠券
 */
const handleCurrentChange = (val: any) => {
  if (val && val.length > props.limitNum) {
    tableRef.value!.clearSelection();
    state.currentList = [];
    return ElMessage.error(`最多只能选择【${props.limitNum}】个优惠券`);
  }
  state.currentList = val;
};
const onSubmit = () => {
  if (state.currentList.length <= 0) {
    return ElMessage.error('请选择优惠券');
  }
  if (state.currentList.length > props.limitNum) {
    return ElMessage.error(`最多只能选择【${props.limitNum}】个优惠券`);
  }
  const message = `已选择 【${state.currentList[0].couponName}】等${
    state.currentList.length
  }个优惠券, 是否继续?`;
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
    <ElDialog v-model="state.dialog" width="80%" title="优惠券选择器">
      <ElTable
        ref="tableRef"
        v-loading="state.loading"
        :data="state.tableData"
        highlight-current-row
        @selection-change="handleCurrentChange"
        :max-height="state.maxHeight"
      >
        <ElTableColumn type="selection" width="55" />
        <ElTableColumn
          prop="couponName"
          label="优惠券名称"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn
          prop="picUrl"
          label="有效时间"
          align="center"
          show-overflow-tooltip
        >
          <template #default="scope">
            {{ scope.row.receiveStartedAt }}-
            {{ scope.row.receiveEndedAt }}
          </template>
        </ElTableColumn>

        <ElTableColumn prop="couponType" label="优惠形式" align="center">
          <template #default="scope">
            <span v-if="scope.row.couponType === '1'" type="danger">
              指定金额:{{ scope.row.amount }}元
            </span>
            <span v-if="scope.row.couponType === '2'" type="success">
              {{ scope.row.discount }}折
            </span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="useRange" label="可用范围" align="center">
          <template #default="scope">
            <ElTag v-if="scope.row.useRange === '1'" type="success">
              全部商品
            </ElTag>
            <ElTag v-if="scope.row.useRange === '2'" type="danger">
              指定商品
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="totalNum" label="发行数量" align="center" />
        <ElTableColumn prop="assignCount" label="已发放数量" align="center" />
        <ElTableColumn prop="usedCount" label="已使用数量" align="center" />
        <ElTableColumn prop="remainNum" label="剩余数量" align="center" />
        <ElTableColumn prop="status" label="状态" align="center">
          <template #default="scope">
            <DictTag :options="status" :value="scope.row.status" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="创建时间" width="180" />
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
