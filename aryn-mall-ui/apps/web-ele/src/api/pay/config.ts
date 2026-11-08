import { requestClient } from '#/api/request';

export interface PayConfigRoute {
  id?: string;
  type: string;
  terminalType: string;
  appId: string;
  mchId: string;
  enabled?: string;
  activeRevisionId?: string;
  configVersion?: number;
  materialReady?: boolean;
  checkStatus?: string;
  createTime?: string;
  updateTime?: string;
}

export interface PayMaterial {
  materialId: string;
  purpose: string;
  fingerprint?: string;
  contentType?: string;
  byteSize?: number;
  certSerialNo?: string;
  certNotBefore?: string;
  certNotAfter?: string;
  createTime?: string;
  sensitive?: boolean;
}

export interface PayMaterialUpload extends PayMaterial {
  keyPairMatched?: boolean;
}

export interface PayRevision {
  id: string;
  configId: string;
  revisionNo: number;
  channel: string;
  terminalType: string;
  appId: string;
  mchId: string;
  environment: string;
  wxPublicKeyId?: string;
  buyerIdentityMode?: string;
  checkStatus?: string;
  checkSummary?: string;
  createTime?: string;
  materials?: PayMaterial[];
}

export interface PayCheckItem {
  code: string;
  name: string;
  passed: boolean;
  message?: string;
}

export interface PayCheckResult {
  configId: string;
  revisionId: string;
  passed: boolean;
  proofLevel: string;
  checkedAt?: string;
  items: PayCheckItem[];
}

export interface PayCapability {
  tradeType: string;
  channel: string;
  terminalType?: string;
  invokeType: string;
  available: boolean;
  reason?: string;
}

export async function getPage(query: any) {
  return requestClient.get('/pay/payconfig/page', { params: query });
}

export async function getById(id: string) {
  return requestClient.get<PayConfigRoute>(`/pay/payconfig/${id}`);
}

export async function addObj(data: Partial<PayConfigRoute>) {
  return requestClient.post('/pay/payconfig', data);
}

export async function editObj(data: Partial<PayConfigRoute>) {
  return requestClient.put('/pay/payconfig', data);
}

export async function delObj(id: string) {
  return requestClient.delete(`/pay/payconfig/${id}`);
}

export async function listMaterials(configId: string) {
  return requestClient.get<PayMaterial[]>(
    `/pay/payconfig/${configId}/materials`,
  );
}

export async function uploadMaterial(
  configId: string,
  purpose: string,
  file: File,
) {
  const formData = new FormData();
  formData.append('purpose', purpose);
  formData.append('file', file);
  return requestClient.post<PayMaterialUpload>(
    `/pay/payconfig/${configId}/materials`,
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  );
}

export async function listRevisions(configId: string) {
  return requestClient.get<PayRevision[]>(
    `/pay/payconfig/${configId}/revisions`,
  );
}

export async function createRevision(
  configId: string,
  data: {
    appId: string;
    buyerIdentityMode?: string;
    environment: string;
    materials: Array<{ materialId: string; purpose: string }>;
    mchId: string;
    wxPublicKeyId?: string;
  },
) {
  return requestClient.post<PayRevision>(
    `/pay/payconfig/${configId}/revisions`,
    data,
  );
}

export async function publishRevision(
  configId: string,
  revisionId: string,
  expectedVersion?: number,
) {
  return requestClient.post<PayRevision>(
    `/pay/payconfig/${configId}/revisions/${revisionId}/publish`,
    null,
    { params: { expectedVersion } },
  );
}

export async function disableConfig(
  configId: string,
  expectedVersion?: number,
) {
  return requestClient.post(`/pay/payconfig/${configId}/disable`, null, {
    params: { expectedVersion },
  });
}

export async function checkRevision(configId: string, revisionId: string) {
  return requestClient.post<PayCheckResult>(
    `/pay/payconfig/${configId}/revisions/${revisionId}/check`,
  );
}

export async function migrateLegacy() {
  return requestClient.post('/pay/payconfig/legacy/migrate');
}

export async function getCapabilities(terminalType?: string) {
  return requestClient.get<PayCapability[]>('/pay/pay/capability', {
    params: { terminalType },
  });
}
