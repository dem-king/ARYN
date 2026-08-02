import { requestClient } from '#/api/request';

/**
 * 秒杀活动管理 API
 * 对应后端：/seckill/activity
 */

/** 秒杀活动状态：0未开始 1进行中 2已结束 3已暂停 */
export type SeckillActivityStatus = 0 | 1 | 2 | 3;

/** 秒杀活动分页查询参数 */
export interface SeckillActivityQuery {
  /** 活动名称（模糊匹配） */
  activityName?: string;
  /** 活动状态 */
  status?: number | string;
  /** 当前页码 */
  current?: number;
  /** 每页条数 */
  size?: number;
  /** 排序字段 */
  desc?: string;
}

/** 秒杀商品表单 */
export interface SeckillGoodsForm {
  /** 商品 SPU ID */
  spuId: string;
  /** 商品 SKU ID */
  skuId: string;
  /** 商品名称 */
  spuName?: string;
  /** 商品图片地址列表 */
  spuUrls?: string[];
  /** 秒杀价 */
  seckillPrice: number;
  /** 秒杀库存 */
  seckillStock: number;
  /** 每人限购数 */
  limitPerUser?: number;
}

/** 秒杀场次表单 */
export interface SeckillSessionForm {
  /** 场次 ID（编辑时存在） */
  id?: string;
  /** 场次名称 */
  sessionName: string;
  /** 场次时间范围（前端绑定） */
  datatimes?: string[];
  /** 场次开始时间 */
  startTime?: string;
  /** 场次结束时间 */
  endTime?: string;
  /** 场次下的商品列表 */
  goodsList: SeckillGoodsForm[];
}

/** 秒杀活动表单 */
export interface SeckillActivityForm {
  /** 活动 ID（编辑时存在） */
  id?: string;
  /** 活动名称 */
  activityName: string;
  /** 活动描述 */
  description?: string;
  /** 活动时间范围（前端绑定） */
  datatimes?: string[];
  /** 活动开始时间 */
  startTime?: string;
  /** 活动结束时间 */
  endTime?: string;
  /** 场次列表 */
  sessions: SeckillSessionForm[];
}

/** 秒杀活动列表记录 */
export interface SeckillActivityRecord {
  /** 活动 ID */
  id: string;
  /** 活动名称 */
  activityName: string;
  /** 活动开始时间 */
  startTime: string;
  /** 活动结束时间 */
  endTime: string;
  /** 场次数 */
  sessionCount: number;
  /** 活动状态 */
  status: SeckillActivityStatus;
  /** 创建时间 */
  createTime?: string;
}

/** 分页响应 */
export interface SeckillActivityPageResponse {
  /** 当前页记录 */
  records: SeckillActivityRecord[];
  /** 总数 */
  total: number;
}

/** 分页查询秒杀活动 */
export async function getPage(
  query: SeckillActivityQuery,
): Promise<SeckillActivityPageResponse> {
  return requestClient.get('/promotion/seckill/activity/page', {
    params: query,
  });
}

/** 根据 ID 获取秒杀活动详情（含场次与商品） */
export async function getById(
  id: string,
): Promise<SeckillActivityForm & { sessions: SeckillSessionForm[] }> {
  return requestClient.get(`/promotion/seckill/activity/${id}`);
}

/** 新增秒杀活动 */
export async function addObj(
  data: SeckillActivityForm,
): Promise<SeckillActivityForm> {
  return requestClient.post('/promotion/seckill/activity', data);
}

/** 修改秒杀活动 */
export async function editObj(
  data: SeckillActivityForm,
): Promise<SeckillActivityForm> {
  return requestClient.put('/promotion/seckill/activity', data);
}

/** 删除秒杀活动 */
export async function delObj(id: string): Promise<void> {
  return requestClient.delete(`/promotion/seckill/activity/${id}`);
}

/** 更新秒杀活动状态（0未开始 1进行中 2已结束 3已暂停） */
export async function updateStatus(id: string, status: number): Promise<void> {
  return requestClient.put(`/promotion/seckill/activity/${id}/status`, {
    status,
  });
}
