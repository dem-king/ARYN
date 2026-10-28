/**
 * 秒杀活动管理 - 表格列定义与配置
 */

/** 活动状态选项：0未开始 1进行中 2已结束 3已暂停 */
export const activityStatusOptions = [
  { label: '未开始', value: 0, tagType: 'info' as const },
  { label: '进行中', value: 1, tagType: 'success' as const },
  { label: '已结束', value: 2, tagType: 'danger' as const },
  { label: '已暂停', value: 3, tagType: 'warning' as const },
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

/** 列表页表格列定义 */
export const tableColumns = [
  {
    prop: 'activityName',
    label: '活动名称',
    align: 'center' as const,
    showOverflowTooltip: true,
  },
  {
    prop: 'startTime',
    label: '开始时间',
    align: 'center' as const,
    width: 180,
  },
  {
    prop: 'endTime',
    label: '结束时间',
    align: 'center' as const,
    width: 180,
  },
  {
    prop: 'sessionCount',
    label: '场次数',
    align: 'center' as const,
    width: 90,
  },
  {
    prop: 'status',
    label: '状态',
    align: 'center' as const,
    width: 100,
  },
  {
    prop: 'createTime',
    label: '创建时间',
    align: 'center' as const,
    width: 180,
  },
];

/** 秒杀商品表单默认值 */
export function createDefaultGoodsItem() {
  return {
    spuId: '',
    skuId: '',
    spuName: '',
    spuUrls: [] as string[],
    seckillPrice: 0,
    seckillStock: 0,
    limitPerUser: 1,
  };
}

/** 秒杀场次表单默认值 */
export function createDefaultSessionItem() {
  return {
    id: undefined as string | undefined,
    sessionName: '',
    datatimes: [] as string[],
    startTime: '',
    endTime: '',
    goodsList: [createDefaultGoodsItem()],
  };
}

/** 秒杀活动表单默认值 */
export function createDefaultForm() {
  return {
    id: undefined as string | undefined,
    activityName: '',
    description: '',
    datatimes: [] as string[],
    startTime: '',
    endTime: '',
    sessions: [createDefaultSessionItem()],
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
};
