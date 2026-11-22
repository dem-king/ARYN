/**
 * 促销域（价格政策 / 采购档期）共享状态口径。
 *
 * 状态字符串收敛为常量，避免各页散落 '0'/'1'/'2' 魔法值；
 * 文案 / 语义色 / 时间格式化两页共用，改一处即同步。
 */

/** 促销单据状态：0 草稿 / 1 生效中 / 2 已停用 */
export const PROMO_STATUS = {
  ACTIVE: '1',
  DISABLED: '2',
  DRAFT: '0',
} as const;

export type PromoStatus = (typeof PROMO_STATUS)[keyof typeof PROMO_STATUS];

/** 状态文案（列表标签 / 筛选下拉共用） */
export const PROMO_STATUS_TEXT: Record<string, string> = {
  [PROMO_STATUS.DRAFT]: '草稿',
  [PROMO_STATUS.ACTIVE]: '生效中',
  [PROMO_STATUS.DISABLED]: '已停用',
};

/** 状态标签语义色 */
export const PROMO_STATUS_TYPE: Record<string, 'danger' | 'info' | 'success'> =
  {
    [PROMO_STATUS.DRAFT]: 'info',
    [PROMO_STATUS.ACTIVE]: 'success',
    [PROMO_STATUS.DISABLED]: 'danger',
  };

/** 列表时间展示：去掉 ISO 的 T，截到分钟 */
export const fmtPromoTime = (value?: string) =>
  value ? value.replace('T', ' ').slice(0, 16) : '-';
