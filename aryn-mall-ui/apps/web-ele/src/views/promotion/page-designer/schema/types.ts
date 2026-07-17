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
  components: DecorationComponent[];
  page: PageSettings;
  schemaVersion: typeof DECORATION_SCHEMA_VERSION;
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
