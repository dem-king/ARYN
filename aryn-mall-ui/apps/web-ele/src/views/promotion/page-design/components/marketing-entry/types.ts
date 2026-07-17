import type { RetailComponentBaseProps } from '../../../page-designer/schema/retail-components';
import type { DecorationLink } from '../../../page-designer/schema/types';

import {
  createRetailCommonStyle,
  validateRetailBase,
} from '../../../page-designer/schema/retail-components';
import { validateDecorationLink } from '../../../page-designer/utils/link-utils';

export interface MarketingEntry {
  iconUrl: string;
  id: string;
  link: DecorationLink;
  title: string;
}

export interface MarketingEntryProps extends RetailComponentBaseProps {
  columns: 4 | 5;
  entries: MarketingEntry[];
}

export function createMarketingEntryDefaults(): MarketingEntryProps {
  return {
    columns: 4,
    commonStyle: createRetailCommonStyle(),
    count: 4,
    dataSource: { mode: 'manual' },
    emptyStrategy: 'placeholder',
    entries: [
      {
        iconUrl: '',
        id: 'customer-service',
        link: { params: {}, path: '', type: 'customer-service' },
        title: '联系客服',
      },
    ],
    invalidStrategy: 'hide',
  };
}

export function validateMarketingEntry(props: MarketingEntryProps): string[] {
  const errors = validateRetailBase(props, 10);
  if (props.entries.length === 0) errors.push('请至少添加一个营销入口');
  if (props.entries.length > props.count)
    errors.push('营销入口数量超过展示数量');
  for (const entry of props.entries) {
    if (!entry.title.trim()) errors.push('营销入口标题不能为空');
    errors.push(...validateDecorationLink(entry.link));
  }
  return [...new Set(errors)];
}
