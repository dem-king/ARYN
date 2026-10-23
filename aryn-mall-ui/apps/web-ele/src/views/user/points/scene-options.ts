export interface PointsSceneOption {
  hint: string;
  label: string;
  ruleType: '1' | '2';
  value: string;
}

/**
 * 积分配置可维护场景。与后端 PointsSceneEnum#isConfigurable 保持一致。
 */
export const pointsSceneOptions: PointsSceneOption[] = [
  {
    label: '订单完成',
    value: 'ORDER_COMPLETE',
    ruleType: '1',
    hint: '每 1 元商品实付获得的基础积分，实际发放再乘以会员积分倍率',
  },
  {
    label: '注册奖励',
    value: 'REGISTER',
    ruleType: '1',
    hint: '新用户注册时一次性发放的固定积分',
  },
  {
    label: '积分抵现',
    value: 'ORDER_DEDUCT',
    ruleType: '2',
    hint: '抵扣 1 元所需积分；单笔最多抵扣商品应付金额的 20%（不含运费）',
  },
];

export function getPointsSceneOption(value?: string) {
  if (!value) return undefined;
  return pointsSceneOptions.find((item) => item.value === value);
}
