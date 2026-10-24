/**
 * 积分/余额记录触发场景中文映射
 * 键与后端 recordPointsChange/recordBalanceChange 写入的 triggerScene 取值一致
 */
const triggerSceneMap: Record<string, string> = {
  ORDER_COMPLETE: '订单完成',
  SIGN_IN: '签到',
  RECHARGE: '充值',
  ADMIN_ADJUST: '后台调整',
  REGISTER: '注册奖励',
  ORDER_DEDUCT: '积分抵现',
  POINTS_EXPIRE: '积分过期',
  POINTS_EXCHANGE: '积分兑换',
  ORDER_REFUND: '退款返还',
};

export function formatTriggerScene(scene?: null | string): string {
  if (!scene) return '';
  return triggerSceneMap[scene] ?? scene;
}
