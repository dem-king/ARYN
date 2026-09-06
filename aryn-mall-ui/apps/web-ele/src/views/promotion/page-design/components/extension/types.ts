import type { DecorationLink } from '../../../page-designer/schema/types';

const emptyLink = (): DecorationLink => ({
  params: {},
  path: '',
  type: 'custom',
});

const extensionCommonStyle = {
  bgColorDirection: 'to right',
  bgEndColor: '',
  bgPicUrl: '',
  bgStartColor: 'rgba(255, 255, 255, 1)',
  styleBottomMargin: 10,
  styleBottomPadding: 0,
  styleLbRadius: 0,
  styleLeftMargin: 0,
  styleLeftPadding: 0,
  styleLtRadius: 0,
  styleRbRadius: 0,
  styleRightMargin: 0,
  styleRightPadding: 0,
  styleRtRadius: 0,
  styleTopMargin: 0,
  styleTopPadding: 0,
};

export interface ExtensionDataSourceConfig {
  cacheTtl?: number;
  categoryId?: string;
  mode: 'automatic' | 'manual' | 'ranking';
  sort?: string;
  targetIds?: string[];
}

export function createGoodsWaterfallDefaults() {
  return {
    columns: 2 as 2 | 3,
    commonStyle: { ...extensionCommonStyle },
    count: 6,
    dataSource: {
      cacheTtl: 60,
      categoryId: '',
      mode: 'automatic' as const,
      sort: 'sales',
      targetIds: [] as string[],
    },
    showPrice: true,
    showSales: true,
    title: '猜你喜欢',
  };
}

export function validateGoodsWaterfall(props: Record<string, unknown>) {
  const errors: string[] = [];
  const dataSource = props.dataSource as ExtensionDataSourceConfig | undefined;
  if (!props.count || Number(props.count) <= 0)
    errors.push('展示数量必须大于 0');
  if (
    dataSource?.mode === 'manual' &&
    (dataSource.targetIds ?? []).length === 0
  ) {
    errors.push('手选商品不能为空');
  }
  return errors;
}

export function createCouponComboDefaults() {
  return {
    commonStyle: { ...extensionCommonStyle },
    count: 3,
    dataSource: {
      cacheTtl: 60,
      mode: 'automatic' as const,
      targetIds: [] as string[],
    },
    showReceiveBtn: true,
    showThreshold: true,
  };
}

export function validateCouponCombo(props: Record<string, unknown>) {
  const errors: string[] = [];
  const dataSource = props.dataSource as ExtensionDataSourceConfig | undefined;
  if (!props.count || Number(props.count) <= 0)
    errors.push('展示数量必须大于 0');
  if (
    dataSource?.mode === 'manual' &&
    (dataSource.targetIds ?? []).length === 0
  ) {
    errors.push('手选优惠券不能为空');
  }
  return errors;
}

export function createMemberBenefitsDefaults() {
  return {
    commonStyle: { ...extensionCommonStyle },
    dataSource: { mode: 'manual' as const, targetIds: [] as string[] },
    entries: [
      {
        description: '专属折扣与积分',
        iconUrl: '',
        id: 'benefit-1',
        link: emptyLink(),
        title: '会员权益',
      },
    ],
    title: '会员中心',
  };
}

export function validateMemberBenefits(props: Record<string, unknown>) {
  const entries = (props.entries ?? []) as Array<{ title?: string }>;
  if (!Array.isArray(entries) || entries.length === 0)
    return ['请至少配置一个权益项'];
  return entries.some((entry) => !entry.title) ? ['权益项标题不能为空'] : [];
}

export function createServicePromiseDefaults() {
  return {
    commonStyle: { ...extensionCommonStyle },
    items: [
      {
        description: '7 天无理由退货',
        iconUrl: '',
        id: 'promise-1',
        title: '放心退',
      },
      {
        description: '官方自营发货',
        iconUrl: '',
        id: 'promise-2',
        title: '正品保障',
      },
    ],
    title: '服务保障',
  };
}

export function validateServicePromise(props: Record<string, unknown>) {
  const items = (props.items ?? []) as Array<{ title?: string }>;
  if (!Array.isArray(items) || items.length === 0)
    return ['请至少配置一条服务承诺'];
  return items.some((item) => !item.title) ? ['服务承诺标题不能为空'] : [];
}

export function createBottomNavDefaults() {
  return {
    activeColor: '#ff5000',
    backgroundColor: '#ffffff',
    commonStyle: { ...extensionCommonStyle },
    items: [
      { iconUrl: '', id: 'nav-1', link: emptyLink(), text: '首页' },
      { iconUrl: '', id: 'nav-2', link: emptyLink(), text: '分类' },
      { iconUrl: '', id: 'nav-3', link: emptyLink(), text: '购物车' },
      { iconUrl: '', id: 'nav-4', link: emptyLink(), text: '我的' },
    ],
    textColor: '#333333',
  };
}

export function validateBottomNav(props: Record<string, unknown>) {
  const items = (props.items ?? []) as Array<{ text?: string }>;
  if (!Array.isArray(items) || items.length === 0)
    return ['请至少配置一个导航项'];
  return items.some((item) => !item.text) ? ['导航项文字不能为空'] : [];
}

export function createVideoLiveDefaults() {
  return {
    commonStyle: { ...extensionCommonStyle },
    coverUrl: '',
    liveId: '',
    mode: 'live' as 'live' | 'video',
    title: '精彩视频',
    videoUrl: '',
  };
}

export function validateVideoLive(props: Record<string, unknown>) {
  if (props.mode === 'video' && !props.videoUrl) return ['请填写视频地址'];
  return [];
}
