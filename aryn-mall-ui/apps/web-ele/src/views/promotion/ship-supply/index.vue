<script lang="ts" setup>
import {
  computed,
  defineAsyncComponent,
  onActivated,
  onMounted,
  reactive,
  ref,
} from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  ElAlert,
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getDisplayByIds } from '#/api/product/goods-sku';
import { getById as getSpuById } from '#/api/product/goods-spu';
import {
  addActivity,
  deleteActivity,
  dryRunActivity,
  getActivityPage,
  getPublishConflicts,
  pauseActivity,
  publishActivity,
  updateActivity,
} from '#/api/promotion/ship-activity';
import { getTradeCalendars } from '#/api/promotion/trade-calendar';
import { getDeclaredVesselCalls, getVesselPage } from '#/api/vessel/vessel';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import PageToolbar from '#/components/page/page-toolbar.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';
import { formatMoney } from '#/utils/util';

const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const SelectGoods = defineAsyncComponent(
  () => import('#/components/select-goods/index.vue'),
);

const state = reactive({
  page: { total: 0, currentPage: 1, pageSize: 10 },
  query: { activityType: '', status: '' },
  tableData: [] as any[],
});
const loading = ref(false);

const dialogVisible = ref(false);
const editingId = ref('');
/** 本次弹窗是否由「靠港日历/船舶档案 → 建活动」带入，用于顶部提示与后续清理 */
const routedPrefill = ref(false);
const route = useRoute();
const router = useRouter();
const form = reactive<any>({
  activityName: '',
  activityType: '4',
  consumedGiftStock: 0,
  endTime: '',
  giftStock: 0,
  perUserLimit: 0,
  priority: 0,
  purchaseScene: '',
  remark: '',
  scopeType: '1',
  scopeValue: '',
  startTime: '',
  totalBudget: 0,
  // 叠加策略（活动级）：新人专享 type=9 默认关（防 0 元单），其余类型默认开（维持现状）
  couponStackable: '1',
  pointsStackable: '1',
});
const skuLadders = ref<
  Array<{
    ladders: Array<{ minQty: number; unitPrice: number }>;
    salesPrice: number | string;
    skuId: string;
    skuName: string;
    spuName: string;
  }>
>([]);
const tiers = ref<
  Array<{ discountAmount: number; minAmount: number; minQty?: number }>
>([{ discountAmount: 0, minAmount: 0 }]);
/** type=7 首单引擎：首单专享、主体类型、月预算、单笔封顶（写在 rules JSON，不新增表字段）。 */
const firstOrderOnly = ref(false);
const firstOrderSubjectType = ref<'1' | '2'>('1');
const monthlyBudget = ref(0);
const maxDiscountAmount = ref(0);
const giftThreshold = ref(0);
const newbieSkus = ref<
  Array<{
    price: number;
    salesPrice: number | string;
    skuId: string;
    skuName: string;
    spuName: string;
  }>
>([]);
/**
 * 档期特供（type=12，2026-11-14 B2B）：归属采购档期的限量特惠。
 * 规则 skus 与新人价同形态（[{skuId,price}]）；限量 total_quota 落活动级列，
 * 每人限购沿用 form.perUserLimit（0=不限）。
 */
const specialSkus = ref<
  Array<{
    price: number;
    salesPrice: number | string;
    skuId: string;
    skuName: string;
    spuName: string;
  }>
>([]);
const calendarId = ref('');
const calendarOptions = ref<Array<{ calendarName: string; id: string }>>([]);
const specialTotalQuota = ref(0);
const registerWithinDays = ref<null | number>(null);
/**
 * 新人专享「长期有效」。
 *
 * 规则层只存 longTerm 标记；结束时间统一写远期哨兵（见 NEWBIE_LONG_TERM_END），
 * 让活动生效、发布冲突、列表展示态继续沿用既有 endTime 区间比较。
 */
const newbieLongTerm = ref(false);
const NEWBIE_LONG_TERM_END = '2099-12-31 23:59:59';
const gifts = ref<
  Array<{ quantity: number; skuId: string; skuName: string; spuName: string }>
>([]);
const vesselOptions = ref<Array<{ id: string; vesselName: string }>>([]);
const callOptions = ref<any[]>([]);
const portOptions = ref<Array<{ code: string; name: string }>>([]);
const isPublished = ref(false);
const selectMode = ref<
  'dry' | 'gift' | 'ladder' | 'newbie' | 'scope' | 'special'
>('ladder');
const dryVisible = ref(false);
const dryLoading = ref(false);
const dryResult = ref<any>(null);
const dryForm = reactive<any>({
  items: [] as Array<{
    quantity: number;
    salesPrice: number | string;
    skuId: string;
    skuName: string;
    spuName: string;
  }>,
  portCode: '',
  purchaseScene: '2',
  vesselCallId: '',
  vesselId: '',
});

const typeLabel: Record<string, string> = {
  '4': '阶梯价',
  '5': '买赠',
  '7': '整船优惠',
  '9': '新人价',
  '11': '出清价',
  '12': '档期特供',
};
const statusLabel: Record<string, string> = {
  '1': '草稿',
  '2': '已发布',
  '3': '已暂停',
  '4': '已结束',
  '5': '待开始',
};

/** 活动状态标签语义色（标签文案沿用 statusLabel，不改变含义） */
const statusTagType = (
  status: string,
): 'danger' | 'info' | 'primary' | 'success' | 'warning' => {
  if (status === '2') return 'success';
  if (status === '3') return 'warning';
  if (status === '5') return 'primary';
  return 'info';
};
const scopeLabel: Record<string, string> = {
  '1': '全场',
  '2': '指定SKU',
  '5': '指定船舶',
  '6': '指定靠港',
  '7': '指定港口',
  '8': '常购用户',
};

/** skuId → 商品展示信息，活动规则只存 skuId 时用于回显与试算结果展示 */
const skuDisplayMap = ref<Record<string, any>>({});

/**
 * 活动规则只持久化 skuId（阶梯价/新人价等类型的展示字段不落库），编辑回显时
 * 按 skuId 批量换取商品名/规格/售价，避免弹窗只显示一串 ID。
 * 商品服务不可用时保留规则内已有展示字段，不阻断配置。
 */
const enrichSkuDisplay = async (rows: any[]) => {
  const ids = [...new Set(rows.map((row) => row.skuId).filter(Boolean))];
  if (ids.length === 0) return;
  try {
    const displays = (await getDisplayByIds(ids as string[])) ?? [];
    const map = { ...skuDisplayMap.value };
    displays.forEach((item) => {
      map[item.skuId] = item;
    });
    skuDisplayMap.value = map;
    rows.forEach((row) => {
      const display = map[row.skuId];
      if (!display) return;
      if (display.spuName) row.spuName = display.spuName;
      if (display.skuName) row.skuName = display.skuName;
      if (display.salesPrice !== null && display.salesPrice !== undefined) {
        row.salesPrice = display.salesPrice;
      }
    });
  } catch {
    // 商品服务不可用：保留规则内已有展示字段，缺失时回退显示 skuId
  }
};

/** 试算结果只回传 skuId：优先用购物车行/已解析的商品名展示 */
const skuDisplayText = (skuId: string) => {
  const item = dryForm.items.find((row: any) => row.skuId === skuId);
  const display = skuDisplayMap.value[skuId];
  const name = [
    item?.spuName || display?.spuName,
    item?.skuName || display?.skuName,
  ]
    .filter(Boolean)
    .join(' ');
  return name || skuId;
};

const initPage = async () => {
  loading.value = true;
  try {
    const response = await getActivityPage({
      current: state.page.currentPage,
      size: state.page.pageSize,
      activityType: state.query.activityType,
      status: state.query.status,
    });
    state.tableData = response.records;
    state.page.total = response.total;
  } finally {
    loading.value = false;
  }
};

const openDialog = (row?: any) => {
  routedPrefill.value = false;
  editingId.value = row?.id ?? '';
  isPublished.value = row?.status === '2';
  form.activityName = row?.activityName ?? '';
  form.activityType = row?.activityType ?? '4';
  form.scopeType = row?.scopeType ?? '1';
  form.scopeValue = row?.scopeValue ?? '';
  form.purchaseScene = row?.purchaseScene ?? '';
  form.priority = row?.priority ?? 0;
  form.totalBudget = row?.totalBudget ?? 0;
  form.perUserLimit = row?.perUserLimit ?? 0;
  form.giftStock = row?.giftStock ?? 0;
  form.consumedGiftStock = row?.consumedGiftStock ?? 0;
  form.startTime = row?.startTime ?? '';
  form.endTime = row?.endTime ?? '';
  form.remark = row?.remark ?? '';
  // 叠加策略回显：存量数据可能未带字段，缺省按类型默认补齐（新人/档期特供=关，其余=开）
  const lockStackableType = ['9', '12'].includes(form.activityType);
  form.couponStackable =
    row?.couponStackable ?? (lockStackableType ? '0' : '1');
  form.pointsStackable =
    row?.pointsStackable ?? (lockStackableType ? '0' : '1');
  calendarId.value = row?.calendarId ?? '';
  specialTotalQuota.value = row?.totalQuota ?? 0;
  try {
    const rules = row ? JSON.parse(row.rules) : {};
    skuLadders.value = rules.skuLadders ?? [];
    tiers.value = rules.tiers ?? [{ discountAmount: 0, minAmount: 0 }];
    firstOrderOnly.value = rules.firstOrderOnly === true;
    firstOrderSubjectType.value =
      String(rules.subjectType || '1') === '2' ? '2' : '1';
    monthlyBudget.value = Number(rules.monthlyBudget || 0);
    maxDiscountAmount.value = Number(rules.maxDiscountAmount || 0);
    giftThreshold.value = rules.minAmount ?? 0;
    gifts.value = rules.gifts ?? [];
    newbieSkus.value = rules.skus ?? [];
    specialSkus.value = form.activityType === '12' ? (rules.skus ?? []) : [];
    registerWithinDays.value = rules.registerWithinDays ?? null;
    newbieLongTerm.value = rules.longTerm === true;
    // 长期有效不展示 2099 哨兵时间；关闭开关后必须重新选择结束时间
    if (newbieLongTerm.value) form.endTime = '';
  } catch {
    skuLadders.value = [];
    tiers.value = [{ discountAmount: 0, minAmount: 0 }];
    firstOrderOnly.value = false;
    firstOrderSubjectType.value = '1';
    monthlyBudget.value = 0;
    maxDiscountAmount.value = 0;
    giftThreshold.value = 0;
    gifts.value = [];
    newbieSkus.value = [];
    registerWithinDays.value = null;
    newbieLongTerm.value = false;
  }
  dryResult.value = null;
  switch (form.activityType) {
    case '4': {
      enrichSkuDisplay(skuLadders.value);
      loadCalendarOptions();

      break;
    }
    case '9': {
      enrichSkuDisplay(newbieSkus.value);

      break;
    }
    case '12': {
      enrichSkuDisplay(specialSkus.value);
      loadCalendarOptions();

      break;
    }
    // No default
  }
  loadVesselData();
  dialogVisible.value = true;
};

/** 档期特供：加载可选采购档期（生效/草稿都可选，发布时引擎按档期窗口过滤） */
const loadCalendarOptions = async () => {
  if (calendarOptions.value.length > 0) return;
  try {
    calendarOptions.value = (await getTradeCalendars()) ?? [];
  } catch {
    calendarOptions.value = [];
  }
};

/** 活动类型切换时按类型默认值重置叠加策略（新人/档期特供=关，其余=开） */
const onActivityTypeChange = (value: boolean | number | string | undefined) => {
  const lockStackable = value === '9' || value === '12';
  form.couponStackable = lockStackable ? '0' : '1';
  form.pointsStackable = lockStackable ? '0' : '1';
  if (value !== '9') newbieLongTerm.value = false;
  if (value === '12' || value === '4') loadCalendarOptions();
};

/** 列表展示：长期有效活动不显示 2099 哨兵时间 */
const isLongTermActivity = (row: any) => {
  if (row?.activityType !== '9') return false;
  try {
    return JSON.parse(row.rules || '{}').longTerm === true;
  } catch {
    return false;
  }
};

const buildPayload = () => {
  let rules: Record<string, any> = { tiers: tiers.value };
  switch (form.activityType) {
    case '4': {
      rules = {
        skuLadders: skuLadders.value.map((row) => ({
          skuId: row.skuId,
          spuName: row.spuName,
          skuName: row.skuName,
          ladders: row.ladders,
        })),
      };

      break;
    }
    case '5': {
      rules = { minAmount: giftThreshold.value, gifts: gifts.value };

      break;
    }
    case '7': {
      rules = {
        // minQty 0/空=不限件数：落 JSON 前剥掉，避免后端校验把 0 拒掉
        tiers: tiers.value.map((row) => ({
          discountAmount: row.discountAmount,
          minAmount: row.minAmount,
          ...(row.minQty && row.minQty > 0 ? { minQty: row.minQty } : {}),
        })),
        firstOrderOnly: firstOrderOnly.value,
        monthlyBudget: monthlyBudget.value,
        maxDiscountAmount: maxDiscountAmount.value,
        ...(firstOrderOnly.value
          ? { subjectType: firstOrderSubjectType.value }
          : {}),
      };

      break;
    }
    case '9': {
      rules = {
        limitPerUser: 1,
        longTerm: newbieLongTerm.value,
        registerWithinDays: registerWithinDays.value,
        skus: newbieSkus.value.map((row) => ({
          price: row.price,
          skuId: row.skuId,
          skuName: row.skuName,
          spuName: row.spuName,
        })),
      };

      break;
    }
    case '12': {
      rules = {
        skus: specialSkus.value.map((row) => ({
          price: row.price,
          skuId: row.skuId,
          skuName: row.skuName,
          spuName: row.spuName,
        })),
      };

      break;
    }
    // No default
  }
  const longTerm = form.activityType === '9' && newbieLongTerm.value;
  const payload: Record<string, any> = {
    ...form,
    endTime: longTerm ? NEWBIE_LONG_TERM_END : form.endTime,
    rules: JSON.stringify(rules),
  };
  if (['4', '9', '12'].includes(form.activityType)) {
    payload.scopeType = '2';
    const skuRowsByType: Record<string, Array<{ skuId: string }>> = {
      '12': specialSkus.value,
      '4': skuLadders.value,
      '9': newbieSkus.value,
    };
    payload.scopeValue = (skuRowsByType[form.activityType] || [])
      .map((row) => row.skuId)
      .join(',');
  }
  if (form.activityType === '4' || form.activityType === '12') {
    // 阶梯价可选归属档期；档期特供必选
    payload.calendarId = calendarId.value;
  }
  if (form.activityType === '12') {
    // 0=不限：后端 total_quota NULL 视为不限，落库前归一
    payload.totalQuota =
      specialTotalQuota.value > 0 ? specialTotalQuota.value : null;
  }
  return payload;
};

const handleSave = () => {
  const longTerm = form.activityType === '9' && newbieLongTerm.value;
  if (!form.activityName || !form.startTime || (!longTerm && !form.endTime)) {
    ElMessage.warning('请完善活动名称与时间');
    return;
  }
  if (form.activityType === '4') {
    if (skuLadders.value.length === 0) {
      ElMessage.warning('请添加活动商品并配置档位');
      return;
    }
    const bad = skuLadders.value.find(
      (row) =>
        row.ladders.length === 0 ||
        row.ladders.some((ladder) => !ladder.minQty || ladder.unitPrice <= 0),
    );
    if (bad) {
      ElMessage.warning(
        `商品【${bad.spuName}】的档位数量与档价需完整且档价大于 0`,
      );
      return;
    }
  }
  if (form.activityType === '5' && gifts.value.length === 0) {
    ElMessage.warning('请添加赠品');
    return;
  }
  if (form.activityType === '9') {
    if (newbieSkus.value.length === 0) {
      ElMessage.warning('请添加新人价商品');
      return;
    }
    const bad = newbieSkus.value.find((row) => !row.price || row.price <= 0);
    if (bad) {
      ElMessage.warning(`商品【${bad.spuName}】的新人价需大于 0`);
      return;
    }
  }
  if (form.activityType === '12') {
    if (!calendarId.value) {
      ElMessage.warning('请选择归属采购档期');
      return;
    }
    if (specialSkus.value.length === 0) {
      ElMessage.warning('请添加档期特供商品');
      return;
    }
    const badSpecial = specialSkus.value.find(
      (row) => !row.price || row.price <= 0,
    );
    if (badSpecial) {
      ElMessage.warning(`商品【${badSpecial.spuName}】的特供价需大于 0`);
      return;
    }
    if (specialTotalQuota.value < 0) {
      ElMessage.warning('活动限量不能为负数');
      return;
    }
  }
  const request = editingId.value
    ? updateActivity(editingId.value, buildPayload())
    : addActivity(buildPayload());
  request
    .then(() => {
      ElMessage.success('保存成功');
      dialogVisible.value = false;
      initPage();
    })
    .catch(() => {});
};

const doPublish = (row: any, force: boolean) => {
  publishActivity(row.id, force)
    .then(() => {
      ElMessage.success('已发布');
      initPage();
    })
    .catch(() => {});
};
const handlePublish = (row: any) => {
  getPublishConflicts(row.id)
    .then((conflicts: any[]) => {
      if (conflicts.length > 0) {
        const names = conflicts.map((item) => item.activityName).join('、');
        ElMessageBox.confirm(
          `与已发布活动时间重叠：${names}。运行时同类型取最优，确认强制发布？`,
          '发布冲突',
          {
            confirmButtonText: '强制发布',
            cancelButtonText: '取消',
            type: 'warning',
          },
        )
          .then(() => doPublish(row, true))
          .catch(() => {});
      } else {
        doPublish(row, false);
      }
    })
    .catch(() => {});
};

const handlePause = (row: any) => {
  pauseActivity(row.id)
    .then(() => {
      ElMessage.success('已暂停');
      initPage();
    })
    .catch(() => {});
};

const handleDelete = (row: any) => {
  ElMessageBox.confirm('确认删除该活动?', '提示')
    .then(() => deleteActivity(row.id))
    .then(() => {
      ElMessage.success('已删除');
      initPage();
    })
    .catch(() => {});
};

const selectGoodsRef = ref();
const openSelect = (
  mode: 'dry' | 'gift' | 'ladder' | 'newbie' | 'scope' | 'special',
) => {
  selectMode.value = mode;
  selectGoodsRef.value?.initPage();
};
const openSelectGoods = () => openSelect('ladder');

const resolveSkuRows = async (spuList: any[]) => {
  const rows: Array<{
    ladders?: Array<{ minQty: number; unitPrice: number }>;
    quantity?: number;
    salesPrice: number | string;
    skuId: string;
    skuName: string;
    spuName: string;
  }> = [];
  for (const spu of spuList) {
    try {
      const detail: any = await getSpuById(spu.id);
      const skus = detail?.goodsSkus ?? [];
      if (skus.length === 0) {
        ElMessage.warning(`商品【${spu.name}】无可用 SKU，已跳过`);
        continue;
      }
      for (const sku of skus) {
        rows.push({
          skuId: sku.id,
          spuName: spu.name,
          skuName:
            (sku.specsArr ?? [])
              .map((spec: any) => spec.specsValueName)
              .filter(Boolean)
              .join(' ') || '单规格',
          salesPrice: sku.salesPrice,
        });
      }
    } catch {
      ElMessage.error(`商品【${spu.name}】加载失败`);
    }
  }
  return rows;
};

const onGoodsSelected = async (spuList: any[]) => {
  const rows = await resolveSkuRows(spuList);
  switch (selectMode.value) {
    case 'gift': {
      for (const row of rows) {
        if (gifts.value.some((gift) => gift.skuId === row.skuId)) continue;
        gifts.value.push({ ...row, quantity: 1 });
      }

      break;
    }
    case 'ladder': {
      for (const row of rows) {
        if (skuLadders.value.some((item) => item.skuId === row.skuId)) continue;
        skuLadders.value.push({
          ...row,
          ladders: [{ minQty: 10, unitPrice: 0 }],
        });
      }

      break;
    }
    case 'newbie': {
      for (const row of rows) {
        if (newbieSkus.value.some((sku) => sku.skuId === row.skuId)) continue;
        newbieSkus.value.push({ ...row, price: 0 });
      }

      break;
    }
    case 'scope': {
      const existing = new Set(
        (form.scopeValue || '').split(',').filter(Boolean),
      );
      rows.forEach((row) => existing.add(row.skuId));
      form.scopeValue = [...existing].join(',');

      break;
    }
    case 'special': {
      for (const row of rows) {
        if (specialSkus.value.some((sku) => sku.skuId === row.skuId)) continue;
        specialSkus.value.push({ ...row, price: 0 });
      }

      break;
    }
    default: {
      for (const row of rows) {
        if (dryForm.items.some((item: any) => item.skuId === row.skuId))
          continue;
        dryForm.items.push({ ...row, quantity: 1 });
      }
    }
  }
};

const scopeSkuCount = () =>
  (form.scopeValue || '').split(',').filter(Boolean).length;

const scopeValueOptions = computed(() => {
  if (form.scopeType === '5') {
    return vesselOptions.value.map((vessel) => ({
      value: vessel.id,
      label: vessel.vesselName,
    }));
  }
  if (form.scopeType === '6') {
    return callOptions.value.map((call) => ({
      value: call.id,
      label:
        `${call.portName ?? ''} ${String(call.eta ?? '').slice(0, 10)}`.trim(),
    }));
  }
  if (form.scopeType === '7') {
    return portOptions.value.map((port) => ({
      value: port.code,
      label: `${port.name}（${port.code}）`,
    }));
  }
  return [];
});

const loadVesselData = async () => {
  if (vesselOptions.value.length > 0) return;
  try {
    const [vessels, calls] = await Promise.all([
      getVesselPage({ current: 1, size: 200 }),
      getDeclaredVesselCalls(),
    ]);
    vesselOptions.value = vessels.records ?? [];
    callOptions.value = calls ?? [];
    const seen = new Set<string>();
    portOptions.value = (calls ?? [])
      .filter(
        (call: any) =>
          call.portCode && !seen.has(call.portCode) && seen.add(call.portCode),
      )
      .map((call: any) => ({ code: call.portCode, name: call.portName }));
  } catch {
    // 下拉数据加载失败时降级为空选项，不阻断活动配置
  }
};

const scopeText = (row: any) => {
  const label = scopeLabel[row.scopeType] ?? row.scopeType;
  if (row.scopeType === '2' || row.scopeType === '8') {
    return `${label}(${(row.scopeValue || '').split(',').filter(Boolean).length})`;
  }
  if (row.scopeType === '5') {
    return (
      vesselOptions.value.find((vessel) => vessel.id === row.scopeValue)
        ?.vesselName ?? label
    );
  }
  if (row.scopeType === '6') {
    const call = callOptions.value.find((item) => item.id === row.scopeValue);
    return call ? `${call.portName} ${String(call.eta).slice(0, 10)}` : label;
  }
  if (row.scopeType === '7') {
    return (
      portOptions.value.find((port) => port.code === row.scopeValue)?.name ??
      label
    );
  }
  return label;
};

const openDry = () => {
  loadVesselData();
  if (dryForm.items.length === 0 && skuLadders.value.length > 0) {
    dryForm.items = skuLadders.value.map((row) => ({
      skuId: row.skuId,
      spuName: row.spuName,
      skuName: row.skuName,
      salesPrice: row.salesPrice,
      quantity: row.ladders[0]?.minQty ?? 1,
    }));
  }
  dryResult.value = null;
  dryVisible.value = true;
};

const runDry = async () => {
  if (dryForm.items.length === 0) {
    ElMessage.warning('请添加试算购物车商品');
    return;
  }
  dryLoading.value = true;
  try {
    dryResult.value = await dryRunActivity({
      purchaseScene: dryForm.purchaseScene,
      vesselId: dryForm.vesselId || null,
      vesselCallId: dryForm.vesselCallId || null,
      portCode: dryForm.portCode || null,
      skuItems: dryForm.items.map((item: any) => ({
        skuId: item.skuId,
        quantity: item.quantity,
        salesPrice: item.salesPrice,
      })),
    });
  } finally {
    dryLoading.value = false;
  }
};

const onDryCallChange = (callId: string) => {
  const call = callOptions.value.find((item) => item.id === callId);
  dryForm.portCode = call?.portCode ?? '';
  dryForm.vesselId = call?.vesselId ?? dryForm.vesselId;
};

const addLadder = (row: (typeof skuLadders.value)[number]) => {
  row.ladders.push({ minQty: 1, unitPrice: 0 });
};
const addTier = () => {
  tiers.value.push({ discountAmount: 0, minAmount: 0 });
};

/** 已消费的建活动入口：keepAlive 页面再次激活或刷新时不重复弹窗 */
let handledCreateIntent = '';

/**
 * 从靠港日历/船舶档案的「建船供活动」进入：自动打开新建弹窗，
 * 默认整船优惠 + 指定靠港/船舶（来源页带入的上下文仍可修改）。
 */
const consumeCreateIntent = async () => {
  const { action, vesselCallId, vesselCallLabel, vesselId, vesselName } =
    route.query as Record<string, string | undefined>;
  if (action !== 'create' || (!vesselCallId && !vesselId)) return;
  const intentKey = `${vesselId ?? ''}|${vesselCallId ?? ''}`;
  if (handledCreateIntent === intentKey) return;
  handledCreateIntent = intentKey;
  router.replace({ path: route.path, query: {} }).catch(() => {});
  await loadVesselData();
  // 来源页已带展示文案：靠港申报列表只含待处理靠港、船舶列表只取前 200 条，
  // 目标项可能不在下拉数据里，补一条选中项以免弹窗只显示雪花 ID。
  if (
    vesselCallId &&
    vesselCallLabel &&
    !callOptions.value.some((call) => call.id === vesselCallId)
  ) {
    callOptions.value.unshift({
      id: vesselCallId,
      portName: vesselCallLabel,
      eta: '',
    });
  }
  if (
    !vesselCallId &&
    vesselId &&
    vesselName &&
    !vesselOptions.value.some((vessel) => vessel.id === vesselId)
  ) {
    vesselOptions.value.unshift({ id: vesselId, vesselName });
  }
  openDialog({
    activityType: '7',
    scopeType: vesselCallId ? '6' : '5',
    scopeValue: vesselCallId || vesselId,
  });
  routedPrefill.value = true;
};

onMounted(() => {
  initPage();
  consumeCreateIntent();
});
onActivated(consumeCreateIntent);

useKeepAliveRefresh(initPage);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索 -->
      <PageFilterPanel
        :model="state.query"
        @reset="initPage"
        @search="initPage"
      >
        <ElFormItem label="活动类型" prop="activityType">
          <ElSelect
            v-model="state.query.activityType"
            clearable
            placeholder="请选择活动类型"
          >
            <ElOption
              v-for="(label, value) in typeLabel"
              :key="value"
              :label="label"
              :value="value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="状态" prop="status">
          <ElSelect
            v-model="state.query.status"
            clearable
            placeholder="请选择状态"
          >
            <ElOption
              v-for="(label, value) in statusLabel"
              :key="value"
              :label="label"
              :value="value"
            />
          </ElSelect>
        </ElFormItem>
      </PageFilterPanel>

      <!-- 工具栏 -->
      <PageToolbar :total="state.page.total" total-label="个船供活动">
        <template #left>
          <ElButton
            type="primary"
            v-access:code="'promotion:shipactivity:save'"
            @click="openDialog()"
          >
            新建活动
          </ElButton>
        </template>
      </PageToolbar>

      <ElTable
        v-loading="loading"
        :data="state.tableData"
        class="hx-data-table"
        row-key="id"
        border
        empty-text="暂无船供活动"
      >
        <ElTableColumn prop="activityName" label="活动名称" min-width="160" />
        <ElTableColumn label="类型" width="100">
          <template #default="scope">
            {{ typeLabel[scope.row.activityType] ?? scope.row.activityType }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="范围" width="140">
          <template #default="scope">
            {{ scopeText(scope.row) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="叠加策略" width="130">
          <template #default="scope">
            <template v-if="scope.row.activityType === '9'">
              <span
                >券{{ scope.row.couponStackable === '1' ? '✓' : '✗' }} · 积分{{
                  scope.row.pointsStackable === '1' ? '✓' : '✗'
                }}</span
              >
            </template>
            <span v-else style="color: var(--el-text-color-secondary)">—</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="额度使用" width="190">
          <template #default="scope">
            <template v-if="scope.row.activityType === '5'">
              <span v-if="Number(scope.row.giftStock) > 0">
                赠品 {{ scope.row.consumedGiftStock ?? 0 }} /
                {{ scope.row.giftStock }} 件
              </span>
              <span v-else
                >赠品不限（已赠
                {{ scope.row.consumedGiftStock ?? 0 }} 件）</span
              >
            </template>
            <template v-else>
              <span v-if="Number(scope.row.totalBudget) > 0" class="hx-money">
                ¥{{ formatMoney(scope.row.consumedBudget) }} / ¥{{
                  formatMoney(scope.row.totalBudget)
                }}
              </span>
              <span v-else class="hx-money"
                >预算不限（已用 ¥{{
                  formatMoney(scope.row.consumedBudget)
                }}）</span
              >
            </template>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="startTime" label="开始" width="170" />
        <ElTableColumn label="结束" width="170">
          <template #default="{ row }">
            {{ isLongTermActivity(row) ? '长期有效' : row.endTime }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="90">
          <template #default="scope">
            <ElTag :type="statusTagType(scope.row.status)">
              {{ statusLabel[scope.row.status] ?? scope.row.status }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="220">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'promotion:shipactivity:save'"
              @click="openDialog(scope.row)"
            >
              编辑
            </ElButton>
            <ElButton
              link
              type="success"
              v-access:code="'promotion:shipactivity:publish'"
              :disabled="scope.row.status !== '1' && scope.row.status !== '3'"
              @click="handlePublish(scope.row)"
            >
              发布
            </ElButton>
            <ElButton
              link
              type="warning"
              v-access:code="'promotion:shipactivity:publish'"
              :disabled="scope.row.status !== '2'"
              @click="handlePause(scope.row)"
            >
              暂停
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'promotion:shipactivity:save'"
              :disabled="scope.row.status === '2'"
              @click="handleDelete(scope.row)"
            >
              删除
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

      <ElDialog
        v-model="dialogVisible"
        :title="editingId ? '编辑活动' : '新建活动'"
        class="ship-activity-dialog"
        top="6vh"
        width="720px"
      >
        <ElAlert
          v-if="routedPrefill"
          style="margin-bottom: 12px"
          type="info"
          :closable="true"
          show-icon
          title="已从靠港/船舶带入适用范围，并按「整船优惠」预填；可按需调整活动类型与范围。"
          @close="routedPrefill = false"
        />
        <ElForm label-width="auto">
          <ElFormItem label="活动名称" required>
            <ElInput v-model="form.activityName" />
          </ElFormItem>
          <ElFormItem label="活动类型">
            <ElRadioGroup
              v-model="form.activityType"
              :disabled="!!editingId"
              @change="onActivityTypeChange"
            >
              <ElRadioButton value="4">船供阶梯价</ElRadioButton>
              <ElRadioButton value="7">船供整船优惠</ElRadioButton>
              <ElRadioButton value="5">买赠</ElRadioButton>
              <ElRadioButton value="9">新人专享价</ElRadioButton>
              <ElRadioButton value="12">档期特供</ElRadioButton>
            </ElRadioGroup>
          </ElFormItem>
          <ElFormItem
            v-if="['9', '12'].includes(form.activityType)"
            label="优惠叠加"
          >
            <div style="display: flex; gap: 8px; align-items: center">
              <ElSwitch
                :model-value="form.couponStackable !== '0'"
                @update:model-value="form.couponStackable = $event ? '1' : '0'"
              />
              <span style="margin-right: 12px">可用优惠券</span>
              <ElSwitch
                :model-value="form.pointsStackable !== '0'"
                @update:model-value="form.pointsStackable = $event ? '1' : '0'"
              />
              <span>可用积分抵现</span>
              <span style="color: var(--el-text-color-secondary)">
                {{
                  form.activityType === '9'
                    ? '（新人价已是最低价，默认关闭防叠加击穿；可按需放开）'
                    : '（档期特供为限量硬促销，默认关闭防叠加击穿；可按需放开）'
                }}
              </span>
            </div>
          </ElFormItem>
          <ElFormItem
            v-if="form.activityType !== '4' && form.activityType !== '9'"
            label="适用范围"
          >
            <ElSelect
              v-model="form.scopeType"
              :disabled="!!editingId"
              style="width: 130px"
              @change="loadVesselData"
            >
              <ElOption
                v-for="(label, value) in scopeLabel"
                :key="value"
                :label="label"
                :value="value"
              />
            </ElSelect>
            <ElSelect
              v-if="
                form.scopeType === '5' ||
                form.scopeType === '6' ||
                form.scopeType === '7'
              "
              v-model="form.scopeValue"
              :disabled="!!editingId || isPublished"
              class="ml-10px"
              clearable
              filterable
              placeholder="请选择"
              style="width: 260px"
            >
              <ElOption
                v-for="option in scopeValueOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </ElSelect>
            <template
              v-else-if="form.scopeType === '2' || form.scopeType === '8'"
            >
              <ElButton
                class="ml-10px"
                :disabled="isPublished"
                @click="openSelect('scope')"
              >
                选择SKU
              </ElButton>
              <span class="ml-8px">已选 {{ scopeSkuCount() }} 个 SKU</span>
            </template>
          </ElFormItem>
          <ElFormItem v-else label="适用范围">
            <span style="color: var(--el-text-color-secondary)">
              指定SKU（由活动商品自动带出，无需手填）
            </span>
          </ElFormItem>
          <ElFormItem label="限定场景">
            <ElSelect
              v-model="form.purchaseScene"
              clearable
              placeholder="不限"
              style="width: 160px"
            >
              <ElOption label="仅个人购买" value="1" />
              <ElOption label="仅船供采购" value="2" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="预算金额">
            <ElInputNumber
              v-model="form.totalBudget"
              :min="0"
              :precision="2"
              controls-position="right"
            />
            <span class="ml-8px"
              >元，0
              为不限；额度用尽后活动自动失效，订单按剩余优惠或原价成交</span
            >
          </ElFormItem>
          <ElFormItem label="每人限享">
            <ElInputNumber
              v-model="form.perUserLimit"
              :min="0"
              controls-position="right"
            />
            <span class="ml-8px">次，0 为不限</span>
          </ElFormItem>
          <ElFormItem v-if="form.activityType === '5'" label="赠品总量">
            <ElInputNumber
              v-model="form.giftStock"
              :min="0"
              controls-position="right"
            />
            <span class="ml-8px">
              件，0 为不限；赠品领完后本单自动不赠，订单继续成交
              <template v-if="editingId"
                >（已赠 {{ form.consumedGiftStock ?? 0 }} 件）</template
              >
            </span>
          </ElFormItem>
          <ElFormItem label="开始时间" required>
            <ElDatePicker
              v-model="form.startTime"
              type="datetime"
              placeholder="选择开始时间"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
            />
          </ElFormItem>
          <ElFormItem v-if="form.activityType === '9'" label="长期有效">
            <ElSwitch v-model="newbieLongTerm" :disabled="isPublished" />
            <span class="ml-8px" style="color: var(--el-text-color-secondary)">
              开启后不设置结束时间，C 端新人专享组件不展示倒计时
            </span>
          </ElFormItem>
          <ElFormItem
            v-if="form.activityType !== '9' || !newbieLongTerm"
            label="结束时间"
            required
          >
            <ElDatePicker
              v-model="form.endTime"
              type="datetime"
              placeholder="选择结束时间"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
            />
          </ElFormItem>
          <ElFormItem v-else label="结束时间">
            <span style="color: var(--el-text-color-secondary)">
              长期有效，不设置结束时间
            </span>
          </ElFormItem>
          <ElFormItem label="备注">
            <ElInput
              v-model="form.remark"
              type="textarea"
              :rows="2"
              maxlength="200"
              show-word-limit
              placeholder="选填，活动说明仅供后台内部查看"
            />
          </ElFormItem>

          <template v-if="form.activityType === '4'">
            <ElFormItem label="归属档期">
              <ElSelect
                v-model="calendarId"
                :disabled="isPublished"
                clearable
                filterable
                placeholder="不限（长期生效）"
                style="width: 320px"
              >
                <ElOption
                  v-for="calendar in calendarOptions"
                  :key="calendar.id"
                  :label="calendar.calendarName"
                  :value="calendar.id"
                />
              </ElSelect>
              <span
                class="ml-8px"
                style="color: var(--el-text-color-secondary)"
              >
                可选：归属后仅在档期窗口内参与试算
              </span>
            </ElFormItem>
            <ElFormItem label="活动商品">
              <ElButton
                type="primary"
                :disabled="isPublished"
                @click="openSelectGoods"
              >
                添加商品
              </ElButton>
              <span
                class="ml-10px"
                style="color: var(--el-text-color-secondary)"
              >
                按 SKU 配置满件档位与档价（满件 = 单内同一 SKU
                合计），档价需低于当前售价
              </span>
            </ElFormItem>
            <ElFormItem
              v-for="(row, rowIndex) in skuLadders"
              :key="row.skuId"
              :label="row.spuName"
            >
              <div style="width: 100%">
                <div style="margin-bottom: 6px">
                  <span>{{ row.skuName }}</span>
                  <span
                    class="ml-10px hx-money"
                    style="color: var(--el-text-color-secondary)"
                  >
                    售价 ¥{{ formatMoney(row.salesPrice) }}
                  </span>
                  <ElButton
                    link
                    type="danger"
                    class="ml-10px"
                    :disabled="isPublished"
                    @click="skuLadders.splice(rowIndex, 1)"
                  >
                    移除
                  </ElButton>
                </div>
                <div
                  v-for="(ladder, ladderIndex) in row.ladders"
                  :key="ladderIndex"
                  style="margin-bottom: 6px"
                >
                  <span>档位 {{ ladderIndex + 1 }} 满</span>
                  <ElInputNumber
                    v-model="ladder.minQty"
                    :min="1"
                    :disabled="isPublished"
                    controls-position="right"
                    style="width: 110px; margin: 0 8px"
                  />
                  <span>件，单价</span>
                  <ElInputNumber
                    v-model="ladder.unitPrice"
                    :min="0"
                    :precision="2"
                    :disabled="isPublished"
                    controls-position="right"
                    style="width: 120px; margin: 0 8px"
                  />
                </div>
                <ElButton
                  link
                  type="primary"
                  :disabled="isPublished"
                  @click="addLadder(row)"
                >
                  加档
                </ElButton>
              </div>
            </ElFormItem>
          </template>
          <template v-else-if="form.activityType === '5'">
            <ElFormItem label="消费门槛">
              <ElInputNumber
                v-model="giftThreshold"
                :min="0"
                :precision="2"
                :disabled="isPublished"
                controls-position="right"
              />
              <span class="ml-8px">元</span>
            </ElFormItem>
            <ElFormItem label="赠品">
              <ElButton
                type="primary"
                :disabled="isPublished"
                @click="openSelect('gift')"
              >
                添加赠品
              </ElButton>
              <span
                class="ml-10px"
                style="color: var(--el-text-color-secondary)"
              >
                赠品随单追加 0 元明细，结算按真实库存校验
              </span>
            </ElFormItem>
            <ElFormItem
              v-for="(gift, index) in gifts"
              :key="gift.skuId"
              :label="`赠品 ${index + 1}`"
            >
              <span style="min-width: 220px">
                {{ gift.spuName || gift.skuId }} {{ gift.skuName }}
              </span>
              <ElInputNumber
                v-model="gift.quantity"
                :min="1"
                :disabled="isPublished"
                controls-position="right"
                style="width: 110px; margin: 0 8px"
              />
              <ElButton
                link
                type="danger"
                :disabled="isPublished"
                @click="gifts.splice(index, 1)"
              >
                移除
              </ElButton>
            </ElFormItem>
          </template>
          <template v-else-if="form.activityType === '9'">
            <ElFormItem label="注册时限">
              <ElInputNumber
                v-model="registerWithinDays"
                :min="1"
                :disabled="isPublished"
                controls-position="right"
              />
              <span class="ml-8px">天内注册的新用户可见，清空为不限</span>
            </ElFormItem>
            <ElFormItem label="活动商品">
              <ElButton
                type="primary"
                :disabled="isPublished"
                @click="openSelect('newbie')"
              >
                添加商品
              </ElButton>
              <span
                class="ml-10px"
                style="color: var(--el-text-color-secondary)"
              >
                每人每 SKU 限购 1 件，新人价需低于当前售价
              </span>
            </ElFormItem>
            <ElFormItem
              v-for="(row, rowIndex) in newbieSkus"
              :key="row.skuId"
              :label="row.spuName"
            >
              <span class="hx-money" style="min-width: 220px">
                {{ row.skuName }} 售价 ¥{{ formatMoney(row.salesPrice) }}
              </span>
              <ElInputNumber
                v-model="row.price"
                :min="0"
                :precision="2"
                :disabled="isPublished"
                controls-position="right"
                style="width: 120px; margin: 0 8px"
              />
              <ElButton
                link
                type="danger"
                :disabled="isPublished"
                @click="newbieSkus.splice(rowIndex, 1)"
              >
                移除
              </ElButton>
            </ElFormItem>
          </template>
          <template v-else-if="form.activityType === '12'">
            <ElFormItem label="归属档期">
              <ElSelect
                v-model="calendarId"
                :disabled="isPublished"
                filterable
                placeholder="选择采购档期"
                style="width: 320px"
              >
                <ElOption
                  v-for="calendar in calendarOptions"
                  :key="calendar.id"
                  :label="calendar.calendarName"
                  :value="calendar.id"
                />
              </ElSelect>
              <span
                class="ml-8px"
                style="color: var(--el-text-color-secondary)"
              >
                档期停用或窗口外时特供自动退出试算
              </span>
            </ElFormItem>
            <ElFormItem label="活动限量">
              <ElInputNumber
                v-model="specialTotalQuota"
                :min="0"
                :disabled="isPublished"
                controls-position="right"
              />
              <span
                class="ml-8px"
                style="color: var(--el-text-color-secondary)"
              >
                全活动累计限购件数（按下单件数原子扣减，0=不限）；每人限购在下方通用设置
              </span>
            </ElFormItem>
            <ElFormItem label="特供商品">
              <ElButton
                type="primary"
                :disabled="isPublished"
                @click="openSelect('special')"
              >
                添加商品
              </ElButton>
              <span
                class="ml-10px"
                style="color: var(--el-text-color-secondary)"
              >
                特供价需低于当前售价；档期特供为硬促销，默认不可叠券/积分
              </span>
            </ElFormItem>
            <ElFormItem
              v-for="(row, rowIndex) in specialSkus"
              :key="row.skuId"
              :label="row.spuName"
            >
              <span class="hx-money" style="min-width: 220px">
                {{ row.skuName }} 售价 ¥{{ formatMoney(row.salesPrice) }}
              </span>
              <ElInputNumber
                v-model="row.price"
                :min="0"
                :precision="2"
                :disabled="isPublished"
                controls-position="right"
                style="width: 120px; margin: 0 8px"
              />
              <ElButton
                link
                type="danger"
                :disabled="isPublished"
                @click="specialSkus.splice(rowIndex, 1)"
              >
                移除
              </ElButton>
            </ElFormItem>
          </template>
          <template v-else>
            <ElFormItem
              v-for="(tier, index) in tiers"
              :key="index"
              :label="`档位 ${index + 1}`"
            >
              <span>满</span>
              <ElInputNumber
                v-model="tier.minAmount"
                :min="0"
                :precision="2"
                :disabled="isPublished"
                controls-position="right"
                style="width: 130px; margin: 0 8px"
              />
              <span>元</span>
              <ElInputNumber
                v-model="tier.minQty"
                :min="0"
                :precision="0"
                :disabled="isPublished"
                controls-position="right"
                style="width: 110px; margin: 0 8px"
              />
              <span>件（金额与件数填其一或同填=同时满足），减</span>
              <ElInputNumber
                v-model="tier.discountAmount"
                :min="0"
                :precision="2"
                :disabled="isPublished"
                controls-position="right"
                style="width: 120px; margin: 0 8px"
              />
              <ElButton
                link
                type="primary"
                :disabled="isPublished"
                @click="addTier"
              >
                加档
              </ElButton>
            </ElFormItem>
            <ElFormItem label="首单专享">
              <ElSwitch
                v-model="firstOrderOnly"
                :disabled="isPublished"
                active-text="仅主体首单可享"
              />
            </ElFormItem>
            <ElFormItem v-if="firstOrderOnly" label="首单主体">
              <ElSelect
                v-model="firstOrderSubjectType"
                :disabled="isPublished"
                style="width: 180px"
              >
                <ElOption label="按船（船供采购）" value="1" />
                <ElOption label="按采购主体（企业/工厂）" value="2" />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="月预算">
              <ElInputNumber
                v-model="monthlyBudget"
                :disabled="isPublished"
                :min="0"
                :precision="2"
                controls-position="right"
                style="width: 160px"
              />
              <span
                class="ml-10px"
                style="color: var(--el-text-color-secondary)"
              >
                0 表示不限；达到 80% 时运营预警
              </span>
            </ElFormItem>
            <ElFormItem label="单笔封顶">
              <ElInputNumber
                v-model="maxDiscountAmount"
                :disabled="isPublished"
                :min="0"
                :precision="2"
                controls-position="right"
                style="width: 160px"
              />
              <span
                class="ml-10px"
                style="color: var(--el-text-color-secondary)"
              >
                0 表示不封顶
              </span>
            </ElFormItem>
          </template>
        </ElForm>
        <template #footer>
          <div class="hx-form-footer">
            <ElButton @click="openDry">试算</ElButton>
            <ElButton @click="dialogVisible = false">取消</ElButton>
            <ElButton type="primary" @click="handleSave">确定</ElButton>
          </div>
        </template>
      </ElDialog>

      <ElDialog
        v-model="dryVisible"
        class="ship-activity-dialog"
        title="命中试算（不落库）"
        top="8vh"
        width="640px"
      >
        <ElForm label-width="auto">
          <ElFormItem label="购买场景">
            <ElSelect v-model="dryForm.purchaseScene" style="width: 160px">
              <ElOption label="个人购买" value="1" />
              <ElOption label="船供采购" value="2" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="靠港">
            <ElSelect
              v-model="dryForm.vesselCallId"
              clearable
              filterable
              placeholder="不限"
              style="width: 320px"
              @change="onDryCallChange"
            >
              <ElOption
                v-for="call in callOptions"
                :key="call.id"
                :label="`${call.portName} ${String(call.eta).slice(0, 10)}`"
                :value="call.id"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="购物车">
            <ElButton type="primary" @click="openSelect('dry')">
              添加商品
            </ElButton>
          </ElFormItem>
          <ElFormItem
            v-for="(item, index) in dryForm.items"
            :key="item.skuId"
            :label="item.spuName"
          >
            <span style="min-width: 160px">{{ item.skuName }}</span>
            <ElInputNumber
              v-model="item.quantity"
              :min="1"
              controls-position="right"
              style="width: 110px; margin: 0 8px"
            />
            <ElButton
              link
              type="danger"
              @click="dryForm.items.splice(index, 1)"
            >
              移除
            </ElButton>
          </ElFormItem>
        </ElForm>
        <template v-if="dryResult">
          <div class="hx-money" style="font-weight: bold">
            合计优惠：¥{{ formatMoney(dryResult.totalDiscount ?? 0) }}
          </div>
          <div
            v-for="detail in dryResult.details || []"
            :key="detail.activityId"
            class="hx-money"
            style="margin-top: 4px; color: var(--el-color-warning)"
          >
            {{ detail.activityName }}：优惠 ¥{{
              formatMoney(detail.discountAmount)
            }}
          </div>
          <div
            v-for="override in dryResult.ladderOverrides || []"
            :key="override.skuId"
            class="hx-money"
            style="margin-top: 4px; color: var(--el-color-warning)"
          >
            阶梯价 {{ skuDisplayText(override.skuId) }} → ¥{{
              formatMoney(override.unitPrice)
            }}
          </div>
          <div
            v-for="gift in dryResult.gifts || []"
            :key="gift.skuId"
            style="margin-top: 4px; color: var(--el-color-warning)"
          >
            赠品 {{ skuDisplayText(gift.skuId) }} ×{{ gift.quantity }}
          </div>
          <div
            v-if="
              (dryResult.details || []).length === 0 &&
              (dryResult.gifts || []).length === 0
            "
            style="margin-top: 4px; color: var(--el-text-color-secondary)"
          >
            无命中活动
          </div>
        </template>
        <template #footer>
          <div class="hx-form-footer">
            <ElButton @click="dryVisible = false">取消</ElButton>
            <ElButton :loading="dryLoading" type="primary" @click="runDry">
              确定
            </ElButton>
          </div>
        </template>
      </ElDialog>

      <SelectGoods
        ref="selectGoodsRef"
        :limit-num="50"
        @current-row="onGoodsSelected"
      />
    </div>
  </div>
</template>

<style scoped>
/* 活动表单较长：把弹窗限制在视口内、内容在 body 内滚动，
   标题与底部按钮始终可点，避免整页跟随滚动。
   弹窗 class 通过 $attrs 透传到 .el-dialog，拿不到 scoped 属性，需从容器下沉 */
.hx-layout-container :deep(.ship-activity-dialog) {
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - var(--el-dialog-margin-top, 15vh) - 56px);
}

.hx-layout-container :deep(.ship-activity-dialog .el-dialog__body) {
  flex: 1;
  min-height: 0;
  padding-top: 4px;
  overflow-y: auto;
}
</style>
