import { describe, expect, it } from 'vitest'
import { CATEGORY_BADGES, resolveCategoryBadge } from './category-badge'

/**
 * 服务端下发的文字角标：旧 key '1' 被运营改成了紫色「新」。
 * 类目角标配置化后，类目树 `extra.badge` 下发这份配置，C 端应优先采用。
 */
const SERVER_BADGE = { text: '新', bgColor: '#722ED1', color: '#FFFFFF' }

/** 图片角标：运营只配图，文案与配色都不下发 */
const SERVER_ICON_BADGE = { iconUrl: 'https://cdn.example.com/badge/recommend.png' }

/** 未配置尺寸时的默认尺寸（与改造前 CSS 写死的 28rpx 高 / 4rpx 圆角 / 19rpx 字号 / 5rpx 内边距等价） */
const DEFAULT_SIZE = { height: 28, borderRadius: 4, fontSize: 19, paddingX: 5 }

describe('category badge', () => {
  describe('旧语义兜底（旧后端 / 未迁移数据）', () => {
    it('maps the recommend badge to 荐', () => {
      expect(resolveCategoryBadge('1')?.text).toBe('荐')
    })

    it('maps the hot badge to 热', () => {
      expect(resolveCategoryBadge('2')?.text).toBe('热')
    })

    it('returns the complete default shape so legacy rendering is unchanged', () => {
      // 旧数据没有图片与尺寸配置：必须补齐默认字段，且默认尺寸与改造前 CSS 完全一致
      expect(resolveCategoryBadge('1')).toEqual({
        text: '荐',
        bgColor: '#00B578',
        color: '#FFFFFF',
        iconUrl: null,
        ...DEFAULT_SIZE,
      })
      expect(resolveCategoryBadge(2)).toEqual({
        text: '热',
        bgColor: '#FF4D2D',
        color: '#FFFFFF',
        iconUrl: null,
        ...DEFAULT_SIZE,
      })
    })

    it('returns null when there is no badge so callers render no node', () => {
      // 无角标时不返回空角标：左栏用 v-if 控制，多一层空盒子会改变对齐
      expect(resolveCategoryBadge('0')).toBeNull()
      expect(resolveCategoryBadge('')).toBeNull()
      expect(resolveCategoryBadge(null)).toBeNull()
      expect(resolveCategoryBadge(undefined)).toBeNull()
    })

    it('returns null for unknown values instead of guessing a semantic', () => {
      expect(resolveCategoryBadge('9')).toBeNull()
      expect(resolveCategoryBadge('hot')).toBeNull()
    })

    it('accepts both string and number, since JSON serialization varies', () => {
      expect(resolveCategoryBadge(1)?.text).toBe('荐')
      expect(resolveCategoryBadge(2)?.text).toBe('热')
    })

    it('keeps recommend and hot visually distinct', () => {
      // 两种角标必须能一眼区分：参考图里「荐」是绿、「热」是红
      expect(CATEGORY_BADGES[1].bgColor).not.toBe(CATEGORY_BADGES[2].bgColor)
    })

    it('exposes only the two supported semantics', () => {
      expect(Object.keys(CATEGORY_BADGES)).toEqual(['1', '2'])
    })
  })

  describe('服务端配置优先（类目角标配置化）', () => {
    it('uses the server badge instead of the legacy mapping for a known key', () => {
      // 旧 key '1' 被运营改成紫色「新」：以服务端配置为准，不能回退成本地绿「荐」
      expect(resolveCategoryBadge('1', SERVER_BADGE)).toEqual({
        ...SERVER_BADGE,
        iconUrl: null,
        ...DEFAULT_SIZE,
      })
    })

    it('renders a new server key that the local mapping does not know', () => {
      // 配置化后新建角标的 key 是雪花 id，本地没有映射，只能靠服务端下发渲染
      expect(resolveCategoryBadge('3f2a9c8d7e6b5a4c3d2e1f0a', SERVER_BADGE)).toEqual({
        ...SERVER_BADGE,
        iconUrl: null,
        ...DEFAULT_SIZE,
      })
    })

    it('trims surrounding whitespace from server fields', () => {
      const padded = { text: ' 新 ', bgColor: ' #722ED1 ', color: ' #FFFFFF ' }
      expect(resolveCategoryBadge('1', padded)).toEqual({
        ...SERVER_BADGE,
        iconUrl: null,
        ...DEFAULT_SIZE,
      })
    })
  })

  describe('尺寸与圆角（配置化新增字段）', () => {
    it('derives font size and horizontal padding from the configured height', () => {
      // 字号 = round(height * 0.68)，左右内边距 = round(height * 0.18)
      const badge = resolveCategoryBadge('1', { ...SERVER_BADGE, height: 40, borderRadius: 12 })!
      expect(badge.height).toBe(40)
      expect(badge.borderRadius).toBe(12)
      expect(badge.fontSize).toBe(27) // round(40 * 0.68) = 27
      expect(badge.paddingX).toBe(7) // round(40 * 0.18) = 7
    })

    it('clamps the derived metrics for the shortest and tallest allowed heights', () => {
      const shortest = resolveCategoryBadge('1', { ...SERVER_BADGE, height: 16 })!
      expect(shortest.fontSize).toBe(11) // round(16 * 0.68) = 10.88 → 11
      expect(shortest.paddingX).toBe(3) // round(16 * 0.18) = 2.88 → clamp 3

      const tallest = resolveCategoryBadge('1', { ...SERVER_BADGE, height: 96 })!
      expect(tallest.fontSize).toBe(48) // round(96 * 0.68) = 65 → clamp 48
      expect(tallest.paddingX).toBe(16) // round(96 * 0.18) = 17 → clamp 16
    })

    it('accepts the border radius boundaries, including square corners', () => {
      expect(resolveCategoryBadge('1', { ...SERVER_BADGE, borderRadius: 0 })!.borderRadius).toBe(0)
      expect(resolveCategoryBadge('1', { ...SERVER_BADGE, borderRadius: 48 })!.borderRadius).toBe(48)
    })

    it('falls back to defaults for out-of-range height and radius', () => {
      // 越界按脏数据处理：不 clamp 到边界，直接回退默认，避免渲染出运营意料外的角标
      for (const height of [15, 97, 0, -28])
        expect(resolveCategoryBadge('1', { ...SERVER_BADGE, height })!.height).toBe(28)
      for (const borderRadius of [49, -1, 1000])
        expect(resolveCategoryBadge('1', { ...SERVER_BADGE, borderRadius })!.borderRadius).toBe(4)
    })

    it('falls back to defaults for non-numeric height and radius values', () => {
      for (const height of ['36', null, true, Number.NaN, Number.POSITIVE_INFINITY, [36]])
        expect(resolveCategoryBadge('1', { ...SERVER_BADGE, height })!.height).toBe(28)
      for (const borderRadius of ['4', null, false, Number.NaN]) {
        expect(resolveCategoryBadge('1', { ...SERVER_BADGE, borderRadius })!.borderRadius).toBe(4)
      }
    })

    it('does not throw for dirty size values, and the badge keeps rendering', () => {
      // 接口异常时不能因为一个坏字段把整块左栏搞崩：解析必须兜住
      const dirty = { text: '新', bgColor: '#722ED1', color: '#FFFFFF', height: {}, borderRadius: [] }
      expect(resolveCategoryBadge('1', dirty)).toMatchObject({ text: '新', ...DEFAULT_SIZE })
    })
  })

  describe('图片角标（iconUrl 优先于文字）', () => {
    it('accepts an image-only badge and fills in default colors and size', () => {
      // 只配图、文字为空也是合法角标：文案留空串，配色回退默认
      expect(resolveCategoryBadge('3f2a9c8d7e6b5a4c3d2e1f0a', SERVER_ICON_BADGE)).toEqual({
        text: '',
        bgColor: '#00B578',
        color: '#FFFFFF',
        iconUrl: 'https://cdn.example.com/badge/recommend.png',
        ...DEFAULT_SIZE,
      })
    })

    it('keeps the text for an image badge that also has text', () => {
      const badge = resolveCategoryBadge('1', { ...SERVER_ICON_BADGE, text: '荐' })!
      expect(badge.iconUrl).toBe(SERVER_ICON_BADGE.iconUrl)
      expect(badge.text).toBe('荐')
    })

    it('prefers the image even when the badge also has colors', () => {
      // 渲染端按 iconUrl 是否存在分派：这里只保证 iconUrl 被透传，优先级由模板控制
      const badge = resolveCategoryBadge('1', { ...SERVER_ICON_BADGE, ...SERVER_BADGE })!
      expect(badge.iconUrl).toBe(SERVER_ICON_BADGE.iconUrl)
      expect(badge.bgColor).toBe('#722ED1')
    })

    it('trims the iconUrl and treats blank values as no image', () => {
      const badge = resolveCategoryBadge('3f2a9c8d7e6b5a4c3d2e1f0a', {
        iconUrl: ' https://cdn.example.com/x.png ',
      })!
      expect(badge.iconUrl).toBe('https://cdn.example.com/x.png')

      // 空白 iconUrl = 无图；既没有文案也没有配色时视为未配置
      expect(resolveCategoryBadge('3f2a9c8d7e6b5a4c3d2e1f0a', { iconUrl: '   ' })).toBeNull()
      // 旧 key 则回退旧语义
      expect(resolveCategoryBadge('1', { iconUrl: '' })?.text).toBe('荐')
    })

    it('honors the configured size and radius for an image badge', () => {
      const badge = resolveCategoryBadge('1', { ...SERVER_ICON_BADGE, height: 64, borderRadius: 0 })!
      expect(badge.height).toBe(64)
      expect(badge.borderRadius).toBe(0)
      expect(badge.fontSize).toBe(44) // round(64 * 0.68) = 43.52 → 44
      expect(badge.paddingX).toBe(12) // round(64 * 0.18) = 11.52 → 12
    })

    it('falls back to default colors when an image badge has blank colors', () => {
      const badge = resolveCategoryBadge('1', {
        iconUrl: 'https://cdn.example.com/x.png',
        bgColor: '  ',
        color: '',
      })!
      expect(badge.bgColor).toBe('#00B578')
      expect(badge.color).toBe('#FFFFFF')
    })

    it('treats an over-long iconUrl as dirty data', () => {
      // 512 是正常 CDN 地址的宽松上限，超长多半是脏数据；无文字/配色时等同未配置
      const tooLong = `https://cdn.example.com/${'a'.repeat(500)}.png`
      expect(tooLong.length).toBeGreaterThan(512)
      expect(resolveCategoryBadge('3f2a9c8d7e6b5a4c3d2e1f0a', { iconUrl: tooLong })).toBeNull()
      expect(resolveCategoryBadge('1', { iconUrl: tooLong })?.text).toBe('荐')
    })
  })

  describe('服务端 badge 非法时回退旧映射', () => {
    it('falls back to the legacy badge when server fields are missing', () => {
      expect(resolveCategoryBadge('1', { text: '新' })?.text).toBe('荐')
      expect(resolveCategoryBadge('1', { text: '新', bgColor: '#722ED1' })?.text).toBe('荐')
      expect(resolveCategoryBadge('1', { bgColor: '#722ED1', color: '#FFFFFF' })?.text).toBe('荐')
    })

    it('falls back when a server field is an empty or blank string', () => {
      const purple = { bgColor: '#722ED1', color: '#FFFFFF' }
      expect(resolveCategoryBadge('2', { ...purple, text: '' })?.text).toBe('热')
      expect(resolveCategoryBadge('2', { ...purple, text: '   ' })?.text).toBe('热')
      expect(resolveCategoryBadge('2', { ...purple, text: '热', bgColor: '' })?.text).toBe('热')
      expect(resolveCategoryBadge('2', { ...purple, text: '热', color: '' })?.text).toBe('热')
    })

    it('falls back when the server badge is not an object', () => {
      // 接口异常时 badge 可能是 null / undefined / 字符串 / 数组
      expect(resolveCategoryBadge('1', null)?.text).toBe('荐')
      expect(resolveCategoryBadge('1', undefined)?.text).toBe('荐')
      expect(resolveCategoryBadge('1', '荐')?.text).toBe('荐')
      expect(resolveCategoryBadge('1', ['荐'])?.text).toBe('荐')
    })

    it('falls back when only an invalid icon is provided', () => {
      // iconUrl 类型不对 / 是数组 → 无图；又缺文案配色 → 视为未配置
      expect(resolveCategoryBadge('2', { iconUrl: 123 })?.text).toBe('热')
      expect(resolveCategoryBadge('2', { iconUrl: ['https://cdn.example.com/x.png'] })?.text).toBe('热')
    })

    it('returns null for an unknown key when the server badge is invalid', () => {
      expect(resolveCategoryBadge('3f2a9c8d7e6b5a4c3d2e1f0a', { text: '新' })).toBeNull()
    })
  })

  describe('显式无角标', () => {
    it('returns null for badgeType 0 with a null server badge', () => {
      // 新后端「无角标」类目：badgeType='0' + badge=null
      expect(resolveCategoryBadge('0', null)).toBeNull()
    })

    it('ignores a server badge when badgeType is 0', () => {
      // 服务端正常不会下发；防御脏数据，避免「无角标」类目被渲染出角标
      expect(resolveCategoryBadge('0', SERVER_BADGE)).toBeNull()
      expect(resolveCategoryBadge(0, SERVER_BADGE)).toBeNull()
    })

    it('ignores an image badge when badgeType is 0', () => {
      expect(resolveCategoryBadge('0', SERVER_ICON_BADGE)).toBeNull()
      expect(resolveCategoryBadge(0, SERVER_ICON_BADGE)).toBeNull()
    })
  })
})
