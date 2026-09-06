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
    paddingY: 0,
    sticky: false,
  };
}

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
