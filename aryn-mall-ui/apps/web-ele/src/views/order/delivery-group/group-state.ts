import type { DeliveryGroup } from '#/api/order/delivery-group';

export const groupStatus: Record<string, string> = {
  CANCELLED: '已取消',
  CLOSED: '已截单',
  COMPLETED: '已完成',
  FULFILLING: '履约中',
  OPEN: '开放中',
};
export function canJoin(
  group: DeliveryGroup,
  allowLate: boolean,
  now = Date.now(),
) {
  const end = group.windowEnd
    ? Date.parse(group.windowEnd.replace(' ', 'T'))
    : Number.NaN;
  const cutoff = group.cutoff
    ? Date.parse(group.cutoff.replace(' ', 'T'))
    : Number.NaN;
  return (
    !!group.windowStart &&
    end > now &&
    Number.isFinite(cutoff) &&
    ((group.status === 'OPEN' && cutoff > now) ||
      (allowLate && ['CLOSED', 'OPEN'].includes(group.status)))
  );
}
/** 请求响应丢失后，未改动命令继续使用原请求号；修订或输入变化才创建新信封。 */
export function commandRequest(
  previous: undefined | { id: string; signature: string },
  command: unknown,
  createId: () => string,
) {
  const signature = JSON.stringify(command);
  return previous?.signature === signature
    ? previous
    : { id: createId(), signature };
}
