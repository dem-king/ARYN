<script lang="ts" setup name="refund">
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElCol,
  ElForm,
  ElFormItem,
  ElImage,
  ElOption,
  ElRow,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getPage } from '#/api/order/order-refund';
import { useDict } from '#/utils/dict';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);
const Refund = defineAsyncComponent(() => import('./refund.vue'));

// 字典
const { refund_status, refund_type } = useDict('refund_status', 'refund_type');
const state = reactive({
  queryParams: {
    status: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: '',
    desc: 'create_time',
  },
  tableData: [],
});
const refundRef = ref();
const loading = ref(false);
const showSearch = ref(true);
const queryRef = ref();
/**
 * 列表查询
 */
const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
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
const doRefund = (id: string) => {
  refundRef.value.initPage(id);
};
/**
 * 重置搜索表单
 */
const resetQuery = () => {
  queryRef.value.resetFields();
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
        <ElFormItem label="退款状态" prop="status">
          <ElSelect
            style="width: 200px"
            v-model="state.queryParams.status"
            clearable
            placeholder="请选择退款状态"
          >
            <ElOption
              v-for="item in refund_status"
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
          <ElButton @click="resetQuery" :icon="Refresh"> 重置 </ElButton>
        </ElFormItem>
      </ElForm>
      <!-- 工具栏 -->
      <div class="hx-table-toolbar">
        <div></div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>
      <Refund ref="refundRef" @init-page="initPage" />

      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="orderItemList" label="商品信息" width="450">
          <template #default="scope">
            <ElRow v-if="scope.row.orderItem">
              <ElCol :span="6">
                <ElImage
                  style="width: 60px; height: 60px"
                  :src="scope.row.orderItem.picUrl"
                  fit="cover"
                  :preview-teleported="true"
                />
              </ElCol>
              <ElCol :span="18">
                <div class="overflow-line-clamp-2 name">
                  {{ scope.row.orderItem.spuName }}
                </div>
                <p>{{ scope.row.orderItem.specsInfo }}</p>
                <div style="display: flex; justify-content: space-between">
                  <span style="color: red">
                    ￥{{ scope.row.orderItem.paymentPrice }}
                  </span>
                  x{{ scope.row.orderItem.buyQuantity }}
                </div>
              </ElCol>
            </ElRow>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="status" label="状态" width="220">
          <template #default="scope">
            <DictTag :options="refund_status" :value="scope.row.status" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="refundType" label="退款类型" width="220">
          <template #default="scope">
            <DictTag :options="refund_type" :value="scope.row.refundType" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="refundAmount" label="退款金额" />
        <ElTableColumn prop="refundReason" label="退款原因" />
        <ElTableColumn label="操作" width="200" align="center" fixed="right">
          <template #default="scope">
            <ElButton link type="primary" @click="doRefund(scope.row.id)">
              {{
                scope.row.status === '1'
                  ? '审核'
                  : scope.row.status === '3'
                    ? '确认收货'
                    : scope.row.status === '4'
                      ? '确认退款'
                      : '查看详情'
              }}
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
