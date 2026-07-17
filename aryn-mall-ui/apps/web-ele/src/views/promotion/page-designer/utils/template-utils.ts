import type { DecorationDocument } from '../schema/types';

import { nanoid } from 'nanoid';

import { cloneDesignerValue } from '../schema/clone';

export function cloneTemplateDocument(
  template: DecorationDocument,
  idFactory: () => string = nanoid,
): DecorationDocument {
  const cloned = cloneDesignerValue(template);
  cloned.components = cloned.components.map((component) => ({
    ...component,
    id: idFactory(),
  }));
  return cloned;
}
