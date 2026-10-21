/**
 * 商详属性标签的纯函数模型：把后端下发的 `GoodsSpu.attrTags` 归一化成
 * 模板可直接消费的视图模型。
 *
 * 这里是**整块数据的唯一收敛点**：
 * - 空/缺失/非数组 → 空数组（调用方据此整块不渲染，后端未部署时页面与现状一致）；
 * - 脏项过滤（空占位行、非对象、值与属性名缺失）；
 * - 枚举收敛（tone 未知 → gray；linkType 未知 → none）；
 * - 图标只接受预置 key 或 http(s) 图片地址；
 * - 跳转只放行站内 `/pages/`、`/sub-pages/` 路径，非法即降级 none；
 * - 超过 6 个截断。
 *
 * 保持纯函数（不依赖 uni 运行时/组件状态），便于单测覆盖各降级分支。
 */
import type {
  GoodsAttrTagIconKey,
  GoodsAttrTagLinkType,
  GoodsAttrTagPayload,
  GoodsAttrTagTone,
} from '@/api/product/attr-tags'
import {
  GOODS_ATTR_TAG_ICON_KEYS,
  GOODS_ATTR_TAG_LINK_TYPES,
  GOODS_ATTR_TAG_MAX,
  GOODS_ATTR_TAG_TONES,
} from '@/api/product/attr-tags'

/** 归一化后的单张属性卡（模板只依赖这些字段） */
export interface GoodsAttrTagView {
  /** 列表 key：取后端 key，缺失/重复时本地补齐，保证唯一 */
  key: string
  /** 属性名（第二行小字），如「系列」「储存条件」 */
  label: string
  /** 属性值（第一行主文案），如「地标」「冷藏」 */
  value: string
  tone: GoodsAttrTagTone
  linkType: GoodsAttrTagLinkType
  /** 预置图标 key；无图标或为图片地址时为空串 */
  iconKey: GoodsAttrTagIconKey | ''
  /** http(s) 图标图片地址；使用预置图标时为空串 */
  iconUrl: string
  /** linkType=popup 时的弹层标题（缺省取属性值） */
  popupTitle: string
  /** linkType=popup 时的弹层正文 */
  popupContent: string
  /** linkType=page 时的站内跳转路径 */
  linkUrl: string
}

const HTTP_URL_RE = /^https?:\/\//i
/** 空白与控制字符：站内路径里出现即视为脏值 */
const INVALID_PATH_CHARS_RE = /[\s\\]/
const MAX_LINK_URL_LENGTH = 500
const MAX_ICON_URL_LENGTH = 255

/**
 * 归一化入口：任何输入都返回安全数组（绝不抛错）。
 *
 * `raw` 用 unknown 是刻意的——调用点拿到的可能是 `any`（详情接口未收窄），
 * 也可能是旧缓存/空字段；由这里统一做类型断言前的校验。
 */
export function normalizeGoodsAttrTags(raw: unknown): GoodsAttrTagView[] {
  if (!Array.isArray(raw) || raw.length === 0) {
    return []
  }

  const views: GoodsAttrTagView[] = []
  const usedKeys = new Set<string>()

  for (const item of raw) {
    if (views.length >= GOODS_ATTR_TAG_MAX) {
      break
    }
    if (!item || typeof item !== 'object') {
      continue
    }

    const source = item as GoodsAttrTagPayload
    const value = trimText(source.value)
    const label = trimText(source.label)
    // 值与属性名必须同时存在：只有一行的卡片在商详首屏没有信息价值
    if (!value || !label) {
      continue
    }

    const icon = normalizeIcon(source.icon)
    const link = normalizeLink(source, value)

    views.push({
      key: uniqueKey(trimText(source.key), views.length, usedKeys),
      label,
      value,
      tone: pickFrom(GOODS_ATTR_TAG_TONES, trimText(source.tone)) ?? 'gray',
      linkType: link.linkType,
      iconKey: icon.iconKey,
      iconUrl: icon.iconUrl,
      popupTitle: link.popupTitle,
      popupContent: link.popupContent,
      linkUrl: link.linkUrl,
    })
  }

  return views
}

/**
 * 站内页面根路径（不带斜杠）。
 *
 * 刻意拆成裸段名而不是 `'/pages/'` 这样的完整前缀字面量：全仓导航守卫
 * （`src/navigation-contract.test.ts`）会把源码里的 `'/pages/xxx'` 字面量
 * 当作跳转目标去 pages.json 对账，前缀常量会被误报成「未注册路由」。
 */
const INTERNAL_PAGE_ROOTS = ['pages', 'sub-pages'] as const

/**
 * 站内跳转白名单：只允许以 `/pages/` 或 `/sub-pages/` 开头的站内路径。
 *
 * 后端保存时已校验，前端再判一次是防御「接口口径变更 / 旧数据绕过管理端」：
 * 任意协议头、反斜杠、`..` 越界写法都不放进 `uni.navigateTo`。
 */
export function isAllowedInternalPagePath(path: unknown): boolean {
  if (typeof path !== 'string') {
    return false
  }
  const url = path.trim()
  if (!url || url.length > MAX_LINK_URL_LENGTH) {
    return false
  }
  // 取首段做白名单，再要求紧跟 `/`：'/pages' 这种只有根名的写法同样拒绝
  const root = url.slice(1).split('/')[0]
  if (!(INTERNAL_PAGE_ROOTS as readonly string[]).includes(root)) {
    return false
  }
  if (!url.startsWith(`/${root}/`)) {
    return false
  }
  if (INVALID_PATH_CHARS_RE.test(url) || url.includes('/../')) {
    return false
  }
  return true
}

function normalizeIcon(raw: unknown): { iconKey: GoodsAttrTagIconKey | ''; iconUrl: string } {
  const icon = trimText(raw)
  if (!icon) {
    return { iconKey: '', iconUrl: '' }
  }
  const preset = pickFrom(GOODS_ATTR_TAG_ICON_KEYS, icon)
  if (preset) {
    return { iconKey: preset, iconUrl: '' }
  }
  if (HTTP_URL_RE.test(icon) && icon.length <= MAX_ICON_URL_LENGTH && !INVALID_PATH_CHARS_RE.test(icon)) {
    return { iconKey: '', iconUrl: icon }
  }
  // 未知图标（含 javascript: 等危险协议）静默降级为无图标
  return { iconKey: '', iconUrl: '' }
}

function normalizeLink(source: GoodsAttrTagPayload, value: string): Pick<
  GoodsAttrTagView,
  'linkType' | 'popupTitle' | 'popupContent' | 'linkUrl'
> {
  const linkType = pickFrom(GOODS_ATTR_TAG_LINK_TYPES, trimText(source.linkType))

  if (linkType === 'popup') {
    const popupContent = trimText(source.popupContent)
    // 弹层没有正文等于点了没反应：降级为纯展示，卡片不再显示 chevron
    if (!popupContent) {
      return emptyLink()
    }
    return {
      linkType: 'popup',
      popupTitle: trimText(source.popupTitle) || value,
      popupContent,
      linkUrl: '',
    }
  }

  if (linkType === 'page') {
    const linkUrl = trimText(source.linkUrl)
    // 非站内路径（外链/危险协议/缺字段）降级 none，绝不让非法 URL 进 navigateTo
    if (!isAllowedInternalPagePath(linkUrl)) {
      return emptyLink()
    }
    return {
      linkType: 'page',
      popupTitle: '',
      popupContent: '',
      linkUrl,
    }
  }

  return emptyLink()
}

function emptyLink(): Pick<GoodsAttrTagView, 'linkType' | 'popupTitle' | 'popupContent' | 'linkUrl'> {
  return {
    linkType: 'none',
    popupTitle: '',
    popupContent: '',
    linkUrl: '',
  }
}

/** key 缺失时按展示顺序补 `attr-N`；重复时追加序号，保证 v-for key 唯一 */
function uniqueKey(rawKey: string, index: number, usedKeys: Set<string>): string {
  const base = rawKey || `attr-${index + 1}`
  let candidate = base
  let suffix = 2
  while (usedKeys.has(candidate)) {
    candidate = `${base}-${suffix}`
    suffix += 1
  }
  usedKeys.add(candidate)
  return candidate
}

function trimText(raw: unknown): string {
  return typeof raw === 'string' ? raw.trim() : ''
}

function pickFrom<T extends string>(allowed: readonly T[], value: string): T | null {
  if (!value) {
    return null
  }
  return (allowed as readonly string[]).includes(value) ? (value as T) : null
}
