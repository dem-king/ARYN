<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { ChevronRight } from '@vben/icons';

import {
  ElButton,
  ElCol,
  ElForm,
  ElFormItem,
  ElIcon,
  ElImage,
  ElOption,
  ElPopover,
  ElRow,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getPage } from '#/api/order/order-info';
import { useDict } from '#/utils/dict';

const props = defineProps({
  userId: {
    type: String,
    default: '',
  },
});
const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);
// 字典
const { order_type, delivery_way, order_status, order_item_status } = useDict(
  'order_type',
  'delivery_way',
  'order_status',
  'order_item_status',
);
const state = reactive({
  queryParams: {
    orderType: '',
    userId: '',
    payStatus: '1',
    deliveryWay: '',
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
const showSearch = ref(true);
const loading = ref(false);
const queryRef = ref();
const initPage = async () => {
  if (!props.userId) {
    return;
  }
  state.queryParams.userId = props.userId;

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

/**
 * 重置搜索表单
 */
const resetQuery = () => {
  queryRef.value.resetFields();
};
initPage();
</script>
<template>
  <div>
    <!-- 搜索 -->
    <ElForm
      :model="state.queryParams"
      ref="queryRef"
      :inline="true"
      v-show="showSearch"
    >
      <ElFormItem label="订单类型" prop="orderType">
        <ElSelect
          v-model="state.queryParams.orderType"
          style="width: 200px"
          placeholder="请选择订单类型"
          clearable
        >
          <ElOption
            v-for="item in order_type"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="配送方式" prop="deliveryWay">
        <ElSelect
          v-model="state.queryParams.deliveryWay"
          style="width: 200px"
          placeholder="请选择配送方式"
          clearable
        >
          <ElOption
            v-for="item in delivery_way"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem>
        <ElButton type="primary" @click="initPage"> 搜索 </ElButton>
        <ElButton @click="resetQuery"> 重置 </ElButton>
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
    <!-- 列表 -->
    <ElTable v-loading="loading" :data="state.tableData" border>
      <ElTableColumn prop="orderItemList" label="订单信息" width="450">
        <template #default="scope">
          <ElRow
            :gutter="24"
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
              <span class="overflow-line-clamp-2 name">
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
            <ElCol :span="5">
              <span style="color: #f56c6c">{{ item.paymentPrice }}元</span>
              <p>x{{ item.buyQuantity }}</p>
              <DictTag
                v-if="
                  item.status === '3' ||
                  item.status === '5' ||
                  item.status === '6'
                "
                :options="order_item_status"
                :value="item.status"
              />
            </ElCol>
          </ElRow>
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="orderType"
        label="订单类型"
        align="center"
        width="100"
      >
        <template #default="scope">
          <DictTag :options="order_type" :value="scope.row.orderType" />
        </template>
      </ElTableColumn>
      <ElTableColumn
        prop="deliveryWay"
        label="配送方式"
        align="center"
        width="100"
      >
        <template #default="scope">
          <DictTag :options="delivery_way" :value="scope.row.deliveryWay" />
        </template>
      </ElTableColumn>
      <ElTableColumn prop="status" label="订单状态" align="center" width="100">
        <template #default="scope">
          <ElTag
            v-if="scope.row.deliveryWay === '2' && scope.row.status === '3'"
          >
            待自提
          </ElTag>
          <DictTag v-else :options="order_status" :value="scope.row.status" />
        </template>
      </ElTableColumn>

      <ElTableColumn prop="createTime" label="时间" width="250">
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
      <ElTableColumn prop="totalPrice" label="订单金额（元）" width="220">
        <template #default="scope">
          <div>订单金额：￥{{ scope.row.totalPrice }}</div>
          <ElPopover placement="right" :width="400" trigger="click">
            <template #reference>
              <div style="display: flex; align-items: center">
                <span style="color: red">
                  实付金额：￥{{ scope.row.paymentPrice }}
                </span>
                <ElIcon style="color: red"> <ChevronRight /></ElIcon>
              </div>
            </template>
            <template #default>
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
            </template>
          </ElPopover>
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
</template>
