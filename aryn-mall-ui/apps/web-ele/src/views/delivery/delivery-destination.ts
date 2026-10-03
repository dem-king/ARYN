/**
 * 配送目的地展示口径（管理端）。
 *
 * C 端司机端已有同一套口径（uniapp `utils/delivery-navigation.ts`）：船供内部配送
 * （delivery_way=4）下单时没有街道地址，`delivery_task.recipient_address` 为空，
 * 只落了船舶/港口/泊位快照。此时回落到「港口 + 泊位」，否则页面表现为「地址丢了」。
 *
 * 泊位单独出现时（如「321」）没有检索价值，必须有港口名才拼接。
 */
export interface DeliveryDestination {
  /** 配送港口名称快照 */
  portName?: null | string;
  /** 收货完整地址 */
  recipientAddress?: null | string;
  /** 泊位快照 */
  berth?: null | string;
}

function clean(value?: null | string): string {
  return typeof value === 'string' ? value.trim() : '';
}

/** 可展示的目的地文本；没有可用目的地时返回空串 */
export function resolveDeliveryDestination(
  target?: DeliveryDestination | null,
): string {
  if (!target) return '';

  const address = clean(target.recipientAddress);
  if (address) return address;

  const port = clean(target.portName);
  if (!port) return '';

  const berth = clean(target.berth);
  return berth ? `${port} ${berth}` : port;
}
