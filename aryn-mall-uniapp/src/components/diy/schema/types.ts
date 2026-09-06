export const DECORATION_SCHEMA_VERSION = 2 as const

export type DecorationLinkType
  = | 'activity'
    | 'category'
    | 'coupon'
    | 'custom'
    | 'customer-service'
    | 'goods'
    | 'mini-program'
    | 'page'

export interface DecorationLink {
  params: Record<string, string>
  path: string
  targetId?: string
  type: DecorationLinkType
}

export interface DecorationComponent<TProps extends Record<string, unknown> = Record<string, unknown>> {
  id: string
  props: TProps
  type: string
  version: number
}

export interface PageSettings {
  backgroundColor: string
  backgroundImage: string
  enablePullDownRefresh: boolean
  navigation: {
    backgroundColor: string
    textColor: string
    title: string
    visible: boolean
  }
  share: {
    description: string
    imageUrl: string
    title: string
  }
}

export interface DecorationDocument {
  components: DecorationComponent[]
  page: PageSettings
  schemaVersion: typeof DECORATION_SCHEMA_VERSION
  sections: DecorationSection[]
}

/**
 * Schema v3：管理端发布契约（页面 -> 区块 -> 组件）。
 * 移动端只消费已发布快照：components 为拍平后的渲染列表，
 * sections 保留区块背景/间距/横滑/吸顶/条件显示等包装信息。
 * terminalOverrides/themeRef 为后续多终端与主题能力的预留字段。
 */
export const DECORATION_SCHEMA_VERSION_V3 = 3 as const

export type DecorationTerminal = 'admin' | 'h5' | 'weapp'

export type SectionCondition = 'always' | 'guest' | 'login'

export interface SectionStyle {
  backgroundColor: string
  backgroundImage: string
  condition: SectionCondition
  horizontalScroll: boolean
  paddingY: number
  sticky: boolean
}

export interface DecorationSection {
  components: DecorationComponent[]
  id: string
  name?: string
  style: SectionStyle
  type: string
}

export interface DecorationDocumentV3 {
  analytics?: { campaignId?: string, enabled: boolean }
  page: PageSettings
  schemaVersion: typeof DECORATION_SCHEMA_VERSION_V3
  sections: DecorationSection[]
  terminalOverrides?: Partial<Record<DecorationTerminal, Partial<PageSettings>>>
  themeRef?: string
}
