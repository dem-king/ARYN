/**
 * 折扣活动管理 - 表格列定义与配置
 */

/** 活动状态选项：0未开始 1进行中 2已结束 3已暂停 */
export const activityStatusOptions = [
  { label: '未开始', value: 0, tagType: 'info' as const },
  { label: '进行中', value: 1, tagType: 'success' as const },
  { label: '已结束', value: 2, tagType: 'danger' as const },
  { label: '已暂停', value: 3, tagType: 'warning' as const },
];

/** 折扣类型选项：1打折 2减价 3固定价 */
export const discountTypeOptions = [
  { label: '打折', value: 1, hint: '如 0.8 表示 8 折' },
  { label: '减价', value: 2, hint: '如 20 表示减 20 元' },
  { label: '固定价', value: 3, hint: '如 99 表示固定售价 99 元' },
];

/** 适用范围选项：1全场 2指定商品 */
export const scopeOptions = [
  { label: '全场商品', value: 1 },
  { label: '指定商品', value: 2 },
];

/** 根据状态值获取标签类型 */
export function getStatusTagType(status: number) {
  const found = activityStatusOptions.find((item) => item.value === status);
  return found ? found.tagType : 'info';
}

/** 根据状态值获取标签文本 */
export function getStatusLabel(status: number) {
  const found = activityStatusOptions.find((item) => item.value === status);
  return found ? found.label : '未知';
}

/** 根据折扣类型获取标签文本 */
export function getDiscountTypeLabel(type: number) {
  const found = discountTypeOptions.find((item) => item.value === type);
  return found ? found.label : '未知';
}

/** 根据适用范围获取标签文本 */
export function getScopeLabel(scope: number) {
  const found = scopeOptions.find((item) => item.value === scope);
  return found ? found.label : '未知';
}

/** 折扣商品表单默认值 */
export function createDefaultGoodsItem() {
  return {
    spuId: '',
    skuId: '',
    spuName: '',
    spuUrls: [] as string[],
  };
}

/** 折扣活动表单默认值 */
export function createDefaultForm() {
  return {
    id: undefined as string | undefined,
    activityName: '',
    description: '',
    datatimes: [] as string[],
    startTime: '',
    endTime: '',
    discountType: 1,
    discountValue: 0.8,
    scope: 1,
    goodsList: [] as Array<ReturnType<typeof createDefaultGoodsItem>>,
  };
}

/** 表单校验规则 */
export const formRules = {
  activityName: [
    { required: true, message: '请输入活动名称', trigger: 'change' },
    { max: 128, message: '活动名称不能超过128个字符', trigger: 'change' },
  ],
  datatimes: [
    { required: true, message: '请选择活动时间范围', trigger: 'change' },
  ],
  discountType: [
    { required: true, message: '请选择折扣类型', trigger: 'change' },
  ],
  discountValue: [
    { required: true, message: '请输入折扣值', trigger: 'change' },
  ],
  scope: [{ required: true, message: '请选择适用范围', trigger: 'change' }],
};
