import type { Component } from 'vue';

import { componentRegistry } from '../page-designer/registry/component-registry';

export const componentsMap: Record<string, Component> = {};

for (const definition of Object.values(componentRegistry)) {
  componentsMap[definition.type] = definition.preview;
  componentsMap[`${definition.type}-setting`] = definition.settings;
}
