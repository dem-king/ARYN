import type { RetailComponentBaseProps } from '../../../page-designer/schema/retail-components';

import {
  createRetailCommonStyle,
  validateRetailBase,
} from '../../../page-designer/schema/retail-components';

/**
 * 新人专享楼层。
 *
 * 数据源固定为「当前进行中的新人价活动」，无需运营手配活动 ID，
 * 故不暴露数据来源选择（dataSource 保留在模型中以满足基础契约）。
 */
export interface NewbieFloorProps extends RetailComponentBaseProps {
  /** 是否展示活动结束倒计时 */
  showCountdown: boolean;
  /** 是否展示「新人」角标/副标题 */
  showTag: boolean;
  /**
   * 自动前进（分页式：一次一张卡）。
   * 字段名沿用 autoScroll 以兼容已发布页面的存量数据；
   * 语义已由「连续跑马灯」改为「分页步进 + 触摸暂停」。
   */
  autoScroll: boolean;
  /** 每张卡片的停留时长（毫秒） */
  interval: number;
  /** 区块主色（6 位 hex）用于淡色底纹，空串跟随页面主题色 */
  mainColor: string;
  title: string;
}

export function createNewbieFloorDefaults(): NewbieFloorProps {
  return {
    autoScroll: false,
    commonStyle: createRetailCommonStyle(),
    count: 6,
    dataSource: { mode: 'automatic' },
    emptyStrategy: 'hide',
    interval: 3000,
    invalidStrategy: 'hide',
    mainColor: '',
    showCountdown: true,
    showTag: true,
    title: '新人专享',
  };
}

export function validateNewbieFloor(props: NewbieFloorProps): string[] {
  // 取数固定读进行中活动：无活动时按空态策略隐藏，不做「必填活动」校验
  return validateRetailBase(props, 20);
}
