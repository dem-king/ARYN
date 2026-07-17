import type {
  DecorationComponent,
  DecorationDocument,
  PageSettings,
} from './types';

import { cloneDesignerValue } from './clone';
import { createDefaultPageSettings } from './defaults';
import { DECORATION_SCHEMA_VERSION } from './types';

interface UnknownRecord {
  [key: string]: unknown;
}

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
    type: value.type,
    version:
      typeof value.version === 'number' && value.version > 0
        ? value.version
        : 1,
  };
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
  const components = Array.isArray(record.components)
    ? record.components
        .map((component) => migrateComponent(component))
        .filter((component): component is DecorationComponent => !!component)
    : [];

  return {
    components,
    page: migratePageSettings(record.page),
    schemaVersion: DECORATION_SCHEMA_VERSION,
  };
}
