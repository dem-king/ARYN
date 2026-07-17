import type { DecorationComponent, DecorationDocument, PageSettings } from './types'
import { DECORATION_SCHEMA_VERSION } from './types'

type UnknownRecord = Record<string, unknown>

function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function cloneRecord(value: unknown): UnknownRecord {
  if (!isRecord(value))
    return {}
  return JSON.parse(JSON.stringify(value)) as UnknownRecord
}

function createDefaultPageSettings(): PageSettings {
  return {
    backgroundColor: '#f5f5f5',
    backgroundImage: '',
    enablePullDownRefresh: true,
    navigation: {
      backgroundColor: '#ffffff',
      textColor: '#000000',
      title: '',
      visible: true,
    },
    share: { description: '', imageUrl: '', title: '' },
  }
}

function migrateComponent(value: unknown): DecorationComponent | null {
  if (!isRecord(value) || typeof value.id !== 'string' || typeof value.type !== 'string')
    return null
  return {
    id: value.id,
    props: cloneRecord('props' in value ? value.props : value.formData),
    type: value.type,
    version: typeof value.version === 'number' && value.version > 0 ? value.version : 1,
  }
}

export function migratePageContent(input: unknown): DecorationDocument {
  let parsed = input
  if (typeof input === 'string') {
    try {
      parsed = JSON.parse(input) as unknown
    }
    catch {
      parsed = {}
    }
  }
  const source = isRecord(parsed) ? parsed : {}
  const defaults = createDefaultPageSettings()
  const rawPage = isRecord(source.page) ? source.page : {}
  const components = Array.isArray(source.components)
    ? source.components.map(migrateComponent).filter((item): item is DecorationComponent => !!item)
    : []

  return {
    components,
    page: {
      ...defaults,
      ...cloneRecord(rawPage),
      navigation: {
        ...defaults.navigation,
        ...(isRecord(rawPage.navigation) ? cloneRecord(rawPage.navigation) : {}),
      },
      share: {
        ...defaults.share,
        ...(isRecord(rawPage.share) ? cloneRecord(rawPage.share) : {}),
      },
    } as PageSettings,
    schemaVersion: DECORATION_SCHEMA_VERSION,
  }
}
