import type { RetailCommonProps } from '../../../page-designer/schema/retail-components';
import type { DecorationLink } from '../../../page-designer/schema/types';

import {
  createRetailCommonStyle,
  validateRetailCommon,
} from '../../../page-designer/schema/retail-components';
import { validateDecorationLink } from '../../../page-designer/utils/link-utils';

/**
 * 活动入口条（diy-activity-strip）。
 *
 * 与 C 端 diy-activity-strip 同源：字段口径严格对齐
 * `aryn-mall-uniapp/src/components/diy/retail-types.ts` 的
 * ActivityStripEntry / ActivityStripDataSource / ActivityStripProps，
 * 两端读写同一份 props，改字段必须同时改两端。
 *
 * 自动模式并行取「进行中的秒杀/拼团/折扣各一条」组装卡片；
 * 手动模式由运营手配 ActivityStripEntry[]（含跳转链接），C 端直接渲染。
 */

/**
 * 三类内置入口的固定会场路由（与 C 端 activity-strip.ts 的
 * ACTIVITY_STRIP_HALLS 逐字一致）。
 *
 * 入口永远指向会场聚合页而不是某一单场/单活动：活动结束后入口不失效，
 * 只会因为「该类无进行中活动」整张卡隐藏。
 */
export const ACTIVITY_STRIP_HALLS = {
  discount: '/pages/promotion/discount',
  'group-buy': '/sub-pages/promotion/group-buy/group-buy-list/index',
  seckill: '/pages/promotion/seckill',
} as const;

/** 卡片类型：内置三类固定跳对应会场，custom 走运营配置的 link */
export type ActivityStripEntryKey =
  'custom' | 'discount' | 'group-buy' | 'seckill';

/** 活动入口条的单张小卡（已组装好，渲染侧只做展示） */
export interface ActivityStripEntry {
  /** 卡片类型：内置三类固定跳对应会场，custom 走 link */
  key: ActivityStripEntryKey;
  /** 活动名，如「限时秒杀」；缺图时取首字做色块兜底 */
  title: string;
  /** 钩子文案，如「14:00 场·整点开抢」；空则不渲染该行 */
  hook: string;
  /** 角标文字，如「抢」「省¥4」「9.5折」；空则不渲染角标 */
  badge: string;
  /** 缩略图，空或远程加载失败走首字兜底 */
  imageUrl: string;
  /** 跳转：内置三类的 link 在自动模式下填固定会场路由 */
  link: DecorationLink;
}

/**
 * 活动入口条数据源。
 *
 * · automatic：并行取「进行中的秒杀/拼团/折扣各一条」自动组装，入口固定指向
 *   会场聚合页，活动轮换后入口不失效（默认）；
 * · manual：运营手配 ActivityStripEntry[]，不实时拉活动，可放领券中心等任意链接。
 */
export type ActivityStripDataSource =
  { entries: ActivityStripEntry[]; mode: 'manual' } | { mode: 'automatic' };

/**
 * 不再提供「每行列数 / 展示数量」：自动模式最多三类活动各一张，
 * 手配模式卡片数就是运营添加的数量，渲染侧按实际卡数等宽排布
 * （一行最多三张，超出换行）。
 */
export interface ActivityStripProps extends RetailCommonProps {
  /** 自动取数（默认）或运营手配卡片 */
  dataSource: ActivityStripDataSource;
}

/**
 * 手动模式的示例卡片：切到「手动配置卡片」时预填，运营改文案/图/链接即可。
 *
 * 与 C 端 buildActivityStripEntries 的自动组装结果同形（key/标题/角标/钩子/
 * 会场路由一致），这样两种模式切换时画布视觉不突变。
 */
export function createActivityStripManualEntries(): ActivityStripEntry[] {
  return [
    {
      badge: '抢',
      hook: '午间场·整点开抢',
      imageUrl: '',
      key: 'seckill',
      link: { params: {}, path: ACTIVITY_STRIP_HALLS.seckill, type: 'custom' },
      title: '限时秒杀',
    },
    {
      badge: '省¥4',
      hook: '2人成团立省',
      imageUrl: '',
      key: 'group-buy',
      link: {
        params: {},
        path: ACTIVITY_STRIP_HALLS['group-buy'],
        type: 'custom',
      },
      title: '2人拼团',
    },
    {
      badge: '9.5折',
      hook: '9.5折专区',
      imageUrl: '',
      key: 'discount',
      link: { params: {}, path: ACTIVITY_STRIP_HALLS.discount, type: 'custom' },
      title: '限时折扣',
    },
  ];
}

/**
 * 默认值：与 C 端 diy-activity-strip 的 showData 兜底一致
 * （automatic、emptyStrategy/invalidStrategy=hide）。
 *
 * 入口条没有活动时「整条不渲染」是默认行为（§5.5.6），运营显式配
 * placeholder 时才渲染空态文案。
 */
export function createActivityStripDefaults(): ActivityStripProps {
  return {
    commonStyle: createRetailCommonStyle(),
    dataSource: { mode: 'automatic' },
    emptyStrategy: 'hide',
    invalidStrategy: 'hide',
  };
}

export function validateActivityStrip(props: ActivityStripProps): string[] {
  const errors = validateRetailCommon(props);
  // 自动模式不校验活动 ID：三类活动由 loader 并行取「进行中」，
  // 无活动时按空态策略整条隐藏，不属于配置错误。
  if (props.dataSource?.mode === 'manual') {
    const entries = Array.isArray(props.dataSource.entries)
      ? props.dataSource.entries
      : [];
    if (entries.length === 0) errors.push('请至少添加一个活动卡片');
    if (entries.length > 10) errors.push('活动卡片不能超过 10 张');
    for (const entry of entries) {
      if (!entry.title?.trim()) errors.push('活动卡片标题不能为空');
      errors.push(...validateDecorationLink(entry.link));
    }
  }
  return [...new Set(errors)];
}
