import type { AiModel, AiProvider, AiUsage } from '#/api/upms/ai-config';

import { nanoid } from 'nanoid';

export interface ProviderEditor extends AiProvider {
  keyOperation: 'CLEAR' | 'KEEP' | 'REPLACE';
  apiKey: string;
}
export function providerEditor(provider: AiProvider): ProviderEditor {
  return { ...provider, apiKey: '', keyOperation: 'KEEP' };
}

export const aiUses = [
  {
    capability: 'STRUCTURED_TEXT',
    label: '采购清单解析',
    value: 'PROCUREMENT_PARSE',
  },
  { capability: 'STRUCTURED_TEXT', label: '商品候选排序', value: 'SKU_RERANK' },
  {
    capability: 'STRUCTURED_TEXT',
    label: '营销草稿',
    value: 'MARKETING_DRAFT',
  },
  {
    capability: 'STRUCTURED_TEXT',
    label: '商品文案（预留）',
    value: 'PRODUCT_COPY',
  },
  {
    capability: 'EMBEDDING',
    label: '商品语义搜索',
    value: 'CATALOG_EMBEDDING',
  },
] as const;

export const quickTextUses = aiUses.slice(0, 3);
export type QuickTextUsage =
  'MARKETING_DRAFT' | 'PROCUREMENT_PARSE' | 'SKU_RERANK';

export interface QuickTextForm {
  apiKey: string;
  endpoint: string;
  model: string;
  name: string;
  usages: QuickTextUsage[];
}

export function newTextModel(providerId: string): AiModel {
  return {
    id: `model_${nanoid()}`,
    providerId,
    model: '',
    capability: 'STRUCTURED_TEXT',
    timeoutMillis: 20_000,
    maxInputChars: 32_000,
    maxOutputTokens: 8192,
    dimension: null,
    parameters: {},
  };
}

export function serializeQuickTextConfiguration(
  expectedRevision: number,
  providers: ProviderEditor[],
  models: AiModel[],
  bindings: Partial<Record<AiUsage, string>>,
  form: QuickTextForm,
) {
  const name = form.name.trim();
  const endpoint = form.endpoint.trim();
  const modelName = form.model.trim();
  if (!name || !endpoint || !modelName || form.usages.length === 0) {
    throw new Error('请填写供应商、API 地址和模型 ID，并选择使用场景');
  }
  if (/\/(?:chat\/completions|embeddings)\/?$/i.test(endpoint)) {
    throw new Error('请填写 API 基础地址，去掉末尾的调用路径');
  }
  const provider: ProviderEditor = {
    id: `provider_${nanoid()}`,
    name,
    endpoint,
    protocol: 'OPENAI_COMPATIBLE',
    enabled: true,
    credentialVersion: null,
    keyOperation: 'REPLACE',
    apiKey: form.apiKey.trim(),
  };
  const model = { ...newTextModel(provider.id), model: modelName };
  return serializeConfiguration(
    expectedRevision,
    [...providers, provider],
    [...models, model],
    {
      ...bindings,
      ...Object.fromEntries(form.usages.map((usage) => [usage, model.id])),
    },
  );
}

export function serializeConfiguration(
  expectedRevision: number,
  providers: ProviderEditor[],
  models: AiModel[],
  bindings: Partial<Record<AiUsage, string>>,
) {
  return {
    expectedRevision,
    providers: providers.map(
      ({
        credentialVersion: _credential,
        apiKey,
        keyOperation,
        ...provider
      }) => {
        if (
          keyOperation === 'REPLACE' &&
          (!apiKey.trim() || apiKey.includes('*') || /[\r\n]/.test(apiKey))
        )
          throw new Error('请输入新的完整 API Key');
        return {
          ...provider,
          keyOperation,
          ...(keyOperation === 'REPLACE' ? { apiKey } : {}),
        };
      },
    ),
    models: models.map((model) => {
      if (!['EMBEDDING', 'STRUCTURED_TEXT'].includes(model.capability))
        throw new Error('尚未适配该能力');
      return {
        ...model,
        parameters: Object.fromEntries(
          Object.entries(model.parameters).filter(([key]) =>
            (model.capability === 'EMBEDDING'
              ? ['dimensions']
              : ['temperature', 'top_p', 'thinking', 'response_format']
            ).includes(key),
          ),
        ),
      };
    }),
    bindings: Object.fromEntries(
      Object.entries(bindings).filter(([, value]) => value),
    ),
  };
}
