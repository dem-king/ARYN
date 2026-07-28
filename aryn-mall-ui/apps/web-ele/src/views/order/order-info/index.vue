<script lang="ts" setup name="order">
import type { FormInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import {
  ArrowRightBold,
  Box,
  Delete,
  Refresh,
  Search,
  Van,
  View,
} from '@element-plus/icons-vue';
import {
  ElButton,
  ElCol,
  ElDatePicker,
  ElForm,
  ElFormItem,
  ElIcon,
  ElImage,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPopover,
  ElRow,
  ElSegmented,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getOrderFulfillmentAction } from '#/api/order/delivery-task';
import {
  cancelObj,
  delObj,
  getPage,
  selffetchObj,
} from '#/api/order/order-info';
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

const Deliver = defineAsyncComponent(() => import('./deliver/index.vue'));

// 字典
const queryRef = ref<FormInstance>();
const { pay_type, delivery_way, order_status, order_item_status } = useDict(
  'pay_type',
  'delivery_way',
  'order_status',
  'order_item_status',
);
const state = reactive({
  queryParams: {
    deliveryWay: '',
    payType: '',
    paymentQueryTimes: '',
    orderNo: '',
    recipientName: '',
    recipientPhone: '',
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
const activeTypeOptions = ref([
  {
    value: '',
    label: '全部',
  },
  {
    value: '1',
    label: '待付款',
  },
  {
    value: '2',
    label: '待发货',
  },
  {
    value: '3',
    label: '待收货/待自提',
  },
  {
    value: '4',
    label: '已完成',
  },
  {
    value: '11',
    label: '交易已关闭',
  },
]);

const shortcuts = [
  {
    text: '最近一周',
    value: () => {
      const end = new Date();
      const start = new Date();
      start.setDate(start.getDate() - 7);
      return [start, end];
    },
  },
  {
    text: '最近一月',
    value: () => {
      const end = new Date();
      const start = new Date();
      start.setMonth(start.getMonth() - 1);
      return [start, end];
    },
  },
  {
    text: '最近三个月',
    value: () => {
      const end = new Date();
      const start = new Date();
      start.setMonth(start.getMonth() - 3);
      return [start, end];
    },
  },
];
const $route = useRouter();
const showSearch = ref(true);
const loading = ref(false);
const deliverRef = ref();
const printRef = ref();
const activeType = ref('');

const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
    status: activeType.value,
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
/**
 * 重置搜索表单
 */
const resetQuery = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  formEl.resetFields();
};
/**
 * 订单详情
 */
const detail = (row: any) => {
  $route.push({ path: '/order/detail', query: { id: row.id } });
};
/**
 * 删除按钮
 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该订单，是否继续?', '提示', {
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
/**
 * 取消订单
 */
const cancel = (id: string) => {
  ElMessageBox.confirm('此操作将取消该订单，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    cancelObj(id)
      .then(() => {
        ElMessage.success('取消成功');
        initPage();
      })
      .catch(() => {});
  });
};
/**
 * 订单状态切换
 */
const tabHandle = (event: any) => {
  activeType.value = event;
  initPage();
};
/**
 * 发货
 */
const deliverOrder = (row: any) => {
  deliverRef.value.initPage(row.id);
};
const assignDeliveryOrder = (row: any) => {
  $route.push({
    path: '/order/delivery-task',
    query: { orderNo: row.orderNo, openAssign: row.id },
  });
};
/**
 * 自提
 */
const selffetchOrder = (row: any) => {
  ElMessageBox.confirm('此操作将完成订单提货，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    selffetchObj({ id: row.id })
      .then(() => {
        ElMessage.success('提货成功');
        initPage();
      })
      .catch(() => {});
  });
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
        <ElFormItem label="支付类型" prop="payType">
          <ElSelect
            v-model="state.queryParams.payType"
            clearable
            placeholder="请选择支付类型"
            style="width: 200px"
          >
            <ElOption
              v-for="item in pay_type"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="配送方式" prop="deliveryWay">
          <ElSelect
            v-model="state.queryParams.deliveryWay"
            clearable
            placeholder="请选择配送方式"
            style="width: 200px"
          >
            <ElOption
              v-for="item in delivery_way"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="支付时间" prop="paymentQueryTimes">
          <ElDatePicker
            style="width: 360px"
            v-model="state.queryParams.paymentQueryTimes"
            type="datetimerange"
            :shortcuts="shortcuts"
            value-format="YYYY-MM-DD HH:mm:ss"
            range-separator="-"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
          />
        </ElFormItem>
        <ElFormItem label="订单号" prop="orderNo">
          <ElInput
            v-model="state.queryParams.orderNo"
            clearable
            placeholder="请输入订单号"
          />
        </ElFormItem>
        <ElFormItem label="收货人姓名" prop="recipientName">
          <ElInput
            v-model="state.queryParams.recipientName"
            clearable
            placeholder="请输入收货人姓名"
          />
        </ElFormItem>
        <ElFormItem label="收货人电话" prop="recipientPhone">
          <ElInput
            v-model="state.queryParams.recipientPhone"
            clearable
            placeholder="请输入收货人电话"
          />
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
      <!-- 工具栏 -->
      <div class="hx-table-toolbar">
        <ElSegmented
          v-model="activeType"
          :options="activeTypeOptions"
          @change="tabHandle"
        />
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>
      <Deliver ref="deliverRef" @handle-success="initPage" />

      <!-- 列表 -->
      <ElTable
        ref="printRef"
        v-loading="loading"
        :data="state.tableData"
        border
      >
        <ElTableColumn prop="orderItemList" label="订单信息" width="480">
          <template #default="scope">
            <ElRow
              style="padding: 0 !important"
              v-for="(item, index) in scope.row.orderItemList"
              :key="index"
              class="order-item"
            >
              <ElCol :span="4">
                <ElImage
                  style="width: 60px; height: 60px"
                  :src="item.picUrl"
                  fit="cover"
                  :preview-teleported="true"
                />
              </ElCol>
              <ElCol :span="15">
                <span class="name line-clamp-2">
                  {{ item.spuName }}
                </span>
                <p style="font-size: 12px; color: #a8abb2">
                  {{ item.specsInfo }}
                </p>
                <p>
                  <span style="color: red" v-if="item.couponPrice > 0">
                    优惠券减免：-{{ item.couponPrice }}元
                  </span>
                </p>
              </ElCol>
              <ElCol :span="5" style="padding-left: 20px">
                <span style="color: #f56c6c">￥{{ item.paymentPrice }}</span>
                <p>x{{ item.buyQuantity }}</p>
                <DictTag
                  v-if="item.status === '3' || item.status === '6'"
                  :options="order_item_status"
                  :value="item.status"
                />
              </ElCol>
            </ElRow>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="deliveryWay"
          label="配送方式"
          align="center"
          width="120"
        >
          <template #default="scope">
            <DictTag :options="delivery_way" :value="scope.row.deliveryWay" />
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="status"
          label="订单状态"
          align="center"
          width="120"
        >
          <template #default="scope">
            <ElTag
              v-if="scope.row.deliveryWay === '2' && scope.row.status === '3'"
            >
              待自提
            </ElTag>
            <DictTag v-else :options="order_status" :value="scope.row.status" />
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="时间" width="240">
          <template #default="scope">
            <div>创建时间：{{ scope.row.createTime }}</div>
            <div v-if="scope.row.paymentTime">
              付款时间：{{ scope.row.paymentTime }}
            </div>
            <div v-if="scope.row.deliverTime">
              {{ scope.row.deliveryWay === '2' ? '自提时间：' : '发货时间：'
              }}{{ scope.row.deliverTime }}
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="totalPrice" label="订单金额（元）" width="240">
          <template #default="scope">
            <div>订单金额：￥{{ scope.row.totalPrice }}</div>
            <ElPopover placement="right" :width="400" trigger="click">
              <template #reference>
                <div style="display: flex; align-items: center">
                  <span style="color: red">
                    实付金额：￥{{ scope.row.paymentPrice }}
                  </span>
                  <ElIcon style="font-size: 12px; color: red">
                    <ArrowRightBold />
                  </ElIcon>
                </div>
              </template>
              <div>订单金额：￥{{ scope.row.totalPrice }}</div>
              <div>运费金额：￥{{ scope.row.freightPrice }}</div>
              <div style="color: red">
                优惠券：-￥{{
                  scope.row.couponPrice ? scope.row.couponPrice : 0
                }}
              </div>
              <div style="color: red">
                实付金额：￥{{ scope.row.paymentPrice }}
              </div>
            </ElPopover>
          </template>
        </ElTableColumn>
        <ElTableColumn
          label="操作"
          min-width="200"
          align="center"
          fixed="right"
        >
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'order:orderinfo:get'"
              @click="detail(scope.row)"
              :icon="View"
            >
              详情
            </ElButton>
            <ElButton
              link
              type="primary"
              v-access:code="'order:orderinfo:deliver'"
              v-if="
                (scope.row.status === '2' || scope.row.status === '7') &&
                scope.row.deliveryWay === '1'
              "
              @click="deliverOrder(scope.row)"
              :icon="Van"
            >
              发货
            </ElButton>
            <ElButton
              link
              type="primary"
              v-access:code="'order:delivery:assign'"
              v-if="getOrderFulfillmentAction(scope.row) === 'ASSIGN'"
              @click="assignDeliveryOrder(scope.row)"
              :icon="Van"
            >
              派单
            </ElButton>
            <ElButton
              link
              type="primary"
              v-access:code="'order:orderinfo:deliver'"
              v-if="scope.row.status === '3' && scope.row.deliveryWay === '2'"
              @click="selffetchOrder(scope.row)"
              :icon="Box"
            >
              提货
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'order:orderinfo:del'"
              v-if="scope.row.status === '11' && scope.row.payStatus === '0'"
              @click="del(scope.row.id)"
              :icon="Delete"
            >
              删除
            </ElButton>
            <ElButton
              link
              type="warning"
              v-access:code="'order:orderinfo:cancel'"
              v-if="scope.row.status === '1' && scope.row.payStatus === '0'"
              @click="cancel(scope.row.id)"
            >
              取消
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
<style scoped lang="scss">
.order-item {
  margin-right: 0 !important;
  margin-left: 0 !important;

  .name {
    color: #409eff;
  }
}
</style>
