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
  /**
   * 是否跟随商城默认主题配色（页面背景/导航），缺省视为跟随；
   * false 表示页面设置里关闭了跟随，页面自存配色直接生效（自定义配色）。
   */
  followMallTheme?: boolean;
  navigation: NavigationSettings;
  share: ShareSettings;
}

export interface DecorationDocument {
  page: PageSettings;
  schemaVersion: typeof DECORATION_SCHEMA_VERSION_V3;
  sections: DecorationSection[];
  /**
   * 页面引用的主题令牌（v3 文档携带，迁移器透传）：
   * 预览画布据此应用「页面指定主题 > 商城默认主题」的有效主题色。
   * 编辑器工作态不走此字段（themeRef 由编辑器单独持有，保存时经 toV3Document 写入）。
   */
  themeRef?: string;
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

/**
 * 区块条件显示：向后兼容字符串简写（登录态三态），
 * 同时支持「登录态 + 会员等级 + 用户标签 + 时间段」的对象组合条件，
 * 契约与 C 端（aryn-mall-uniapp diy/schema）保持一致。
 */
export type SectionCondition =
  'always' | 'guest' | 'login' | SectionConditionGroup;

export interface SectionConditionGroup {
  logic: 'and' | 'or';
  rules: SectionConditionRule[];
}

export type SectionConditionRule =
  | { endTime: string; startTime: string; type: 'timeRange' }
  | { memberLevelIds: string[]; type: 'memberLevel' }
  | { type: 'guest' }
  | { type: 'login' }
  | { type: 'userTag'; userTagIds: string[] };

/**
 * 区块容器样式。长度单位一律为 px，与画布（375px 手机壳）一致，
 * 也与 C 端 `diy/schema/types.ts` 的 SectionStyle 保持同构。
 */
export interface SectionStyle {
  backgroundColor: string;
  backgroundImage: string;
  condition: SectionCondition;
  horizontalScroll: boolean;
  /** 左右外边距：让区块脱离通栏、成为一张卡片（配合 radius 使用） */
  marginX: number;
  /** 上下外边距：撑开区块之间的距离 */
  marginY: number;
  /** 左右内边距：让区块内的组件不贴边，避免盖住圆角 */
  paddingX: number;
  paddingY: number;
  /** 圆角：0 表示通栏直角区块 */
  radius: number;
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
