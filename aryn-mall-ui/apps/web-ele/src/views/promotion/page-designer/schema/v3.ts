import type { DecorationDocument, DecorationDocumentV3 } from './types';

import { cloneDesignerValue } from './clone';
import { DECORATION_SCHEMA_VERSION_V3 } from './types';

/**
 * 编辑器默认区块标识与类型；迁移器把 v1/v2 扁平文档包进该区块。
 * Phase 2 起编辑器原生支持多区块（页面 -> 区块 -> 组件，最多三层）。
 */
export const DEFAULT_SECTION_ID = 'section-root';
export const DEFAULT_SECTION_TYPE = 'default';

/**
 * 生成保存/发布/申请快照使用的 v3 契约文档（编辑器模型即 v3，这里补齐主题引用）。
 */
export function toV3Document(
  document: DecorationDocument,
  options?: { themeRef?: string },
): DecorationDocumentV3 {
  const v3Document: DecorationDocumentV3 = {
    page: cloneDesignerValue(document.page),
    schemaVersion: DECORATION_SCHEMA_VERSION_V3,
    sections: cloneDesignerValue(document.sections),
  };
  if (options?.themeRef) {
    v3Document.themeRef = options.themeRef;
  }
  return v3Document;
}
