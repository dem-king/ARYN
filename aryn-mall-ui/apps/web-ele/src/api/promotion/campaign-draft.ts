import type { PageDesignEditorResponse } from './page-design';

import { requestClient } from '#/api/request';

export type MainType = '4' | '5' | '7' | '12' | 'POLICY';
export interface CampaignActivity {
  activityName: string;
  activityType: Exclude<MainType, 'POLICY'>;
  rules: string;
  giftStock?: number;
  totalQuota?: number;
  perUserLimit?: number;
  couponStackable?: string;
}
export interface CampaignDefinition {
  expectedRevision?: number;
  title: string;
  intent: string;
  purchaseScene: string;
  sampleUserId: string;
  vesselId?: string;
  vesselCallId?: string;
  buyerOrgId?: string;
  skuIds: string[];
  startTime?: string;
  endTime?: string;
  budget?: number;
  maxDiscountPercent?: number;
  primaryType?: MainType;
  calendarId?: string;
  activity?: CampaignActivity;
  coupon?: {
    amount: number;
    name: string;
    quantity: number;
    threshold: number;
  };
}
export interface CampaignDraft {
  id: string;
  revision: number;
  status: string;
  definition: CampaignDefinition;
  missingFields: string[];
  generationReason?: string;
  mainActivityId?: string;
  mainPolicyId?: string;
  pageId?: string;
  pageRevision?: number;
  pageDigest?: string;
  simulationId?: string;
}
export interface CampaignGeneration {
  id: string;
  status: string;
  attempt: number;
  nextRetryAt?: string;
  errorCode?: string;
  model?: string;
  configVersion?: string;
  baseRevision?: number;
}
export interface CampaignQuote {
  goodsPayable: number;
  couponDiscount: number;
  memberDiscount: number;
  wholeDiscount: number;
  couponReason?: string;
  lines: {
    goodsPayable: number;
    quantity: number;
    skuId: string;
    unitPrice: number;
  }[];
}
export interface CampaignSimulation {
  id: string;
  campaignDraftId: string;
  expectedCampaignRevision: number;
  current: boolean;
  staleReason?: string;
  inputDigest: string;
  factDigest: string;
  expiresAt: string;
  orderComparison: { baseline: CampaignQuote; proposed: CampaignQuote };
  risks: { code: string; message: string; skuId?: string }[];
  skuFacts: {
    costLower?: number;
    costStatus: string;
    sampleQuantity: number;
    skuId: string;
    stock?: number;
  }[];
  assumptions: {
    consumedBudget?: number;
    overlappingActivities: string[];
    totalBudget?: number;
    totalQuota?: number;
  };
}
export interface CampaignPagePreview {
  campaignDraftId: string;
  campaignRevision: number;
  page: PageDesignEditorResponse;
  simulation: CampaignSimulation;
  current: boolean;
  pageDigest: string;
  goods: {
    id: string;
    name: string;
    originalPrice: number;
    price: number;
    skuId: string;
    stock?: number;
  }[];
}
export interface PageCommand {
  expectedRevision: number;
  expectedPageRevision?: number;
  simulationId?: string;
}
export interface PublishCommand {
  requestId: string;
  expectedRevision: number;
  expectedPageRevision: number;
  simulationId: string;
  inputDigest: string;
  factDigest: string;
  pageDigest: string;
  acceptedRiskCodes: string[];
  acceptOverlap: boolean;
}
export interface CampaignPublication {
  id: string;
  draftId: string;
  requestId: string;
  status: string;
  steps: string[];
  failureCode?: string;
  couponId?: string;
  pageId?: string;
  pageVersionId?: string;
  updateTime: string;
}
const base = '/promotion/campaign-draft';
export const listCampaigns = () => requestClient.get<CampaignDraft[]>(base);
export const getCampaign = (id: string) =>
  requestClient.get<CampaignDraft>(`${base}/${id}`);
export const saveCampaign = (
  id: string | undefined,
  data: CampaignDefinition,
) =>
  id
    ? requestClient.put<CampaignDraft>(`${base}/${id}`, data)
    : requestClient.post<CampaignDraft>(base, data);
export const generateCampaign = (
  id: string,
  expectedRevision: number,
  requestId: string,
) =>
  requestClient.post<CampaignGeneration>(`${base}/${id}/generation`, {
    expectedRevision,
    requestId,
  });
export const getCampaignJobs = (id: string) =>
  requestClient.get<CampaignGeneration[]>(`${base}/${id}/generation`);
export const generationAction = (jobId: string, action: 'cancel' | 'retry') =>
  requestClient.post(`${base}/generation/${jobId}/${action}`);
export const generateCampaignPage = (id: string, data: PageCommand) =>
  requestClient.post<CampaignDraft>(`${base}/${id}/page`, data);
export const previewCampaignPage = (
  id: string,
  data: PageCommand,
  review = false,
) =>
  requestClient.post<CampaignPagePreview>(
    `${base}/${id}/${review ? 'page-review' : 'page-preview'}`,
    data,
  );
export const simulateCampaign = (data: {
  campaignDraftId: string;
  expectedCampaignRevision: number;
  sample: {
    activityTime: string;
    buyerOrgId?: string;
    purchaseScene: string;
    skuItems: { quantity: number; skuId: string }[];
    userId: string;
    vesselCallId?: string;
    vesselId?: string;
  };
}) =>
  requestClient.post<CampaignSimulation>(
    '/promotion/campaign-simulation',
    data,
  );
export const getCampaignSimulation = (id: string) =>
  requestClient.get<CampaignSimulation>(`/promotion/campaign-simulation/${id}`);
export const publishCampaign = (id: string, data: PublishCommand) =>
  requestClient.post<CampaignPublication>(`${base}/${id}/publish`, data);
export const getCampaignPublications = (id: string) =>
  requestClient.get<CampaignPublication[]>(`${base}/${id}/publications`);
export const retryCampaignPublication = (id: string) =>
  requestClient.post<CampaignPublication>(`${base}/publications/${id}/retry`);
export const saveCampaignPolicy = (
  id: string,
  data: {
    expectedRevision: number;
    items: { minQty: number; price: number; skuId: string }[];
    name: string;
  },
) => requestClient.put<CampaignDraft>(`${base}/${id}/policy`, data);
export const getCampaignMetrics = (id: string) =>
  requestClient.get<Record<string, null | number>>(`${base}/${id}/metrics`);
