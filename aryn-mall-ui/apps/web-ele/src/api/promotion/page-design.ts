import { requestClient } from '#/api/request';

export type PageDesignType = '0' | '1';
export type PublishStatus = '0' | '1';

export interface PageDesignQuery {
  current: number;
  pageName?: string;
  pageType?: '' | PageDesignType;
  publishedStatus?: '' | PublishStatus;
  size: number;
  status?: string;
}

export interface PageDesignRecord {
  createTime?: string;
  draftRevision: number;
  homeStatus: '0' | '1';
  id: string;
  pageContent?: Record<string, unknown> | string;
  pageName: string;
  pageType: PageDesignType;
  publishedAt?: string;
  publishedStatus: PublishStatus;
  publishedVersionId?: string;
  schemaVersion: number;
  status: string;
  updateTime?: string;
}

export interface PageDesignPage {
  current: number;
  pages: number;
  records: PageDesignRecord[];
  size: number;
  total: number;
}

export type PageDesignEditorResponse = Omit<PageDesignRecord, 'pageContent'> & {
  pageContent: Record<string, unknown>;
};

export interface PageDesignDraftPayload {
  draftRevision: number;
  pageContent: Record<string, unknown>;
  pageName: string;
  schemaVersion: number;
}

export interface PageDesignPublishPayload {
  draftRevision: number;
  publishRemark?: string;
}

export interface PageDesignVersion {
  id: string;
  pageDesignId: string;
  pageName: string;
  pageType: PageDesignType;
  publishBy?: string;
  publishRemark?: string;
  publishedAt?: string;
  schemaVersion: number;
  versionNo: number;
}

export interface PageDesignPreviewResponse {
  draftRevision?: number;
  id: string;
  pageContent: Record<string, unknown>;
  pageName: string;
  pageType: PageDesignType;
  publishedAt?: string;
  publishedVersionId?: string;
  publishedVersionNo?: number;
  schemaVersion: number;
}

export interface PageDesignMutationPayload {
  homeStatus?: '0' | '1';
  id?: string;
  pageContent?: string;
  pageName: string;
  pageType?: PageDesignType;
  schemaVersion?: number;
  status?: string;
}

export interface PageDesignTemplatePayload {
  industryTag?: string;
  pageType: '0' | '1' | '2';
  schemaVersion: number;
  systemFlag: '0' | '1';
  templateContent: Record<string, unknown>;
  templateName: string;
  templateType: '0' | '1';
}

export interface PageDesignTemplateRecord extends PageDesignTemplatePayload {
  id: string;
  industryTag?: string;
  sort?: number;
  status?: string;
}

/** 服务端结构化校验问题项 */
export interface PageDesignValidationIssue {
  code: string;
  componentId?: string;
  componentType?: string;
  field?: string;
  message: string;
}

/** 发布前结构化校验结果：错误阻断、警告可配置、引用与性能预算 */
export interface PageDesignValidationResult {
  errors: PageDesignValidationIssue[];
  performance: {
    componentCount: number;
    contentBytes: number;
    imageCount: number;
    requestCount: number;
  };
  references: {
    activityIds: string[];
    categoryIds: string[];
    couponIds: string[];
    goodsIds: string[];
  };
  warnings: PageDesignValidationIssue[];
}

export type PageDesignReleaseStatus = '0' | '1' | '2' | '3';

/** 发布申请：0.待审核 1.已发布 2.已拒绝 3.已取消 */
export interface PageDesignRelease {
  auditAt?: string;
  auditBy?: string;
  auditRemark?: string;
  draftRevision: number;
  id: string;
  pageContent?: Record<string, unknown>;
  pageDesignId: string;
  publishRemark?: string;
  releaseNo: number;
  releaseStatus: PageDesignReleaseStatus;
  releaseStrategy: string;
  releaseVersionId?: string;
  schemaVersion: number;
  submitAt?: string;
  submitBy?: string;
}

export interface PageDesignReleasePayload {
  draftRevision: number;
  planPublishAt?: string;
  publishRemark?: string;
  releaseStrategy?: '0' | '1' | '2';
}

export interface PageDesignReleaseAuditPayload {
  approved: boolean;
  auditRemark?: string;
}

export interface PageDesignDiffFieldChange {
  field: string;
  from?: unknown;
  to?: unknown;
}

export interface PageDesignDiffComponentChange {
  componentId: string;
  componentType: string;
}

export interface PageDesignDiffComponentUpdate {
  changes: PageDesignDiffFieldChange[];
  componentId: string;
  componentType: string;
}

export interface PageDesignDiff {
  added: PageDesignDiffComponentChange[];
  changed: PageDesignDiffComponentUpdate[];
  fromSchemaVersion: number;
  fromVersionId: string;
  fromVersionNo: number;
  pageChanged: PageDesignDiffFieldChange[];
  removed: PageDesignDiffComponentChange[];
  toSchemaVersion: number;
  toVersionId: string;
  toVersionNo: number;
}

export interface PageDesignAuditLog {
  action: string;
  afterRevision?: number;
  afterVersionId?: string;
  beforeRevision?: number;
  beforeVersionId?: string;
  createTime?: string;
  id: string;
  operator?: string;
  operatorIp?: string;
  releaseId?: string;
  remark?: string;
  result: string;
}

/** 装修主题（id 同时作为 v3 文档 themeRef 令牌） */
export interface PageDesignTheme {
  id: string;
  navigationColor?: string;
  navigationTextColor?: string;
  pageBackgroundColor?: string;
  primaryColor?: string;
  radius?: number;
  sort?: number;
  status?: string;
  systemFlag?: '0' | '1';
  themeName: string;
}

export interface PageDesignMetricDaily {
  clickCount: number;
  componentType: string;
  errorCount: number;
  metricDate: string;
  pageDesignId: string;
  totalCount?: number;
  versionId: string;
  viewCount: number;
}

export interface PageDesignThemePayload {
  navigationColor?: string;
  navigationTextColor?: string;
  pageBackgroundColor?: string;
  primaryColor?: string;
  radius?: number;
  sort?: number;
  themeName: string;
}

/** 素材引用检查结果（引用从草稿实时提取） */
export interface PageDesignAssetRef {
  componentId: string;
  componentType: string;
  external: boolean;
  insecure: boolean;
  mediaType: 'image' | 'video';
  url: string;
}

export interface PageDesignAssetResult {
  assets: PageDesignAssetRef[];
  imageCount: number;
  insecureCount: number;
  pageDesignId: string;
  videoCount: number;
}

export async function getPage(query: PageDesignQuery) {
  return requestClient.get<PageDesignPage>('/promotion/pagedesign/page', {
    params: query,
  });
}

export async function getById(id: string) {
  return requestClient.get<PageDesignRecord>(`/promotion/pagedesign/${id}`);
}

export async function addObj(data: PageDesignMutationPayload) {
  return requestClient.post<string>('/promotion/pagedesign', data);
}

export async function delObj(id: string) {
  return requestClient.delete<boolean>(`/promotion/pagedesign/${id}`);
}

export async function editObj(data: PageDesignMutationPayload) {
  return requestClient.put<boolean>('/promotion/pagedesign', data);
}

export async function getHomeDesign() {
  return requestClient.get<PageDesignRecord>('/promotion/pagedesign/home-edit');
}

export async function getEditor(id: string) {
  return requestClient.get<PageDesignEditorResponse>(
    `/promotion/pagedesign/${id}/editor`,
  );
}

export async function saveDraft(id: string, data: PageDesignDraftPayload) {
  return requestClient.put<number>(`/promotion/pagedesign/${id}/draft`, data);
}

export async function copyPage(id: string) {
  return requestClient.post<PageDesignRecord>(
    `/promotion/pagedesign/${id}/copy`,
  );
}

export async function publishPage(id: string, data: PageDesignPublishPayload) {
  return requestClient.post<PageDesignVersion>(
    `/promotion/pagedesign/${id}/publish`,
    data,
  );
}

export async function unpublishPage(id: string) {
  return requestClient.post<boolean>(`/promotion/pagedesign/${id}/unpublish`);
}

export async function getVersions(id: string) {
  return requestClient.get<PageDesignVersion[]>(
    `/promotion/pagedesign/${id}/versions`,
  );
}

export async function rollbackVersion(
  id: string,
  versionId: string,
  publishRemark?: string,
) {
  return requestClient.post<PageDesignVersion>(
    `/promotion/pagedesign/${id}/versions/${versionId}/rollback`,
    { publishRemark },
  );
}

export async function createPreviewToken(id: string, draftRevision: number) {
  return requestClient.post<string>(
    `/promotion/pagedesign/${id}/preview-token`,
    undefined,
    { params: { draftRevision } },
  );
}

export async function getPreview(token: string) {
  return requestClient.get<PageDesignPreviewResponse>(
    `/promotion/app/pagedesign/preview/${encodeURIComponent(token)}`,
  );
}

export async function getTemplates(pageType: PageDesignType) {
  return requestClient.get<PageDesignTemplateRecord[]>(
    '/promotion/pagedesign/templates',
    { params: { pageType } },
  );
}

export async function createTemplate(data: PageDesignTemplatePayload) {
  return requestClient.post<PageDesignTemplateRecord>(
    '/promotion/pagedesign/templates',
    data,
  );
}

export async function updateTemplate(
  id: string,
  data: PageDesignTemplatePayload,
) {
  return requestClient.put<PageDesignTemplateRecord>(
    `/promotion/pagedesign/templates/${id}`,
    data,
  );
}

export async function deleteTemplate(id: string) {
  return requestClient.delete<boolean>(`/promotion/pagedesign/templates/${id}`);
}

/** 发布前结构化校验当前草稿（服务端） */
export async function validatePage(id: string) {
  return requestClient.post<PageDesignValidationResult>(
    `/promotion/pagedesign/${id}/validate`,
  );
}

/** 提交发布申请：未开启审批时服务端自动完成发布 */
export async function submitRelease(
  id: string,
  data: PageDesignReleasePayload,
) {
  return requestClient.post<PageDesignRelease>(
    `/promotion/pagedesign/${id}/release`,
    data,
  );
}

export async function getReleases(id: string) {
  return requestClient.get<PageDesignRelease[]>(
    `/promotion/pagedesign/${id}/releases`,
  );
}

/** 审批发布申请（approved=false 表示拒绝） */
export async function auditRelease(
  releaseId: string,
  data: PageDesignReleaseAuditPayload,
) {
  return requestClient.post<PageDesignRelease>(
    `/promotion/pagedesign/releases/${releaseId}/approve`,
    data,
  );
}

export async function cancelRelease(id: string, releaseId: string) {
  return requestClient.post<PageDesignRelease>(
    `/promotion/pagedesign/${id}/releases/${releaseId}/cancel`,
  );
}

export async function getVersionDiff(
  id: string,
  fromVersionId: string,
  toVersionId: string,
) {
  return requestClient.get<PageDesignDiff>(
    `/promotion/pagedesign/${id}/versions/${fromVersionId}/diff/${toVersionId}`,
  );
}

export async function getAuditLogs(id: string) {
  return requestClient.get<PageDesignAuditLog[]>(
    `/promotion/pagedesign/${id}/audit-logs`,
  );
}

export async function getThemes() {
  return requestClient.get<PageDesignTheme[]>('/promotion/pagedesign/themes');
}

export async function createTheme(data: PageDesignThemePayload) {
  return requestClient.post<PageDesignTheme>(
    '/promotion/pagedesign/themes',
    data,
  );
}

export async function updateTheme(id: string, data: PageDesignThemePayload) {
  return requestClient.put<boolean>(`/promotion/pagedesign/themes/${id}`, data);
}

export async function deleteTheme(id: string) {
  return requestClient.delete<boolean>(`/promotion/pagedesign/themes/${id}`);
}

export async function getMetrics(id: string, days = 7) {
  return requestClient.get<PageDesignMetricDaily[]>(
    `/promotion/pagedesign/${id}/metrics`,
    { params: { days } },
  );
}

export async function getAssets(id: string) {
  return requestClient.get<PageDesignAssetResult>(
    `/promotion/pagedesign/${id}/assets`,
  );
}
