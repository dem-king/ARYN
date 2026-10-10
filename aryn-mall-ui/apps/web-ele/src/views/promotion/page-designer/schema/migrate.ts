import type {
  DecorationComponent,
  DecorationDocument,
  DecorationSection,
  PageSettings,
  SectionCondition,
  SectionConditionGroup,
  SectionConditionRule,
  SectionStyle,
} from './types';

import { cloneDesignerValue } from './clone';
import {
  createDefaultPageSettings,
  createDefaultSectionStyle,
} from './defaults';
import { DECORATION_SCHEMA_VERSION_V3 } from './types';
import { DEFAULT_SECTION_ID, DEFAULT_SECTION_TYPE } from './v3';

interface UnknownRecord {
  [key: string]: unknown;
}

const LEGACY_COMPONENT_TYPE_ALIASES: Record<string, string> = {
  imageAd: 'image-ad',
};

function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function cloneRecord(value: unknown): Record<string, unknown> {
  if (!isRecord(value)) return {};
  return cloneDesignerValue(value);
}

function migrateComponent(value: unknown): DecorationComponent | null {
  if (!isRecord(value) || typeof value.id !== 'string') return null;
  if (typeof value.type !== 'string') return null;

  const props = 'props' in value ? value.props : value.formData;
  return {
    id: value.id,
    props: cloneRecord(props),
    type: LEGACY_COMPONENT_TYPE_ALIASES[value.type] ?? value.type,
    version:
      typeof value.version === 'number' && value.version > 0
        ? value.version
        : 1,
  };
}

function migrateComponents(values: unknown): DecorationComponent[] {
  return Array.isArray(values)
    ? values
        .map((component) => migrateComponent(component))
        .filter((component): component is DecorationComponent => !!component)
    : [];
}

/**
 * 归一化区块条件：
 * - 旧字符串简写（always/guest/login）原样保留；
 * - 合法的组合条件对象深拷贝透传，规则字段缺省时补默认值；
 * - 未知格式（含 'loginXxx'、空对象等）归一为 'always'。
 */
function normalizeCondition(value: unknown): SectionCondition {
  if (value === 'always' || value === 'guest' || value === 'login') {
    return value;
  }
  if (!isRecord(value)) return 'always';

  const rawRules = value.rules;
  if (!Array.isArray(rawRules)) return 'always';

  const rules: SectionConditionRule[] = [];
  for (const rawRule of rawRules) {
    if (!isRecord(rawRule) || typeof rawRule.type !== 'string') continue;
    switch (rawRule.type) {
      case 'guest':
      case 'login': {
        rules.push({ type: rawRule.type });
        break;
      }
      case 'memberLevel': {
        rules.push({
          type: 'memberLevel',
          memberLevelIds: Array.isArray(rawRule.memberLevelIds)
            ? rawRule.memberLevelIds.filter(
                (id): id is string => typeof id === 'string',
              )
            : [],
        });
        break;
      }
      case 'timeRange': {
        rules.push({
          type: 'timeRange',
          startTime:
            typeof rawRule.startTime === 'string' ? rawRule.startTime : '',
          endTime: typeof rawRule.endTime === 'string' ? rawRule.endTime : '',
        });
        break;
      }
      case 'userTag': {
        rules.push({
          type: 'userTag',
          userTagIds: Array.isArray(rawRule.userTagIds)
            ? rawRule.userTagIds.filter(
                (id): id is string => typeof id === 'string',
              )
            : [],
        });
        break;
      }
      default: {
        // 未知规则类型丢弃，保证旧文档向前兼容
        break;
      }
    }
  }

  const group: SectionConditionGroup = {
    logic: value.logic === 'or' ? 'or' : 'and',
    rules,
  };
  return group;
}

/**
 * 区块长度字段归一化：负数与非法值回落默认值，超大值封顶。
 * 与 C 端 `diy/schema/migrate.ts` 保持同口径，避免编辑器预览与实机渲染不一致。
 */
const SECTION_LENGTH_MAX = 200;

function normalizeSectionLength(value: unknown, fallback = 0): number {
  if (typeof value !== 'number' || !Number.isFinite(value)) return fallback;
  return Math.min(Math.max(value, 0), SECTION_LENGTH_MAX);
}

function migrateSectionStyle(value: unknown): SectionStyle {
  const defaults = createDefaultSectionStyle();
  if (!isRecord(value)) return defaults;
  const condition = normalizeCondition(value.condition);
  const merged = {
    ...defaults,
    ...cloneRecord(value),
    condition,
  } as SectionStyle;
  merged.marginX = normalizeSectionLength(value.marginX);
  merged.marginY = normalizeSectionLength(value.marginY);
  merged.paddingX = normalizeSectionLength(value.paddingX);
  merged.paddingY = normalizeSectionLength(value.paddingY);
  merged.radius = normalizeSectionLength(value.radius);
  return merged;
}

function migrateSections(record: UnknownRecord): DecorationSection[] {
  if (Array.isArray(record.sections)) {
    const sections: DecorationSection[] = [];
    for (const section of record.sections) {
      if (!isRecord(section) || typeof section.id !== 'string') continue;
      sections.push({
        components: migrateComponents(section.components),
        id: section.id,
        name: typeof section.name === 'string' ? section.name : undefined,
        style: migrateSectionStyle(section.style),
        type:
          typeof section.type === 'string' && section.type
            ? section.type
            : DEFAULT_SECTION_TYPE,
      });
    }
    if (sections.length > 0) return sections;
  }
  // v1/v2 扁平文档与旧 formData 组件：包进唯一默认区块
  return [
    {
      components: migrateComponents(record.components),
      id: DEFAULT_SECTION_ID,
      style: createDefaultSectionStyle(),
      type: DEFAULT_SECTION_TYPE,
    },
  ];
}

function migratePageSettings(value: unknown): PageSettings {
  const defaults = createDefaultPageSettings();
  if (!isRecord(value)) return defaults;

  return {
    ...defaults,
    ...cloneRecord(value),
    navigation: {
      ...defaults.navigation,
      ...(isRecord(value.navigation) ? cloneRecord(value.navigation) : {}),
    },
    share: {
      ...defaults.share,
      ...(isRecord(value.share) ? cloneRecord(value.share) : {}),
    },
  } as PageSettings;
}

function parseContent(value: unknown): unknown {
  if (typeof value !== 'string') return value;
  try {
    return JSON.parse(value) as unknown;
  } catch {
    return {};
  }
}

export function migratePageContent(input: unknown): DecorationDocument {
  const source = parseContent(input);
  const record = isRecord(source) ? source : {};

  return {
    page: migratePageSettings(record.page),
    schemaVersion: DECORATION_SCHEMA_VERSION_V3,
    sections: migrateSections(record),
    // 页面引用的主题令牌透传：预览画布据此应用页面级主题（未引用时不产出该字段）
    ...(typeof record.themeRef === 'string' && record.themeRef
      ? { themeRef: record.themeRef }
      : {}),
  };
}
