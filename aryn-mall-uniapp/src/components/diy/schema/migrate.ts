import type { DecorationComponent, DecorationDocument, DecorationSection, PageSettings, SectionStyle } from './types'
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

function createDefaultSectionStyle(): SectionStyle {
  return {
    backgroundColor: '',
    backgroundImage: '',
    condition: 'always',
    horizontalScroll: false,
    paddingY: 0,
    sticky: false,
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

function migrateComponents(values: unknown): DecorationComponent[] {
  return Array.isArray(values)
    ? values.map(migrateComponent).filter((item): item is DecorationComponent => !!item)
    : []
}

function migrateSectionStyle(value: unknown): SectionStyle {
  const defaults = createDefaultSectionStyle()
  if (!isRecord(value))
    return defaults
  const condition
    = value.condition === 'login' || value.condition === 'guest' ? value.condition : defaults.condition
  return { ...defaults, ...cloneRecord(value), condition } as SectionStyle
}

function migrateSections(source: UnknownRecord): DecorationSection[] {
  if (Array.isArray(source.sections)) {
    const sections: DecorationSection[] = []
    for (const section of source.sections) {
      if (!isRecord(section) || typeof section.id !== 'string')
        continue
      sections.push({
        components: migrateComponents(section.components),
        id: section.id,
        name: typeof section.name === 'string' ? section.name : undefined,
        style: migrateSectionStyle(section.style),
        type: typeof section.type === 'string' && section.type ? section.type : 'default',
      })
    }
    if (sections.length > 0)
      return sections
  }
  return [
    {
      components: migrateComponents(source.components),
      id: 'section-root',
      style: createDefaultSectionStyle(),
      type: 'default',
    },
  ]
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
  const sections = migrateSections(source)
  // 渲染列表：区块内组件按序拍平
  const components = sections.flatMap(section => section.components)
  // 发布时固化的主题快照优先于页面设置，保证线上视觉与发布时一致
  const theme = isRecord(source.themeSnapshot) ? source.themeSnapshot : null
  const themePageBackground
    = theme && typeof theme.pageBackgroundColor === 'string' && theme.pageBackgroundColor
      ? theme.pageBackgroundColor
      : null
  const themeNavigationColor
    = theme && typeof theme.navigationColor === 'string' && theme.navigationColor
      ? theme.navigationColor
      : null
  const themeNavigationTextColor
    = theme && typeof theme.navigationTextColor === 'string' && theme.navigationTextColor
      ? theme.navigationTextColor
      : null

  return {
    components,
    page: {
      ...defaults,
      ...cloneRecord(rawPage),
      ...(themePageBackground ? { backgroundColor: themePageBackground } : {}),
      navigation: {
        ...defaults.navigation,
        ...(isRecord(rawPage.navigation) ? cloneRecord(rawPage.navigation) : {}),
        ...(themeNavigationColor ? { backgroundColor: themeNavigationColor } : {}),
        ...(themeNavigationTextColor ? { textColor: themeNavigationTextColor } : {}),
      },
      share: {
        ...defaults.share,
        ...(isRecord(rawPage.share) ? cloneRecord(rawPage.share) : {}),
      },
    } as PageSettings,
    schemaVersion: DECORATION_SCHEMA_VERSION,
    sections,
  }
}
