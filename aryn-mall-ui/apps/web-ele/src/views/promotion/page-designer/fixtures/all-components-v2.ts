import type { DecorationDocument } from '../schema/types';

import { createDefaultPageSettings } from '../schema/defaults';
import {
  componentRegistry,
  legacyComponentTypes,
  retailComponentTypes,
} from '../registry/component-registry';

const allComponentTypes = [...legacyComponentTypes, ...retailComponentTypes];

export const allComponentsV2: DecorationDocument = {
  components: allComponentTypes.map((type) => ({
    id: `fixture-${type}`,
    props: componentRegistry[type].createDefaultProps(),
    type,
    version: componentRegistry[type].version,
  })),
  page: createDefaultPageSettings(),
  schemaVersion: 2,
};

export const allLegacyComponentsV2 = allComponentsV2;
