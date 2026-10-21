<script lang="ts" setup name="refund">
import {
  defineAsyncComponent,
  nextTick,
  onMounted,
  reactive,
  ref,
  watch,
} from 'vue';
import { useRoute } from 'vue-router';

import { Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElImage,
  ElOption,
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
const route = useRoute();

const applyRouteStatus = () => {
  const raw = route.query.status;
  const status = Array.isArray(raw) ? raw[0] : raw;
  const next = typeof status === 'string' ? status : '';
  if (state.queryParams.status === next) {
    return false;
  }
  state.queryParams.status = next;
  state.page.currentPage = 1;
  return true;
};

/** 深链已消费的退款单 ID：避免异步子组件就绪与 query 监听重复打开同一弹窗。 */
const openedRouteRefundId = ref<string>();
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
/**
 * 站内信等入口可能带 `?refundId=xxx` 直达退单详情。
 *
 * 只消费 query、不改变列表加载口径：列表仍由 onMounted 的 initPage() 拉取；
 * 详情弹窗是异步组件，实例未就绪时先不动，等 watch 在挂载后补一次，
 * 否则用户会看到「URL 对了但弹窗没打开」。
 */
const openRouteRefund = () => {
  const raw = route.query.refundId;
  const queryRefundId = Array.isArray(raw) ? raw[0] : raw;
  const refundId =
    typeof queryRefundId === 'string' ? queryRefundId.trim() : '';
  if (!refundId || openedRouteRefundId.value === refundId) {
    return;
  }
  if (!refundRef.value?.initPage) {
    return;
  }
  openedRouteRefundId.value = refundId;
  refundRef.value.initPage(refundId);
};

onMounted(async () => {
  // 首屏列表加载行为与原先顶层 initPage() 保持一致
  applyRouteStatus();
  initPage();
  await nextTick();
  openRouteRefund();
});

// 异步子组件加载完成后再补一次深链打开
watch(refundRef, () => void nextTick(openRouteRefund));
// 同一页面内 query 变化（例如从不同通知再次跳转）也要打开对应退单
watch(
  () => route.query.refundId,
  () => void nextTick(openRouteRefund),
);
watch(
  () => route.query.status,
  () => {
    if (applyRouteStatus()) {
      initPage();
    }
  },
);
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
            <div v-if="scope.row.orderItem" class="order-item">
              <ElImage
                style="width: 60px; height: 60px"
                :src="scope.row.orderItem.picUrl"
                fit="cover"
                :preview-teleported="true"
              />
              <div class="main">
                <div class="name line-clamp-2">
                  {{ scope.row.orderItem.spuName }}
                </div>
                <p>{{ scope.row.orderItem.specsInfo }}</p>
                <div style="display: flex; justify-content: space-between">
                  <span style="color: red">
                    ￥{{ scope.row.orderItem.paymentPrice }}
                  </span>
                  x{{ scope.row.orderItem.buyQuantity }}
                </div>
              </div>
            </div>
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
<style lang="scss" scoped>
/* 图片固定宽、文字区自适应：避免窄列下图片压住商品名 */
.order-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;

  .main {
    min-width: 0;
  }

  .name {
    overflow-wrap: anywhere;
  }
}
</style>
