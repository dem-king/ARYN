<script lang="ts" setup name="order">
import type { UploadRequestOptions, UploadUserFile } from 'element-plus';

import { defineAsyncComponent, reactive, ref, watch } from 'vue';
import useClipboard from 'vue-clipboard3';
import { useRoute, useRouter } from 'vue-router';

import {
  ArrowRightBold,
  Box,
  CircleClose,
  CopyDocument,
  Delete,
  Download,
  Money,
  Plus,
  User,
  Van,
  View,
} from '@element-plus/icons-vue';
import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPopover,
  ElSegmented,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
  ElUpload,
} from 'element-plus';

import {
  cancelObj,
  confirmOfflinePayment,
  delObj,
  getPage,
  selffetchObj,
} from '#/api/order/order-info';
import { uploadFile } from '#/api/upms/upload';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';
import { useDict } from '#/utils/dict';
import { downloadBlobFile, formatMoney } from '#/utils/util';

import OrderItemsCell from './order-items-cell.vue';
import { purchaseSceneText } from './purchase-scene';

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
const { pay_type, delivery_way, order_status } = useDict(
  'pay_type',
  'delivery_way',
  'order_status',
);
const state = reactive({
  queryParams: {
    deliveryWay: '',
    paymentType: '',
    paymentQueryTimes: '',
    orderNo: '',
    purchaseScene: '',
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
const route = useRoute();
const showSearch = ref(true);
const loading = ref(false);
const deliverRef = ref();
const printRef = ref();
const activeType = ref('');

const { toClipboard } = useClipboard();
const copyText = async (text?: string, label = '内容') => {
  if (!text) return;
  try {
    await toClipboard(String(text));
    ElMessage.success(`${label}已复制`);
  } catch {
    ElMessage.error('复制失败，请手动选择复制');
  }
};
/** 列表只保留到分钟，秒级精度在详情页查看 */
const formatDateTime = (value?: string) =>
  value ? String(value).replace('T', ' ').slice(0, 16) : '';
const amountLabel = (row: any) =>
  row.payStatus === '1' ? '实付金额' : '应付金额';
const deliverTimeLabel = (row: any) => {
  if (row.deliveryWay === '2') {
    return '自提';
  }
  return row.deliveryWay === '3' || row.deliveryWay === '4' ? '出发' : '发货';
};
const sceneTagType = (scene?: string): 'info' | 'primary' | 'success' => {
  if (scene === '2') return 'primary';
  if (scene === '3') return 'success';
  return 'info';
};
const vesselText = (row: any) =>
  [row.vesselName, row.portName].filter(Boolean).join(' · ');
const vesselTitle = (row: any) =>
  [row.vesselName, row.portName, row.berth].filter(Boolean).join(' / ');

const applyRouteStatus = () => {
  const raw = route.query.status;
  const status = Array.isArray(raw) ? raw[0] : raw;
  const next =
    typeof status === 'string' &&
    activeTypeOptions.value.some((item) => item.value === status)
      ? status
      : '';
  if (activeType.value === next) {
    return false;
  }
  activeType.value = next;
  state.page.currentPage = 1;
  return true;
};

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
 * 订单详情
 */
const detail = (row: any) => {
  $route.push({ path: '/order/detail', query: { id: row.id } });
};
// 商城配送/内部配送派单后订单仍为待发货（司机出发才转待收货），
// 列表需要副标签展示派单进度，避免误以为没有点过发货
const DISPATCHED_TASK_LABELS: Record<string, string> = {
  '2': '已派单·待取货',
  '3': '已派单·配货中',
  '4': '已派单·待送达',
  '8': '已派单·异常',
};
const dispatchedTaskLabel = (row: any) => {
  if (row.status !== '2') {
    return '';
  }
  if (row.deliveryWay !== '3' && row.deliveryWay !== '4') {
    return '';
  }
  return DISPATCHED_TASK_LABELS[row.deliveryTask?.status] ?? '';
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
  const query = { ...route.query };
  if (event) {
    query.status = event;
  } else {
    delete query.status;
  }
  $route.replace({ path: route.path, query });
  initPage();
};
/**
 * 发货
 */
const deliverOrder = (row: any) => {
  deliverRef.value.initPage(row.id);
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
/**
 * 货到付款确认收款（实收金额 + 付款凭证上传）
 */
interface PayVoucherFile extends UploadUserFile {
  materialId?: string;
}
const payConfirmState = reactive({
  visible: false,
  submitting: false,
  orderId: '',
  orderNo: '',
  // 应收金额（总金额-优惠+运费），线下收款可能有折扣
  receivable: 0,
  actualPayPrice: 0,
  voucherFiles: [] as PayVoucherFile[],
});
/**
 * 确认收款入口判据：货到付款 + 未收款 + 货物已送达。
 *
 * delivered 由后端下发（onDelivery 判定）：已完成、自提到店、或配送任务已送达/签收。
 * 不能只认 status=4——客户常当面付款却不在小程序点确认收货，只认完成会把入口
 * 拖到超时自动收货之后（默认 7 天）；也不能只看待发货/待收货，否则货未到就收款。
 */
const canConfirmPay = (row: any) =>
  row.paymentType === '3' &&
  row.payStatus === '0' &&
  row.delivered === true &&
  row.status !== '11';
const openPayConfirm = (row: any) => {
  payConfirmState.orderId = row.id;
  payConfirmState.orderNo = row.orderNo || row.id;
  payConfirmState.receivable = Number(row.paymentPrice ?? row.totalPrice ?? 0);
  payConfirmState.actualPayPrice = payConfirmState.receivable;
  payConfirmState.voucherFiles = [];
  payConfirmState.visible = true;
};
const uploadPayVoucher = async (options: UploadRequestOptions) => {
  const formData = new FormData();
  formData.append('file', options.file);
  formData.append('type', '1');
  const res: any = await uploadFile(formData);
  const fileItem = payConfirmState.voucherFiles.at(-1);
  if (fileItem) {
    fileItem.materialId = res?.id;
    fileItem.url = res?.url;
  }
  options.onSuccess?.(res);
  return res;
};
const beforePayVoucherUpload = (file: any) => {
  const isImage = file.type?.startsWith('image/');
  if (!isImage) {
    ElMessage.error('付款凭证仅支持图片');
    return false;
  }
  if (payConfirmState.voucherFiles.length >= 6) {
    ElMessage.error('付款凭证最多上传6张');
    return false;
  }
  return true;
};
const confirmPaySubmit = () => {
  if (!payConfirmState.actualPayPrice || payConfirmState.actualPayPrice <= 0) {
    ElMessage.error('请填写大于0的实收金额');
    return;
  }
  if (payConfirmState.actualPayPrice > payConfirmState.receivable) {
    ElMessage.error('实收金额不能大于应收金额');
    return;
  }
  const pending = payConfirmState.voucherFiles.some(
    (file) => file.status === 'uploading' || !file.materialId,
  );
  if (pending) {
    ElMessage.error('付款凭证还在上传中，请稍候');
    return;
  }
  payConfirmState.submitting = true;
  confirmOfflinePayment(payConfirmState.orderId, {
    actualPayPrice: payConfirmState.actualPayPrice,
    voucherMaterialIds: payConfirmState.voucherFiles
      .map((file) => file.materialId)
      .filter((id): id is string => id !== undefined),
  })
    .then(() => {
      ElMessage.success('确认收款成功');
      payConfirmState.visible = false;
      initPage();
    })
    .catch(() => {})
    .finally(() => {
      payConfirmState.submitting = false;
    });
};

/**
 * 导出订单明细（商品行按分类分组、含分类小计与汇总）：
 * 已勾选订单时仅导出所选订单，否则按当前筛选条件导出
 */
const exporting = ref(false);
const selectedRows = ref<any[]>([]);
const handleSelectionChange = (rows: any[]) => {
  selectedRows.value = rows;
};
const localTimestamp = () => {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return (
    `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}` +
    `${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
  );
};
const handleExport = () => {
  const selectedIds = selectedRows.value.map((row) => row.id);
  const exportSelected = selectedIds.length > 0;
  ElMessageBox.confirm(
    exportSelected
      ? `已选择 ${selectedIds.length} 个订单，将导出所选订单明细（商品按分类分组），是否继续?`
      : '将按当前筛选条件导出订单明细（商品按分类分组），是否继续?',
    '订单导出',
    {
      confirmButtonText: '导出',
      cancelButtonText: '取消',
      type: 'info',
    },
  ).then(() => {
    exporting.value = true;
    downloadBlobFile(
      '/mall-order/orderinfo/export',
      exportSelected
        ? { ids: selectedIds.join(',') }
        : { ...state.queryParams, status: activeType.value },
      `订单明细_${localTimestamp()}.xlsx`,
    ).finally(() => {
      exporting.value = false;
    });
  });
};
applyRouteStatus();
initPage();
useKeepAliveRefresh(initPage);

// keepAlive 缓存后切去其它页面（如进详情）也会触发本 watch（route 变为对方路由，
// query.status 变 undefined），此时必须忽略，否则会把页签重置回「全部」
const listRoutePath = route.path;

watch(
  () => route.query.status,
  () => {
    if (route.path !== listRoutePath) {
      return;
    }
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
      <PageFilterPanel
        :model="state.queryParams"
        :visible="showSearch"
        @reset="initPage"
        @search="initPage"
      >
        <ElFormItem label="支付类型" prop="paymentType">
          <ElSelect
            v-model="state.queryParams.paymentType"
            clearable
            placeholder="请选择支付类型"
          >
            <ElOption
              v-for="item in pay_type"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="购买场景" prop="purchaseScene">
          <ElSelect
            v-model="state.queryParams.purchaseScene"
            clearable
            placeholder="全部场景"
          >
            <ElOption label="海员个人购买" value="1" />
            <ElOption label="船供采购" value="2" />
            <ElOption label="企业采购" value="3" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="配送方式" prop="deliveryWay">
          <ElSelect
            v-model="state.queryParams.deliveryWay"
            clearable
            placeholder="请选择配送方式"
          >
            <ElOption
              v-for="item in delivery_way"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="订单号" prop="orderNo">
          <ElInput
            v-model="state.queryParams.orderNo"
            clearable
            placeholder="请输入订单号"
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="支付时间" prop="paymentQueryTimes">
          <ElDatePicker
            v-model="state.queryParams.paymentQueryTimes"
            type="datetimerange"
            :shortcuts="shortcuts"
            value-format="YYYY-MM-DD HH:mm:ss"
            range-separator="-"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
          />
        </ElFormItem>
        <ElFormItem label="收货人姓名" prop="recipientName">
          <ElInput
            v-model="state.queryParams.recipientName"
            clearable
            placeholder="请输入收货人姓名"
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem label="收货人电话" prop="recipientPhone">
          <ElInput
            v-model="state.queryParams.recipientPhone"
            clearable
            placeholder="请输入收货人电话"
            @keyup.enter="initPage"
          />
        </ElFormItem>
      </PageFilterPanel>
      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="笔订单">
        <template #left>
          <ElSegmented
            v-model="activeType"
            :options="activeTypeOptions"
            @change="tabHandle"
          />
        </template>
        <template #right>
          <ElButton
            type="warning"
            plain
            :icon="Download"
            :loading="exporting"
            v-access:code="'order:orderinfo:export'"
            @click="handleExport"
          >
            导出
          </ElButton>
          <RightToolbar
            :search-btn="true"
            :refresh-btn="true"
            @search="showSearch = !showSearch"
            @refresh="initPage"
          />
        </template>
      </PageToolbar>
      <Deliver ref="deliverRef" @handle-success="initPage" />

      <!-- 列表 -->
      <ElTable
        ref="printRef"
        v-loading="loading"
        class="hx-data-table"
        :data="state.tableData"
        row-key="id"
        border
        empty-text="暂无订单"
        @selection-change="handleSelectionChange"
      >
        <ElTableColumn type="selection" width="44" align="center" />
        <ElTableColumn label="购买场景" width="110" align="center">
          <template #default="scope">
            <div class="oi-scene">
              <ElTag
                :type="sceneTagType(scope.row.purchaseScene)"
                effect="plain"
                size="small"
                :disable-transitions="true"
              >
                {{ purchaseSceneText(scope.row.purchaseScene) }}
              </ElTag>
              <span
                v-if="scope.row.purchaseScene === '2' && vesselText(scope.row)"
                class="oi-scene-sub"
                :title="vesselTitle(scope.row)"
              >
                {{ vesselText(scope.row) }}
              </span>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="订单信息" min-width="364">
          <template #default="scope">
            <div class="oi-order-cell">
              <div class="oi-order-head">
                <span v-if="scope.row.orderNo" class="oi-order-no">
                  <span class="oi-order-no-label">单号</span>
                  <span class="oi-mono">{{ scope.row.orderNo }}</span>
                  <ElButton
                    class="oi-copy"
                    link
                    :icon="CopyDocument"
                    title="复制订单号"
                    @click.stop="copyText(scope.row.orderNo, '订单号')"
                  />
                </span>
                <span v-else class="oi-order-no is-empty">单号 —</span>
                <span
                  v-if="scope.row.recipientName || scope.row.recipientPhone"
                  class="oi-recipient"
                  :title="scope.row.recipientAddress"
                >
                  <ElIcon class="oi-recipient-icon" :size="12">
                    <User />
                  </ElIcon>
                  <span
                    v-if="scope.row.recipientName"
                    class="oi-recipient-name"
                  >
                    {{ scope.row.recipientName }}
                  </span>
                  <span
                    v-if="scope.row.recipientPhone"
                    class="oi-recipient-phone"
                  >
                    {{ scope.row.recipientPhone }}
                  </span>
                </span>
              </div>
              <OrderItemsCell :row="scope.row" />
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="deliveryWay"
          label="配送方式"
          align="center"
          width="116"
        >
          <template #default="scope">
            <div class="oi-delivery">
              <DictTag :options="delivery_way" :value="scope.row.deliveryWay" />
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="paymentType"
          label="支付方式"
          align="center"
          width="96"
        >
          <template #default="scope">
            <span v-if="!scope.row.paymentType" class="oi-muted">—</span>
            <DictTag
              v-else
              :options="pay_type"
              :value="scope.row.paymentType"
            />
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="status"
          label="订单状态"
          align="center"
          width="120"
        >
          <template #default="scope">
            <div class="oi-status">
              <ElTag
                v-if="scope.row.deliveryWay === '2' && scope.row.status === '3'"
                :disable-transitions="true"
              >
                待自提
              </ElTag>
              <DictTag
                v-else
                :options="order_status"
                :value="scope.row.status"
              />
              <ElTag
                v-if="dispatchedTaskLabel(scope.row)"
                size="small"
                type="warning"
                :disable-transitions="true"
              >
                {{ dispatchedTaskLabel(scope.row) }}
              </ElTag>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="时间" width="160">
          <template #default="scope">
            <div class="oi-time">
              <div class="oi-time-row is-main">
                <span class="oi-time-label">创建</span>
                <span class="oi-time-value">
                  {{ formatDateTime(scope.row.createTime) }}
                </span>
              </div>
              <div v-if="scope.row.paymentTime" class="oi-time-row">
                <span class="oi-time-label">付款</span>
                <span class="oi-time-value">
                  {{ formatDateTime(scope.row.paymentTime) }}
                </span>
              </div>
              <div v-if="scope.row.deliverTime" class="oi-time-row">
                <span class="oi-time-label">
                  {{ deliverTimeLabel(scope.row) }}
                </span>
                <span class="oi-time-value">
                  {{ formatDateTime(scope.row.deliverTime) }}
                </span>
              </div>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="paymentPrice" label="金额（元）" width="136">
          <template #default="scope">
            <ElPopover
              placement="left"
              :width="268"
              trigger="click"
              :show-arrow="false"
            >
              <template #reference>
                <button type="button" class="oi-amount" title="查看金额明细">
                  <span class="oi-amount-main">
                    <span class="oi-currency">¥</span>
                    <span>{{ formatMoney(scope.row.paymentPrice) }}</span>
                  </span>
                  <span class="oi-amount-sub">
                    商品 ¥{{ formatMoney(scope.row.totalPrice) }}
                    <ElIcon :size="10"><ArrowRightBold /></ElIcon>
                  </span>
                </button>
              </template>
              <div class="oi-breakdown">
                <div class="oi-breakdown-title">金额明细</div>
                <div class="oi-breakdown-row">
                  <span>商品总价</span>
                  <span>¥{{ formatMoney(scope.row.totalPrice) }}</span>
                </div>
                <div class="oi-breakdown-row">
                  <span>运费</span>
                  <span>
                    {{
                      Number(scope.row.freightPrice) > 0
                        ? `+¥${formatMoney(scope.row.freightPrice)}`
                        : '包邮'
                    }}
                  </span>
                </div>
                <div
                  v-if="Number(scope.row.couponPrice) > 0"
                  class="oi-breakdown-row is-discount"
                >
                  <span>优惠券</span>
                  <span>-¥{{ formatMoney(scope.row.couponPrice) }}</span>
                </div>
                <div
                  v-if="Number(scope.row.promoPrice) > 0"
                  class="oi-breakdown-row is-discount"
                >
                  <span>活动优惠</span>
                  <span>-¥{{ formatMoney(scope.row.promoPrice) }}</span>
                </div>
                <div
                  v-if="Number(scope.row.memberDiscountPrice) > 0"
                  class="oi-breakdown-row is-discount"
                >
                  <span>会员优惠</span>
                  <span
                    >-¥{{ formatMoney(scope.row.memberDiscountPrice) }}</span
                  >
                </div>
                <div
                  v-if="Number(scope.row.pointsDeductPrice) > 0"
                  class="oi-breakdown-row is-discount"
                >
                  <span>积分抵扣（{{ scope.row.pointsUsed || 0 }}积分）</span>
                  <span>-¥{{ formatMoney(scope.row.pointsDeductPrice) }}</span>
                </div>
                <div class="oi-breakdown-row is-total">
                  <span>{{ amountLabel(scope.row) }}</span>
                  <span>¥{{ formatMoney(scope.row.paymentPrice) }}</span>
                </div>
                <div
                  v-if="
                    Number(scope.row.actualPayPrice) > 0 &&
                    Number(scope.row.actualPayPrice) !==
                      Number(scope.row.paymentPrice)
                  "
                  class="oi-breakdown-row"
                >
                  <span>实收金额</span>
                  <span>¥{{ formatMoney(scope.row.actualPayPrice) }}</span>
                </div>
              </div>
            </ElPopover>
          </template>
        </ElTableColumn>
        <ElTableColumn
          label="操作"
          min-width="176"
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
              v-access:code="'order:orderinfo:deliver'"
              v-if="
                scope.row.status === '2' &&
                (scope.row.deliveryWay === '3' ||
                  scope.row.deliveryWay === '4') &&
                (!scope.row.deliveryTask ||
                  scope.row.deliveryTask.status === '1')
              "
              @click="deliverOrder(scope.row)"
              :icon="Van"
            >
              派单发货
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
              type="primary"
              v-access:code="'order:orderinfo:payconfirm'"
              v-if="canConfirmPay(scope.row)"
              @click="openPayConfirm(scope.row)"
              :icon="Money"
            >
              确认收款
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
              :icon="CircleClose"
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

    <!-- 货到付款确认收款弹窗 -->
    <ElDialog
      v-model="payConfirmState.visible"
      :title="`确认收款${payConfirmState.orderNo ? ` - ${payConfirmState.orderNo}` : ''}`"
      width="520px"
      :close-on-click-modal="false"
    >
      <ElForm label-width="90px">
        <ElFormItem label="应收金额">
          <span>￥{{ payConfirmState.receivable }}</span>
        </ElFormItem>
        <ElFormItem label="实收金额" required>
          <ElInputNumber
            v-model="payConfirmState.actualPayPrice"
            :min="0.01"
            :max="payConfirmState.receivable"
            :precision="2"
            :step="1"
            controls-position="right"
            style="width: 200px"
          />
          <span class="pl-6px text-12px text-gray-400"
            >客户线下实付，可低于应收</span
          >
        </ElFormItem>
        <ElFormItem label="付款凭证">
          <ElUpload
            v-model:file-list="payConfirmState.voucherFiles"
            list-type="picture-card"
            accept="image/*"
            :limit="6"
            :before-upload="beforePayVoucherUpload"
            :http-request="uploadPayVoucher"
          >
            <ElIcon><Plus /></ElIcon>
          </ElUpload>
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="payConfirmState.visible = false">取消</ElButton>
        <ElButton
          type="primary"
          :loading="payConfirmState.submitting"
          @click="confirmPaySubmit"
        >
          确认收款
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped lang="scss">
.oi-scene {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  min-width: 0;
}

.oi-scene-sub {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.oi-delivery {
  word-break: keep-all;
}

.oi-order-cell {
  min-width: 0;
}

.oi-order-head {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  align-items: center;
  padding-bottom: 7px;
  margin-bottom: 8px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.oi-order-no {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);

  &.is-empty {
    color: var(--el-text-color-placeholder);
  }
}

.oi-order-no-label {
  color: var(--el-text-color-placeholder);
}

.oi-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-regular);
  letter-spacing: 0.2px;
}

.oi-copy {
  width: 18px;
  height: 18px;
  padding: 0;
  color: var(--el-text-color-placeholder);
  transition: color 0.15s;

  &:hover {
    color: var(--el-color-primary);
  }
}

.oi-recipient {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  min-width: 0;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-regular);
}

.oi-recipient-icon {
  flex: none;
  color: var(--el-text-color-placeholder);
}

.oi-recipient-name {
  font-weight: 500;
}

.oi-recipient-phone {
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-secondary);
}

.oi-status {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
}

.oi-time {
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: 12px;
  line-height: 1.5;
}

.oi-time-row {
  display: flex;
  gap: 6px;
  color: var(--el-text-color-secondary);

  &.is-main {
    color: var(--el-text-color-regular);
  }
}

.oi-time-label {
  flex: none;
  width: 26px;
  color: var(--el-text-color-placeholder);
}

.oi-time-value {
  font-variant-numeric: tabular-nums;
}

/* ---------- 金额 ---------- */
.oi-amount {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: flex-start;
  width: 100%;
  padding: 0;
  text-align: left;
  cursor: pointer;
  background: none;
  border: 0;
}

.oi-amount-main {
  display: inline-flex;
  align-items: baseline;
  font-size: 15px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1.3;
  color: var(--el-color-danger);
}

.oi-currency {
  margin-right: 1px;
  font-size: 11px;
  font-weight: 500;
}

.oi-amount-sub {
  display: inline-flex;
  gap: 2px;
  align-items: center;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-secondary);
  transition: color 0.15s;
}

.oi-amount:hover .oi-amount-sub {
  color: var(--el-color-primary);
}

.oi-breakdown {
  font-size: 13px;
}

.oi-breakdown-title {
  padding-bottom: 8px;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.oi-breakdown-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 3px 0;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-regular);

  &.is-discount span:last-child {
    color: var(--el-color-danger);
  }

  &.is-total {
    padding-top: 8px;
    margin-top: 6px;
    font-weight: 600;
    border-top: 1px dashed var(--el-border-color-lighter);

    span:last-child {
      font-size: 15px;
      color: var(--el-color-danger);
    }
  }
}

.oi-muted {
  color: var(--el-text-color-placeholder);
}
</style>
