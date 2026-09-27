import type { DecorationDocument } from '../schema/types';

import {
  createDefaultPageSettings,
  createDefaultSectionStyle,
} from '../schema/defaults';
import {
  componentRegistry,
  legacyComponentTypes,
  retailComponentTypes,
} from '../registry/component-registry';

const allComponentTypes = [...legacyComponentTypes, ...retailComponentTypes];

/**
 * custom-html 的默认 props 刻意留空（空块不允许发布，见 component-registry.test.ts），
 * 而本 fixture 要同时充当「内容合法的模板」被 template-dialog 直接提交，
 * 因此这里补一段最小展示型 HTML，让 fixture 整体通过校验。
 */
const samplePropsByType: Partial<Record<string, Record<string, unknown>>> = {
  'custom-html': {
    height: 300,
    html: '<div style="padding: 12px; text-align: center;">示例自定义内容</div>',
  },
};

/**
 * 全组件 fixture：v3 区块模型，组件清单由注册表推导（全部 legacy + retail 组件挂在默认区块下）。
 * 保留 v2 命名以延续既有契约测试语义。
 */
export const allComponentsV2: DecorationDocument = {
  page: createDefaultPageSettings(),
  schemaVersion: 3,
  sections: [
    {
      components: allComponentTypes.map((type) => ({
        id: `fixture-${type}`,
        props: {
          ...componentRegistry[type].createDefaultProps(),
          ...samplePropsByType[type],
        },
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
