import type { DecorationDocument } from '../schema/types';

import { createDefaultPageSettings, createDefaultSectionStyle } from '../schema/defaults';
import {
  componentRegistry,
  legacyComponentTypes,
  retailComponentTypes,
} from '../registry/component-registry';

const allComponentTypes = [...legacyComponentTypes, ...retailComponentTypes];

/**
 * 全组件 fixture：v3 区块模型（17 个组件挂在默认区块下）。
 * 保留 v2 命名以延续既有契约测试语义。
 */
export const allComponentsV2: DecorationDocument = {
  page: createDefaultPageSettings(),
  schemaVersion: 3,
  sections: [
    {
      components: allComponentTypes.map((type) => ({
        id: `fixture-${type}`,
        props: componentRegistry[type].createDefaultProps(),
        type,
        version: componentRegistry[type].version,
      })),
      id: 'section-root',
      style: createDefaultSectionStyle(),
      type: 'default',
    },
  ],
};

export const allLegacyComponentsV2 = allComponentsV2;
