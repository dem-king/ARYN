<script setup lang="ts">
import type { RetailGoodsItem } from '../page-design/components/common/retail-preview/retail-data';

import type {
  CampaignDefinition,
  CampaignDraft,
  CampaignGeneration,
  CampaignPagePreview,
  CampaignPublication,
  CampaignSimulation,
  PublishCommand,
} from '#/api/promotion/campaign-draft';

import {
  computed,
  defineAsyncComponent,
  nextTick,
  onActivated,
  onBeforeUnmount,
  onDeactivated,
  onMounted,
  ref,
  toRaw,
  watch,
} from 'vue';
import { useRouter } from 'vue-router';

import { useAccess } from '@vben/access';
import { useUserStore } from '@vben/stores';

import { useTimestamp } from '@vueuse/core';
import {
  ElAlert,
  ElButton,
  ElCheckbox,
  ElCheckboxGroup,
  ElDatePicker,
  ElDialog,
  ElDrawer,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getDisplayByIds } from '#/api/product/goods-sku';
import { getById as getSpu } from '#/api/product/goods-spu';
import * as api from '#/api/promotion/campaign-draft';

import CampaignObjectSelect from './campaign-object-select.vue';
import CampaignRuleEditor from './campaign-rule-editor.vue';
import CampaignSamplePreview from './campaign-sample-preview.vue';
import {
  campaignTemplates,
  canPublish,
  generationStatus,
  isCurrentSimulation,
  mainTypes,
  missingCampaignFields,
  publicationEnvelope,
  publicationStatus,
  requiredFieldLabels,
} from './campaign-state';

const SelectGoods = defineAsyncComponent(
  () => import('#/components/select-goods/index.vue'),
);
function copy<T>(value: T): T {
  return structuredClone(toRaw(value));
}
const router = useRouter();
const user = useUserStore();
const { hasAccessByCodes } = useAccess();
const allowed = (code: string) => hasAccessByCodes([code]);
const list = ref<CampaignDraft[]>([]);
const draft = ref<CampaignDraft>();
const jobs = ref<CampaignGeneration[]>([]);
const publications = ref<CampaignPublication[]>([]);
const simulation = ref<CampaignSimulation>();
const preview = ref<CampaignPagePreview>();
const metrics = ref<Record<string, null | number>>();
const busy = ref(false);
const error = ref('');
const steps = ['说需求', '看方案', '预览发布'];
const step = ref(0);
const panel = ref<HTMLElement>();
const showDetails = ref(false);
const showActivities = ref(false);
const listFilter = ref<'all' | 'draft' | 'published'>('all');
const showSample = ref(false);
const editRules = ref(false);
const checkedFields = ref(false);
const activeJob = computed(() => jobs.value[0]);
const generating = computed(() => {
  const job = activeJob.value;
  return Boolean(
    job && (['QUEUED', 'RUNNING'].includes(job.status) || job.nextRetryAt),
  );
});
const published = computed(() => draft.value?.status === 'PUBLISHED');
const canEdit = computed(() => allowed('promotion:campaign:edit'));
const canGenerate = computed(() => allowed('promotion:campaign:generate'));
const canSimulate = computed(() => allowed('promotion:campaign:simulate'));
const canPreview = computed(
  () => canSimulate.value && allowed('promotion:pagedesign:edit'),
);
const canCreatePage = computed(
  () =>
    canEdit.value &&
    allowed('promotion:pagedesign:add') &&
    allowed('promotion:pagedesign:edit'),
);
const canSavePolicy = computed(
  () => canEdit.value && allowed('promotion:pricepolicy:save'),
);
const canRelease = computed(
  () =>
    canSimulate.value &&
    allowed('promotion:campaign:publish') &&
    allowed('promotion:pagedesign:publish') &&
    allowed(
      form.value.primaryType === 'POLICY'
        ? 'promotion:pricepolicy:publish'
        : 'promotion:shipactivity:publish',
    ) &&
    (!form.value.coupon || allowed('promotion:couponinfo:add')),
);
const visibleCampaigns = computed(() =>
  list.value.filter(
    (item) =>
      listFilter.value === 'all' ||
      (item.status === 'PUBLISHED') === (listFilter.value === 'published'),
  ),
);
const policyPrices = ref<{ minQty: number; price: number; skuId: string }[]>(
  [],
);
const sampleQuantities = ref<Record<string, number>>({});
const sampleTime = ref('');
interface SkuOption {
  id: string;
  label: string;
  minimum?: number;
  unit?: string;
}
const skuLabels = ref<SkuOption[]>([]);
const skuOptions = ref<SkuOption[]>([]);
const skuSelection = ref<string[]>([]);
const showSkuPicker = ref(false);
const selectedSkus = computed(() =>
  form.value.skuIds.map(
    (id) => skuLabels.value.find((sku) => sku.id === id) ?? { id, label: id },
  ),
);
const picker = ref<InstanceType<typeof SelectGoods>>();
const defaultForm = (): CampaignDefinition => ({
  title: '',
  intent: '',
  purchaseScene: '',
  sampleUserId: '',
  skuIds: [],
  startTime: '',
  endTime: '',
});
const form = ref(defaultForm());
const objectLabels = ref<Record<string, string>>({});
const missingFields = computed(() => missingCampaignFields(form.value));
const selectedType = computed(
  () => form.value.primaryType ?? form.value.activity?.activityType,
);
const typeLabel = computed(
  () => mainTypes.find((type) => type.value === selectedType.value)?.label,
);
const activeTemplate = computed(() =>
  campaignTemplates.find((item) => item.type === selectedType.value),
);
const sceneLabel = computed(
  () =>
    ({ '1': '个人采购', '2': '船供采购', '3': '企业采购' })[
      form.value.purchaseScene
    ] ?? '待选择采购场景',
);
const subjectLabel = computed(() => {
  if (form.value.purchaseScene === '2' && form.value.vesselId) {
    return objectLabels.value[form.value.vesselId] ?? '所选船舶';
  }
  if (form.value.purchaseScene === '3' && form.value.buyerOrgId) {
    return objectLabels.value[form.value.buyerOrgId] ?? '所选企业';
  }
  return sceneLabel.value;
});
const summary = computed(() => {
  const rule = form.value.activity;
  if (rule?.activityType !== '7') return rule?.activityName || typeLabel.value;
  try {
    const tiers: {
      discountAmount: number;
      minAmount?: number;
      minQty?: number;
    }[] = JSON.parse(rule.rules).tiers ?? [];
    return (
      tiers
        .filter((tier) => tier.discountAmount > 0)
        .map(
          (tier) =>
            `满 ${tier.minAmount ? money(tier.minAmount) : `${tier.minQty} 件`} 减 ${money(tier.discountAmount)}`,
        )
        .join(' · ') || rule.activityName
    );
  } catch {
    return rule.activityName;
  }
});
const baseline = ref(JSON.stringify(form.value));
const policyBaseline = ref('[]');
const dirty = computed(
  () =>
    JSON.stringify(form.value) !== baseline.value ||
    JSON.stringify(policyPrices.value) !== policyBaseline.value,
);
let epoch = 0;
let envelope: PublishCommand | undefined;
let generationEnvelope: undefined | { requestId: string; revision: number };
const acceptedRisks = ref<string[]>([]);
const overlap = ref(false);
const overlapError = ref('');
const riskConfirmations = computed(() =>
  (simulation.value?.risks ?? []).filter(
    (r) =>
      ![
        'CURRENT_FACT_ASSUMPTION',
        'EXCLUDES_FREIGHT_POINTS',
        'GIFT_STOCK_UNPROVEN',
        'STOCK_SHORT',
      ].includes(r.code),
  ),
);
const now = useTimestamp({ interval: 1000 });
const currentSimulation = computed(
  () =>
    !dirty.value &&
    isCurrentSimulation(draft.value, simulation.value, now.value),
);
const savings = computed(
  () =>
    (simulation.value?.orderComparison.baseline.goodsPayable ?? 0) -
    (simulation.value?.orderComparison.proposed.goodsPayable ?? 0),
);
const metricLabels = [
  { id: 'PAGE_VIEW', label: '近 7 日页面浏览事件' },
  { id: 'COMPONENT_CLICK', label: '组件点击事件' },
  { id: 'RENDER_ERROR', label: '渲染错误事件' },
  { id: 'ACTIVITY_CONSUMED_BUDGET', label: '活动累计消耗额度' },
];
const stockIssues = computed(() =>
  (simulation.value?.risks ?? []).filter((risk) =>
    ['GIFT_STOCK_UNPROVEN', 'STOCK_SHORT'].includes(risk.code),
  ),
);
const hasOverlap = computed(
  () =>
    Boolean(overlapError.value) ||
    (simulation.value?.assumptions.overlappingActivities.length ?? 0) > 0,
);
const publicationBlock = computed(() => {
  if (!canRelease.value) return '当前账号缺少发布所需权限。';
  if (stockIssues.value.length > 0) return '处理库存问题后重新试算。';
  if (!currentSimulation.value) return '请先完成本版本的订单试算。';
  if (!preview.value?.current) return '请先核对活动页预览。';
  if (overlapError.value && !overlap.value)
    return '请核对与其他活动的时间冲突。';
  if (
    riskConfirmations.value.some(
      (risk) => !acceptedRisks.value.includes(risk.code),
    )
  )
    return '请核对以下试算风险。';
  return '';
});
const ready = computed(
  () =>
    !publicationBlock.value &&
    canPublish(
      draft.value,
      simulation.value,
      preview.value,
      dirty.value,
      now.value,
    ),
);
const editable = computed(
  () => canEdit.value && !published.value && !generating.value,
);
let timer: ReturnType<typeof setTimeout> | undefined;
function invalidate() {
  simulation.value = undefined;
  preview.value = undefined;
  acceptedRisks.value = [];
  overlap.value = false;
  overlapError.value = '';
  envelope = undefined;
}

function money(value: number | undefined) {
  return value === null || value === undefined
    ? '—'
    : new Intl.NumberFormat('zh-CN', {
        style: 'currency',
        currency: 'CNY',
      }).format(value);
}

function showTime(value?: string) {
  return value?.replace('T', ' ').slice(0, 16) || '待设置';
}

function revealDetails() {
  if (!form.value.intent.trim()) {
    checkedFields.value = true;
    panel.value?.querySelector<HTMLElement>('textarea')?.focus();
    return;
  }
  showDetails.value = true;
  checkedFields.value = false;
  void nextTick(() =>
    panel.value
      ?.querySelector<HTMLElement>('[data-field="purchaseScene"] input')
      ?.focus(),
  );
}

function skuName(id: string) {
  return selectedSkus.value.find((sku) => sku.id === id)?.label ?? id;
}

function riskText(risk: CampaignSimulation['risks'][number]) {
  return risk.skuId ? `${skuName(risk.skuId)} · ${risk.message}` : risk.message;
}

function nextAction(item: CampaignDraft) {
  if (item.status === 'PUBLISHED') return '查看发布结果';
  if (item.status === 'READY')
    return item.simulationId ? '继续核对' : '查看方案';
  return item.missingFields.length > 0 ? '补齐信息' : '继续制作';
}

function goTo(value: number) {
  step.value = value;
  error.value = '';
  void nextTick(() => {
    panel.value?.focus({ preventScroll: true });
    panel.value?.scrollIntoView({ block: 'start' });
  });
}

function pickTemplate(item: (typeof campaignTemplates)[number]) {
  if (
    !form.value.intent.trim() ||
    campaignTemplates.some((template) => template.example === form.value.intent)
  ) {
    form.value.intent = item.example;
  }
  if (form.value.primaryType !== item.type) changeType(item.type);
  if (item.type === '12' && !form.value.purchaseScene)
    form.value.purchaseScene = '2';
}

function changeType(value: CampaignDefinition['primaryType']) {
  form.value.primaryType = value;
  form.value.activity = undefined;
  if (value !== '12') form.value.calendarId = undefined;
  if (value !== 'POLICY') policyPrices.value = [];
}

function changeScene(value: string) {
  form.value.purchaseScene = value;
  form.value.sampleUserId = '';
  form.value.vesselId = undefined;
  form.value.vesselCallId = undefined;
  form.value.buyerOrgId = undefined;
}

function checkDetails() {
  checkedFields.value = true;
  showDetails.value = true;
  const missing = missingFields.value[0];
  if (missing) {
    void nextTick(() => {
      const field = panel.value?.querySelector(`[data-field="${missing}"]`);
      field?.scrollIntoView({ block: 'center' });
      field
        ?.querySelector<HTMLElement>('input, textarea, button')
        ?.focus({ preventScroll: true });
    });
    return false;
  }
  if (
    form.value.startTime &&
    form.value.endTime &&
    form.value.startTime >= form.value.endTime
  ) {
    error.value = '结束时间须晚于开始时间。';
    return false;
  }
  return true;
}

function dateRange(days: number) {
  const start = new Date();
  const end = new Date(start);
  end.setDate(start.getDate() + days);
  const local = (date: Date) =>
    new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
      .toISOString()
      .slice(0, 19);
  form.value.startTime = local(start);
  form.value.endTime = local(end);
}
watch(
  () => JSON.stringify(form.value),
  () => {
    if (dirty.value) invalidate();
  },
);
watch([sampleTime, sampleQuantities], invalidate, { deep: true });
watch(
  policyPrices,
  () => {
    if (dirty.value) invalidate();
  },
  { deep: true },
);
watch(
  () => user.userInfo,
  () => {
    epoch++;
    clear();
    list.value = [];
  },
  { deep: true },
);
function clear() {
  clearTimeout(timer);
  step.value = 0;
  showDetails.value = false;
  checkedFields.value = false;
  showSample.value = false;
  editRules.value = false;
  showSkuPicker.value = false;
  draft.value = undefined;
  form.value = defaultForm();
  baseline.value = JSON.stringify(form.value);
  invalidate();
  jobs.value = [];
  publications.value = [];
  metrics.value = undefined;
  generationEnvelope = undefined;
  policyPrices.value = [];
  policyBaseline.value = '[]';
  sampleQuantities.value = {};
  sampleTime.value = '';
  skuLabels.value = [];
  objectLabels.value = {};
}

function rememberObject(id: string, label: string) {
  objectLabels.value[id] = label;
}

function operationMessage(failure: unknown) {
  const response = (failure as { response?: { data?: { msg?: string } } })
    ?.response;
  return (
    response?.data?.msg ||
    (failure instanceof Error ? failure.message : '操作失败，请重试。')
  );
}

async function guard(action: (ticket: number) => Promise<void>) {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  const ticket = epoch;
  try {
    await action(ticket);
  } catch (error_) {
    if (ticket === epoch) error.value = operationMessage(error_);
  } finally {
    if (ticket === epoch) busy.value = false;
  }
}
async function loadList() {
  if (!canEdit.value) return;
  const ticket = epoch;
  const data = await api.listCampaigns();
  if (ticket === epoch) list.value = data;
}

async function changeJob(action: 'cancel' | 'retry') {
  const job = activeJob.value;
  if (!job) return;
  await guard(async (ticket) => {
    await api.generationAction(job.id, action);
    await refreshRecords(ticket);
    if (ticket === epoch && action === 'retry') schedulePoll();
  });
}
function apply(value: CampaignDraft) {
  const sameDraft = draft.value?.id === value.id;
  const quantities = sameDraft ? sampleQuantities.value : {};
  draft.value = value;
  const definition = copy(value.definition);
  form.value = {
    ...definition,
    title: definition.title ?? '',
    intent: definition.intent ?? '',
    skuIds: definition.skuIds ?? [],
  };
  baseline.value = JSON.stringify(form.value);
  generationEnvelope = undefined;
  const startTime = definition.startTime ?? '';
  const endTime = definition.endTime ?? '';
  if (!sameDraft || sampleTime.value < startTime || sampleTime.value >= endTime)
    sampleTime.value = startTime;
  sampleQuantities.value = Object.fromEntries(
    form.value.skuIds.map((id) => [
      id,
      quantities[id] ??
        selectedSkus.value.find((sku) => sku.id === id)?.minimum ??
        1,
    ]),
  );
  invalidate();
  const policy = (
    value.definition as CampaignDefinition & {
      policy?: { items: { minQty: number; price: number; skuId: string }[] };
    }
  ).policy;
  policyPrices.value = copy(policy?.items ?? []);
  policyBaseline.value = JSON.stringify(policyPrices.value);
}
async function mayLeaveDraft() {
  if (!dirty.value) return true;
  try {
    await ElMessageBox.confirm(
      '当前修改尚未保存，继续会离开这份活动。',
      '离开当前草稿',
      { confirmButtonText: '继续', cancelButtonText: '返回编辑' },
    );
    return true;
  } catch {
    return false;
  }
}

async function newCampaign() {
  if (busy.value || !(await mayLeaveDraft())) return;
  epoch++;
  showActivities.value = false;
  clear();
  goTo(0);
}

async function open(value: CampaignDraft) {
  if (busy.value || !(await mayLeaveDraft())) return;
  epoch++;
  clearTimeout(timer);
  await guard(async (ticket) => {
    const loaded = await api.getCampaign(value.id);
    if (ticket !== epoch) return;
    clear();
    showActivities.value = false;
    apply(loaded);
    showDetails.value = true;
    await resume(ticket);
  });
}

async function resume(ticket: number) {
  const existing = draft.value;
  if (!existing) return;
  await refreshRecords(ticket);
  if (ticket !== epoch) return;
  if (published.value) {
    goTo(2);
    return;
  }
  if (generating.value) schedulePoll();
  if (existing.status === 'READY' || activeJob.value) goTo(1);
  if (!existing.simulationId || !canSimulate.value || dirty.value) return;
  const result = await api.getCampaignSimulation(existing.simulationId);
  if (ticket !== epoch) return;
  sampleQuantities.value = Object.fromEntries(
    result.skuFacts.map((row) => [row.skuId, row.sampleQuantity]),
  );
  await nextTick();
  if (ticket !== epoch) return;
  simulation.value = result;
  if (currentSimulation.value) {
    goTo(2);
    if (existing.pageId && canPreview.value) await loadPreview(ticket);
  }
}
async function refreshRecords(ticket = epoch) {
  if (!draft.value) return;
  const id = draft.value.id;
  const results = await Promise.allSettled([
    allowed('promotion:campaign:generate')
      ? api.getCampaignJobs(id)
      : Promise.resolve([]),
    allowed('promotion:campaign:publish')
      ? api.getCampaignPublications(id)
      : Promise.resolve([]),
    allowed('promotion:pagedesign:metrics')
      ? api.getCampaignMetrics(id)
      : Promise.resolve(undefined),
    form.value.skuIds.length > 0
      ? getDisplayByIds(form.value.skuIds)
      : Promise.resolve([]),
  ]);
  if (ticket !== epoch || draft.value?.id !== id) return;
  if (results[0].status === 'fulfilled') jobs.value = results[0].value;
  if (results[1].status === 'fulfilled') publications.value = results[1].value;
  if (results[2].status === 'fulfilled') metrics.value = results[2].value;
  if (results[3].status === 'fulfilled')
    skuLabels.value = results[3].value.map((s) => ({
      ...skuLabels.value.find((sku) => sku.id === s.skuId),
      id: s.skuId,
      label: `${s.spuName ?? ''} ${s.skuName ?? s.skuId}`.trim(),
    }));
}
async function save(ticket: number) {
  const payload = copy(form.value) as CampaignDefinition & {
    policy?: unknown;
  };
  delete payload.policy;
  payload.title = payload.title.trim() || payload.intent.trim().slice(0, 36);
  payload.startTime ||= undefined;
  payload.endTime ||= undefined;
  payload.budget ||= undefined;
  payload.maxDiscountPercent ||= undefined;
  payload.expectedRevision = draft.value?.revision;
  if (payload.primaryType === 'POLICY') {
    payload.primaryType = undefined;
    payload.activity = undefined;
  }
  const saved = await api.saveCampaign(draft.value?.id, payload);
  if (ticket !== epoch) return;
  const isPolicy = form.value.primaryType === 'POLICY';
  const prices = copy(policyPrices.value);
  apply(saved);
  if (isPolicy) {
    form.value.primaryType = 'POLICY';
    policyPrices.value = prices;
  }
  await loadList();
}
async function savePolicy(ticket: number) {
  if (!canSavePolicy.value) throw new Error('当前账号缺少保存协议价的权限。');
  if (!draft.value || JSON.stringify(form.value) !== baseline.value)
    await save(ticket);
  if (ticket !== epoch || !draft.value) return;
  const saved = await api.saveCampaignPolicy(draft.value.id, {
    expectedRevision: draft.value.revision,
    name: form.value.title,
    items: policyPrices.value,
  });
  if (ticket === epoch) {
    apply(saved);
    await loadList();
  }
}

async function saveDraft() {
  await guard(async (ticket) => {
    if (form.value.primaryType === 'POLICY' && policyPrices.value.length > 0)
      await savePolicy(ticket);
    else if (dirty.value || !draft.value) await save(ticket);
  });
}
async function generate() {
  if (generating.value || !checkDetails()) return;
  if (form.value.activity) {
    try {
      await ElMessageBox.confirm(
        '新方案生成后会替换当前优惠规则和建议券草稿。',
        '重新生成优惠方案',
        { confirmButtonText: '重新生成', cancelButtonText: '继续核对' },
      );
    } catch {
      return;
    }
  }
  await guard(async (ticket) => {
    if (dirty.value || !draft.value) await save(ticket);
    if (ticket !== epoch || !draft.value) return;
    generationEnvelope ??= {
      revision: draft.value.revision,
      requestId: crypto.randomUUID(),
    };
    await api.generateCampaign(
      draft.value.id,
      generationEnvelope.revision,
      generationEnvelope.requestId,
    );
    await refreshRecords(ticket);
    if (ticket === epoch) {
      editRules.value = false;
      goTo(1);
      schedulePoll();
    }
  });
}
function schedulePoll() {
  clearTimeout(timer);
  timer = setTimeout(async () => {
    if (!draft.value || busy.value) {
      schedulePoll();
      return;
    }
    const ticket = epoch;
    try {
      await refreshRecords(ticket);
      if (ticket !== epoch) return;
      if (
        jobs.value.some(
          (j) =>
            ['QUEUED', 'RUNNING'].includes(j.status) || Boolean(j.nextRetryAt),
        )
      ) {
        schedulePoll();
      } else if (!dirty.value) {
        const current = await api.getCampaign(draft.value.id);
        if (ticket === epoch && current.revision !== draft.value.revision) {
          apply(current);
          await loadList();
        }
        generationEnvelope = undefined;
      }
    } catch {
      if (ticket === epoch) error.value = '生成进度读取失败，可刷新活动包恢复';
    }
  }, 3000);
}
async function runTrial() {
  if (missingFields.value.length > 0) {
    goTo(0);
    checkDetails();
    return;
  }
  await guard(async (ticket) => {
    if (!draft.value || dirty.value) {
      await (form.value.primaryType === 'POLICY'
        ? savePolicy(ticket)
        : save(ticket));
    }
    if (ticket !== epoch || draft.value?.status !== 'READY') return;
    const d = form.value;
    const activityTime = sampleTime.value || d.startTime;
    if (!activityTime) throw new Error('请设置试算时间。');
    const result = await api.simulateCampaign({
      campaignDraftId: draft.value.id,
      expectedCampaignRevision: draft.value.revision,
      sample: {
        userId: d.sampleUserId,
        purchaseScene: d.purchaseScene,
        vesselId: d.vesselId,
        buyerOrgId: d.buyerOrgId,
        vesselCallId: d.vesselCallId,
        activityTime,
        skuItems: d.skuIds.map((skuId) => ({
          skuId,
          quantity: sampleQuantities.value[skuId] ?? 1,
        })),
      },
    });
    if (ticket === epoch) {
      await nextTick();
      if (ticket !== epoch) return;
      simulation.value = result;
      preview.value = undefined;
      acceptedRisks.value = [];
      overlap.value = false;
      overlapError.value = '';
      envelope = undefined;
      goTo(2);
      if (!draft.value.pageId && canCreatePage.value) await preparePage(ticket);
      if (ticket === epoch && draft.value.pageId && canPreview.value)
        await loadPreview(ticket);
    }
  });
}
async function preparePage(ticket: number) {
  if (!draft.value) return;
  const value = await api.generateCampaignPage(draft.value.id, {
    expectedRevision: draft.value.revision,
    expectedPageRevision: draft.value.pageRevision,
  });
  if (ticket === epoch) {
    draft.value = value;
    preview.value = undefined;
  }
}
async function pageGenerate() {
  await guard(async (ticket) => {
    await preparePage(ticket);
    if (ticket === epoch && canPreview.value) await loadPreview(ticket);
  });
}
async function loadPreview(ticket: number, review = false) {
  if (!draft.value?.pageId || !simulation.value || !currentSimulation.value)
    return;
  const value = await api.previewCampaignPage(
    draft.value.id,
    {
      expectedRevision: draft.value.revision,
      expectedPageRevision:
        preview.value?.page.draftRevision ?? draft.value.pageRevision,
      simulationId: simulation.value.id,
    },
    review,
  );
  if (ticket === epoch) {
    preview.value = value;
    simulation.value = value.simulation;
    envelope = undefined;
    if (review) {
      draft.value.pageRevision = value.page.draftRevision;
      draft.value.pageDigest = value.pageDigest;
    }
  }
}
async function pagePreview(review = false) {
  await guard((ticket) => loadPreview(ticket, review));
}

function editPage() {
  const id = draft.value?.pageId;
  if (id) void router.push({ name: 'PageDesigner', params: { id } });
}
async function publish() {
  await guard(async (ticket) => {
    if (!ready.value || !draft.value || !preview.value || !simulation.value)
      return;
    try {
      await ElMessageBox.confirm(
        `将启用「${form.value.title}」的主方案与页面。${form.value.coupon ? '定向券将保存为停用模板。' : ''}`,
        '人工发布确认',
        { confirmButtonText: '确认发布', cancelButtonText: '返回核对' },
      );
    } catch {
      return;
    }
    if (
      ticket !== epoch ||
      !ready.value ||
      !draft.value ||
      !preview.value ||
      !simulation.value
    )
      return;
    envelope = publicationEnvelope(envelope, {
      expectedRevision: draft.value.revision,
      expectedPageRevision: preview.value.page.draftRevision,
      simulationId: simulation.value.id,
      inputDigest: simulation.value.inputDigest,
      factDigest: simulation.value.factDigest,
      pageDigest: preview.value.pageDigest,
      acceptedRiskCodes: acceptedRisks.value,
      acceptOverlap: overlap.value,
    });
    try {
      const result = await api.publishCampaign(draft.value.id, envelope);
      if (ticket === epoch) {
        if (result.status !== 'PUBLISHED')
          throw new Error('发布尚未完成，请查看发布记录。');
        ElMessage.success(
          form.value.coupon ? '活动已发布，定向券模板已准备' : '活动已发布',
        );
        apply(await api.getCampaign(draft.value.id));
        await loadList();
      }
    } catch (error_) {
      if (ticket === epoch) {
        const message = operationMessage(error_);
        if (message.includes('与已发布活动时间重叠'))
          overlapError.value = message;
      }
      throw error_;
    } finally {
      await refreshRecords(ticket);
    }
  });
}
async function retryPublication(record: CampaignPublication) {
  await guard(async (ticket) => {
    try {
      await ElMessageBox.confirm(
        '使用原发布记录重新核验并尝试提交；已改变的页面或事实将被拒绝。',
        '恢复发布',
      );
    } catch {
      return;
    }
    await api.retryCampaignPublication(record.id);
    if (ticket === epoch && draft.value) {
      apply(await api.getCampaign(draft.value.id));
      await refreshRecords(ticket);
      await loadList();
    }
  });
}
async function selectProducts(rows: { id: string; name: string }[]) {
  await guard(async (ticket) => {
    const details = await Promise.all(rows.map((row) => getSpu(row.id)));
    if (ticket !== epoch) return;
    const options = new Map(selectedSkus.value.map((sku) => [sku.id, sku]));
    for (const [index, detail] of details.entries()) {
      for (const sku of detail.goodsSkus ?? []) {
        options.set(sku.id, {
          id: sku.id,
          label: [rows[index]?.name, sku.name ?? sku.id, sku.salesUnit]
            .filter(Boolean)
            .join(' · '),
          minimum: sku.minOrderQty || 1,
          unit: sku.salesUnit,
        });
      }
    }
    skuOptions.value = [...options.values()];
    skuSelection.value = [...form.value.skuIds];
    showSkuPicker.value = true;
  });
}
function confirmSkus() {
  form.value.skuIds = [...skuSelection.value];
  skuLabels.value = skuOptions.value.filter((sku) =>
    skuSelection.value.includes(sku.id),
  );
  for (const sku of skuLabels.value)
    sampleQuantities.value[sku.id] ??= sku.minimum ?? 1;
  showSkuPicker.value = false;
}

function manual() {
  if (!checkDetails()) return;
  if (form.value.primaryType !== 'POLICY' && !form.value.activity) {
    form.value.primaryType ??= '7';
    form.value.activity = {
      activityName: form.value.title || form.value.intent.slice(0, 36),
      activityType: form.value.primaryType,
      rules: '{}',
    };
  }
  editRules.value = true;
  goTo(1);
}
const capturedGoods = computed<ReadonlyMap<string, RetailGoodsItem>>(
  () =>
    new Map(
      (preview.value?.goods ?? []).map((g) => [
        g.id,
        {
          id: g.id,
          name: g.name,
          imageUrl: '',
          price: Number(g.price),
          originalPrice: Number(g.originalPrice),
          stock: g.stock ?? Number.NaN,
          sales: 0,
        },
      ]),
    ),
);
onMounted(() => {
  void guard(async () => loadList());
});
let deactivated = false;
onActivated(() => {
  if (!deactivated) return;
  deactivated = false;
  if (draft.value && !dirty.value) void guard(resume);
  else if (canEdit.value) void guard(loadList);
});
onDeactivated(() => {
  deactivated = true;
  epoch++;
  busy.value = false;
  clearTimeout(timer);
  invalidate();
});
onBeforeUnmount(() => {
  epoch++;
  clearTimeout(timer);
  invalidate();
});
</script>

<template>
  <div class="workbench">
    <header class="page-head">
      <div>
        <h1>采购营销工作台</h1>
        <p>用 AI 生成采购优惠，试算核对后发布。</p>
      </div>
      <ElButton @click="showActivities = true">我的活动</ElButton>
    </header>

    <nav class="stepbar" aria-label="活动制作步骤">
      <button
        v-for="(label, index) in steps"
        :key="label"
        type="button"
        :class="{ active: step === index, done: step > index }"
        :aria-current="step === index ? 'step' : undefined"
        :disabled="
          busy ||
          (published
            ? index !== 2
            : index > step &&
              !(
                index === 1 &&
                (form.activity || policyPrices.length > 0 || activeJob)
              ) &&
              !(index === 2 && currentSimulation))
        "
        @click="goTo(index)"
      >
        <span class="step-number">{{ step > index ? '✓' : index + 1 }}</span>
        {{ label }}
      </button>
    </nav>

    <main ref="panel" class="stage" tabindex="-1" :aria-label="steps[step]">
      <ElAlert
        v-if="error"
        :title="error"
        type="error"
        show-icon
        :closable="false"
      />

      <template v-if="step === 0">
        <section class="section">
          <h2>你想做什么活动？</h2>
          <div class="templates">
            <button
              v-for="item in campaignTemplates"
              :key="item.type"
              type="button"
              :aria-pressed="activeTemplate?.type === item.type"
              :disabled="busy || !editable"
              @click="pickTemplate(item)"
            >
              <strong>{{ item.label }}</strong>
              <span>{{ item.description }}</span>
            </button>
          </div>
          <ElForm label-position="top" :disabled="busy || !editable">
            <ElFormItem
              data-field="intent"
              label="用一句话描述活动"
              :error="
                checkedFields && missingFields.includes('intent')
                  ? '请描述要做的活动。'
                  : undefined
              "
            >
              <ElInput
                v-model="form.intent"
                type="textarea"
                :rows="3"
                maxlength="4000"
                placeholder="例如：给海星号的饮用水和纸品做一次满额优惠，优惠总预算 500 元，最多让利 8%。"
              />
            </ElFormItem>
          </ElForm>
        </section>

        <section v-if="showDetails" class="section">
          <h2>补齐适用范围</h2>
          <p
            v-if="checkedFields && missingFields.length > 0"
            class="field-note"
            role="status"
          >
            请补充
            {{
              missingFields
                .map((field) => requiredFieldLabels[field])
                .join('、')
            }}
          </p>
          <ElForm label-position="top" :disabled="busy || !editable">
            <div class="form-grid">
              <ElFormItem data-field="purchaseScene" label="采购场景">
                <ElSelect
                  :model-value="form.purchaseScene"
                  placeholder="选择采购场景"
                  @change="changeScene"
                >
                  <ElOption label="个人采购" value="1" />
                  <ElOption label="船供采购" value="2" />
                  <ElOption label="企业采购" value="3" />
                </ElSelect>
              </ElFormItem>
              <ElFormItem
                v-if="form.purchaseScene === '2'"
                data-field="vesselId"
                label="船舶"
              >
                <CampaignObjectSelect
                  :model-value="form.vesselId"
                  kind="vessel"
                  @label="rememberObject"
                  @update:model-value="
                    (id) => {
                      form.vesselId = id;
                      form.sampleUserId = '';
                      form.vesselCallId = undefined;
                    }
                  "
                />
              </ElFormItem>
              <ElFormItem
                v-else-if="form.purchaseScene === '3'"
                data-field="buyerOrgId"
                label="采购企业"
              >
                <CampaignObjectSelect
                  :model-value="form.buyerOrgId"
                  kind="org"
                  @label="rememberObject"
                  @update:model-value="
                    (id) => {
                      form.buyerOrgId = id;
                      form.sampleUserId = '';
                    }
                  "
                />
              </ElFormItem>
              <ElFormItem
                v-if="form.purchaseScene"
                data-field="sampleUserId"
                label="用于试算的采购人"
              >
                <CampaignObjectSelect
                  v-if="
                    form.purchaseScene === '1' ||
                    form.vesselId ||
                    form.buyerOrgId
                  "
                  :key="form.purchaseScene"
                  :model-value="form.sampleUserId || undefined"
                  :kind="
                    form.purchaseScene === '2'
                      ? 'vesselMember'
                      : form.purchaseScene === '3'
                        ? 'orgMember'
                        : 'member'
                  "
                  :parent-id="form.vesselId || form.buyerOrgId"
                  @update:model-value="form.sampleUserId = $event || ''"
                />
                <small class="muted">{{
                  form.purchaseScene !== '1' &&
                  !form.vesselId &&
                  !form.buyerOrgId
                    ? '请先选定采购主体。'
                    : '用这个账号核对购买资格和实际优惠。'
                }}</small>
              </ElFormItem>
              <ElFormItem
                v-if="form.purchaseScene === '2' && form.vesselId"
                label="靠港计划（需要到船时选择）"
              >
                <CampaignObjectSelect
                  v-model="form.vesselCallId"
                  kind="call"
                  :parent-id="form.vesselId"
                />
              </ElFormItem>
              <ElFormItem
                v-if="selectedType === '12'"
                data-field="calendarId"
                label="采购档期"
              >
                <CampaignObjectSelect
                  v-model="form.calendarId"
                  kind="calendar"
                />
              </ElFormItem>
            </div>

            <div class="date-shortcuts">
              <span>活动时间</span>
              <ElButton text size="small" @click="dateRange(3)">
                未来 3 天
              </ElButton>
              <ElButton text size="small" @click="dateRange(7)">
                未来 7 天
              </ElButton>
            </div>
            <div class="form-grid">
              <ElFormItem data-field="startTime" label="开始时间">
                <ElDatePicker
                  v-model="form.startTime"
                  type="datetime"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  placeholder="选择开始时间"
                />
              </ElFormItem>
              <ElFormItem data-field="endTime" label="结束时间">
                <ElDatePicker
                  v-model="form.endTime"
                  type="datetime"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  placeholder="选择结束时间"
                />
              </ElFormItem>
              <ElFormItem data-field="budget" label="活动优惠总预算（元）">
                <ElInputNumber
                  v-model="form.budget"
                  :min="0.01"
                  :precision="2"
                  :controls="false"
                  placeholder="例如 500"
                />
              </ElFormItem>
              <ElFormItem
                data-field="maxDiscountPercent"
                label="最高让利比例（%）"
              >
                <ElInputNumber
                  v-model="form.maxDiscountPercent"
                  :min="0.0001"
                  :max="100"
                  :precision="4"
                  :controls="false"
                  placeholder="例如 8"
                />
              </ElFormItem>
            </div>
            <ElFormItem data-field="skuIds" label="参与商品规格">
              <div class="goods-selection">
                <ElButton @click="picker?.initPage()">选择商品及规格</ElButton>
                <small class="muted"
                  >已选 {{ selectedSkus.length }} / 100 个规格</small
                >
                <ul v-if="selectedSkus.length > 0" class="selected-skus">
                  <li v-for="sku in selectedSkus" :key="sku.id">
                    <span>{{ sku.label }}</span>
                    <ElButton
                      link
                      type="danger"
                      :aria-label="`移除 ${sku.label}`"
                      @click="
                        form.skuIds = form.skuIds.filter((id) => id !== sku.id)
                      "
                    >
                      移除
                    </ElButton>
                  </li>
                </ul>
              </div>
            </ElFormItem>
            <details
              class="more-settings"
              :open="
                form.primaryType === 'POLICY' ||
                Boolean(form.coupon) ||
                undefined
              "
            >
              <summary>更多设置</summary>
              <div class="form-grid">
                <ElFormItem label="活动名称">
                  <ElInput
                    v-model="form.title"
                    maxlength="100"
                    placeholder="留空时使用活动描述"
                  />
                </ElFormItem>
                <ElFormItem label="优惠方式">
                  <ElSelect
                    :model-value="selectedType"
                    placeholder="由 AI 建议"
                    clearable
                    @change="changeType"
                  >
                    <ElOption
                      v-for="type in mainTypes"
                      :key="type.value"
                      :label="type.label"
                      :value="type.value"
                    />
                  </ElSelect>
                </ElFormItem>
              </div>
              <ElFormItem label="准备定向满减券模板">
                <ElSwitch
                  :model-value="Boolean(form.coupon)"
                  @change="
                    form.coupon = $event
                      ? { name: '', amount: 0, threshold: 0, quantity: 1 }
                      : undefined
                  "
                />
              </ElFormItem>
              <div v-if="form.coupon" class="form-grid">
                <ElFormItem label="券名称">
                  <ElInput v-model="form.coupon.name" maxlength="50" />
                </ElFormItem>
                <ElFormItem label="满额门槛">
                  <ElInputNumber
                    v-model="form.coupon.threshold"
                    :min="0"
                    :precision="2"
                  />
                </ElFormItem>
                <ElFormItem label="每份优惠">
                  <ElInputNumber
                    v-model="form.coupon.amount"
                    :min="0"
                    :precision="2"
                  />
                </ElFormItem>
                <ElFormItem label="计划份数">
                  <ElInputNumber
                    v-model="form.coupon.quantity"
                    :min="1"
                    :max="100000"
                    :precision="0"
                  />
                </ElFormItem>
              </div>
            </details>
          </ElForm>
        </section>
      </template>

      <template v-else-if="step === 1">
        <section class="section">
          <h2>核对优惠方案</h2>
          <ElAlert
            v-if="activeJob && (generating || !form.activity)"
            :title="
              generationStatus[activeJob.status] ?? '可在我的活动查看生成状态'
            "
            :type="activeJob.status === 'FAILED' ? 'warning' : 'info'"
            :closable="false"
            show-icon
          >
            <p v-if="generating">草稿已保存，稍后可从「我的活动」继续。</p>
            <ElButton
              v-if="generating && canGenerate"
              link
              @click="changeJob('cancel')"
            >
              取消生成
            </ElButton>
            <ElButton
              v-if="activeJob.status === 'FAILED' && canGenerate"
              link
              type="primary"
              @click="changeJob('retry')"
            >
              重试
            </ElButton>
          </ElAlert>
          <div
            v-if="form.activity || form.primaryType === 'POLICY'"
            class="proposal"
          >
            <ElTag size="small" effect="plain">
              {{
                draft?.generationReason ? 'AI 建议' : (typeLabel ?? '手工方案')
              }}
            </ElTag>
            <h3>{{ summary || '填写协议价' }}</h3>
            <p v-if="draft?.generationReason" class="muted">
              {{ draft.generationReason }}
            </p>
            <dl class="facts">
              <div>
                <dt>适用对象</dt>
                <dd>{{ subjectLabel }}</dd>
              </div>
              <div>
                <dt>参与规格</dt>
                <dd>{{ form.skuIds.length }} 个</dd>
              </div>
              <div>
                <dt>优惠预算</dt>
                <dd>{{ money(form.budget) }}</dd>
              </div>
              <div>
                <dt>最高让利比例</dt>
                <dd>{{ form.maxDiscountPercent }}%</dd>
              </div>
            </dl>
            <div class="date-line">
              {{ showTime(form.startTime) }} 至 {{ showTime(form.endTime) }}
            </div>
          </div>
          <ElEmpty
            v-else-if="!generating && editable"
            description="可以根据当前需求生成方案，也可以手工填写。"
          >
            <ElButton @click="manual">手工填写</ElButton>
          </ElEmpty>
        </section>
        <section
          v-if="form.activity || form.primaryType === 'POLICY'"
          class="section"
        >
          <div class="section-heading">
            <h3>优惠规则</h3>
            <ElButton v-if="form.activity" text @click="editRules = !editRules">
              {{ editRules ? '收起规则' : '查看和修改规则' }}
            </ElButton>
          </div>
          <ElForm label-position="top" :disabled="busy || !editable">
            <template v-if="form.primaryType === 'POLICY'">
              <ElAlert
                title="协议价没有累计预算自动停用，请核对采购主体和长期让利。"
                type="warning"
                :closable="false"
              />
              <div
                v-for="(row, index) in policyPrices"
                :key="index"
                class="rule-row"
              >
                <ElFormItem label="商品规格">
                  <ElSelect v-model="row.skuId" placeholder="选择规格">
                    <ElOption
                      v-for="sku in selectedSkus"
                      :key="sku.id"
                      :label="sku.label"
                      :value="sku.id"
                    />
                  </ElSelect>
                </ElFormItem>
                <ElFormItem label="协议单价">
                  <ElInputNumber
                    v-model="row.price"
                    :min="0.01"
                    :precision="2"
                  />
                </ElFormItem>
                <ElFormItem label="起订件数">
                  <ElInputNumber v-model="row.minQty" :min="1" :precision="0" />
                </ElFormItem>
                <ElButton link @click="policyPrices.splice(index, 1)">
                  移除
                </ElButton>
              </div>
              <ElButton
                @click="policyPrices.push({ skuId: '', price: 0, minQty: 1 })"
              >
                增加协议明细
              </ElButton>
            </template>
            <CampaignRuleEditor
              v-else-if="editRules && form.activity"
              :activity="form.activity"
              :main-type="form.activity.activityType"
              :skus="selectedSkus"
              @change="form.activity = $event"
            />
            <p v-else-if="typeLabel" class="muted">
              {{ typeLabel
              }}<template v-if="form.coupon"> · 含定向满减券草稿</template>
            </p>
          </ElForm>
        </section>
        <section
          v-if="
            !generating &&
            (form.activity || policyPrices.length > 0) &&
            canSimulate
          "
          class="section"
        >
          <div class="section-heading">
            <h3>样本订单</h3>
            <ElButton text @click="showSample = !showSample">
              {{ showSample ? '收起试算条件' : '修改试算条件' }}
            </ElButton>
          </div>
          <p class="muted">
            按选定采购人和规格试算商品应付，运费、积分和储值另计。
          </p>
          <div v-if="showSample" class="sample-settings">
            <ElForm label-position="top">
              <ElFormItem label="试算时间">
                <ElDatePicker
                  v-model="sampleTime"
                  type="datetime"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  :disabled="busy"
                  placeholder="默认使用活动开始时间"
                />
              </ElFormItem>
              <ElFormItem
                v-for="sku in selectedSkus"
                :key="sku.id"
                :label="sku.label"
              >
                <ElInputNumber
                  v-model="sampleQuantities[sku.id]"
                  :min="sku.minimum ?? 1"
                  :precision="0"
                  :disabled="busy"
                />
                <span v-if="sku.unit" class="muted">{{ sku.unit }}</span>
              </ElFormItem>
            </ElForm>
          </div>
        </section>
      </template>

      <template v-else>
        <section class="section">
          <div class="section-heading">
            <h2>{{ published ? '活动已发布' : '预览并核对活动' }}</h2>
            <ElTag v-if="published" type="success" effect="plain">已发布</ElTag>
          </div>
          <h3>{{ form.activity?.activityName || form.title || summary }}</h3>
          <p class="muted">
            {{ subjectLabel }} · {{ typeLabel }} ·
            {{ form.skuIds.length }} 个规格
          </p>
          <p class="muted">
            {{ showTime(form.startTime) }} 至 {{ showTime(form.endTime) }}
          </p>
          <ElAlert
            v-if="form.coupon"
            title="定向券随活动准备停用模板，启用和发放走既有流程。"
            :closable="false"
            type="info"
          />
        </section>
        <section v-if="!published" class="section">
          <div class="section-heading">
            <h3>样本订单试算</h3>
            <ElButton
              text
              @click="
                showSample = true;
                goTo(1);
              "
            >
              修改试算条件
            </ElButton>
          </div>
          <div v-if="currentSimulation && simulation" class="quote-grid">
            <div>
              <span>原方案商品应付</span
              ><strong>{{
                money(simulation.orderComparison.baseline.goodsPayable)
              }}</strong>
            </div>
            <div>
              <span>当前方案商品应付</span
              ><strong>{{
                money(simulation.orderComparison.proposed.goodsPayable)
              }}</strong>
            </div>
            <div>
              <span>{{ savings < 0 ? '多付' : '少付' }}</span
              ><strong>{{ money(Math.abs(savings)) }}</strong>
            </div>
          </div>
          <ElAlert
            v-else
            :title="simulation?.staleReason || '需要重新试算当前活动条件。'"
            type="warning"
            :closable="false"
          />
          <p v-if="currentSimulation && simulation" class="muted">
            样本商品应付 · 运费、积分和储值另计 · 有效至
            {{ showTime(simulation.expiresAt) }}
          </p>
          <details v-if="currentSimulation && simulation" class="more-settings">
            <summary>查看商品、成本和库存</summary>
            <ElTable :data="simulation.skuFacts">
              <ElTableColumn label="商品规格">
                <template #default="{ row }">
                  {{ skuName(row.skuId) }}
                </template>
              </ElTableColumn>
              <ElTableColumn prop="sampleQuantity" label="样本数量" />
              <ElTableColumn label="当前成本">
                <template #default="{ row }">
                  {{
                    row.costStatus === 'KNOWN' ? money(row.costLower) : '未知'
                  }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="当前库存">
                <template #default="{ row }">
                  {{ row.stock ?? '未知' }}
                </template>
              </ElTableColumn>
            </ElTable>
          </details>
          <ElAlert
            v-for="risk in stockIssues"
            :key="`${risk.code}:${risk.skuId}`"
            type="error"
            :title="riskText(risk)"
            :closable="false"
            show-icon
          >
            <ElButton
              link
              @click="
                showSample = true;
                goTo(1);
              "
            >
              调整后重新试算
            </ElButton>
          </ElAlert>
          <ElCheckboxGroup
            v-if="currentSimulation && canRelease"
            v-model="acceptedRisks"
          >
            <ElCheckbox
              v-for="risk in riskConfirmations"
              :key="`${risk.code}:${risk.skuId}`"
              :value="risk.code"
            >
              已核对 {{ riskText(risk) }}
            </ElCheckbox>
          </ElCheckboxGroup>
          <p
            v-else
            v-for="risk in riskConfirmations"
            :key="`${risk.code}:${risk.skuId}`"
            class="muted"
          >
            {{ riskText(risk) }}
          </p>
          <ElCheckbox
            v-if="currentSimulation && hasOverlap && canRelease"
            v-model="overlap"
          >
            接受同类活动重叠，按正式规则取优
          </ElCheckbox>
        </section>
        <section v-if="!published" class="section">
          <div class="section-heading">
            <h3>小程序活动页</h3>
            <div class="actions">
              <ElButton
                v-if="draft?.pageId && canPreview"
                text
                :disabled="busy || !currentSimulation"
                @click="pagePreview()"
              >
                刷新预览
              </ElButton>
              <ElButton
                v-if="draft?.pageId && allowed('promotion:pagedesign:edit')"
                text
                :disabled="busy"
                @click="editPage"
              >
                编辑活动页
              </ElButton>
            </div>
          </div>
          <CampaignSamplePreview
            v-if="preview && currentSimulation"
            :preview="preview"
            :goods="capturedGoods"
          />
          <ElAlert
            v-else-if="!busy"
            :title="
              canPreview
                ? '完成试算和页面准备后，可查看小程序活动页。'
                : '当前账号缺少活动页预览权限。'
            "
            type="info"
            :closable="false"
          />
          <ElAlert
            v-if="preview && currentSimulation && !preview.current"
            title="活动页已有新版本，请查看预览后核对修改。"
            type="warning"
            :closable="false"
          />
        </section>
        <section
          v-if="publications.length > 0 || (published && metrics)"
          class="section"
        >
          <details
            class="more-settings"
            :open="
              publications.some((record) => record.status !== 'PUBLISHED') ||
              undefined
            "
          >
            <summary>发布记录与数据</summary>
            <div
              v-for="record in publications"
              :key="record.id"
              class="publication"
            >
              <div>
                <strong>{{ publicationStatus(record.status) }}</strong>
                <p class="muted">
                  {{ record.steps.map(publicationStatus).join(' → ') }}
                </p>
                <small class="muted"
                  >{{ showTime(record.updateTime) }}
                  {{ record.failureCode }}</small
                >
              </div>
              <ElButton
                v-if="record.status !== 'PUBLISHED' && canRelease"
                text
                :disabled="busy"
                @click="retryPublication(record)"
              >
                按原记录恢复
              </ElButton>
            </div>
            <dl v-if="metrics" class="facts">
              <div v-for="metric in metricLabels" :key="metric.id">
                <dt>{{ metric.label }}</dt>
                <dd>{{ metrics[metric.id] ?? '暂无' }}</dd>
              </div>
            </dl>
          </details>
        </section>
      </template>

      <footer v-if="!(step === 2 && published)" class="stage-footer">
        <span v-if="step < 2" class="muted" role="status">{{
          dirty ? '有未保存修改' : draft ? '草稿已保存' : ''
        }}</span>
        <span v-else-if="publicationBlock" class="muted" role="status">{{
          publicationBlock
        }}</span>
        <div class="actions">
          <template v-if="step === 0">
            <ElButton
              v-if="canEdit"
              text
              :disabled="busy || !editable || (!dirty && Boolean(draft))"
              @click="saveDraft"
            >
              保存草稿
            </ElButton>
            <ElButton
              v-if="!showDetails"
              type="primary"
              :disabled="busy || !editable"
              @click="revealDetails"
            >
              继续 · 补齐信息
            </ElButton>
            <template v-else-if="editable">
              <ElButton
                v-if="form.primaryType !== 'POLICY' && canGenerate"
                text
                :disabled="busy"
                @click="manual"
              >
                手工填写
              </ElButton>
              <ElButton
                v-if="form.primaryType !== 'POLICY' && canGenerate"
                type="primary"
                :loading="busy"
                @click="generate"
              >
                AI 生成方案
              </ElButton>
              <ElButton v-else type="primary" :loading="busy" @click="manual">
                {{
                  form.primaryType === 'POLICY'
                    ? '继续 · 设置协议价'
                    : '继续 · 填写优惠规则'
                }}
              </ElButton>
            </template>
          </template>
          <template v-else-if="step === 1">
            <ElButton text :disabled="busy" @click="goTo(0)">返回需求</ElButton>
            <ElButton
              v-if="canGenerate && !generating && form.primaryType !== 'POLICY'"
              text
              :disabled="busy"
              @click="generate"
            >
              {{ form.activity ? '重新生成' : 'AI 生成方案' }}
            </ElButton>
            <ElButton
              v-if="
                canSimulate &&
                !generating &&
                (form.activity || policyPrices.length > 0)
              "
              type="primary"
              :loading="busy"
              @click="runTrial"
            >
              确认方案并试算
            </ElButton>
            <ElButton
              v-else-if="editable && (form.activity || policyPrices.length > 0)"
              type="primary"
              :loading="busy"
              @click="saveDraft"
            >
              保存方案
            </ElButton>
          </template>
          <template v-else>
            <ElButton text :disabled="busy" @click="goTo(1)">返回方案</ElButton>
            <ElButton
              v-if="!currentSimulation && canSimulate"
              type="primary"
              :loading="busy"
              @click="runTrial"
            >
              重新试算
            </ElButton>
            <ElButton
              v-else-if="preview && !preview.current && canPreview"
              type="primary"
              :loading="busy"
              @click="pagePreview(true)"
            >
              确认此版本页面
            </ElButton>
            <ElButton
              v-else-if="!draft?.pageId && canCreatePage && currentSimulation"
              type="primary"
              :loading="busy"
              @click="pageGenerate"
            >
              生成并预览活动页
            </ElButton>
            <ElButton
              v-else-if="!preview && canPreview && currentSimulation"
              type="primary"
              :loading="busy"
              @click="pagePreview()"
            >
              加载活动页预览
            </ElButton>
            <ElButton
              v-else-if="canRelease"
              type="primary"
              :loading="busy"
              :disabled="!ready"
              @click="publish"
            >
              确认发布
            </ElButton>
          </template>
        </div>
      </footer>
    </main>

    <ElDrawer
      v-model="showActivities"
      title="我的活动"
      size="min(440px, 100vw)"
    >
      <div class="drawer-top">
        <ElSelect v-model="listFilter" aria-label="筛选活动" size="small">
          <ElOption label="全部活动" value="all" />
          <ElOption label="进行中的草稿" value="draft" />
          <ElOption label="已发布" value="published" />
        </ElSelect>
        <ElButton
          v-if="canEdit"
          type="primary"
          :disabled="busy"
          @click="newCampaign"
        >
          新建活动
        </ElButton>
        <ElButton text :disabled="busy" @click="guard(loadList)">刷新</ElButton>
      </div>
      <ElEmpty
        v-if="visibleCampaigns.length === 0"
        description="暂无符合条件的活动"
      />
      <div v-else class="activity-list">
        <button
          v-for="item in visibleCampaigns"
          :key="item.id"
          type="button"
          :disabled="busy"
          @click="open(item)"
        >
          <strong>{{
            item.definition.title ||
            item.definition.intent?.slice(0, 36) ||
            '未命名活动'
          }}</strong>
          <span>{{ nextAction(item) }} <span aria-hidden="true">→</span></span>
        </button>
      </div>
    </ElDrawer>

    <SelectGoods ref="picker" :limit-num="100" @current-row="selectProducts" />
    <ElDialog
      v-model="showSkuPicker"
      title="勾选参与的商品规格"
      width="min(580px, 96vw)"
    >
      <p class="muted">已勾选 {{ skuSelection.length }} / 100</p>
      <ElCheckboxGroup v-model="skuSelection" :max="100" class="sku-options">
        <ElCheckbox v-for="sku in skuOptions" :key="sku.id" :value="sku.id">
          {{ sku.label }}
        </ElCheckbox>
      </ElCheckboxGroup>
      <ElEmpty
        v-if="skuOptions.length === 0"
        description="所选商品暂无可用规格"
      />
      <template #footer>
        <ElButton @click="showSkuPicker = false">取消</ElButton>
        <ElButton type="primary" @click="confirmSkus">确定规格</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.workbench {
  max-width: 1240px;
  padding: 24px;
  margin-inline: auto;
}

.page-head,
.section-heading,
.stage-footer,
.drawer-top,
.publication {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.page-head {
  margin-bottom: 20px;
}

.page-head h1,
.section h2,
.section h3,
.proposal h3 {
  margin: 0;
  font-weight: 650;
}

.page-head h1 {
  font-size: 26px;
  letter-spacing: -0.025em;
}

.page-head p,
.muted,
.date-line {
  margin-block: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.stepbar {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 4px;
  padding: 5px;
  margin-bottom: 16px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}

.stepbar button {
  display: flex;
  gap: 9px;
  align-items: center;
  padding: 10px 14px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  text-align: left;
  border-radius: 8px;
}

.stepbar button:not(:disabled),
.templates button:not(:disabled),
.activity-list button:not(:disabled) {
  cursor: pointer;
}

.stepbar button.active {
  color: var(--el-color-primary);
  background: var(--el-bg-color);
  box-shadow: 0 1px 3px rgb(0 0 0 / 8%);
}

.stepbar button.done {
  color: var(--el-text-color-primary);
}

.step-number {
  display: inline-grid;
  flex: 0 0 27px;
  place-items: center;
  width: 27px;
  height: 27px;
  font-size: 13px;
  background: var(--el-fill-color);
  border-radius: 50%;
}

.stepbar button.active .step-number {
  color: var(--el-color-white);
  background: var(--el-color-primary);
}

.stage {
  position: relative;
  min-width: 0;
  overflow: clip;
  scroll-margin-top: 12px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
}

.stage:focus {
  outline: none;
}

.stage > .el-alert {
  width: auto;
  margin: 16px 24px 0;
}

.section {
  display: grid;
  gap: 12px;
  padding: 22px 24px;
}

.section + .section {
  border-top: 1px solid var(--el-border-color-lighter);
}

.section h2 {
  font-size: 21px;
}

.section h3 {
  font-size: 16px;
}

.templates {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.templates button {
  display: grid;
  gap: 4px;
  align-content: start;
  min-height: 76px;
  padding: 12px 14px;
  color: var(--el-text-color-primary);
  text-align: left;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color);
  border-radius: 9px;
}

.templates strong {
  font-size: 14px;
}

.templates span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.templates button[aria-pressed='true'],
.templates button:hover:not(:disabled) {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.workbench button:focus-visible,
.workbench summary:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.form-grid :deep(.el-select),
.form-grid :deep(.el-date-editor),
.form-grid :deep(.el-input-number) {
  width: 100%;
}

.section :deep(.el-form-item__label) {
  font-weight: 600;
}

.section :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.form-grid :deep(.el-form-item:last-child) {
  margin-bottom: 18px;
}

.field-note {
  font-size: 13px;
  color: var(--el-color-danger);
}

.date-shortcuts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
  font-weight: 600;
}

.date-shortcuts :deep(.el-button + .el-button) {
  margin-left: 0;
}

.goods-selection {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  width: 100%;
}

.selected-skus {
  display: grid;
  flex-basis: 100%;
  gap: 4px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.selected-skus li {
  display: flex;
  gap: 10px;
  justify-content: space-between;
  padding-inline: 10px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}

.more-settings {
  padding: 10px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

.more-settings summary {
  width: fit-content;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-color-primary);
  cursor: pointer;
}

.more-settings[open] summary {
  margin-bottom: 12px;
}

.proposal {
  display: grid;
  gap: 9px;
  justify-items: start;
  padding: 18px;
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-7);
  border-radius: 10px;
}

.proposal h3 {
  font-size: 23px;
}

.facts,
.quote-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
  gap: 10px 16px;
  width: 100%;
  margin: 0;
}

.facts dt,
.quote-grid span {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.facts dd {
  margin: 2px 0 0;
  font-size: 16px;
  font-weight: 650;
}

.rule-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-block: 10px;
}

.rule-row :deep(.el-select) {
  min-width: 200px;
}

.quote-grid > div {
  display: grid;
  gap: 2px;
  padding: 13px 16px;
  background: var(--el-fill-color-light);
  border-radius: 9px;
}

.quote-grid > div:last-child {
  background: var(--el-color-primary-light-9);
}

.quote-grid strong {
  font-size: 23px;
  font-variant-numeric: tabular-nums;
}

.stage-footer {
  position: sticky;
  bottom: 0;
  z-index: 1;
  min-height: 60px;
  padding: 12px 24px;
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-light);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.stage-footer .actions {
  margin-left: auto;
}

.actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.drawer-top :deep(.el-select) {
  flex: 1;
  min-width: 100px;
}

.activity-list {
  display: grid;
  gap: 8px;
  margin-top: 16px;
}

.activity-list button {
  display: grid;
  gap: 3px;
  justify-items: start;
  padding: 11px 12px;
  text-align: left;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}

.activity-list button:hover {
  background: var(--el-fill-color-light);
}

.activity-list span {
  font-size: 13px;
  color: var(--el-color-primary);
}

.sku-options {
  display: grid;
  max-height: min(400px, 55vh);
  overflow-y: auto;
}

.sku-options :deep(.el-checkbox) {
  height: auto;
  min-height: 30px;
  white-space: normal;
}

.sample-settings :deep(.el-input-number) {
  margin-right: 8px;
}

@media (max-width: 1060px) {
  .templates {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 740px) {
  .workbench {
    padding: 12px;
  }

  .stage > .el-alert {
    margin-inline: 14px;
  }

  .stepbar button {
    gap: 5px;
    padding: 8px 5px;
    font-size: 12px;
  }

  .step-number {
    flex-basis: 20px;
    width: 20px;
    height: 20px;
  }

  .section,
  .stage-footer {
    padding: 14px;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
