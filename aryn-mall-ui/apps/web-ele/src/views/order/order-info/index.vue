<script lang="ts" setup name="order">
import type {
  FormInstance,
  UploadRequestOptions,
  UploadUserFile,
} from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import {
  ArrowRightBold,
  Box,
  Delete,
  Download,
  Money,
  Plus,
  Refresh,
  Search,
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
import { useDict } from '#/utils/dict';
import { downloadBlobFile } from '#/utils/util';

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
const queryRef = ref<FormInstance>();
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
        <ElFormItem label="支付类型" prop="paymentType">
          <ElSelect
            v-model="state.queryParams.paymentType"
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
        <ElFormItem label="购买场景" prop="purchaseScene">
          <ElSelect
            v-model="state.queryParams.purchaseScene"
            clearable
            placeholder="全部场景"
            style="width: 160px"
          >
            <ElOption label="海员个人购买" value="1" />
            <ElOption label="船供采购" value="2" />
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
      </div>
      <Deliver ref="deliverRef" @handle-success="initPage" />

      <!-- 列表 -->
      <ElTable
        ref="printRef"
        v-loading="loading"
        :data="state.tableData"
        border
        @selection-change="handleSelectionChange"
      >
        <ElTableColumn type="selection" width="55" align="center" />
        <ElTableColumn label="购买场景" width="110" align="center">
          <template #default="scope">
            {{ purchaseSceneText(scope.row.purchaseScene) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="orderItemList" label="订单信息" width="420">
          <template #default="scope">
            <OrderItemsCell :row="scope.row" />
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
          prop="paymentType"
          label="支付方式"
          align="center"
          width="110"
        >
          <template #default="scope">
            <span v-if="!scope.row.paymentType">—</span>
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
            <ElTag
              v-if="scope.row.deliveryWay === '2' && scope.row.status === '3'"
            >
              待自提
            </ElTag>
            <DictTag v-else :options="order_status" :value="scope.row.status" />
            <div v-if="dispatchedTaskLabel(scope.row)" class="mt-1">
              <ElTag size="small" type="warning">
                {{ dispatchedTaskLabel(scope.row) }}
              </ElTag>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createTime" label="时间" width="240">
          <template #default="scope">
            <div>创建时间：{{ scope.row.createTime }}</div>
            <div v-if="scope.row.paymentTime">
              付款时间：{{ scope.row.paymentTime }}
            </div>
            <div v-if="scope.row.deliverTime">
              {{
                scope.row.deliveryWay === '2'
                  ? '自提时间：'
                  : scope.row.deliveryWay === '3' ||
                      scope.row.deliveryWay === '4'
                    ? '配送出发时间：'
                    : '发货时间：'
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
