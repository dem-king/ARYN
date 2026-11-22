import type {
  CampaignDefinition,
  CampaignDraft,
  CampaignPagePreview,
  CampaignSimulation,
  MainType,
  PublishCommand,
} from '#/api/promotion/campaign-draft';

export const campaignTemplates = [
  {
    type: '12',
    label: '靠港备货特供',
    description: '在采购档期给指定商品设特供价',
    example: '给即将靠港的船舶准备饮用水和纸品，设置档期特供价。',
  },
  {
    type: '7',
    label: '大单满减',
    description: '采购满一定金额或件数减价',
    example:
      '给指定船舶采购的饮用水和纸品做一次满额优惠，活动优惠总预算 500 元，最多让利 8%。',
  },
  {
    type: '4',
    label: '批量采购价',
    description: '按采购数量设置不同单价',
    example: '给采购企业设置批量采购价，按整箱数量设置不同单价。',
  },
  {
    type: '5',
    label: '采购送赠品',
    description: '达到门槛时赠送指定商品',
    example: '客户采购指定日用品达到门槛时赠送纸巾。',
  },
] as const;

export const generationStatus: Record<string, string> = {
  CANCELLED: '已取消生成',
  FAILED: '生成失败，可以重试或手工编辑',
  QUEUED: '准备生成优惠方案',
  RUNNING: '正在生成优惠方案',
  STALE: '需求已修改，可以重新生成',
  SUCCEEDED: '方案已生成',
};

export const requiredFieldLabels: Record<string, string> = {
  budget: '活动优惠总预算',
  buyerOrgId: '采购企业',
  calendarId: '采购档期',
  endTime: '结束时间',
  intent: '活动需求',
  maxDiscountPercent: '最高让利比例',
  purchaseScene: '采购场景',
  sampleUserId: '用于试算的采购人',
  skuIds: '参与商品规格',
  startTime: '开始时间',
  vesselId: '船舶',
};

export function missingCampaignFields(form: CampaignDefinition) {
  const required = [
    'intent',
    'purchaseScene',
    'sampleUserId',
    'skuIds',
    'startTime',
    'endTime',
    'budget',
    'maxDiscountPercent',
  ] as const;
  const missing: string[] = required.filter((field) => {
    const value = form[field];
    return Array.isArray(value)
      ? value.length === 0
      : !value?.toString().trim() || value === 0;
  });
  if (form.purchaseScene === '2' && !form.vesselId) missing.push('vesselId');
  if (form.purchaseScene === '3' && !form.buyerOrgId)
    missing.push('buyerOrgId');
  if (
    (form.primaryType ?? form.activity?.activityType) === '12' &&
    !form.calendarId
  )
    missing.push('calendarId');
  return missing;
}

export function isCurrentSimulation(
  draft: CampaignDraft | undefined,
  simulation: CampaignSimulation | undefined,
  now = Date.now(),
) {
  return Boolean(
    draft &&
    simulation?.current &&
    Date.parse(simulation.expiresAt) > now &&
    simulation.campaignDraftId === draft.id &&
    simulation.expectedCampaignRevision === draft.revision,
  );
}

export const mainTypes: { label: string; value: MainType }[] = [
  { value: '12', label: '档期特供' },
  { value: '4', label: '阶梯价' },
  { value: '7', label: '满量 / 满额优惠' },
  { value: '5', label: '买赠' },
  { value: 'POLICY', label: '协议政策（显式核定）' },
];
export function canPublish(
  draft: CampaignDraft | undefined,
  simulation: CampaignSimulation | undefined,
  preview: CampaignPagePreview | undefined,
  dirty: boolean,
  now = Date.now(),
) {
  return Boolean(
    !dirty &&
    draft?.status === 'READY' &&
    simulation &&
    isCurrentSimulation(draft, simulation, now) &&
    preview?.current &&
    preview.campaignRevision === draft.revision &&
    preview.simulation.id === simulation.id,
  );
}
/** 响应丢失时保留同一信封；内容改变必须建立新requestId。不持久化样本价格。 */
export function publicationEnvelope(
  prior: PublishCommand | undefined,
  input: Omit<PublishCommand, 'requestId'>,
): PublishCommand {
  const stable = {
    ...input,
    acceptedRiskCodes: [...input.acceptedRiskCodes].sort(),
  };
  if (
    prior &&
    JSON.stringify({ ...prior, requestId: undefined }) ===
      JSON.stringify({ ...stable, requestId: undefined })
  )
    return prior;
  return { ...stable, requestId: crypto.randomUUID() };
}
export function publicationStatus(value: string) {
  return (
    (
      {
        VALIDATING: '校验中，可按原请求恢复',
        DRAFT_VALIDATED: '草稿已核验',
        RESOURCES_PREPARED: '资源已准备',
        PUBLISHING: '提交中',
        PUBLISHED: '已发布',
        FAILED_RECOVERABLE: '未生效，可恢复',
      } as Record<string, string>
    )[value] ?? value
  );
}
