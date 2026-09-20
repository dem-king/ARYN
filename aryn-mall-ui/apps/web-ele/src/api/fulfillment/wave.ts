import { requestClient } from '#/api/request';

/** 拣货波次 */
export interface FulfillmentWave {
  completedTime?: string;
  handedOverTime?: string;
  id?: string;
  planDeliveryTime?: string;
  portCode?: string;
  portName?: string;
  pickedTime?: string;
  remark?: string;
  reviewedTime?: string;
  status?: string;
  vesselCallId?: string;
  waveNo?: string;
  warehouseId?: string;
}

/** 拣货明细 */
export interface FulfillmentPickItem {
  id: string;
  orderItemId?: string;
  pickStatus?: string;
  pickedQuantity?: number;
  pickerId?: string;
  requiredQuantity?: number;
  shortQuantity?: number;
  skuBarcode?: string;
  skuId?: string;
  skuName?: string;
  spuName?: string;
  substitutedSkuId?: string;
}

/** 履约异常 */
export interface FulfillmentException {
  description?: string;
  evidenceUrls?: string;
  exceptionType?: string;
  handleRemark?: string;
  handlerId?: string;
  id?: string;
  orderId?: string;
  status?: string;
  taskId?: string;
  waveId?: string;
}

export async function createWave(data: Partial<FulfillmentWave>) {
  return requestClient.post('/mall-order/fulfillment/wave', data);
}

export async function getWavePage(query: any) {
  return requestClient.get('/mall-order/fulfillment/wave/page', {
    params: query,
  });
}

export async function getWaveItems(waveId: string) {
  return requestClient.get(`/mall-order/fulfillment/wave/${waveId}/items`);
}

export async function addOrderToWave(waveId: string, orderId: string) {
  return requestClient.post(
    `/mall-order/fulfillment/wave/${waveId}/orders`,
    null,
    {
      params: { orderId },
    },
  );
}

export async function scanPick(data: {
  itemId: string;
  quantity: number;
  scannedCode: string;
  waveId: string;
}) {
  return requestClient.post('/mall-order/fulfillment/pick/scan', data);
}

export async function reportShort(data: {
  actualQuantity: number;
  itemId: string;
  reasonCode: string;
  reasonDesc?: string;
  substitutedSkuId?: string;
  waveId: string;
}) {
  return requestClient.post('/mall-order/fulfillment/pick/short', data);
}

export async function reviewWave(waveId: string) {
  return requestClient.post(`/mall-order/fulfillment/wave/${waveId}/review`);
}

export async function handOverWave(waveId: string, staffId?: string) {
  return requestClient.post(
    `/mall-order/fulfillment/wave/${waveId}/hand-over`,
    null,
    {
      params: { staffId },
    },
  );
}

export async function getExceptionPage(query: any) {
  return requestClient.get('/mall-order/fulfillment/exception/page', {
    params: query,
  });
}

export async function closeException(
  exceptionId: string,
  handleRemark?: string,
) {
  return requestClient.post(
    `/mall-order/fulfillment/exception/${exceptionId}/close`,
    null,
    { params: { handleRemark } },
  );
}

/** 履约异常上报（含证据照片 URL 列表 JSON） */
export async function reportException(data: {
  description?: string;
  evidenceUrls?: string;
  exceptionType: string;
  orderId?: string;
  taskId?: string;
  waveId?: string;
}) {
  return requestClient.post('/mall-order/fulfillment/exception', data);
}
