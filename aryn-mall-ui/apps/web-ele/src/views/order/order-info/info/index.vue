<script lang="ts" setup>
import type { UploadRequestOptions, UploadUserFile } from 'element-plus';

import type { Component } from 'vue';

import type { OrderCategoryGroup } from '../category-group';

import {
  computed,
  defineAsyncComponent,
  onBeforeUnmount,
  reactive,
  ref,
  watch,
} from 'vue';
import useClipboard from 'vue-clipboard3';
import { useRoute, useRouter } from 'vue-router';

import {
  Box,
  Check,
  CircleCheckFilled,
  CircleCloseFilled,
  Clock,
  CopyDocument,
  Money,
  Picture,
  Plus,
  Van,
  WarningFilled,
} from '@element-plus/icons-vue';
import {
  ElAvatar,
  ElButton,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElIcon,
  ElImage,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElTag,
  ElTooltip,
  ElUpload,
} from 'element-plus';

import {
  confirmOfflinePayment,
  getById,
  selffetchObj,
} from '#/api/order/order-info';
import { getPromotionSnapshots } from '#/api/order/shared-cart';
import { uploadFile } from '#/api/upms/upload';
import { getById as getUserById } from '#/api/user/user-info';
import PageSection from '#/components/page/page-section.vue';
import { useDict } from '#/utils/dict';
import { formatMoney } from '#/utils/util';

import { groupOrderItems } from '../category-group';
import { purchaseSceneText } from '../purchase-scene';

const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);
const Deliver = defineAsyncComponent(() => import('../deliver/index.vue'));

type TagType = 'danger' | 'info' | 'primary' | 'success' | 'warning';

interface StatusView {
  desc: string;
  /** 附加状态（如「已完成」订单的货到付款待确认收款） */
  extraLabel?: string;
  headline: string;
  icon: Component;
  label: string;
  showSteps: boolean;
  tone: TagType;
}

interface OrderStep {
  state: 'active' | 'done' | 'todo';
  time: string;
  title: string;
}

interface DataState {
  dialog: boolean;
  orderInfo: {
    actualPayPrice?: null | number;
    agentName?: string;
    agentPhone?: string;
    berth?: string;
    cancelTime?: string;
    couponPrice: number;
    createTime?: string;
    delivered?: boolean;
    deliverTime: string;
    deliveryTask?: any;
    deliveryWay: string;
    deliveryWindowEnd?: string;
    deliveryWindowStart?: string;
    freightPrice: number;
    id: string;
    memberDiscountPrice?: number;
    orderDelivery: any;
    orderItemList: any[];
    orderNo: string;
    paymentPrice: number;
    paymentTime: string;
    paymentType: string;
    payStatus: string;
    payVouchers?: string;
    pointsDeductPrice?: number;
    pointsMultiplier?: number;
    pointsUsed?: number;
    portCode?: string;
    portName?: string;
    promoPrice?: number;
    purchaseScene?: string;
    receiverTime: string;
    recipientAddress: string;
    recipientArea: string;
    recipientCity: string;
    recipientName: string;
    recipientPhone: string;
    recipientProvince: string;
    remark?: string;
    /** 归属销售（下单时快照，换绑不影响历史订单） */
    salesStaffId?: string;
    salesStaffName?: string;
    sharedCartId?: string;
    status: string;
    totalPrice: number;
    userId?: string;
    vesselId?: string;
    vesselName?: string;
  };
  userInfo: {
    avatarUrl: string;
    nickname: string;
    phone: string;
  };
  stepActive: number;
}

const route = useRoute();
const router = useRouter();
const { toClipboard } = useClipboard();

const state = reactive<DataState>({
  orderInfo: {
    id: '',
    orderNo: '',
    payStatus: '',
    paymentType: '',
    paymentTime: '',
    paymentPrice: 0,
    totalPrice: 0,
    freightPrice: 0,
    couponPrice: 0,
    receiverTime: '',
    deliverTime: '',
    deliveryWay: '',
    orderItemList: [],
    status: '',
    recipientName: '',
    recipientPhone: '',
    recipientProvince: '',
    recipientCity: '',
    recipientArea: '',
    recipientAddress: '',
    orderDelivery: null,
  },
  userInfo: { avatarUrl: '', phone: '', nickname: '' },
  stepActive: 0,
  dialog: false,
});

const loading = ref(false);
const refDeliver = ref();
const promotionSnapshots = ref<any[]>([]);

/**
 * 侧栏悬浮常驻依赖页头的实际高度：页头吸顶且高度会随窄屏换行变化，
 * 用 ResizeObserver 把高度写回 CSS 变量 `--od-head-h`，页面只读变量不硬编码。
 * 页头在订单数据返回后才渲染（v-if），所以用 watch 监听 ref 出现，而不是只在 mounted 里绑定一次。
 */
const headRef = ref<HTMLElement | null>(null);
const headHeight = ref(0);
let headResizeObserver: null | ResizeObserver = null;
const observeHead = (element: HTMLElement | null) => {
  headResizeObserver?.disconnect();
  headResizeObserver = null;
  if (!element) {
    headHeight.value = 0;
    return;
  }
  const updateHeadHeight = () => {
    headHeight.value = element.offsetHeight;
  };
  updateHeadHeight();
  headResizeObserver = new ResizeObserver(updateHeadHeight);
  headResizeObserver.observe(element);
};
watch(headRef, observeHead, { flush: 'post' });
onBeforeUnmount(() => {
  headResizeObserver?.disconnect();
  headResizeObserver = null;
});

// 字典
const { pay_type, delivery_way, order_item_status } = useDict(
  'pay_type',
  'delivery_way',
  'order_item_status',
);

/** 金额类字段的“有值”判断：接口里 0 / null / 空串都按 0 处理 */
const positive = (value: any) => Number(value) > 0;

/** 带金额的字段统一两位小数，避免同列出现 9.5 / 9.50 抖动 */
const money = (value: any) => formatMoney(value ?? 0);

/** 步骤条/时间轴的时间留到分钟即可，完整时间在页头与信息卡展示 */
const shortTime = (time?: string) => {
  if (!time) {
    return '';
  }
  const matched = String(time).match(
    /^\d{4}-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})/,
  );
  return matched
    ? `${matched[1]}-${matched[2]} ${matched[3]}:${matched[4]}`
    : String(time);
};

// 付款凭证快照（确认收款时登记的 JSON 数组），仅货到付款已收款订单有值
const payVoucherList = computed<{ materialId: string; materialUrl: string }[]>(
  () => {
    const raw = state.orderInfo.payVouchers;
    if (!raw) {
      return [];
    }
    try {
      const list = JSON.parse(raw);
      return Array.isArray(list) ? list : [];
    } catch {
      return [];
    }
  },
);

// 货到付款订单在确认收款前（payStatus=0）买家尚未付款，文案不能宣称「买家已付款」
const isCodUnpaid = computed(
  () =>
    state.orderInfo.paymentType === '3' && state.orderInfo.payStatus !== '1',
);

// 步骤条第 3 步口径：仅上门自提（way=2）由买家自取为「买家待提货」，
// 快递（1）/商城配送（3）/内部配送（4）均由承运方送达、买家确认收货，
// 与状态文案（商家已发货等待签收 / 司机配送中）保持一致
const receiveStepTitle = computed(() =>
  state.orderInfo.deliveryWay === '2' ? '买家待提货' : '买家待收货',
);

/** 共享采购订单回到来源车：核对成员与明细（按钮按 sharedcart:get 权限显隐） */
const openSharedCart = () => {
  const sharedCartId = state.orderInfo.sharedCartId;
  if (!sharedCartId) {
    return;
  }
  router.push({ path: '/order/shared-cart', query: { id: sharedCartId } });
};

/** 发货/派单能力：仅待发货可操作；公司配送已派单（任务状态非待派单）后不再重复派单 */
const canDeliver = computed(() => {
  const order = state.orderInfo;
  if (order.status !== '2') {
    return false;
  }
  if (order.deliveryWay === '3' || order.deliveryWay === '4') {
    return !order.deliveryTask || order.deliveryTask.status === '1';
  }
  return true;
});

/** 自提订单备货完成（待收货）后由商家点「提货」完成订单 */
const canSelfFetch = computed(
  () => state.orderInfo.status === '3' && state.orderInfo.deliveryWay === '2',
);

const deliverLabel = computed(() =>
  ['3', '4'].includes(state.orderInfo.deliveryWay) ? '派单发货' : '发货',
);

/**
 * 确认收款入口判据：货到付款 + 未收款 + 货物已送达（与列表页同口径）。
 * delivered 由后端下发（isDelivered 判定：已完成、自提到店、或配送任务已送达/签收），
 * 不能只认已完成——客户常当面付款却不在小程序点确认收货，只认完成会把入口拖到超时自动收货之后。
 */
const canConfirmPay = computed(() => {
  const order = state.orderInfo;
  return (
    order.paymentType === '3' &&
    order.payStatus === '0' &&
    order.delivered === true &&
    order.status !== '11'
  );
});

/** 公司配送（3/4）由配送任务驱动，详情页展示任务进度而不是快递轨迹 */
const isTaskDriven = computed(() =>
  ['3', '4'].includes(state.orderInfo.deliveryWay),
);

const deliveryTask = computed(() => state.orderInfo.deliveryTask || null);

/** 配送任务状态文案（与配送任务页、发货抽屉保持同一口径） */
const TASK_STATUS_VIEW: Record<string, { label: string; type: TagType }> = {
  '1': { label: '待派单', type: 'info' },
  '2': { label: '待取货', type: 'warning' },
  '3': { label: '配货中', type: 'warning' },
  '4': { label: '待送达', type: 'primary' },
  '5': { label: '已送达', type: 'success' },
  '6': { label: '已签收', type: 'success' },
  '7': { label: '已取消', type: 'info' },
  '8': { label: '异常', type: 'danger' },
  '9': { label: '待退回', type: 'danger' },
};
const taskStatusView = computed(() => {
  const status = deliveryTask.value?.status;
  return status
    ? TASK_STATUS_VIEW[status] || { label: status, type: 'info' as TagType }
    : null;
});

/** 配送任务里程碑：派单 → 取货 → 出发 → 送达 → 签收 */
const taskMilestones = computed(() => {
  const task = deliveryTask.value;
  if (!task) {
    return [] as { label: string; state: OrderStep['state']; time: string }[];
  }
  const milestones = [
    { label: '派单', time: task.assignTime },
    { label: '取货', time: task.pickUpTime },
    { label: '出发', time: task.departTime },
    { label: '送达', time: task.arriveTime },
    { label: '签收', time: task.signTime },
  ];
  const firstPending = milestones.findIndex((item) => !item.time);
  return milestones.map((item, index) => {
    let state: OrderStep['state'] = 'todo';
    if (item.time) {
      state = 'done';
    } else if (index === firstPending) {
      state = 'active';
    }
    return {
      label: item.label,
      time: shortTime(item.time),
      state,
    };
  });
});

/** 订单整体状态在页头/状态区的呈现口径 */
const statusView = computed<StatusView>(() => {
  const order = state.orderInfo;
  const codUnpaid = isCodUnpaid.value;
  // 退款/关闭/未知状态不套用「下单→发货→收货→完成」的正常流程条
  const showSteps = !['5', '6', '11'].includes(order.status);
  const way = order.deliveryWay;
  switch (order.status) {
    case '1': {
      return {
        tone: 'danger',
        label: '待付款',
        icon: Clock,
        headline: '等待买家付款',
        desc: '超时系统自动取消订单并返还库存。',
        showSteps,
      };
    }
    case '2': {
      let headline = '';
      if (way === '1') {
        headline = codUnpaid
          ? '买家已下单（货到付款），等待商家发货'
          : '买家已付款，等待商家发货';
      } else if (way === '2') {
        headline = codUnpaid
          ? '买家已下单（货到付款），请尽快备货'
          : '买家已付款，请尽快备货';
      } else {
        headline = codUnpaid
          ? '买家已下单（货到付款），等待安排司机配送'
          : '买家已付款，等待安排司机配送';
      }
      const desc = codUnpaid
        ? `该订单为货到付款，买家将在收货时支付货款，请尽快${
            way === '1' ? '发货' : '安排配送'
          }，收款到账后请及时确认收款。`
        : `买家已付款至待结算账户，请尽快${
            way === '1' ? '发货' : '安排配送'
          }，否则买家有权申请退款。`;
      return {
        tone: 'primary',
        label: way === '1' ? '待发货' : '待备货',
        icon: Van,
        headline,
        desc,
        showSteps,
      };
    }
    case '3': {
      let headline = '';
      if (way === '1') {
        headline = '商家已发货，等待买家签收。';
      } else if (way === '2') {
        headline = '商家已备货，等待买家提货。';
      } else {
        headline = '司机配送中，等待买家确认收货。';
      }
      return {
        tone: 'warning',
        label: '待收货',
        icon: Van,
        headline,
        desc:
          way === '2'
            ? '买家到店提货后，订单自动完成。'
            : '买家签收后，订单自动完成。',
        showSteps,
      };
    }
    case '4': {
      return {
        tone: 'success',
        label: '已完成',
        extraLabel: codUnpaid ? '待确认收款' : undefined,
        icon: CircleCheckFilled,
        headline: '交易完成',
        desc: codUnpaid
          ? '订单已完成，货款为货到付款（线下收取）。'
          : '订单已完成，货款已支付。',
        showSteps,
      };
    }
    case '5': {
      return {
        tone: 'warning',
        label: '退款中',
        icon: WarningFilled,
        headline: '退款处理中',
        desc: '订单存在退款申请，请到商城退单中跟进处理。',
        showSteps,
      };
    }
    case '11': {
      return {
        tone: 'info',
        label: '已取消',
        icon: CircleCloseFilled,
        headline: '交易已关闭',
        desc: '订单已取消，不再进行发货与收款。',
        showSteps: false,
      };
    }
    default: {
      return {
        tone: 'info',
        label: '处理中',
        icon: Clock,
        headline: '订单状态处理中',
        desc: '',
        showSteps,
      };
    }
  }
});

/** 流程条：状态与当前节点由订单 status 推导，保持与列表页一致 */
const steps = computed<OrderStep[]>(() => {
  const order = state.orderInfo;
  const way = order.deliveryWay;
  const active = Number.isFinite(state.stepActive) ? state.stepActive : 0;
  const titles = [
    isCodUnpaid.value ? '买家已下单' : '买家待付款',
    way === '1' ? '商家待发货' : '商家待备货',
    receiveStepTitle.value,
    '交易完成',
  ];
  const times = [
    shortTime(order.createTime),
    shortTime(order.deliverTime || deliveryTask.value?.assignTime),
    shortTime(
      order.receiverTime ||
        deliveryTask.value?.signTime ||
        deliveryTask.value?.arriveTime,
    ),
    // 第 4 步是「交易完成」：完成时间取收货/关闭时间，付款时间可能早于发货，不能当完成时间
    shortTime(order.receiverTime || order.cancelTime),
  ];
  return titles.map((title, index) => {
    let stepState: OrderStep['state'] = 'todo';
    if (index < active) {
      stepState = 'done';
    } else if (index === active) {
      stepState = 'active';
    }
    let time = '';
    if (index < active) {
      time = times[index] || '';
    } else if (stepState === 'active' && index === 3) {
      time = isCodUnpaid.value ? '待确认收款' : times[3] || '';
    }
    return { title, state: stepState, time };
  });
});

/** 商品按分类分组：组内保持原始顺序（共享采购按人拆行的归属语义不被打乱） */
const itemGroups = computed<OrderCategoryGroup[]>(() =>
  groupOrderItems(state.orderInfo.orderItemList),
);

/** 商品件数 / 分类数 / 整单唯一订购人：商品清单区头与页脚共用 */
const itemSummary = computed(() => {
  const items = state.orderInfo.orderItemList ?? [];
  const contributors = new Set<string>();
  for (const item of items) {
    if (item.contributorName) {
      contributors.add(item.contributorName);
    }
  }
  return {
    quantity: items.reduce(
      (sum: number, item: any) => sum + (Number(item.buyQuantity) || 0),
      0,
    ),
    categoryCount: itemGroups.value.length,
    // 共享采购按人拆行时整单可能只有同一个人：只在区头展示一次，避免每行重复
    contributor: contributors.size === 1 ? [...contributors][0] : '',
  };
});

/** 整单只有一个订购人时，行内不再重复展示订购人 chip */
const showItemContributor = (item: any) =>
  Boolean(item.contributorName) && !itemSummary.value.contributor;

/** 分组件数：与区头「N 件商品」同口径（购买数量求和），不要用行数冒充件数 */
const groupQuantity = (group: OrderCategoryGroup) =>
  group.items.reduce(
    (sum: number, item: any) => sum + (Number(item.buyQuantity) || 0),
    0,
  );

/** 商品清单区头描述：件数/分类数，整单同一订购人时补一次订阅人 */
const sectionDescription = computed(() => {
  const base = `${itemSummary.value.quantity} 件商品 · ${itemSummary.value.categoryCount} 个分类`;
  return itemSummary.value.contributor
    ? `${base} · 订购人 ${itemSummary.value.contributor}`
    : base;
});

/** 金额明细：只有非 0 的优惠行才展示，避免整屏 -¥0.00 */
const discountRows = computed(() => {
  const order = state.orderInfo;
  const rows: { label: string; value: number }[] = [];
  if (positive(order.couponPrice)) {
    rows.push({ label: '优惠券', value: Number(order.couponPrice) });
  }
  if (positive(order.memberDiscountPrice)) {
    rows.push({ label: '会员优惠', value: Number(order.memberDiscountPrice) });
  }
  if (positive(order.pointsDeductPrice)) {
    rows.push({
      label: `积分抵扣（${order.pointsUsed || 0} 积分）`,
      value: Number(order.pointsDeductPrice),
    });
  }
  // 营销优惠始终给一行总额（快照明细紧跟其后），否则有快照时账本里看不到优惠合计；
  // 历史单 promoPrice 可能为 0 但仍有快照，用快照金额之和兜底
  const promoSnapshotTotal = promotionSnapshots.value.reduce(
    (sum: number, snapshot: any) => sum + Number(snapshot.discountAmount || 0),
    0,
  );
  const promoTotal = Math.max(
    Number(order.promoPrice || 0),
    promoSnapshotTotal,
  );
  if (promoTotal > 0) {
    rows.push({ label: '营销优惠', value: promoTotal });
  }
  return rows;
});

/** 实收金额低于应收时（线下收款让价）单独回显应收，避免金额对不上 */
const showReceivable = computed(() => {
  const order = state.orderInfo;
  const hasActualPay =
    order.actualPayPrice !== null && order.actualPayPrice !== undefined;
  const hasPaymentPrice =
    order.paymentPrice !== null && order.paymentPrice !== undefined;
  return (
    hasActualPay &&
    hasPaymentPrice &&
    Number(order.actualPayPrice) < Number(order.paymentPrice)
  );
});

const finalAmount = computed(
  () => state.orderInfo.actualPayPrice ?? state.orderInfo.paymentPrice,
);

/**
 * 合计行口径：未收款 ≠ 实收。
 * 关闭单叫「订单金额」，货到付款未确认叫「应收金额」，
 * 线上未支付叫「应付金额」，其余（已收款）才是「实收金额」。
 */
const finalAmountLabel = computed(() => {
  const order = state.orderInfo;
  if (order.status === '11') {
    return '订单金额';
  }
  if (isCodUnpaid.value) {
    return order.actualPayPrice === null || order.actualPayPrice === undefined
      ? '应收金额'
      : '实收金额';
  }
  return order.payStatus === '1' ? '实收金额' : '应付金额';
});

/** 买家收货地址（快递单才有省市区，内部配送可能为空） */
const fullAddress = computed(() => {
  const order = state.orderInfo;
  return [
    order.recipientProvince,
    order.recipientCity,
    order.recipientArea,
    order.recipientAddress,
  ]
    .filter(Boolean)
    .join(' ');
});

/**
 * 商品行状态展示策略：**异常状态必显，正常进度状态不重复展示**。
 *
 * <p>旧版给每一行都挂状态标签，整单已完成时 12 行「已完成」纯噪音；
 * 但也不能按「多数状态」隐藏——售后待审/退款完成恰好是整单唯一一行时会被吞掉
 * （真实缺陷：order.status=2 且唯一 item.status=3 时旧版显示「售后处理中」，按多数隐藏则看不到）。
 * 因此：</p>
 * <ul>
 *   <li>售后处理中（3）/ 退款完成（6）等异常状态：无条件展示；</li>
 *   <li>与整单正常进度一致的行（如下表映射）：隐藏，由状态卡/流程条承担；</li>
 *   <li>其余（部分发货、部分退款、共享采购拆行差异等）：展示。</li>
 * </ul>
 */
const normalItemStatus = computed(() => {
  switch (state.orderInfo.status) {
    // 待付款
    case '1': {
      return '0';
    }
    // 待发货 / 待备货
    case '2': {
      return '1';
    }
    // 待收货 / 待提货
    case '3': {
      return '2';
    }
    // 已完成
    case '4': {
      return '8';
    }
    // 已取消
    case '11': {
      return '0';
    }
    // 退款中 / 历史未知状态：不抑制，全部展示
    default: {
      return '';
    }
  }
});

/** 异常状态：无论整单进度如何都必须让商家看到（售后处理中 / 退款完成） */
const EXCEPTIONAL_ITEM_STATUS_META: Record<
  string,
  { label: string; type: TagType }
> = {
  '3': { label: '售后处理中', type: 'warning' },
  '6': { label: '退款完成', type: 'success' },
};

interface ItemStatusView {
  /** 正常进度状态时隐藏，避免一整屏重复标签 */
  hidden: boolean;
  label: string;
  type: TagType;
}

const itemStatusView = (item: any): ItemStatusView => {
  const order = state.orderInfo;
  const status = String(item.status);
  // 1. 异常状态（售后处理中/退款完成）：无论整单进度如何都必须让商家看到
  const exceptional = EXCEPTIONAL_ITEM_STATUS_META[status];
  if (exceptional) {
    return { hidden: false, ...exceptional };
  }
  // 2. 上门自提：备货中/待自提对仓库有作业含义，保持旧版逐行可见
  if (order.deliveryWay === '2' && status === '1') {
    return { hidden: false, label: '备货中', type: 'warning' };
  }
  if (order.deliveryWay === '2' && status === '2') {
    return { hidden: false, label: '待自提', type: 'success' };
  }
  // 3. 与整单正常进度一致（如下表映射）：由状态卡/流程条承担，不逐行重复
  if (status === normalItemStatus.value) {
    return { hidden: true, label: '', type: 'info' };
  }
  if (status === '0') {
    return order.status === '11'
      ? { hidden: false, label: '已取消', type: 'info' }
      : { hidden: false, label: '待付款', type: 'warning' };
  }
  const option = (order_item_status?.value || []).find(
    (dict: any) => String(dict.value) === status,
  );
  if (option) {
    return {
      hidden: false,
      label: option.label,
      type: (option.showClass || 'info') as TagType,
    };
  }
  return { hidden: false, label: '—', type: 'info' };
};

/** 是否任何一行需要状态列：整单状态齐时不占一列空白 */
const hasItemStatus = computed(() =>
  (state.orderInfo.orderItemList || []).some(
    (item: any) => !itemStatusView(item).hidden,
  ),
);

/** 商品行附加信息：仅展示真正有值的运费/优惠，不制造空列 */
const itemExtras = (item: any) => {
  const extras: string[] = [];
  if (positive(item.freightPrice)) {
    extras.push(`运费 ¥${money(item.freightPrice)}`);
  }
  if (positive(item.couponPrice)) {
    extras.push(`优惠 -¥${money(item.couponPrice)}`);
  }
  return extras;
};

const getDetail = () => {
  const { id }: any = route.query;
  if (!id) {
    return;
  }
  loading.value = true;
  getById(id)
    .then((response) => {
      // 订单不存在时接口返回 data:null：保留空态，不能把 null 赋给 orderInfo（模板会取值报错）
      if (!response || !response.id) {
        return;
      }
      state.orderInfo = response;
      const status = state.orderInfo.status;
      state.stepActive = status === '7' ? 1 : Number(status) - 1;
      if (response.userId) {
        getUser(response.userId);
      }
      getPromotionSnapshots(id as string)
        .then((list) => {
          promotionSnapshots.value = list;
        })
        .catch(() => {});
    })
    .catch(() => {})
    .finally(() => {
      loading.value = false;
    });
};

// 查询用户信息：买家账号可能已注销（接口返回 data:null），必须给空对象兜底，
// 否则模板读取 userInfo.nickname 会抛渲染错误、整页白屏
const EMPTY_USER_INFO: DataState['userInfo'] = {
  avatarUrl: '',
  nickname: '',
  phone: '',
};
const getUser = (userId: string) => {
  getUserById(userId)
    .then((response) => {
      state.userInfo = response || { ...EMPTY_USER_INFO };
    })
    .catch(() => {
      state.userInfo = { ...EMPTY_USER_INFO };
    });
};

/** 复制订单号：列表/其它页面常用，避免手工选中长串数字 */
const copyOrderNo = async () => {
  try {
    await toClipboard(state.orderInfo.orderNo || state.orderInfo.id);
    ElMessage.success('订单号已复制');
  } catch {
    ElMessage.error('复制失败，请手动选择订单号');
  }
};

/**
 * 发货
 */
const deliverOrder = (row: any) => {
  refDeliver.value.initPage(row.id, row.orderLogisticsId);
};

/**
 * 自提
 */
const selffetchOrder = () => {
  ElMessageBox.confirm('此操作将完成订单提货，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    selffetchObj({ id: state.orderInfo.id })
      .then(() => {
        ElMessage.success('提货成功');
        getDetail();
      })
      .catch(() => {});
  });
};

/**
 * 货到付款确认收款（实收金额 + 付款凭证上传），与列表页同口径。
 */
interface PayVoucherFile extends UploadUserFile {
  materialId?: string;
}
const payConfirmState = reactive({
  visible: false,
  submitting: false,
  orderNo: '',
  // 应收金额（总金额-优惠+运费），线下收款可能有折扣
  receivable: 0,
  actualPayPrice: 0,
  voucherFiles: [] as PayVoucherFile[],
});
const openPayConfirm = () => {
  const order = state.orderInfo;
  payConfirmState.orderNo = order.orderNo || order.id;
  payConfirmState.receivable = Number(
    order.paymentPrice ?? order.totalPrice ?? 0,
  );
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
  confirmOfflinePayment(state.orderInfo.id, {
    actualPayPrice: payConfirmState.actualPayPrice,
    voucherMaterialIds: payConfirmState.voucherFiles
      .map((file) => file.materialId)
      .filter((id): id is string => id !== undefined),
  })
    .then(() => {
      ElMessage.success('确认收款成功');
      payConfirmState.visible = false;
      getDetail();
    })
    .catch(() => {})
    .finally(() => {
      payConfirmState.submitting = false;
    });
};

getDetail();
</script>

<template>
  <div class="hx-layout-container" v-loading="loading">
    <div
      class="hx-layout-container-auto hx-layout-container-view od-page"
      :class="{ 'od-page--empty': !loading && !state.orderInfo.id }"
      :style="headHeight > 0 ? { '--od-head-h': `${headHeight}px` } : undefined"
    >
      <template v-if="state.orderInfo.id">
        <!-- 页头：订单号 + 关键时间 + 主操作，滚动时吸顶常驻 -->
        <header ref="headRef" class="od-head">
          <div class="od-head__main">
            <div class="od-head__title">
              <span class="od-head__label">订单号</span>
              <h1 class="od-head__no">
                {{ state.orderInfo.orderNo || state.orderInfo.id }}
              </h1>
              <ElTooltip content="复制订单号" placement="top">
                <button
                  aria-label="复制订单号"
                  class="od-icon-btn"
                  type="button"
                  @click="copyOrderNo"
                >
                  <ElIcon><CopyDocument /></ElIcon>
                </button>
              </ElTooltip>
              <span class="od-scene">
                {{ purchaseSceneText(state.orderInfo.purchaseScene) }}
              </span>
              <ElButton
                v-if="state.orderInfo.sharedCartId"
                v-access:code="'sharedcart:get'"
                class="od-link-btn"
                link
                type="primary"
                @click="openSharedCart"
              >
                来源共享购物车
              </ElButton>
            </div>
            <div class="od-head__meta">
              <span class="od-meta">
                <span class="od-meta__k">下单时间</span>
                {{ state.orderInfo.createTime || '—' }}
              </span>
              <span v-if="state.orderInfo.salesStaffName" class="od-meta">
                <span class="od-meta__k">归属销售</span>
                {{ state.orderInfo.salesStaffName }}
              </span>
              <span v-if="state.orderInfo.paymentTime" class="od-meta">
                <span class="od-meta__k">付款时间</span>
                {{ state.orderInfo.paymentTime }}
              </span>
              <span v-if="state.orderInfo.deliverTime" class="od-meta">
                <span class="od-meta__k">发货时间</span>
                {{ state.orderInfo.deliverTime }}
              </span>
              <span v-if="state.orderInfo.receiverTime" class="od-meta">
                <span class="od-meta__k">收货时间</span>
                {{ state.orderInfo.receiverTime }}
              </span>
              <span v-if="state.orderInfo.cancelTime" class="od-meta">
                <span class="od-meta__k">取消时间</span>
                {{ state.orderInfo.cancelTime }}
              </span>
            </div>
          </div>
          <!-- 右侧：主操作 + 买家信息。买家从侧栏上移后，右栏能一屏放下金额/配送/备注 -->
          <div class="od-head__aside">
            <ElButton
              v-if="canDeliver || canSelfFetch"
              v-access:code="'order:orderinfo:deliver'"
              :icon="canSelfFetch ? Box : Van"
              type="primary"
              @click="
                canSelfFetch ? selffetchOrder() : deliverOrder(state.orderInfo)
              "
            >
              {{ canSelfFetch ? '提货' : deliverLabel }}
            </ElButton>
            <div
              class="od-head__buyer"
              :class="{ 'has-divider': canDeliver || canSelfFetch }"
              title="买家信息"
            >
              <ElAvatar :size="32" :src="state.userInfo.avatarUrl" />
              <div class="od-head__buyer-main">
                <span class="od-head__buyer-name">
                  {{ state.userInfo.nickname || '—' }}
                </span>
                <span
                  v-if="state.userInfo.phone"
                  class="od-head__buyer-phone od-num"
                >
                  {{ state.userInfo.phone }}
                </span>
              </div>
            </div>
          </div>
        </header>

        <!-- 状态区：一眼回答「现在什么状态、下一步做什么、走到哪一步」 -->
        <section class="od-hero" :class="`od-hero--${statusView.tone}`">
          <div class="od-hero__status">
            <span class="od-hero__icon">
              <ElIcon><component :is="statusView.icon" /></ElIcon>
            </span>
            <div class="od-hero__text">
              <div class="od-hero__headline">
                {{ statusView.headline }}
                <span class="od-hero__pill">{{ statusView.label }}</span>
                <span
                  v-if="statusView.extraLabel"
                  class="od-hero__pill od-hero__pill--warn"
                >
                  {{ statusView.extraLabel }}
                </span>
              </div>
              <p v-if="statusView.desc" class="od-hero__desc">
                {{ statusView.desc }}
              </p>
            </div>
          </div>
          <template v-if="statusView.showSteps">
            <span aria-hidden="true" class="od-hero__divider"></span>
            <div class="od-steps">
              <div
                v-for="(step, index) in steps"
                :key="step.title"
                class="od-step"
                :class="`is-${step.state}`"
              >
                <div class="od-step__marker">
                  <ElIcon v-if="step.state === 'done'"><Check /></ElIcon>
                  <template v-else>{{ index + 1 }}</template>
                </div>
                <div class="od-step__label">{{ step.title }}</div>
                <div class="od-step__time">{{ step.time || '&nbsp;' }}</div>
              </div>
            </div>
          </template>
        </section>

        <div class="od-grid">
          <!-- 主栏：商品清单 + 物流/配送 -->
          <main class="od-main">
            <PageSection :description="sectionDescription" title="商品清单">
              <template #extra>
                <span class="od-items__total">
                  商品金额
                  <b class="od-num">
                    ¥{{ money(state.orderInfo.totalPrice) }}
                  </b>
                </span>
              </template>

              <div
                v-if="itemGroups.length > 0"
                class="od-items"
                :class="{ 'od-items--no-status': !hasItemStatus }"
              >
                <div
                  v-for="group in itemGroups"
                  :key="group.key"
                  class="od-group"
                >
                  <div class="od-group__head">
                    <span class="od-group__name">{{ group.key }}</span>
                    <span class="od-group__count">
                      {{ groupQuantity(group) }} 件
                    </span>
                    <span class="od-group__line"></span>
                  </div>
                  <div
                    v-for="item in group.items"
                    :key="item.id"
                    class="od-item"
                  >
                    <ElImage
                      class="od-item__pic"
                      :preview-src-list="[item.picUrl]"
                      :preview-teleported="true"
                      :src="item.picUrl"
                      fit="cover"
                    >
                      <!-- 商品图可能已删除/外链失效：给图片位一个图标兜底，不留白框 -->
                      <template #error>
                        <span class="od-item__pic-fallback">
                          <ElIcon><Picture /></ElIcon>
                        </span>
                      </template>
                    </ElImage>
                    <div class="od-item__body">
                      <div class="od-item__name" :title="item.spuName">
                        {{ item.spuName }}
                      </div>
                      <p v-if="item.specsInfo" class="od-item__spec">
                        规格：{{ item.specsInfo }}
                      </p>
                      <div
                        v-if="
                          showItemContributor(item) ||
                          item.memberRemark ||
                          itemExtras(item).length > 0
                        "
                        class="od-item__meta"
                      >
                        <span
                          v-if="showItemContributor(item)"
                          class="od-item__chip"
                          :title="`订购人：${item.contributorName}`"
                        >
                          订购人 {{ item.contributorName }}
                        </span>
                        <span
                          v-if="item.memberRemark"
                          class="od-item__chip"
                          :title="`成员备注：${item.memberRemark}`"
                        >
                          成员备注 {{ item.memberRemark }}
                        </span>
                        <span
                          v-for="extra in itemExtras(item)"
                          :key="extra"
                          class="od-item__extra"
                        >
                          {{ extra }}
                        </span>
                      </div>
                    </div>
                    <div class="od-item__amount">
                      <div class="od-item__unit od-num">
                        ¥{{ money(item.salesPrice) }} × {{ item.buyQuantity }}
                      </div>
                      <div class="od-item__subtotal od-num">
                        ¥{{ money(item.paymentPrice) }}
                      </div>
                    </div>
                    <div
                      v-if="!itemStatusView(item).hidden"
                      class="od-item__status"
                    >
                      <ElTag
                        :type="itemStatusView(item).type"
                        disable-transitions
                        round
                      >
                        {{ itemStatusView(item).label }}
                      </ElTag>
                    </div>
                  </div>
                </div>
              </div>
              <ElEmpty v-else :image-size="72" description="暂无商品明细" />
            </PageSection>

            <!-- 快递：包裹信息 + 物流轨迹。公司配送单若已派单，以「配送进度」为准，
                 避免历史残留的 orderDelivery 与配送任务给出互相矛盾的签收状态 -->
            <PageSection
              v-if="
                state.orderInfo.orderDelivery && !(isTaskDriven && deliveryTask)
              "
              :description="state.orderInfo.orderDelivery.logisticsCompanyName"
              title="物流信息"
            >
              <template #extra>
                <ElTag
                  :type="
                    state.orderInfo.orderDelivery.isCheck === '1'
                      ? 'success'
                      : 'info'
                  "
                  disable-transitions
                  round
                >
                  {{
                    state.orderInfo.orderDelivery.isCheck === '1'
                      ? '已签收'
                      : '未签收'
                  }}
                </ElTag>
              </template>
              <div class="od-kv-grid">
                <div class="od-kv">
                  <span class="od-kv__k">快递公司</span>
                  <span class="od-kv__v">
                    {{
                      state.orderInfo.orderDelivery.logisticsCompanyName || '—'
                    }}
                  </span>
                </div>
                <div class="od-kv">
                  <span class="od-kv__k">快递单号</span>
                  <span class="od-kv__v od-num">
                    {{ state.orderInfo.orderDelivery.logisticsNo || '—' }}
                  </span>
                </div>
                <div class="od-kv">
                  <span class="od-kv__k">发货时间</span>
                  <span class="od-kv__v od-num">
                    {{ state.orderInfo.orderDelivery.deliverTime || '—' }}
                  </span>
                </div>
              </div>
              <ol
                v-if="
                  state.orderInfo.orderDelivery.logisticsList &&
                  state.orderInfo.orderDelivery.logisticsList.length > 0
                "
                class="od-track"
              >
                <li
                  v-for="(track, trackIndex) in state.orderInfo.orderDelivery
                    .logisticsList"
                  :key="trackIndex"
                  class="od-track__item"
                  :class="{ 'is-latest': trackIndex === 0 }"
                >
                  <span class="od-track__dot"></span>
                  <p class="od-track__text">{{ track.logisticsContext }}</p>
                  <span class="od-track__time">{{ track.logisticsTime }}</span>
                </li>
              </ol>
            </PageSection>

            <!-- 公司配送：配送任务进度（派单 → 取货 → 出发 → 送达 → 签收）。
                 已派单时以本卡为准，历史残留的 orderDelivery 不再同时展示签收状态 -->
            <PageSection
              v-if="isTaskDriven && deliveryTask"
              :description="`任务号 ${deliveryTask.taskNo || '—'}`"
              title="配送进度"
            >
              <template #extra>
                <ElTag
                  v-if="taskStatusView"
                  :type="taskStatusView.type"
                  disable-transitions
                  round
                >
                  {{ taskStatusView.label }}
                </ElTag>
              </template>
              <div class="od-kv-grid">
                <div class="od-kv">
                  <span class="od-kv__k">配送员</span>
                  <span class="od-kv__v">
                    {{ deliveryTask.staffName || '待派单' }}
                  </span>
                </div>
                <div class="od-kv">
                  <span class="od-kv__k">配送目的地</span>
                  <span class="od-kv__v">
                    {{ deliveryTask.vesselName || '—' }}
                    <template v-if="deliveryTask.portName">
                      · {{ deliveryTask.portName }}
                      {{ deliveryTask.berth }}
                    </template>
                  </span>
                </div>
                <div v-if="deliveryTask.exceptionDesc" class="od-kv">
                  <span class="od-kv__k">异常说明</span>
                  <span class="od-kv__v">{{ deliveryTask.exceptionDesc }}</span>
                </div>
              </div>
              <div v-if="taskMilestones.length > 0" class="od-milestones">
                <div
                  v-for="milestone in taskMilestones"
                  :key="milestone.label"
                  class="od-milestone"
                  :class="`is-${milestone.state}`"
                >
                  <span class="od-milestone__dot"></span>
                  <div class="od-milestone__label">{{ milestone.label }}</div>
                  <div class="od-milestone__time od-num">
                    {{ milestone.time || '&nbsp;' }}
                  </div>
                </div>
              </div>
            </PageSection>
          </main>

          <!-- 侧栏：金额、买家、配送、备注 -->
          <aside class="od-side">
            <PageSection title="金额汇总">
              <div class="od-receipt">
                <div class="od-receipt__row">
                  <span class="od-receipt__label">商品总价</span>
                  <span class="od-num">
                    ¥{{ money(state.orderInfo.totalPrice) }}
                  </span>
                </div>
                <div class="od-receipt__row">
                  <span class="od-receipt__label">运费</span>
                  <span class="od-num">
                    ¥{{ money(state.orderInfo.freightPrice) }}
                  </span>
                </div>
                <div
                  v-for="row in discountRows"
                  :key="row.label"
                  class="od-receipt__row"
                >
                  <span class="od-receipt__label">{{ row.label }}</span>
                  <span class="od-num od-num--minus">
                    -¥{{ money(row.value) }}
                  </span>
                </div>
                <div
                  v-for="snapshot in promotionSnapshots"
                  :key="snapshot.id"
                  class="od-receipt__row od-receipt__row--sub"
                >
                  <span
                    class="od-receipt__sub-label"
                    :title="snapshot.promotionName"
                  >
                    {{ snapshot.promotionName }}
                  </span>
                  <span class="od-num">
                    -¥{{ money(snapshot.discountAmount) }}
                  </span>
                </div>
                <div v-if="showReceivable" class="od-receipt__row">
                  <span class="od-receipt__label">应收金额</span>
                  <span class="od-num">
                    ¥{{ money(state.orderInfo.paymentPrice) }}
                  </span>
                </div>
                <div class="od-receipt__total">
                  <span class="od-receipt__total-label">
                    {{ finalAmountLabel }}
                  </span>
                  <span class="od-receipt__amount od-num">
                    ¥{{ money(finalAmount) }}
                  </span>
                </div>
                <ElButton
                  v-if="canConfirmPay"
                  v-access:code="'order:orderinfo:payconfirm'"
                  :icon="Money"
                  type="primary"
                  class="od-receipt__pay-btn"
                  @click="openPayConfirm"
                >
                  确认收款
                </ElButton>
                <div
                  v-if="
                    state.orderInfo.paymentType || state.orderInfo.paymentTime
                  "
                  class="od-receipt__meta"
                >
                  <div
                    v-if="state.orderInfo.paymentType"
                    class="od-receipt__row"
                  >
                    <span class="od-receipt__label">付款方式</span>
                    <DictTag
                      :options="pay_type"
                      :value="state.orderInfo.paymentType"
                    />
                  </div>
                  <div
                    v-if="state.orderInfo.paymentTime"
                    class="od-receipt__row"
                  >
                    <span class="od-receipt__label">付款时间</span>
                    <span class="od-num">
                      {{ state.orderInfo.paymentTime }}
                    </span>
                  </div>
                </div>
                <div v-if="payVoucherList.length > 0" class="od-vouchers">
                  <span class="od-vouchers__label">付款凭证</span>
                  <div class="od-vouchers__list">
                    <ElImage
                      v-for="voucher in payVoucherList"
                      :key="voucher.materialId"
                      :initial-index="payVoucherList.indexOf(voucher)"
                      :preview-src-list="
                        payVoucherList.map((v) => v.materialUrl)
                      "
                      :src="voucher.materialUrl"
                      class="od-vouchers__item"
                      fit="cover"
                      preview-teleported
                    >
                      <template #error>
                        <span class="od-item__pic-fallback">
                          <ElIcon><Picture /></ElIcon>
                        </span>
                      </template>
                    </ElImage>
                  </div>
                </div>
              </div>
            </PageSection>

            <PageSection title="配送信息">
              <dl class="od-rows">
                <div class="od-rows__item">
                  <dt class="od-rows__k">配送方式</dt>
                  <dd class="od-rows__v">
                    <DictTag
                      :options="delivery_way"
                      :value="state.orderInfo.deliveryWay"
                    />
                  </dd>
                </div>
                <div v-if="state.orderInfo.recipientName" class="od-rows__item">
                  <dt class="od-rows__k">
                    {{
                      state.orderInfo.deliveryWay === '2' ? '提货人' : '收货人'
                    }}
                  </dt>
                  <dd class="od-rows__v">
                    {{ state.orderInfo.recipientName }}
                  </dd>
                </div>
                <div
                  v-if="state.orderInfo.recipientPhone"
                  class="od-rows__item"
                >
                  <dt class="od-rows__k">联系电话</dt>
                  <dd class="od-rows__v od-num">
                    {{ state.orderInfo.recipientPhone }}
                  </dd>
                </div>
                <!-- 岸上配送（way 3）同样有收货地址；到船单没有地址时自然不渲染 -->
                <div v-if="fullAddress" class="od-rows__item">
                  <dt class="od-rows__k">收货地址</dt>
                  <dd class="od-rows__v">{{ fullAddress }}</dd>
                </div>
                <div v-if="state.orderInfo.vesselName" class="od-rows__item">
                  <dt class="od-rows__k">船舶</dt>
                  <dd class="od-rows__v">{{ state.orderInfo.vesselName }}</dd>
                </div>
                <div v-if="state.orderInfo.portName" class="od-rows__item">
                  <dt class="od-rows__k">港口 / 泊位</dt>
                  <dd class="od-rows__v">
                    {{ state.orderInfo.portName }}
                    <template v-if="state.orderInfo.berth">
                      · {{ state.orderInfo.berth }}
                    </template>
                  </dd>
                </div>
                <div
                  v-if="state.orderInfo.deliveryWindowStart"
                  class="od-rows__item"
                >
                  <dt class="od-rows__k">配送时段</dt>
                  <dd class="od-rows__v od-num">
                    {{ state.orderInfo.deliveryWindowStart }} ~
                    {{ state.orderInfo.deliveryWindowEnd }}
                  </dd>
                </div>
                <div v-if="state.orderInfo.agentName" class="od-rows__item">
                  <dt class="od-rows__k">代办人</dt>
                  <dd class="od-rows__v">
                    {{ state.orderInfo.agentName }}
                    <span
                      v-if="state.orderInfo.agentPhone"
                      class="od-rows__sub od-num"
                    >
                      {{ state.orderInfo.agentPhone }}
                    </span>
                  </dd>
                </div>
              </dl>
            </PageSection>

            <PageSection v-if="state.orderInfo.remark" title="订单备注">
              <p class="od-remark">{{ state.orderInfo.remark }}</p>
            </PageSection>
          </aside>
        </div>

        <Deliver ref="refDeliver" @handle-success="getDetail" />

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
      </template>

      <ElEmpty
        v-else-if="!loading"
        :image-size="96"
        class="od-empty"
        description="订单不存在或已被删除"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
/* ---------- 响应式：窄屏先收侧栏，再收状态区 ---------- */
@media (max-width: 1440px) {
  .od-grid {
    grid-template-columns: minmax(0, 1fr) 340px;
  }
}

/* 1280 视口去掉左侧菜单后仍有约 990px 内容宽，双栏可读；更窄再收成单栏 */
@media (max-width: 1199px) {
  .od-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  /* 单栏时侧栏落到主栏之后，恢复正常文档流，不做悬浮（否则会盖住主栏内容） */
  .od-side {
    position: static;
    max-height: none;
    padding-right: 0;
    margin-right: 0;
    overflow: visible;
  }
}

@media (max-width: 1100px) {
  .od-hero {
    flex-direction: column;
    align-items: stretch;
  }

  .od-hero__status {
    width: auto;
  }

  .od-hero__divider {
    display: none;
  }
}

@media (max-width: 900px) {
  .od-head {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
    padding: 14px 16px 12px;
  }

  /* 页头改纵向后，买家信息与主操作仍靠右收口 */
  .od-head__aside {
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .od-hero {
    margin: 12px 16px;
  }

  .od-grid {
    padding: 0 16px 16px;
  }

  .od-item {
    grid-template-columns: 48px minmax(0, 1fr) 104px;
  }

  .od-items--no-status .od-item {
    grid-template-columns: 48px minmax(0, 1fr) 104px;
  }

  .od-item__pic {
    width: 48px;
    height: 48px;
  }

  .od-item__status {
    grid-column: 2 / -1;
    justify-content: flex-start;
  }
}

@media (prefers-reduced-motion: reduce) {
  .od-icon-btn,
  .od-item {
    transition: none;
  }
}

.od-page.hx-layout-container-auto {
  padding: 0;
}

/* 空态：整页垂直居中，不要贴顶 */
.od-page--empty {
  align-items: center;
  justify-content: center;
}

.od-num {
  font-variant-numeric: tabular-nums;
}

/* ---------- 页头 ---------- */
.od-head {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 18px 24px 14px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
  border-radius: calc(var(--radius) - 2px) calc(var(--radius) - 2px) 0 0;
}

.od-head__main {
  min-width: 0;
}

.od-head__title {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.od-head__label {
  font-size: 13px;
  line-height: 28px;
  color: var(--el-text-color-secondary);
}

.od-head__no {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 28px;
  color: var(--el-text-color-primary);
  letter-spacing: 0.2px;
  overflow-wrap: anywhere;
}

.od-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  background: var(--el-fill-color-light);
  border: none;
  border-radius: 8px;
  transition:
    color 0.15s ease,
    background-color 0.15s ease;
}

.od-icon-btn:hover {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.od-icon-btn:focus-visible {
  outline: 2px solid var(--el-color-primary-light-5);
  outline-offset: 2px;
}

.od-scene {
  padding: 2px 10px;
  font-size: 12px;
  line-height: 20px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 999px;
}

.od-link-btn {
  font-size: 12px;
}

.od-head__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 20px;
  margin-top: 6px;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  line-height: 20px;
  color: var(--el-text-color-secondary);
}

.od-meta__k {
  margin-right: 6px;
  color: var(--el-text-color-placeholder);
}

/* 页头右侧：主 CTA + 买家信息（买家从侧栏上移，给右栏腾出一屏空间） */
.od-head__aside {
  display: flex;
  flex: none;
  gap: 16px;
  align-items: center;
  padding-top: 4px;
}

.od-head__buyer {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.od-head__buyer.has-divider {
  padding-left: 16px;
  border-left: 1px solid var(--el-border-color-lighter);
}

.od-head__buyer-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 18px;
}

.od-head__buyer-name {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.od-head__buyer-phone {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* ---------- 状态区 ---------- */
.od-hero {
  display: flex;
  gap: 24px;
  align-items: center;
  padding: 20px 24px;
  margin: 16px 24px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}

.od-hero--success {
  --od-tone: var(--el-color-success);
  --od-tone-bg: var(--el-color-success-light-9);
}

.od-hero--warning {
  --od-tone: var(--el-color-warning);
  --od-tone-bg: var(--el-color-warning-light-9);
}

.od-hero--primary {
  --od-tone: var(--el-color-primary);
  --od-tone-bg: var(--el-color-primary-light-9);
}

.od-hero--danger {
  --od-tone: var(--el-color-danger);
  --od-tone-bg: var(--el-color-danger-light-9);
}

.od-hero--info {
  --od-tone: var(--el-color-info);
  --od-tone-bg: var(--el-color-info-light-9);
}

.od-hero__status {
  display: flex;
  flex: none;
  gap: 14px;
  align-items: flex-start;
  width: 330px;
}

.od-hero__icon {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  font-size: 22px;
  color: var(--od-tone);
  background: var(--od-tone-bg);
  border-radius: 12px;
}

.od-hero__text {
  min-width: 0;
}

.od-hero__headline {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  line-height: 26px;
  color: var(--el-text-color-primary);
}

.od-hero__pill {
  display: inline-block;
  padding: 1px 8px;
  margin-left: 6px;
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
  vertical-align: 1px;
  color: var(--od-tone);
  background: var(--od-tone-bg);
  border-radius: 999px;
}

/* 附加状态（如货到付款待确认收款）用警示色，与主状态区分 */
.od-hero__pill--warn {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.od-hero__desc {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.od-hero__divider {
  flex: none;
  align-self: stretch;
  width: 1px;
  background: var(--el-border-color-lighter);
}

.od-steps {
  display: flex;
  flex: 1;
  min-width: 0;
}

.od-step {
  position: relative;
  flex: 1;
  min-width: 0;
  padding-top: 2px;
  text-align: center;
}

.od-step:not(:last-child)::after {
  position: absolute;
  top: 13px;
  left: calc(50% + 20px);
  width: calc(100% - 40px);
  height: 2px;
  content: '';
  background: var(--el-border-color);
}

.od-step.is-done:not(:last-child)::after {
  background: var(--el-color-primary);
}

.od-step__marker {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  margin: 0 auto;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-placeholder);
  background: var(--el-fill-color-light);
  border-radius: 50%;
}

.od-step.is-done .od-step__marker {
  color: var(--el-color-white);
  background: var(--el-color-primary);
}

.od-step.is-active .od-step__marker {
  color: var(--el-color-primary);
  background: var(--el-bg-color);
  box-shadow: inset 0 0 0 2px var(--el-color-primary);
}

.od-step__label {
  margin-top: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.od-step.is-active .od-step__label,
.od-step.is-done .od-step__label {
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.od-step__time {
  min-height: 16px;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  line-height: 16px;
  color: var(--el-text-color-placeholder);
  white-space: nowrap;
}

/* ---------- 主栅格 ---------- */
.od-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 16px;
  align-items: start;
  padding: 0 24px 24px;
}

.od-main,
.od-side {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

/**
 * 侧栏悬浮常驻：商品多、主栏很长时，右侧金额/买家等信息跟随滚动停留在视野内，
 * 避免往下滚动后右栏整块留白。max-height 按「视口高度 - 页头 - 应用顶栏/间距」推导，
 * 内容仍超出时在栏内滚动（细滚动条），不遮挡页头与主栏。
 */
.od-side {
  position: sticky;
  top: calc(var(--od-head-h, 88px) + 16px);
  gap: 12px;
  align-self: start;
  max-height: max(360px, calc(100vh - var(--od-head-h, 88px) - 140px));
  padding-right: 4px;
  margin-right: -4px;
  overflow-y: auto;
  scrollbar-color: var(--el-border-color) transparent;
  scrollbar-width: thin;
}

.od-side::-webkit-scrollbar {
  width: 6px;
}

.od-side::-webkit-scrollbar-thumb {
  background: var(--el-border-color);
  border-radius: 3px;
}

.od-side::-webkit-scrollbar-track {
  background: transparent;
}

/* 侧栏卡片压紧一点，尽量让金额/买家/配送在常见视口内一屏放下 */
.od-side :deep(.hx-page-section__header) {
  min-height: 40px;
  padding: 6px 0;
}

.od-side :deep(.hx-page-section__body) {
  padding: 12px 0;
}

.od-main :deep(.hx-page-section),
.od-side :deep(.hx-page-section) {
  margin-bottom: 0;
}

/* ---------- 商品清单 ---------- */
.od-items__total {
  font-size: 12px;
  color: var(--el-text-color-secondary);

  b {
    margin-left: 4px;
    font-size: 14px;
    color: var(--el-text-color-primary);
  }
}

.od-group + .od-group {
  margin-top: 4px;
}

.od-group__head {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 12px 0 4px;
}

.od-group:first-child .od-group__head {
  padding-top: 0;
}

.od-group__name {
  font-size: 13px;
  font-weight: 600;
  line-height: 20px;
  color: var(--el-text-color-primary);
}

.od-group__count {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.od-group__line {
  flex: 1;
  height: 1px;
  background: var(--el-border-color-extra-light);
}

.od-item {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr) 132px 84px;
  gap: 12px 16px;
  align-items: center;
  padding: 10px 8px;
  margin: 0 -8px;
  border-radius: 8px;
  transition: background-color 0.15s ease;
}

.od-item:hover {
  background: var(--el-fill-color-lighter);
}

.od-item__pic {
  width: 56px;
  height: 56px;
  overflow: hidden;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-extra-light);
  border-radius: 8px;
}

.od-item__pic-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 18px;
  color: var(--el-text-color-placeholder);
}

.od-item__body {
  min-width: 0;
}

.od-item__name {
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  font-size: 14px;
  line-height: 20px;
  color: var(--el-text-color-primary);
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
}

.od-item__spec {
  margin: 2px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
}

.od-item__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  align-items: center;
  margin-top: 4px;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.od-item__chip {
  max-width: 240px;
  padding: 0 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 18px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}

.od-item__extra {
  color: var(--el-text-color-secondary);
}

.od-item__amount {
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.od-item__unit {
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.od-item__subtotal {
  font-size: 15px;
  font-weight: 600;
  line-height: 22px;
  color: var(--el-text-color-primary);
}

.od-item__status {
  display: flex;
  justify-content: flex-end;
}

/* 整单商品状态齐时不渲染状态列，行宽回归三列 */
.od-items--no-status .od-item {
  grid-template-columns: 56px minmax(0, 1fr) 132px;
}

/* ---------- 通用键值 / 时间线 ---------- */
.od-kv-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px 20px;
}

.od-kv {
  min-width: 0;
}

.od-kv__k {
  display: block;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.od-kv__v {
  display: block;
  margin-top: 2px;
  font-size: 13px;
  line-height: 20px;
  color: var(--el-text-color-primary);
  overflow-wrap: anywhere;
}

.od-track {
  padding: 16px 0 0;
  margin: 16px 0 0;
  list-style: none;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.od-track__item {
  position: relative;
  padding: 0 0 16px 24px;
}

.od-track__item:last-child {
  padding-bottom: 0;
}

.od-track__item::before {
  position: absolute;
  top: 12px;
  bottom: 0;
  left: 4px;
  width: 1px;
  content: '';
  background: var(--el-border-color-lighter);
}

.od-track__item:last-child::before {
  display: none;
}

.od-track__dot {
  position: absolute;
  top: 5px;
  left: 0;
  width: 9px;
  height: 9px;
  background: var(--el-border-color);
  border-radius: 50%;
}

.od-track__item.is-latest .od-track__dot {
  background: var(--el-color-primary);
  box-shadow: 0 0 0 3px var(--el-color-primary-light-9);
}

.od-track__text {
  margin: 0;
  font-size: 13px;
  line-height: 20px;
  color: var(--el-text-color-regular);
  overflow-wrap: anywhere;
}

.od-track__item.is-latest .od-track__text {
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.od-track__time {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  line-height: 18px;
  color: var(--el-text-color-placeholder);
}

/* ---------- 配送里程碑 ---------- */
.od-milestones {
  display: flex;
  padding-top: 18px;
  margin-top: 16px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.od-milestone {
  position: relative;
  flex: 1;
  min-width: 0;
  text-align: center;
}

.od-milestone:not(:last-child)::after {
  position: absolute;
  top: 8px;
  left: calc(50% + 16px);
  width: calc(100% - 32px);
  height: 2px;
  content: '';
  background: var(--el-border-color);
}

.od-milestone.is-done:not(:last-child)::after {
  background: var(--el-color-primary);
}

.od-milestone__dot {
  position: relative;
  z-index: 1;
  display: block;
  width: 18px;
  height: 18px;
  margin: 0 auto;
  background: var(--el-fill-color-light);
  border: 2px solid var(--el-border-color);
  border-radius: 50%;
}

.od-milestone.is-done .od-milestone__dot {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
}

.od-milestone.is-active .od-milestone__dot {
  background: var(--el-bg-color);
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 3px var(--el-color-primary-light-9);
}

.od-milestone__label {
  margin-top: 6px;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.od-milestone.is-done .od-milestone__label {
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.od-milestone__time {
  min-height: 16px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  line-height: 16px;
  color: var(--el-text-color-placeholder);
  white-space: nowrap;
}

/* ---------- 金额汇总 ---------- */
.od-receipt {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.od-receipt__row {
  display: flex;
  gap: 12px;
  align-items: baseline;
  justify-content: space-between;
  font-size: 13px;
  line-height: 20px;
  color: var(--el-text-color-regular);
}

.od-receipt__row > span:last-child {
  flex: none;
}

.od-receipt__label {
  color: var(--el-text-color-secondary);
}

.od-receipt__row--sub {
  padding-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.od-receipt__sub-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.od-num--minus {
  color: var(--el-color-danger);
}

.od-receipt__total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-top: 12px;
  margin-top: 2px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.od-receipt__total-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.od-receipt__amount {
  font-size: 24px;
  font-weight: 700;
  line-height: 30px;
  color: var(--el-color-danger);
}

.od-receipt__pay-btn {
  width: 100%;
}

.od-receipt__meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 12px;
  margin-top: 2px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.od-vouchers {
  padding-top: 12px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.od-vouchers__label {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.od-vouchers__list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.od-vouchers__item {
  width: 56px;
  height: 56px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-extra-light);
  border-radius: 8px;
}

/* ---------- 配送 / 备注 ---------- */
.od-rows {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0;
}

.od-rows__item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin: 0;
  font-size: 13px;
  line-height: 20px;
}

.od-rows__k {
  flex: none;
  width: 72px;
  color: var(--el-text-color-secondary);
}

.od-rows__v {
  flex: 1;
  min-width: 0;
  margin: 0;
  color: var(--el-text-color-regular);
  overflow-wrap: anywhere;
}

.od-rows__sub {
  margin-left: 6px;
  color: var(--el-text-color-placeholder);
}

.od-remark {
  padding: 10px 12px;
  margin: 0;
  font-size: 13px;
  line-height: 20px;
  color: var(--el-text-color-regular);
  overflow-wrap: anywhere;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.od-empty {
  padding: 64px 0;
}

/* 页面容器铺满视口：页头吸顶、内容区自行留白 */
</style>
