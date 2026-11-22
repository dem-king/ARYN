import { requestClient } from '#/api/request';

export type AiCapability = 'EMBEDDING' | 'STRUCTURED_TEXT';
export type AiUsage =
  | 'CATALOG_EMBEDDING'
  | 'MARKETING_DRAFT'
  | 'PROCUREMENT_PARSE'
  | 'PRODUCT_COPY'
  | 'SKU_RERANK';
export interface AiProvider {
  id: string;
  name: string;
  protocol: string;
  endpoint: string;
  credentialVersion: null | string;
  enabled: boolean;
}
export interface AiModel {
  id: string;
  providerId: string;
  model: string;
  capability: AiCapability;
  timeoutMillis: number;
  maxInputChars: number;
  maxOutputTokens: number;
  dimension: null | number;
  parameters: Record<string, boolean | number | string>;
}
export interface AiConfiguration {
  providers: AiProvider[];
  models: AiModel[];
  bindings: Partial<Record<AiUsage, string>>;
}
export interface AiConfigView {
  version: null | string;
  revision: number;
  activeVersion: null | string;
  emergencyEpoch: number;
  disabled: boolean;
  disabledProviders: string[];
  configuration: AiConfiguration;
}
export interface AiTestResult {
  version: string;
  modelId: string;
  passed: boolean;
  status: string;
  testedAt: string;
}
export interface AiCallLog {
  id: string;
  tenantId: string;
  usageCode: string;
  taskId: string;
  versionId: string;
  providerId: string;
  modelId: string;
  result: string;
  totalTokens: null | number;
  elapsedMillis: null | number;
  createTime: string;
}
export const getConfiguration = () =>
  requestClient.get<AiConfigView>('/upms/ai-config');
export const getVersions = () =>
  requestClient.get<AiConfigView[]>('/upms/ai-config/versions');
export const getTestResults = (version: string) =>
  requestClient.get<AiTestResult[]>(
    `/upms/ai-config/versions/${encodeURIComponent(version)}/tests`,
  );
export const saveConfiguration = (data: unknown) =>
  requestClient.put<AiConfigView>('/upms/ai-config/draft', data);
export const testModel = (version: string, model: string) =>
  requestClient.post<AiTestResult>(
    `/upms/ai-config/versions/${encodeURIComponent(version)}/models/${encodeURIComponent(model)}/test`,
  );
export const activateConfiguration = (
  version: string,
  expectedRevision: number,
  rollback = false,
) =>
  requestClient.post<AiConfigView>(
    `/upms/ai-config/versions/${rollback ? 'rollback' : 'activate'}`,
    { version, expectedRevision },
  );
export const setEmergency = (
  providerId: null | string,
  disabled: boolean,
  expectedRevision: number,
) =>
  requestClient.post<AiConfigView>('/upms/ai-config/emergency', {
    providerId,
    disabled,
    expectedRevision,
  });
export const getCalls = (tenantId?: string) =>
  requestClient.get<AiCallLog[]>('/upms/ai-config/calls', {
    params: { tenantId },
  });

export const bootstrapConfiguration = (
  expectedRevision: number,
  embedding: boolean,
) =>
  requestClient.post<AiConfigView>('/upms/ai-config/bootstrap', {
    expectedRevision,
    embedding,
  });
