import type {
  DecorationComponent,
  DecorationDocument,
  DecorationSection,
  PageSettings,
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

function migrateSectionStyle(value: unknown): SectionStyle {
  const defaults = createDefaultSectionStyle();
  if (!isRecord(value)) return defaults;
  const condition =
    value.condition === 'login' || value.condition === 'guest'
      ? value.condition
      : defaults.condition;
  return {
    ...defaults,
    ...cloneRecord(value),
    condition,
  } as SectionStyle;
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
  };
}
