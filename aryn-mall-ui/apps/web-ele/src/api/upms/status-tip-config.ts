import { requestClient } from '#/api/request';

/** wd-status-tip 的 7 个预设位 */
export type StatusTipPreset =
  'collect' | 'comment' | 'content' | 'halo' | 'message' | 'network' | 'search';

/** 购物车 / 订单 / 秒杀三张会场空态插画 */
export type StatusTipIllustration =
  'empty-cart' | 'empty-order' | 'empty-seckill';

/** 后台可配置的 10 个图片位 */
export type StatusTipImageKey = StatusTipIllustration | StatusTipPreset;

/**
 * 空态插画配置（租户级）。
 *
 * 字段为空串表示该图片位未配置，C 端回落到内置图：7 个 wd-status-tip 预设位
 * 回落随 upms 发布的静态图，3 张会场插画回落随小程序包发布的 PNG。
 * 存的是完整图片 URL（与商品图同一套上传链路，本地存储/MinIO/OSS 均可）。
 */
export interface StatusTipConfig {
  /** 评论为空 */
  commentUrl: string;
  /** 收藏为空 */
  collectUrl: string;
  /** 内容为空 */
  contentUrl: string;
  /** 购物车空态 */
  emptyCartUrl: string;
  /** 订单列表空态 */
  emptyOrderUrl: string;
  /** 秒杀会场空态 */
  emptySeckillUrl: string;
  /** 券/资产为空 */
  haloUrl: string;
  /** 消息为空 */
  messageUrl: string;
  /** 网络异常 */
  networkUrl: string;
  /** 搜索无结果 */
  searchUrl: string;
}

/** 读取当前租户的空态插画配置（管理端原始值） */
export async function getStatusTipConfig() {
  return requestClient.get<StatusTipConfig>('/upms/status-tip-config');
}

/** 保存当前租户的空态插画配置（全字段覆盖，空串=清除自定义图） */
export async function saveStatusTipConfig(data: StatusTipConfig) {
  return requestClient.put('/upms/status-tip-config', data);
}
