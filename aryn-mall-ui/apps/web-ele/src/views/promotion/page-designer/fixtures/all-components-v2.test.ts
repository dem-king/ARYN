import { describe, expect, it } from 'vitest';

import {
  getComponentDefinition,
  legacyComponentTypes,
  retailComponentTypes,
} from '../registry/component-registry';
import { allComponentsV2 } from './all-components-v2';

describe('all-components v2 fixture', () => {
  it('contains every registered component with valid defaults', () => {
    const expectedTypes = [...legacyComponentTypes, ...retailComponentTypes];

    expect(allComponentsV2.schemaVersion).toBe(3);
    expect(allComponentsV2.sections).toHaveLength(1);
    const components = allComponentsV2.sections[0]?.components ?? [];
    expect(components).toHaveLength(17);
    expect(components.map(({ type }) => type).sort()).toEqual(
      expectedTypes.sort(),
    );
    for (const component of components) {
      const definition = getComponentDefinition(component.type);
      expect(definition).toBeDefined();
      expect(definition?.validate(component.props)).toEqual([]);
    }
  });
});
