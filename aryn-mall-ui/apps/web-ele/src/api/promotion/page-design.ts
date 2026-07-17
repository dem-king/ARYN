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
  pageType: '0' | '1' | '2';
  schemaVersion: number;
  systemFlag: '0' | '1';
  templateContent: Record<string, unknown>;
  templateName: string;
  templateType: '0' | '1';
}

export interface PageDesignTemplateRecord extends PageDesignTemplatePayload {
  id: string;
  sort?: number;
  status?: string;
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
