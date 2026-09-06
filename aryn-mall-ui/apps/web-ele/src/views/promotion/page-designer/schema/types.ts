export const DECORATION_SCHEMA_VERSION = 2 as const;

export interface DecorationComponent<
  TProps extends Record<string, unknown> = Record<string, unknown>,
> {
  id: string;
  props: TProps;
  type: string;
  version: number;
}

export type DecorationLinkType =
  | 'activity'
  | 'category'
  | 'coupon'
  | 'custom'
  | 'customer-service'
  | 'goods'
  | 'mini-program'
  | 'page';

export interface DecorationLink {
  params: Record<string, string>;
  path: string;
  targetId?: string;
  type: DecorationLinkType;
}

export interface NavigationSettings {
  backgroundColor: string;
  textColor: string;
  title: string;
  visible: boolean;
}

export interface ShareSettings {
  description: string;
  imageUrl: string;
  title: string;
}

export interface PageSettings {
  backgroundColor: string;
  backgroundImage: string;
  enablePullDownRefresh: boolean;
  navigation: NavigationSettings;
  share: ShareSettings;
}

export interface DecorationDocument {
  page: PageSettings;
  schemaVersion: typeof DECORATION_SCHEMA_VERSION_V3;
  sections: DecorationSection[];
}

/**
 * Schema v3：页面 -> 区块 -> 组件的文档契约。
 * 编辑器以 v3 区块模型工作，最多允许 页面 -> 区块 -> 组件 三层，禁止更深嵌套。
 * terminalOverrides 为多终端差异化预留字段。
 */
export const DECORATION_SCHEMA_VERSION_V3 = 3 as const;

export type DecorationTerminal = 'admin' | 'h5' | 'weapp';

/** 旧版扁平文档常量，仅用于历史测试与迁移器输入识别 */
export const LEGACY_FLAT_SCHEMA_VERSION = 2 as const;

export type SectionCondition = 'always' | 'guest' | 'login';

export interface SectionStyle {
  backgroundColor: string;
  backgroundImage: string;
  condition: SectionCondition;
  horizontalScroll: boolean;
  paddingY: number;
  sticky: boolean;
}

export interface DecorationSection {
  components: DecorationComponent[];
  id: string;
  name?: string;
  style: SectionStyle;
  type: string;
}

export interface DecorationDocumentV3 extends DecorationDocument {
  analytics?: { campaignId?: string; enabled: boolean };
  terminalOverrides?: Partial<
    Record<DecorationTerminal, Partial<PageSettings>>
  >;
  themeRef?: string;
}

export interface ComponentDefinition<
  TProps extends Record<string, unknown> = Record<string, unknown>,
> {
  category: string;
  createDefaultProps: () => TProps;
  label: string;
  type: string;
  validate: (props: TProps) => string[];
  version: number;
}
