import type {
  DecorationDocument,
  DecorationSection,
  PageSettings,
  SectionStyle,
} from './types';

import { nanoid } from 'nanoid';

import { DEFAULT_SECTION_ID, DEFAULT_SECTION_TYPE } from './v3';

export function createDefaultPageSettings(): PageSettings {
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
    share: {
      description: '',
      imageUrl: '',
      title: '',
    },
  };
}

export function createDefaultSectionStyle(): SectionStyle {
  return {
    backgroundColor: '',
    backgroundImage: '',
    condition: 'always',
    horizontalScroll: false,
    marginX: 0,
    marginY: 0,
    paddingX: 0,
    paddingY: 0,
    radius: 0,
    sticky: false,
  };
}

/**
 * 卡片区块预设：一键把通栏区块变成带留白的圆角卡片。
 * 数值取自零售首页常见形态（12px 留白 + 16px 圆角 + 12px 内边距），
 * 运营可在控件里继续微调。
 */
export const CARD_SECTION_PRESET: Pick<
  SectionStyle,
  'marginX' | 'marginY' | 'paddingX' | 'paddingY' | 'radius'
> = {
  marginX: 12,
  marginY: 12,
  paddingX: 12,
  paddingY: 12,
  radius: 16,
};

export function createDefaultSection(): DecorationSection {
  return {
    components: [],
    id: DEFAULT_SECTION_ID,
    style: createDefaultSectionStyle(),
    type: DEFAULT_SECTION_TYPE,
  };
}

export function createEmptySection(idFactory: () => string = nanoid) {
  const section = createDefaultSection();
  section.id = idFactory();
  return section;
}

export function createDefaultDecorationDocument(): DecorationDocument {
  return {
    page: createDefaultPageSettings(),
    schemaVersion: 3,
    sections: [createDefaultSection()],
  };
}
