import type {
  ZoneBatchDetail,
  ZoneBatchPage,
  ZoneBatchPageQuery,
  ZoneBatchPayload,
  ZoneBatchPreviewRequest,
  ZoneBatchResult,
  ZoneGoodsIdsQuery,
  ZoneGoodsIdsResult,
  ZoneGoodsPage,
  ZoneGoodsPageQuery,
  ZonePreview,
  ZoneSyncStatus,
  ZoneTagDetail,
  ZoneTagQueryRequest,
  ZoneTagQueryResult,
  ZoneTagSavePayload,
  ZoneUndoResult,
} from '#/views/product/zone-batch/types';

/**
 * 商品专区标签 / 专区商品管理接口（契约：04-实施方案与接口契约.md §2/§3.1）。
 *
 * 所有路径首段均为微服务域 `product`，boot/cloud 双模式由 `requestClient` 内的
 * `rewriteBootUrl` 统一改写，禁止在调用处拼接 `/boot`。
 *
 * 本文件是专区接口唯一契约入口：`#/api/product/goods-spu.ts#batchZoneTags`
 * 已改为 re-export 本文件，避免两处契约漂移。
 */
import { requestClient } from '#/api/request';

/** 批量预检（只读，≤200/请求）：`POST /product/goodsspu/tags/batch/preview` */
export async function previewZoneTags(data: ZoneBatchPreviewRequest) {
  return requestClient.post<ZonePreview>(
    '/product/goodsspu/tags/batch/preview',
    data,
  );
}

/**
 * 批量执行（≤200/请求，前端 >200 自行分片）：`POST /product/goodsspu/tags/batch`
 *
 * 响应结构已升级为 `{ batchId, summary, zones, sync, items }`（§2.2）。
 */
export async function batchZoneTags(data: ZoneBatchPayload) {
  return requestClient.post<ZoneBatchResult>(
    '/product/goodsspu/tags/batch',
    data,
  );
}

/** 批量读取专区状态（≤200/请求）：`POST /product/goodsspu/tags/query` */
export async function queryZoneTags(data: ZoneTagQueryRequest) {
  return requestClient.post<ZoneTagQueryResult>(
    '/product/goodsspu/tags/query',
    data,
  );
}

/** 专区商品管理分页：`GET /product/goodsspu/zone/page` */
export async function getZonePage(query: ZoneGoodsPageQuery) {
  return requestClient.get<ZoneGoodsPage>('/product/goodsspu/zone/page', {
    params: query,
  });
}

/** 按筛选条件取 ID（全选筛选结果 / 换一批）：`POST /product/goodsspu/ids` */
export async function getZoneIds(data: ZoneGoodsIdsQuery) {
  return requestClient.post<ZoneGoodsIdsResult>('/product/goodsspu/ids', data);
}

/** 单商品专区标签回显（SPU + SKU 覆盖）：`GET /product/goodsspu/tags/{spuId}` */
export async function getZoneTags(spuId: string) {
  return requestClient.get<ZoneTagDetail>(`/product/goodsspu/tags/${spuId}`);
}

/** 单商品专区标签精调保存：`PUT /product/goodsspu/{spuId}/tags` */
export async function saveZoneTags(spuId: string, data: ZoneTagSavePayload) {
  return requestClient.put(`/product/goodsspu/${spuId}/tags`, data);
}

/** 批次记录分页：`GET /product/goodsspu/tags/batch/page` */
export async function getBatchPage(query: ZoneBatchPageQuery) {
  return requestClient.get<ZoneBatchPage>('/product/goodsspu/tags/batch/page', {
    params: query,
  });
}

/** 批次详情：`GET /product/goodsspu/tags/batch/{batchId}` */
export async function getBatchDetail(batchId: string) {
  return requestClient.get<ZoneBatchDetail>(
    `/product/goodsspu/tags/batch/${batchId}`,
  );
}

/** 撤销批次：`POST /product/goodsspu/tags/batch/{batchId}/undo` */
export async function undoBatch(batchId: string) {
  return requestClient.post<ZoneUndoResult>(
    `/product/goodsspu/tags/batch/${batchId}/undo`,
  );
}

/** 专区价格同步状态：`GET /promotion/zone/sync-status` */
export async function getZoneSyncStatus() {
  return requestClient.get<ZoneSyncStatus>('/promotion/zone/sync-status');
}

/** 手动触发专区价格重算：`POST /promotion/zone/sync` */
export async function triggerZoneSync() {
  return requestClient.post<ZoneSyncStatus>('/promotion/zone/sync');
}
