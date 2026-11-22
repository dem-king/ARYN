import { describe, expect, it } from 'vitest';

import {
  newTextModel,
  providerEditor,
  serializeConfiguration,
  serializeQuickTextConfiguration,
} from './ai-config-form';

describe('aI configuration credentials', () => {
  it('fills key editor with KEEP and never with a stored credential reference', () => {
    const editor = providerEditor({
      id: 'a',
      name: 'A',
      endpoint: 'https://a.example/v1',
      protocol: 'OPENAI_COMPATIBLE',
      enabled: true,
      credentialVersion: 'immutable-key-version',
    });
    expect(editor.apiKey).toBe('');
    expect(editor.keyOperation).toBe('KEEP');
    expect(
      JSON.stringify(serializeConfiguration(3, [editor], [], {})),
    ).not.toContain('immutable-key-version');
  });
  it('only REPLACE sends a real key; masks and unadapted abilities fail', () => {
    const editor = providerEditor({
      id: 'a',
      name: 'A',
      endpoint: 'https://a.example/v1',
      protocol: 'OPENAI_COMPATIBLE',
      enabled: true,
      credentialVersion: null,
    });
    editor.keyOperation = 'REPLACE';
    editor.apiKey = '******';
    expect(() => serializeConfiguration(1, [editor], [], {})).toThrow();
    editor.apiKey = 'new-key';
    expect(
      serializeConfiguration(1, [editor], [], {}).providers[0]?.apiKey,
    ).toBe('new-key');
    editor.keyOperation = 'CLEAR';
    expect(
      serializeConfiguration(1, [editor], [], {}).providers[0]?.apiKey,
    ).toBeUndefined();
  });

  it('assigns one new model to selected uses and keeps other bindings and credentials', () => {
    const existing = providerEditor({
      id: 'existing',
      name: '现有供应商',
      endpoint: 'https://existing.example/v1',
      protocol: 'OPENAI_COMPATIBLE',
      enabled: true,
      credentialVersion: 'stored-key-version',
    });
    const model = { ...newTextModel(existing.id), model: 'existing-model' };
    const command = serializeQuickTextConfiguration(
      7,
      [existing],
      [model],
      {
        PROCUREMENT_PARSE: model.id,
        MARKETING_DRAFT: model.id,
        CATALOG_EMBEDDING: 'embedding',
      },
      {
        apiKey: 'fixture-key',
        endpoint: 'https://example.com/v1',
        model: ' text-model ',
        name: '新供应商',
        usages: ['PROCUREMENT_PARSE', 'SKU_RERANK'],
      },
    );

    const [retained, added] = command.models;
    expect(retained).toEqual(model);
    expect(added).toMatchObject({
      model: 'text-model',
      capability: 'STRUCTURED_TEXT',
    });
    expect(command.expectedRevision).toBe(7);
    expect(command.bindings).toEqual({
      CATALOG_EMBEDDING: 'embedding',
      MARKETING_DRAFT: model.id,
      PROCUREMENT_PARSE: added?.id,
      SKU_RERANK: added?.id,
    });
    expect(command.providers[0]).toMatchObject({ keyOperation: 'KEEP' });
    expect(command.providers[0]).not.toHaveProperty('apiKey');
    expect(command.providers[1]).toMatchObject({
      id: added?.providerId,
      keyOperation: 'REPLACE',
    });
  });
});
