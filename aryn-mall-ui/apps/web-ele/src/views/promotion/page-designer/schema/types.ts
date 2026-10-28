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

export type NavigationScrollMode = 'fade' | 'none';
export type NavigationScrollSurface = 'custom' | 'frosted' | 'theme';

/**
 * 导航栏滚动渐显（沉浸式导航）配置。
 *
 * 缺省 `mode='none'` 表示保持现状（存量页面零变化）；所有字段由
 * `normalizeNavigationScrollEffect()` 归一化，渲染端不信任草稿存值。
 * 与 C 端 `components/diy/schema/types.ts` 同构。
 */
export interface NavigationScrollEffect {
  mode: NavigationScrollMode;
  /** 目标底色：磨砂白 / 商城主题导航色 / 自定义色 */
  surface: NavigationScrollSurface;
  /** surface='custom' 时生效；非法值回落磨砂白 */
  surfaceColor: string;
  /** 渐显距离 px（40–400，缺省 140） */
  distance: number;
  /** 目标态是否毛玻璃（仅 surface='frosted' 生效） */
  blur: boolean;
}

export interface NavigationSettings {
  backgroundColor: string;
  /**
   * 导航栏背景来源：
   *   · 'custom'（缺省）—— 用 backgroundColor / 商城主题导航色；
   *   · 'followFirstSection' —— 跟随首个有内容的区块：渐变取顶边起始色，
   *     左右（to right）渐变整段复用；文字颜色默认按背景自动取黑/白。
   * 用于让导航栏与首屏区块的渐变连成一条，不必两处手动对齐色值。
   */
  backgroundMode?: 'custom' | 'followFirstSection';
  /** 跟随首个区块时是否自动取对比文字色；false 时用 textColor */
  textColorAuto?: boolean;
  textColor: string;
  title: string;
  visible: boolean;
  /** 滚动渐显（沉浸式导航）配置；缺省 undefined 视为关闭 */
  scrollEffect?: NavigationScrollEffect;
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
  /**
   * 发布时固化的主题快照主色/辅色（迁移器从 themeSnapshot 提取，仅发布版内容携带）。
   * 与 C 端 diy 渲染器同一规则：字段存在表示页面自带主题，预览画布改用快照色、
   * 不再叠加调用方注入的商城默认主题；编辑器草稿不携带（toV3Document 不序列化）。
   */
  themePrimaryColor?: string;
  themeSecondaryColor?: string;
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
 * 区块背景渐变。与组件通用样式（commonStyle 的 bgColorDirection /
 * bgStartColor / bgEndColor）同语义，供运营把区块底色做成渐变色带。
 * 优先级：backgroundImage（背景图）> backgroundGradient > backgroundColor。
 */
export interface SectionBackgroundGradient {
  /** CSS 渐变方向，只接受白名单值：'to right' | 'to bottom' | '135deg' | '225deg' */
  direction: string;
  endColor: string;
  startColor: string;
}

/**
 * 区块容器样式。长度单位一律为 px，与画布（375px 手机壳）一致，
 * 也与 C 端 `diy/schema/types.ts` 的 SectionStyle 保持同构。
 */
export interface SectionStyle {
  backgroundColor: string;
  /** 背景渐变；起止色都为空视为未启用（回退 backgroundColor） */
  backgroundGradient?: SectionBackgroundGradient;
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
