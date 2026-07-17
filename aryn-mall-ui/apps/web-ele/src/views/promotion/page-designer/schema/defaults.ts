import type { DecorationDocument, PageSettings } from './types';

import { DECORATION_SCHEMA_VERSION } from './types';

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

export function createDefaultDecorationDocument(): DecorationDocument {
  return {
    components: [],
    page: createDefaultPageSettings(),
    schemaVersion: DECORATION_SCHEMA_VERSION,
  };
}
