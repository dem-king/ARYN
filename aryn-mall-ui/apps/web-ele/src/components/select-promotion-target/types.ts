/** 列表列：formatter 输出文本，tag 输出状态标签（优先级高于 formatter） */
export interface PromotionTargetColumn {
  align?: 'center' | 'left';
  formatter?: (row: Record<string, any>) => string;
  label: string;
  prop?: string;
  tag?: (
    row: Record<string, any>,
  ) => null | { text: string; type: 'danger' | 'info' | 'success' | 'warning' };
  width?: number;
}

/** 分页查询函数：query 含 current/size */
export type PromotionTargetLoader = (
  query: Record<string, unknown>,
) => Promise<unknown>;

/** 通用活动选择弹窗的入参（装修器 IdPickerField 按 kind 组装） */
export interface PromotionTargetDialogProps {
  columns: PromotionTargetColumn[];
  limitNum?: number;
  loader: PromotionTargetLoader;
  noun: string;
  rowLabel: (row: Record<string, any>) => string;
  title: string;
}
