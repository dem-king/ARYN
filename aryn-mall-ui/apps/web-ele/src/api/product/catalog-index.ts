import { requestClient } from '#/api/request';

export interface CatalogGeneration {
  id: string;
  configVersion: string;
  modelFingerprint: string;
  dimension: number;
  status: 'BUILDING' | 'FAILED' | 'QUEUED' | 'READY';
  indexedRows: number;
  expectedRows: number;
  errorCode: null | string;
  createTime: string;
}
export interface CatalogIndexStatus {
  activeGeneration: null | string;
  revision: number;
  generations: CatalogGeneration[];
}
export const getCatalogIndex = () =>
  requestClient.get<CatalogIndexStatus>('/product/catalog-index');
export const createCatalogIndex = () =>
  requestClient.post<CatalogGeneration>('/product/catalog-index');
export const retryCatalogIndex = (id: string) =>
  requestClient.post(`/product/catalog-index/${encodeURIComponent(id)}/retry`);
export const activateCatalogIndex = (
  generation: string,
  expectedRevision: number,
) =>
  requestClient.post('/product/catalog-index/activate', {
    generation,
    expectedRevision,
  });
