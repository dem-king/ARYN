/**
 * 待付款时限展示文案的单一来源。
 *
 * 后端在待付款订单详情（/app/orderinfo/{id}、/getByOrderNo）下发
 * payTimeoutMinutes：由订单配置的超时取消延迟级别（字典 mq_delay_time_level，
 * RocketMQ 延迟级别而非分钟数）换算而来，与延迟取消消息、兜底扫描共用同一口径。
 *
 * 约束：收银台 / 订单详情禁止再写死「30分钟」——历史上配置改成 20 分钟后
 * 页面仍提示 30 分钟，用户以为配置无效。
 */

/** 后端未下发（旧包/非待付款单）时的兜底值，与后端默认延迟级别 16（30 分钟）一致 */
export const DEFAULT_PAY_TIMEOUT_MINUTES = 30

/**
 * 把支付时限分钟数格式化为文案时长。
 * 整小时以小时展示（配置档位含 1小时/2小时），非法或缺省回落默认 30 分钟。
 */
export function formatPayTimeout(minutes?: null | number): string {
  const value = Number(minutes)
  if (!Number.isFinite(value) || value <= 0)
    return `${DEFAULT_PAY_TIMEOUT_MINUTES}分钟`
  if (value >= 60 && value % 60 === 0)
    return `${value / 60}小时`
  return `${value}分钟`
}
