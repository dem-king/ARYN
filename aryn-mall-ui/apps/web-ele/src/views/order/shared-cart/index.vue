<script lang="ts" setup>
import { defineAsyncComponent, onMounted, reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
} from 'element-plus';

import {
  getSharedCartDetail,
  getSharedCartPage,
} from '#/api/order/shared-cart';

const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const state = reactive({
  page: { total: 0, currentPage: 1, pageSize: 10 },
  query: { cartNo: '', status: '' },
  tableData: [] as any[],
});
const loading = ref(false);

const detailVisible = ref(false);
const detail = ref<any>({ cart: {}, members: [], items: [] });

const statusLabel: Record<string, string> = {
  '1': '草稿',
  '2': '收集中',
  '3': '待确认',
  '4': '已提交',
  '5': '已关闭',
  '6': '已完成（已送达）',
};
const itemStatusLabel: Record<string, string> = {
  '1': '待确认',
  '2': '已确认',
  '3': '已移除',
};

const initPage = async () => {
  loading.value = true;
  try {
    const response = await getSharedCartPage({
      current: state.page.currentPage,
      size: state.page.pageSize,
      cartNo: state.query.cartNo,
      status: state.query.status,
    });
    state.tableData = response.records;
    state.page.total = response.total;
  } finally {
    loading.value = false;
  }
};

const openDetail = (row: any) => {
  getSharedCartDetail(row.id)
    .then((data) => {
      detail.value = data;
      detailVisible.value = true;
    })
    .catch(() => {});
};

onMounted(initPage);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElForm :inline="true" :model="state.query">
        <ElFormItem label="购物车编号">
          <ElInput v-model="state.query.cartNo" clearable placeholder="SC..." />
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
        </ElFormItem>
      </ElForm>

      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="cartNo" label="编号" width="200" />
        <ElTableColumn prop="vesselCallId" label="靠港计划" min-width="160" />
        <ElTableColumn prop="ownerUserId" label="发起人" min-width="140" />
        <ElTableColumn label="状态" width="90">
          <template #default="scope">
            {{ statusLabel[scope.row.status] ?? scope.row.status }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="expiresAt" label="收集截止" width="170" />
        <ElTableColumn prop="submitOrderId" label="生成订单" min-width="160" />
        <ElTableColumn label="操作" width="80">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'sharedcart:get'"
              @click="openDetail(scope.row)"
            >
              详情
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

      <ElDialog v-model="detailVisible" title="共享购物车详情" width="900px">
        <ElTabs>
          <ElTabPane label="成员" name="members">
            <ElTable :data="detail.members" border>
              <ElTableColumn label="用户" min-width="160">
                <template #default="scope">
                  <span v-if="scope.row.displayName">{{
                    scope.row.displayName
                  }}</span>
                  <span v-else>{{ scope.row.userId }}</span>
                </template>
              </ElTableColumn>
              <ElTableColumn label="角色" width="110">
                <template #default="scope">
                  {{
                    scope.row.memberRole === '1'
                      ? '发起人'
                      : scope.row.memberRole === '3'
                        ? '确认人'
                        : '成员'
                  }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="可确认" width="80">
                <template #default="scope">
                  {{ scope.row.canConfirm === '1' ? '是' : '否' }}
                </template>
              </ElTableColumn>
              <ElTableColumn prop="joinedTime" label="加入时间" width="170" />
            </ElTable>
          </ElTabPane>
          <ElTabPane label="明细" name="items">
            <ElTable :data="detail.items" border>
              <ElTableColumn prop="spuId" label="SPU" min-width="140" />
              <ElTableColumn prop="skuId" label="SKU" min-width="140" />
              <ElTableColumn
                prop="requestedQuantity"
                label="申请数量"
                width="90"
                align="center"
              />
              <ElTableColumn
                prop="approvedQuantity"
                label="核定数量"
                width="90"
                align="center"
              />
              <ElTableColumn
                prop="memberRemark"
                label="成员备注"
                min-width="140"
              />
              <ElTableColumn label="状态" width="90">
                <template #default="scope">
                  {{ itemStatusLabel[scope.row.status] ?? scope.row.status }}
                </template>
              </ElTableColumn>
              <ElTableColumn prop="userId" label="来源成员" min-width="140" />
            </ElTable>
          </ElTabPane>
        </ElTabs>
      </ElDialog>
    </div>
  </div>
</template>
