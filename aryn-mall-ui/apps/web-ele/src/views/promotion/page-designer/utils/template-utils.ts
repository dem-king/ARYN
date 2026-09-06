import type { DecorationDocument } from '../schema/types';

import { nanoid } from 'nanoid';

import { cloneDesignerValue } from '../schema/clone';

export function cloneTemplateDocument(
  template: DecorationDocument,
  idFactory: () => string = nanoid,
): DecorationDocument {
  const cloned = cloneDesignerValue(template);
  cloned.sections = cloned.sections.map((section) => ({
    ...section,
    components: section.components.map((component) => ({
      ...component,
      id: idFactory(),
    })),
  }));
  return cloned;
}
