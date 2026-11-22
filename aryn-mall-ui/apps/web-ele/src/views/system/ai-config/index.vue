<script setup lang="ts">
import type { ProviderEditor, QuickTextForm } from './ai-config-form';

import type {
  AiCallLog,
  AiConfigView,
  AiModel,
  AiProvider,
  AiTestResult,
  AiUsage,
} from '#/api/upms/ai-config';

import { computed, onMounted, ref, watch } from 'vue';

import { useAccess } from '@vben/access';
import { Page } from '@vben/common-ui';

import { useTimestamp } from '@vueuse/core';
import {
  ElAlert,
  ElButton,
  ElCard,
  ElCheckbox,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus';

import {
  activateConfiguration,
  bootstrapConfiguration,
  getCalls,
  getConfiguration,
  getTestResults,
  getVersions,
  saveConfiguration,
  setEmergency,
  testModel,
} from '#/api/upms/ai-config';

import {
  aiUses,
  newTextModel,
  providerEditor,
  serializeConfiguration,
  serializeQuickTextConfiguration,
} from './ai-config-form';
import { canActivate, testIsFresh, testStatusLabel } from './ai-config-state';
import CatalogIndexPanel from './catalog-index-panel.vue';
import QuickTextDialog from './quick-text-dialog.vue';

const state = ref<AiConfigView>();
const providers = ref<ProviderEditor[]>([]);
const models = ref<AiModel[]>([]);
const bindings = ref<Partial<Record<AiUsage, string>>>({});
const versions = ref<AiConfigView[]>([]);
const calls = ref<AiCallLog[]>([]);
const callTenant = ref('');
const tests = ref<AiTestResult[]>([]);
const saved = ref('');
const busy = ref(false);
const error = ref('');
const quickOpen = ref(false);
const editingBindings = ref(false);
const tab = ref('overview');
const now = useTimestamp({ interval: 30_000 });
const currentTime = () => Math.max(now.value, Date.now());
const { hasAccessByCodes } = useAccess();
const canEdit = computed(() => hasAccessByCodes(['upms:ai-config:edit']));
const canTest = computed(() => hasAccessByCodes(['upms:ai-config:test']));
const canPublish = computed(() =>
  hasAccessByCodes(['upms:ai-config:activate']),
);

const signature = () =>
  JSON.stringify({
    providers: providers.value,
    models: models.value,
    bindings: bindings.value,
  });
const dirty = computed(() => !!state.value && saved.value !== signature());
const hasDraft = computed(
  () =>
    !!state.value?.version && state.value.version !== state.value.activeVersion,
);
const activeConfiguration = computed(() => {
  const current = state.value;
  if (!current?.activeVersion) return;
  if (current.activeVersion === current.version) return current.configuration;
  return versions.value.find((view) => view.version === current.activeVersion)
    ?.configuration;
});
const boundModels = computed(() => {
  const ids = new Set(Object.values(bindings.value));
  return models.value.filter((model) => ids.has(model.id));
});
const pendingModels = computed(() =>
  boundModels.value.filter((model) => !hasFreshTest(model.id)),
);
const ready = computed(
  () =>
    hasDraft.value &&
    canActivate(
      dirty.value,
      state.value?.version ?? null,
      boundModels.value.map((model) => model.id),
      tests.value,
      currentTime(),
    ),
);
const testBlocked = computed(
  () =>
    state.value?.disabled ||
    pendingModels.value.some((model) =>
      state.value?.disabledProviders.includes(model.providerId),
    ),
);
const overviewUses = computed(() =>
  aiUses.filter(
    (usage) =>
      usage.value !== 'PRODUCT_COPY' ||
      bindings.value.PRODUCT_COPY ||
      activeConfiguration.value?.bindings.PRODUCT_COPY,
  ),
);
const draftHint = computed(() => {
  if (dirty.value) return '保存编辑后可以测试模型。';
  if (!hasDraft.value || boundModels.value.length === 0)
    return '为需要使用 AI 的场景选择模型。';
  if (testBlocked.value)
    return '待测试模型受紧急停用影响，请在运维中检查状态。';
  if (ready.value)
    return canPublish.value
      ? '所有业务模型已通过测试，可以启用。'
      : '模型测试已通过，等待有启用权限的管理员确认。';
  return canTest.value
    ? '使用固定样本测试模型，结果有效期为 24 小时。'
    : '等待有测试权限的管理员完成模型测试。';
});

function hasFreshTest(modelId: string) {
  const version = state.value?.version;
  return (
    !!version &&
    tests.value.some(
      (result) =>
        result.modelId === modelId &&
        testIsFresh(result, version, currentTime()),
    )
  );
}

function statusFor(modelId: string) {
  if (dirty.value) return '保存后可测试';
  if (hasFreshTest(modelId)) return '测试通过';
  const result = tests.value.find((item) => item.modelId === modelId);
  return result?.passed ? '测试已过期' : testStatusLabel(result?.status);
}

function modelLabel(
  model: AiModel,
  availableProviders: AiProvider[] = providers.value,
) {
  const provider = availableProviders.find(
    (item) => item.id === model.providerId,
  );
  return provider ? `${provider.name} / ${model.model}` : model.model;
}

function activeModelLabel(usage: AiUsage) {
  const config = activeConfiguration.value;
  const model = config?.models.find(
    (item) => item.id === config.bindings[usage],
  );
  if (!config || !model)
    return state.value?.activeVersion && !config ? '暂不可读' : '未启用';
  const label = modelLabel(model, config.providers);
  return state.value?.disabled ||
    state.value?.disabledProviders.includes(model.providerId)
    ? `${label}（已停用）`
    : label;
}

function apply(view: AiConfigView) {
  if (state.value?.version !== view.version) tests.value = [];
  if (
    state.value?.version &&
    state.value.version === view.activeVersion &&
    state.value.version !== view.version &&
    !versions.value.some((item) => item.version === view.activeVersion)
  ) {
    versions.value.unshift(state.value);
  }
  state.value = view;
  providers.value = view.configuration.providers.map((provider) =>
    providerEditor(provider),
  );
  models.value = view.configuration.models.map((model) => ({
    ...model,
    parameters: { ...model.parameters },
  }));
  bindings.value = { ...view.configuration.bindings };
  saved.value = signature();
  editingBindings.value = false;
}
async function run(action: () => Promise<void>) {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    await action();
  } catch (error_) {
    if (error_ === 'cancel' || error_ === 'close') return;
    error.value =
      error_ instanceof Error ? error_.message : '操作失败，请重新加载当前版本';
  } finally {
    busy.value = false;
  }
}
async function load() {
  if (dirty.value) {
    try {
      await ElMessageBox.confirm(
        '刷新后将丢弃尚未保存的编辑。',
        '重新加载配置',
      );
    } catch {
      return;
    }
  }
  await run(async () => {
    const view = await getConfiguration();
    apply(view);
    const results = await Promise.allSettled([
      view.version ? refreshTests(view.version) : undefined,
      tab.value === 'operations' || (hasDraft.value && view.activeVersion)
        ? refreshVersions()
        : undefined,
      tab.value === 'calls' ? refreshCalls() : undefined,
    ]);
    const failure = results.find((result) => result.status === 'rejected');
    if (failure) throw failure.reason;
  });
}

async function refreshTests(version: string) {
  tests.value = await getTestResults(version);
}

async function refreshVersions() {
  versions.value = await getVersions();
}

async function refreshCalls() {
  calls.value = await getCalls(callTenant.value.trim() || undefined);
}

watch(tab, (value) => {
  if (value === 'calls') void run(refreshCalls);
  if (value === 'operations') void run(refreshVersions);
});

function addProvider() {
  const id = `provider_${Date.now()}`;
  providers.value.push(
    providerEditor({
      id,
      name: '新供应商',
      protocol: 'OPENAI_COMPATIBLE',
      endpoint: '',
      credentialVersion: null,
      enabled: true,
    }),
  );
}
function addModel() {
  models.value.push(newTextModel(providers.value[0]?.id ?? ''));
}
async function save() {
  await run(async () => {
    if (!state.value) return;
    apply(
      await saveConfiguration(
        serializeConfiguration(
          state.value.revision,
          providers.value,
          models.value,
          bindings.value,
        ),
      ),
    );
    ElMessage.success('草稿已保存');
  });
}

function openQuick() {
  error.value = '';
  quickOpen.value = true;
}

function closeQuick() {
  if (busy.value) return;
  quickOpen.value = false;
  error.value = '';
}

async function saveQuick(form: QuickTextForm) {
  await run(async () => {
    if (!state.value || dirty.value || !canEdit.value) return;
    apply(
      await saveConfiguration(
        serializeQuickTextConfiguration(
          state.value.revision,
          providers.value,
          models.value,
          bindings.value,
          form,
        ),
      ),
    );
    quickOpen.value = false;
    ElMessage.success('草稿已保存，可以开始测试');
  });
}

async function testModels(targets: AiModel[]) {
  await run(async () => {
    const version = state.value?.version;
    if (!version || dirty.value || targets.length === 0) return;
    await ElMessageBox.confirm(
      `将用固定样本测试 ${targets.length} 个模型，会产生少量供应商用量。`,
      '测试模型',
    );
    const failures: string[] = [];
    for (const model of targets) {
      try {
        const result = await testModel(version, model.id);
        if (result.passed || !hasFreshTest(model.id)) {
          tests.value = [
            ...tests.value.filter((item) => item.modelId !== model.id),
            result,
          ];
        }
        if (!result.passed)
          failures.push(`${model.model} · ${testStatusLabel(result.status)}`);
      } catch (error_) {
        failures.push(
          `${model.model} · ${error_ instanceof Error ? error_.message : '测试失败'}`,
        );
      }
    }
    if (failures.length > 0) throw new Error(failures.join('。'));
    ElMessage.success('模型测试通过');
  });
}
async function activate(version?: string) {
  await run(async () => {
    if (!state.value) return;
    const target = version ?? state.value.version;
    if (!target) return;
    await ElMessageBox.confirm(
      '新任务将使用选定版本；向量用途须配套完成目录索引。紧急停用状态保持。',
      version ? '回滚配置' : '启用配置',
    );
    apply(await activateConfiguration(target, state.value.revision, !!version));
    if (tab.value === 'operations') await refreshVersions();
    ElMessage.success(version ? '已切换版本' : '配置已启用');
  });
}
async function emergency(provider: null | string, disabled: boolean) {
  await run(async () => {
    if (!state.value) return;
    await ElMessageBox.confirm(
      disabled
        ? '将阻止后续 AI 外呼及重试，业务进入规则降级或等待。'
        : '确认恢复 AI 运行权限。',
      disabled ? '紧急停用' : '恢复运行',
    );
    const view = await setEmergency(provider, disabled, state.value.revision);
    if (dirty.value && state.value.version === view.version) state.value = view;
    else apply(view);
  });
}
async function bootstrap(embedding: boolean) {
  await run(async () => {
    if (!state.value || dirty.value) throw new Error('请先保存或重新加载草稿');
    await ElMessageBox.confirm(
      '将部署环境中的旧配置导入为草稿，仍需能力测试和人工启用。',
      '导入旧配置',
    );
    apply(await bootstrapConfiguration(state.value.revision, embedding));
    tab.value = 'overview';
    ElMessage.success('旧配置已导入草稿');
  });
}
onMounted(load);
</script>
<template>
  <Page title="平台 AI 配置">
    <ElAlert
      v-if="error && !quickOpen"
      :title="error"
      type="error"
      :closable="false"
      class="mb-4"
    />
    <ElCard>
      <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div class="flex flex-wrap items-center gap-3">
          <h2 class="text-base font-semibold">AI 运行状态</h2>
          <ElTag v-if="state?.disabled" type="danger">已紧急停用</ElTag>
          <ElTag v-else-if="state?.activeVersion" type="success">
            配置已启用
          </ElTag>
          <ElTag v-else-if="state" type="info">使用业务规则</ElTag>
          <span v-else role="status">{{
            busy ? '正在加载配置…' : '配置暂不可用'
          }}</span>
        </div>
        <ElButton :loading="busy" @click="load">重新加载</ElButton>
      </div>

      <div
        v-if="state && dirty && tab !== 'overview'"
        class="mb-3 flex flex-wrap items-center gap-3"
      >
        <span>有未保存的配置编辑</span>
        <ElButton :disabled="busy" @click="tab = 'overview'">前往保存</ElButton>
      </div>
      <ElTabs v-if="state" v-model="tab" :before-leave="() => !busy">
        <ElTabPane label="业务配置" name="overview">
          <div class="space-y-5 py-2">
            <div class="flex flex-wrap items-start justify-between gap-4">
              <div>
                <h3 class="font-semibold">业务使用的模型</h3>
                <p class="text-muted-foreground mt-1 text-sm">
                  配置采购清单、商品匹配和营销草稿使用的 AI 模型。
                </p>
              </div>
              <ElButton
                v-if="canEdit"
                :type="hasDraft || dirty ? 'default' : 'primary'"
                :disabled="busy || dirty"
                @click="openQuick"
              >
                接入文本模型
              </ElButton>
            </div>

            <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
              <div
                v-for="usage in overviewUses"
                :key="usage.value"
                class="min-w-0 rounded-md border px-4 py-3"
              >
                <div class="text-muted-foreground text-sm">
                  {{ usage.label }}
                </div>
                <div class="mt-1 break-words font-medium">
                  {{ activeModelLabel(usage.value) }}
                </div>
                <button
                  v-if="usage.value === 'CATALOG_EMBEDDING'"
                  type="button"
                  class="text-primary mt-1 text-sm hover:underline"
                  @click="tab = 'index'"
                >
                  查看租户目录索引
                </button>
              </div>
            </div>

            <ElCard v-if="hasDraft || dirty || editingBindings" shadow="never">
              <div
                class="mb-4 flex flex-wrap items-start justify-between gap-3"
              >
                <div>
                  <h3 class="font-semibold">配置草稿</h3>
                  <p
                    aria-live="polite"
                    class="text-muted-foreground mt-1 text-sm"
                  >
                    {{ draftHint }}
                  </p>
                </div>
                <ElButton
                  v-if="dirty && canEdit"
                  type="primary"
                  :disabled="busy"
                  @click="save"
                >
                  保存草稿
                </ElButton>
                <ElButton
                  v-else-if="ready && canPublish"
                  type="primary"
                  :disabled="busy"
                  @click="activate()"
                >
                  启用配置
                </ElButton>
                <ElButton
                  v-else-if="hasDraft && canTest && pendingModels.length > 0"
                  type="primary"
                  :loading="busy"
                  :disabled="!!testBlocked"
                  @click="testModels(pendingModels)"
                >
                  测试业务模型
                </ElButton>
              </div>

              <div
                v-for="usage in aiUses"
                :key="usage.value"
                class="grid items-center gap-2 border-t py-2 md:grid-cols-[10rem_minmax(0,1fr)_minmax(8rem,auto)]"
              >
                <label :for="`ai-usage-${usage.value}`">{{
                  usage.label
                }}</label>
                <ElSelect
                  :id="`ai-usage-${usage.value}`"
                  v-model="bindings[usage.value]"
                  placeholder="使用业务规则"
                  clearable
                  :disabled="busy || !canEdit"
                  class="w-full"
                >
                  <ElOption
                    v-for="model in models.filter(
                      (item) => item.capability === usage.capability,
                    )"
                    :key="model.id"
                    :label="modelLabel(model)"
                    :value="model.id"
                  />
                </ElSelect>
                <span
                  v-if="bindings[usage.value]"
                  class="text-muted-foreground text-sm"
                >
                  {{ statusFor(bindings[usage.value]!) }}
                </span>
              </div>
            </ElCard>
            <div v-else-if="canEdit && state.version" class="flex justify-end">
              <ElButton :disabled="busy" @click="editingBindings = true">
                调整业务模型
              </ElButton>
            </div>
          </div>
        </ElTabPane>
        <ElTabPane label="供应商" name="providers">
          <ElButton
            v-access:code="['upms:ai-config:edit']"
            class="my-3"
            :disabled="busy"
            @click="addProvider"
          >
            新增供应商
          </ElButton>
          <ElCard v-for="provider in providers" :key="provider.id" class="mb-3">
            <div class="grid gap-3 sm:grid-cols-2">
              <label
                >名称<ElInput
                  v-model="provider.name"
                  :disabled="busy || !canEdit"
              /></label>
              <label
                >协议<ElSelect
                  v-model="provider.protocol"
                  :disabled="busy || !canEdit"
                  ><ElOption
                    label="OpenAI 兼容协议"
                    value="OPENAI_COMPATIBLE" /></ElSelect
              ></label>
              <label class="sm:col-span-2"
                >API 基础地址<ElInput
                  v-model="provider.endpoint"
                  :disabled="busy || !canEdit"
                  placeholder="https://provider.example/v1"
              /></label>
              <label
                >密钥 {{ provider.credentialVersion ? '已设置' : '未设置'
                }}<ElSelect
                  v-model="provider.keyOperation"
                  :disabled="busy || !canEdit"
                  ><ElOption label="保留" value="KEEP" /><ElOption
                    label="替换"
                    value="REPLACE" /><ElOption
                    label="清除"
                    value="CLEAR" /></ElSelect
              ></label>
              <label v-if="provider.keyOperation === 'REPLACE'"
                >新 API Key<ElInput
                  v-model="provider.apiKey"
                  :disabled="busy || !canEdit"
                  type="password"
                  autocomplete="new-password"
                  show-password
              /></label>
              <ElCheckbox
                v-model="provider.enabled"
                :disabled="busy || !canEdit"
              >
                允许启用
              </ElCheckbox>
            </div>
            <ElButton
              v-access:code="['upms:ai-config:activate']"
              class="mt-3"
              :disabled="busy"
              @click="
                emergency(
                  provider.id,
                  !state?.disabledProviders.includes(provider.id),
                )
              "
            >
              {{
                state?.disabledProviders.includes(provider.id)
                  ? '解除紧急停用'
                  : '紧急停用此供应商'
              }}
            </ElButton>
          </ElCard>
        </ElTabPane>
        <ElTabPane label="模型" name="models">
          <ElButton
            v-access:code="['upms:ai-config:edit']"
            class="my-3"
            :disabled="busy || providers.length === 0"
            @click="addModel"
          >
            新增模型
          </ElButton>
          <ElCard v-for="model in models" :key="model.id" class="mb-3">
            <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              <label
                >供应商<ElSelect
                  v-model="model.providerId"
                  :disabled="busy || !canEdit"
                  ><ElOption
                    v-for="p in providers"
                    :key="p.id"
                    :label="p.name"
                    :value="p.id" /></ElSelect
              ></label>
              <label
                >模型 ID<ElInput
                  v-model="model.model"
                  :disabled="busy || !canEdit"
              /></label>
              <label
                >能力<ElSelect
                  v-model="model.capability"
                  :disabled="busy || !canEdit"
                  @change="model.parameters = {}"
                  ><ElOption
                    label="结构化文本"
                    value="STRUCTURED_TEXT" /><ElOption
                    label="向量"
                    value="EMBEDDING" /></ElSelect
              ></label>
              <label
                >超时（毫秒）<ElInputNumber
                  v-model="model.timeoutMillis"
                  :disabled="busy || !canEdit"
                  :min="1"
                  :max="120000"
              /></label>
              <label
                >输入字符上限<ElInputNumber
                  v-model="model.maxInputChars"
                  :disabled="busy || !canEdit"
                  :min="1"
                  :max="1000000"
              /></label>
              <label
                >输出 token 上限<ElInputNumber
                  v-model="model.maxOutputTokens"
                  :disabled="busy || !canEdit"
                  :min="1"
                  :max="32768"
              /></label>
              <label v-if="model.capability === 'EMBEDDING'"
                >向量维度<ElInputNumber
                  v-model="model.dimension"
                  :disabled="busy || !canEdit"
                  :min="1"
                  :max="16384"
              /></label>
              <label v-if="model.capability === 'EMBEDDING'"
                ><ElCheckbox
                  :model-value="model.parameters.dimensions !== undefined"
                  :disabled="busy || !canEdit"
                  @change="
                    model.parameters = $event
                      ? { dimensions: model.dimension ?? 1 }
                      : {}
                  "
                  >显式发送 dimensions（仅支持该参数的模型）</ElCheckbox
                ></label
              >
              <template v-else>
                <label
                  ><ElCheckbox
                    :model-value="
                      model.parameters.response_format === 'json_object'
                    "
                    :disabled="busy || !canEdit"
                    @change="
                      model.parameters = $event
                        ? {
                            ...model.parameters,
                            response_format: 'json_object',
                          }
                        : Object.fromEntries(
                            Object.entries(model.parameters).filter(
                              ([k]) => k !== 'response_format',
                            ),
                          )
                    "
                    >请求 JSON 对象格式</ElCheckbox
                  ></label
                >
              </template>
            </div>
            <ElButton
              v-access:code="['upms:ai-config:test']"
              class="mt-3"
              :disabled="
                busy ||
                dirty ||
                !state.version ||
                state.disabled ||
                state.disabledProviders.includes(model.providerId)
              "
              @click="testModels([model])"
            >
              固定样本能力测试
            </ElButton>
            <span class="ml-3">{{ statusFor(model.id) }}</span>
          </ElCard>
        </ElTabPane>
        <ElTabPane label="版本与运维" name="operations">
          <div class="mb-4 flex flex-wrap items-center gap-3">
            <span class="break-all"
              >运行版本 {{ state.activeVersion ?? '无' }}</span
            >
            <ElButton
              v-if="!state.activeVersion && canEdit"
              :disabled="busy || dirty"
              @click="bootstrap(false)"
            >
              导入旧文本配置
            </ElButton>
            <ElButton
              v-if="!state.activeVersion && canEdit"
              :disabled="busy || dirty"
              @click="bootstrap(true)"
            >
              导入旧向量配置
            </ElButton>
            <ElButton
              v-if="canPublish"
              :type="state.disabled ? 'primary' : 'danger'"
              plain
              :disabled="busy"
              @click="emergency(null, !state.disabled)"
            >
              {{ state.disabled ? '恢复运行' : '紧急停用全部' }}
            </ElButton>
          </div>
          <ElTable :data="versions">
            <ElTableColumn prop="version" label="版本" min-width="220" />
            <ElTableColumn prop="revision" label="保存修订" />
            <ElTableColumn
              label="用途"
              :formatter="
                (row: AiConfigView) =>
                  aiUses
                    .filter((usage) => row.configuration.bindings[usage.value])
                    .map((usage) => usage.label)
                    .join('、')
              "
            />
            <ElTableColumn label="操作">
              <template #default="{ row }">
                <ElButton
                  v-access:code="['upms:ai-config:activate']"
                  :disabled="
                    busy ||
                    dirty ||
                    row.version === state?.activeVersion ||
                    row.version === state?.version
                  "
                  @click="activate(row.version)"
                >
                  测试仍有效时回滚
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>
        <ElTabPane label="调用记录" name="calls">
          <div class="mb-3 flex gap-2">
            <ElInput
              v-model="callTenant"
              placeholder="调用租户 ID，留空查询当前租户"
            />
            <ElButton :disabled="busy" @click="run(refreshCalls)">
              查询调用记录
            </ElButton>
          </div>
          <ElTable :data="calls">
            <ElTableColumn
              prop="createTime"
              label="时间"
              min-width="180"
            /><ElTableColumn prop="tenantId" label="租户" /><ElTableColumn
              prop="usageCode"
              label="用途"
              min-width="180"
            /><ElTableColumn prop="modelId" label="模型" /><ElTableColumn
              prop="versionId"
              label="配置版本"
              min-width="220"
            />
            <ElTableColumn prop="result" label="结果" />
            <ElTableColumn
              label="token"
              :formatter="(row: AiCallLog) => String(row.totalTokens ?? '未知')"
            />
            <ElTableColumn prop="elapsedMillis" label="耗时ms" />
          </ElTable>
        </ElTabPane>
        <ElTabPane label="目录索引" name="index" lazy>
          <CatalogIndexPanel />
        </ElTabPane>
      </ElTabs>
    </ElCard>
    <QuickTextDialog
      v-if="quickOpen"
      :busy="busy"
      :error="error"
      @close="closeQuick"
      @save="saveQuick"
    />
  </Page>
</template>
