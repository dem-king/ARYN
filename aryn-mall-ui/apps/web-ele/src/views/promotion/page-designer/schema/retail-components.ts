import type { DiyCommonStyle } from '@vben/types';

export type RetailEmptyStrategy = 'hide' | 'placeholder';
export type RetailInvalidStrategy = 'hide' | 'placeholder';

export interface RetailDataSource {
  categoryId?: string;
  metric?: string;
  mode: 'automatic' | 'current-tenant' | 'manual' | 'ranking' | 'rule';
  sort?: string;
  targetIds?: string[];
}

export interface RetailComponentBaseProps extends Record<string, unknown> {
  commonStyle: DiyCommonStyle;
  count: number;
  dataSource: RetailDataSource;
  emptyStrategy: RetailEmptyStrategy;
  invalidStrategy: RetailInvalidStrategy;
}

export function createRetailCommonStyle(): DiyCommonStyle {
  return {
    bgColorDirection: 'to right',
    bgEndColor: '',
    bgPicUrl: '',
    bgStartColor: '#ffffff',
    styleBottomMargin: 10,
    styleBottomPadding: 12,
    styleLbRadius: 0,
    styleLeftMargin: 10,
    styleLeftPadding: 12,
    styleLtRadius: 0,
    styleRbRadius: 0,
    styleRightMargin: 10,
    styleRightPadding: 12,
    styleRtRadius: 0,
    styleTopMargin: 10,
    styleTopPadding: 12,
  };
}

export function validateRetailBase(
  props: RetailComponentBaseProps,
  maxCount: number,
): string[] {
  const errors: string[] = [];
  if (!Number.isInteger(props.count) || props.count <= 0) {
    errors.push('展示数量必须大于 0');
  } else if (props.count > maxCount) {
    errors.push(`展示数量不能超过 ${maxCount}`);
  }
  if (!props.dataSource?.mode) errors.push('请选择数据来源');
  if (!props.emptyStrategy) errors.push('请选择无数据策略');
  if (!props.invalidStrategy) errors.push('请选择失效数据策略');
  if (!props.commonStyle) errors.push('请配置通用样式');
  return errors;
}
