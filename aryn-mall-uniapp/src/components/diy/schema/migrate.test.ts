import { describe, expect, it } from 'vitest'

import { DECORATION_SCHEMA_VERSION, DECORATION_SCHEMA_VERSION_V3 } from './types'
import { migratePageContent } from './migrate'

describe('diy schema migratePageContent', () => {
  it('keeps a v2 flat document intact', () => {
    const source = {
      components: [{ id: 'c1', props: { text: 'hi' }, type: 'notice', version: 1 }],
      page: { backgroundColor: '#ffffff' },
      schemaVersion: 2,
    }

    const result = migratePageContent(source)

    expect(result.schemaVersion).toBe(DECORATION_SCHEMA_VERSION)
    expect(result.components).toHaveLength(1)
    expect(result.components[0]?.id).toBe('c1')
    expect(result.page.backgroundColor).toBe('#ffffff')
  })

  it('flattens v3 sections into the rendering model', () => {
    const source = {
      page: { backgroundColor: '#ff5500' },
      schemaVersion: DECORATION_SCHEMA_VERSION_V3,
      sections: [
        {
          components: [
            { id: 'gap-1', props: { height: 10 }, type: 'gap', version: 1 },
            { id: 'bad' },
          ],
          id: 'section-root',
          type: 'default',
        },
        {
          components: [
            { id: 'notice-1', formData: { text: '公告' }, type: 'notice', version: 1 },
          ],
          id: 'section-2',
          type: 'default',
        },
      ],
    }

    const result = migratePageContent(source)

    expect(result.schemaVersion).toBe(DECORATION_SCHEMA_VERSION)
    expect(result.components.map(item => item.id)).toEqual(['gap-1', 'notice-1'])
    expect(result.components[1]?.props).toEqual({ text: '公告' })
    expect(result.page.backgroundColor).toBe('#ff5500')
  })

  it('falls back to flat components when v3 sections are absent', () => {
    const result = migratePageContent({
      components: [{ id: 'c1', props: {}, type: 'gap', version: 1 }],
      schemaVersion: 3,
    })

    expect(result.components.map(item => item.id)).toEqual(['c1'])
  })

  it('drops invalid entries from v3 sections and empty sections', () => {
    const result = migratePageContent({
      schemaVersion: 3,
      sections: [
        { components: 'broken', id: 's1' },
        { components: [{ type: 'gap' }], id: 's2' },
        'not-a-section',
      ],
    })

    expect(result.components).toEqual([])
  })

  it('parses a published v3 json string snapshot', () => {
    const snapshot = JSON.stringify({
      page: {},
      schemaVersion: 3,
      sections: [
        {
          components: [{ id: 'c1', props: {}, type: 'gap', version: 1 }],
          id: 's1',
          type: 'default',
        },
      ],
    })

    const result = migratePageContent(snapshot)

    expect(result.components[0]?.id).toBe('c1')
  })

  it('applies published theme snapshot over page settings', () => {
    const result = migratePageContent({
      page: {
        backgroundColor: '#ffffff',
        navigation: { backgroundColor: '#eeeeee', textColor: '#000000' },
      },
      schemaVersion: 3,
      sections: [],
      themeSnapshot: {
        navigationColor: '#111111',
        navigationTextColor: '#ffffff',
        pageBackgroundColor: '#fff7f2',
      },
    })

    expect(result.page.backgroundColor).toBe('#fff7f2')
    expect(result.page.navigation.backgroundColor).toBe('#111111')
    expect(result.page.navigation.textColor).toBe('#ffffff')
  })

  it('keeps page settings when theme snapshot is absent or blank', () => {
    const withPage = {
      page: { backgroundColor: '#abcdef' },
      schemaVersion: 3,
      sections: [],
      themeSnapshot: {},
    }
    const themed = migratePageContent(withPage)
    expect(themed.page.backgroundColor).toBe('#abcdef')

    const unthemed = migratePageContent({
      page: { backgroundColor: '#abcdef' },
      schemaVersion: 3,
      sections: [],
    })
    expect(unthemed.page.backgroundColor).toBe('#abcdef')
  })
})
