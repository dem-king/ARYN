import type {
  CampaignDefinition,
  CampaignDraft,
  CampaignPagePreview,
  CampaignSimulation,
} from '#/api/promotion/campaign-draft';

import { expect, it } from 'vitest';

import {
  canPublish,
  mainTypes,
  missingCampaignFields,
  publicationEnvelope,
} from './campaign-state';

it('只提示适用采购场景的必填项，并检查生成方案的档期', () => {
  const complete: CampaignDefinition = {
    title: '大单满减',
    intent: '饮用水满额优惠',
    purchaseScene: '1',
    sampleUserId: 'member',
    skuIds: ['sku'],
    startTime: '2026-12-01T00:00:00',
    endTime: '2026-12-07T00:00:00',
    budget: 500,
    maxDiscountPercent: 8,
  };
  expect(missingCampaignFields(complete)).toEqual([]);
  expect(
    missingCampaignFields({
      ...complete,
      purchaseScene: '2',
      budget: 0,
      activity: { activityName: '港口特供', activityType: '12', rules: '{}' },
    }),
  ).toEqual(['budget', 'vesselId', 'calendarId']);
  expect(missingCampaignFields({ ...complete, purchaseScene: '3' })).toEqual([
    'buyerOrgId',
  ]);
});

it('只允许现行主方案；修订、未保存、跨包与过期模拟阻止发布', () => {
  expect(mainTypes.map((t) => t.value)).toEqual([
    '12',
    '4',
    '7',
    '5',
    'POLICY',
  ]);
  const draft = { id: 'a', revision: 2, status: 'READY' } as CampaignDraft;
  const simulation = {
    id: 'sim',
    campaignDraftId: 'a',
    expectedCampaignRevision: 2,
    current: true,
    expiresAt: '2099-01-01T00:00:00Z',
  } as CampaignSimulation;
  const preview = {
    campaignRevision: 2,
    current: true,
    simulation,
  } as CampaignPagePreview;
  expect(canPublish(draft, simulation, preview, false)).toBe(true);
  expect(
    canPublish(
      draft,
      simulation,
      preview,
      false,
      Date.parse(simulation.expiresAt),
    ),
  ).toBe(false);
  expect(
    canPublish(draft, { ...simulation, expiresAt: 'invalid' }, preview, false),
  ).toBe(false);
  expect(canPublish(draft, simulation, preview, true)).toBe(false);
  expect(
    canPublish({ ...draft, revision: 3 }, simulation, preview, false),
  ).toBe(false);
  expect(
    canPublish(draft, { ...simulation, current: false }, preview, false),
  ).toBe(false);
  expect(
    canPublish(
      draft,
      { ...simulation, campaignDraftId: 'foreign' },
      preview,
      false,
    ),
  ).toBe(false);
  expect(
    canPublish(draft, simulation, { ...preview, current: false }, false),
  ).toBe(false);
});
it('响应丢失保留原发布信封，确认内容改变生成新请求', () => {
  const input = {
    expectedRevision: 2,
    expectedPageRevision: 1,
    simulationId: 'sim',
    inputDigest: 'input',
    factDigest: 'facts',
    pageDigest: 'page',
    acceptedRiskCodes: ['COST_UNKNOWN', 'BELOW_COST'],
    acceptOverlap: false,
  };
  const first = publicationEnvelope(undefined, input);
  expect(
    publicationEnvelope(first, {
      ...input,
      acceptedRiskCodes: ['BELOW_COST', 'COST_UNKNOWN'],
    }),
  ).toBe(first);
  expect(
    publicationEnvelope(first, { ...input, expectedPageRevision: 2 }).requestId,
  ).not.toBe(first.requestId);
  expect(
    publicationEnvelope(first, { ...input, acceptedRiskCodes: [] }).requestId,
  ).not.toBe(first.requestId);
});
