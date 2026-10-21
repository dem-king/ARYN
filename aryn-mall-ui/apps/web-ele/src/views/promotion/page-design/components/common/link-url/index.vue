<script setup lang="ts">
import type {
  DecorationLink,
  DecorationLinkType,
} from '../../../../page-designer/schema/types';
import type { IdPickerKind } from '../id-picker-field/helpers';

import { computed, ref, watch } from 'vue';

import { ArrowRightBold, CircleClose } from '@element-plus/icons-vue';
import {
  ElAlert,
  ElButton,
  ElCascader,
  ElCollapse,
  ElCollapseItem,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
} from 'element-plus';

import { getPage as getCategoryTree } from '#/api/product/goods-category';

import { cloneDesignerValue } from '../../../../page-designer/schema/clone';
import {
  normalizeDecorationLink,
  validateDecorationLink,
} from '../../../../page-designer/utils/link-utils';
import IdPickerField from '../id-picker-field/index.vue';
import { pickerDefinitions } from '../id-picker-field/registry';
import { COMMON_PAGE_PRESETS, commonPageName } from './common-pages';
import { pickerKindOfType, TARGET_LINK_TYPES } from './link-types';

const props = defineProps<{
  modelValue?: DecorationLink | null | { name?: string; url?: string };
  placeholder?: string;
}>();
const emit = defineEmits<{
  'update:modelValue': [value: DecorationLink | null];
}>();

const linkTypes: Array<{ label: string; value: DecorationLinkType }> = [
  { label: '商品', value: 'goods' },
  { label: '分类', value: 'category' },
  { label: '微页面', value: 'page' },
  { label: '优惠券', value: 'coupon' },
  { label: '活动（拼团）', value: 'activity' },
  { label: '外部小程序', value: 'mini-program' },
  { label: '客服', value: 'customer-service' },
  { label: '自定义路径', value: 'custom' },
];

const miniProgramEnvOptions = [
  { label: '正式版', value: 'release' },
  { label: '体验版', value: 'trial' },
  { label: '开发版', value: 'develop' },
];

const dialogVisible = ref(false);
const attempted = ref(false);
const advancedOpen = ref<string[]>([]);
const form = ref<DecorationLink>(normalizeDecorationLink(props.modelValue));
const paramsText = ref(JSON.stringify(form.value.params ?? {}, null, 2));
const resolvedLabel = ref('');
const presetValue = ref('');

const pickerKind = computed<IdPickerKind>(
  () => pickerKindOfType(form.value.type) ?? 'goods',
);
const showTargetPicker = computed(() =>
  Boolean(pickerKindOfType(form.value.type)),
);
const targetIdList = computed(() =>
  form.value.targetId ? [form.value.targetId] : [],
);
/** 存量数据可能是 preview/develop 之外的未知值，统一归一到 C 端认可的三种 */
const miniProgramEnv = computed(() =>
  normalizeMiniProgramEnv(form.value.params?.envVersion),
);
/** 小程序参数由专用字段维护；其余类型（含分类托管 first/second 之外的扩展参数）可编辑 */
const showParamsEditor = computed(
  () => !['customer-service', 'mini-program'].includes(form.value.type),
);

/** 编辑器保存用 strict：把 C 端会静默不跳/跳错的配置提前拦下（发布校验不受影响） */
const errors = computed(() =>
  validateDecorationLink(form.value, { strict: true }),
);
const targetError = computed(() =>
  attempted.value && errors.value.includes('请选择目标')
    ? '请选择跳转目标'
    : '',
);
const pathError = computed(() => {
  if (!attempted.value) return '';
  const message = errors.value.find(
    (error) =>
      error.startsWith('请输入路径') ||
      error.startsWith('页面路径') ||
      error.startsWith('小程序页面路径'),
  );
  return message ?? '';
});
const appIdError = computed(() =>
  attempted.value && errors.value.includes('请填写小程序 AppID')
    ? '请填写小程序 AppID'
    : '',
);

interface ParsedLinkParams {
  error?: string;
  value?: Record<string, string>;
}

/** 附加参数只接受「扁平对象 + 原始值」：C 端 appendParams 用 Object.entries 拼 query */
function parseParamsText(text: string): ParsedLinkParams {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text || '{}');
  } catch {
    return { error: '参数必须是合法的 JSON' };
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    return { error: '参数必须是 JSON 对象' };
  }
  const params: Record<string, string> = {};
  for (const [key, value] of Object.entries(
    parsed as Record<string, unknown>,
  )) {
    if (value !== null && typeof value === 'object') {
      return { error: '参数值必须是字符串、数字或布尔值' };
    }
    params[key] = String(value);
  }
  return { value: params };
}

const paramsError = computed(() => {
  if (!showParamsEditor.value) return '';
  return parseParamsText(paramsText.value).error ?? '';
});

/**
 * 触发器只回显「已提交」的 modelValue：弹窗里改了但点取消，不能把草稿值显示到触发器上。
 */
const committedLink = computed(() => normalizeDecorationLink(props.modelValue));

const displayValue = computed(() => {
  const link = committedLink.value;
  if (link.type === 'customer-service') return '客服会话';
  if (TARGET_LINK_TYPES.has(link.type)) {
    return (
      resolvedLabel.value || link.targetId || props.placeholder || '选择链接'
    );
  }
  if (link.type === 'custom') {
    return (
      commonPageName(link.path) || link.path || props.placeholder || '选择链接'
    );
  }
  return link.path ? `小程序：${link.path}` : props.placeholder || '选择链接';
});

/**
 * 分类树选择器：金刚区入口常只选一级（如「水果」），
 * 故 checkStrictly 允许选任意层级；选中后把一级/二级 id 分别写进 params，
 * 移动端跳转时直接带正确层级，不再事后查树判断。
 */
const categoryTree = ref<any[]>([]);
const categoryCascader = ref<string[]>([]);
const cascaderProps = {
  label: 'name',
  value: 'id',
  children: 'children',
  checkStrictly: true,
};

/** 分类树单飞：并发打开/回填时复用同一个请求，失败保留旧树，避免互相清空 */
let categoryTreePromise: null | Promise<any[]> = null;
async function loadCategoryTree() {
  if (categoryTree.value.length > 0) return categoryTree.value;
  categoryTreePromise ??= getCategoryTree()
    .then((tree) => {
      categoryTree.value = tree ?? [];
      return categoryTree.value;
    })
    .catch(() => categoryTree.value)
    .finally(() => {
      categoryTreePromise = null;
    });
  return categoryTreePromise;
}

/** 沿已选 id 路径在树里找出最后一级的名字，用于触发器回显 */
function findCategoryLabel(tree: any[], path: string[]): string {
  if (path.length === 0) return '';
  let nodes = tree;
  let label = '';
  for (const id of path) {
    const node = nodes.find((n: any) => String(n.id) === String(id));
    if (!node) return label;
    label = node.name;
    nodes = node.children ?? [];
  }
  return label;
}

/** 旧分类链接只存了 targetId（没有层级 params），按 id 在整棵树里找名字 */
function findCategoryNameById(tree: any[], id: string): string {
  for (const node of tree) {
    if (String(node.id) === id) return node.name ?? '';
    const found = findCategoryNameById(node.children ?? [], id);
    if (found) return found;
  }
  return '';
}

/** 旧分类链接按 targetId 反查完整层级路径，打开弹窗时回填级联选择 */
function findCategoryPathById(
  tree: any[],
  id: string,
  trail: string[] = [],
): string[] {
  for (const node of tree) {
    const current = [...trail, String(node.id)];
    if (String(node.id) === id) return current;
    const found = findCategoryPathById(node.children ?? [], id, current);
    if (found.length > 0) return found;
  }
  return [];
}

function onCategoryChange(value: any) {
  categoryCascader.value = Array.isArray(value) ? [...value] : [];
  const firstId = categoryCascader.value[0] ?? '';
  const secondId = categoryCascader.value[1] ?? '';
  // targetId 仍保留所选节点 id（二级优先），兼容旧校验与展示
  form.value.targetId = secondId || firstId;
  // 层级写进 params，移动端跳转时直接带正确的 first/second
  form.value.params = {
    ...form.value.params,
    categoryFirstId: firstId,
    categorySecondId: secondId,
  };
  paramsText.value = JSON.stringify(form.value.params ?? {}, null, 2);
}

function open() {
  form.value = normalizeDecorationLink(props.modelValue);
  attempted.value = false;
  advancedOpen.value = [];
  presetValue.value = '';
  paramsText.value = JSON.stringify(form.value.params ?? {}, null, 2);
  const params = form.value.params ?? {};
  categoryCascader.value =
    form.value.type === 'category'
      ? ([params.categoryFirstId, params.categorySecondId].filter(
          Boolean,
        ) as string[])
      : [];
  if (form.value.type === 'category') {
    void loadCategoryTree().then((tree) => {
      // 旧数据只有 targetId：树回来后反查层级回填，用户不会看到空选择
      if (
        !dialogVisible.value ||
        categoryCascader.value.length > 0 ||
        !form.value.targetId
      ) {
        return;
      }
      categoryCascader.value = findCategoryPathById(
        tree,
        String(form.value.targetId),
      );
    });
  }
  dialogVisible.value = true;
}

function onTypeChange(value: string) {
  const type = value as DecorationLinkType;
  if (type === form.value.type) return;
  form.value.type = type;
  // 换类型等于换落地目标：清空上一个类型的 id/path/参数，避免「商品链接里
  // 残留优惠券 ID」这类看似有值、实际跳错页的脏配置
  form.value.targetId = undefined;
  form.value.path = '';
  form.value.params =
    type === 'mini-program' ? { appId: '', envVersion: 'release' } : {};
  paramsText.value = JSON.stringify(form.value.params, null, 2);
  categoryCascader.value = [];
  presetValue.value = '';
  attempted.value = false;
  if (type === 'category') void loadCategoryTree();
}

function onTargetPicked(ids: string[]) {
  form.value.targetId = ids[0] ?? '';
}

function setMiniParam(key: 'appId' | 'envVersion', value: string) {
  form.value.params = { ...form.value.params, [key]: value ?? '' };
}

function normalizeMiniProgramEnv(value: string | undefined) {
  return value === 'develop' || value === 'trial' ? value : 'release';
}

function onPresetChange(value: string) {
  if (!value) return;
  form.value.path = value;
  // 换落地页后旧 query 参数大概率失效（C 端会原样 append），一并清掉
  form.value.params = {};
  paramsText.value = '{}';
  // 常用页面只是一次「快速填入」动作，不保持选中态，避免被误认为路径被绑定
  presetValue.value = '';
}

function submit() {
  attempted.value = true;
  const next = cloneDesignerValue(form.value);
  if (showParamsEditor.value) {
    const parsed = parseParamsText(paramsText.value);
    if (parsed.error) {
      advancedOpen.value = ['params'];
      return;
    }
    next.params = parsed.value ?? {};
  }
  if (next.type === 'category') {
    // first/second 由级联选择器托管，JSON 里留旧值也不能用
    next.params = {
      ...next.params,
      categoryFirstId: form.value.params.categoryFirstId ?? '',
      categorySecondId: form.value.params.categorySecondId ?? '',
    };
  }
  if (next.type === 'mini-program') {
    next.params = {
      ...next.params,
      appId: (next.params.appId ?? '').trim(),
      envVersion: normalizeMiniProgramEnv(next.params.envVersion),
    };
  }
  if (next.type === 'custom' || next.type === 'mini-program') {
    next.path = next.path.trim();
  }
  if (next.type === 'mini-program') {
    // 微信 navigateToMiniProgram 的 path 不接受前导 /，顺手归一化
    next.path = next.path.replace(/^\/+/, '');
  }
  if (validateDecorationLink(next, { strict: true }).length > 0) return;
  emit('update:modelValue', cloneDesignerValue(next));
  dialogVisible.value = false;
}

function clear() {
  emit('update:modelValue', null);
}

/**
 * 触发器回显「人话」名称：配置里只存 ID，按类型回源解析。
 * 网络失败/无权限时静默回退到 ID 文本，不阻塞重新配置；关闭弹窗时再解析一次，
 * 让上次失败或实体刚改名的场景能自愈。
 */
let labelToken = 0;
async function resolveCommittedLabel() {
  const token = ++labelToken;
  resolvedLabel.value = '';
  const link = committedLink.value;
  if (link.type === 'category') {
    const tree = await loadCategoryTree();
    if (token !== labelToken) return;
    const path = [
      link.params?.categoryFirstId,
      link.params?.categorySecondId,
    ].filter(Boolean) as string[];
    resolvedLabel.value =
      findCategoryLabel(tree, path) ||
      findCategoryNameById(tree, String(link.targetId ?? ''));
    return;
  }
  const kind = pickerKindOfType(link.type);
  if (!kind || !link.targetId) return;
  try {
    const [item] = await pickerDefinitions[kind].hydrate([
      String(link.targetId),
    ]);
    if (token !== labelToken) return;
    resolvedLabel.value = item && !item.invalid ? item.title : '';
  } catch {
    // 回源失败：触发器继续回显 ID，运营仍可重新选择
  }
}

watch(
  () =>
    [
      committedLink.value.type,
      committedLink.value.targetId,
      committedLink.value.params?.categoryFirstId,
      committedLink.value.params?.categorySecondId,
    ] as const,
  () => {
    void resolveCommittedLabel();
  },
  { immediate: true },
);

watch(dialogVisible, (visible) => {
  if (!visible) void resolveCommittedLabel();
});

watch(
  () => props.modelValue,
  (value) => {
    // 弹窗打开期间父级刷新（如其他面板联动）不覆盖用户正在编辑的草稿
    if (dialogVisible.value) return;
    form.value = normalizeDecorationLink(value);
    paramsText.value = JSON.stringify(form.value.params ?? {}, null, 2);
  },
);
</script>

<template>
  <div class="link-editor">
    <ElInput :model-value="displayValue" readonly @click="open">
      <template #append>
        <ElButton
          v-if="modelValue"
          :icon="CircleClose"
          aria-label="清除链接"
          @click.stop="clear"
        />
        <ElButton
          v-else
          :icon="ArrowRightBold"
          aria-label="选择链接"
          @click="open"
        />
      </template>
    </ElInput>

    <ElDialog
      v-model="dialogVisible"
      append-to-body
      title="链接设置"
      width="560px"
    >
      <ElForm :model="form" label-position="top">
        <ElFormItem label="链接类型">
          <ElSelect
            :model-value="form.type"
            class="full-width"
            @change="onTypeChange"
          >
            <ElOption
              v-for="item in linkTypes"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>

        <ElFormItem
          v-if="showTargetPicker"
          label="跳转目标"
          :error="targetError"
        >
          <IdPickerField
            :key="form.type"
            :kind="pickerKind"
            :limit="1"
            :model-value="targetIdList"
            replace-on-pick
            @update:model-value="onTargetPicked"
          />
        </ElFormItem>

        <ElFormItem
          v-else-if="form.type === 'category'"
          label="选择分类"
          :error="targetError"
        >
          <ElCascader
            v-model="categoryCascader"
            :options="categoryTree"
            clearable
            filterable
            :props="cascaderProps"
            class="full-width"
            placeholder="选择分类（可只选一级，如「水果」）"
            @change="onCategoryChange"
          />
        </ElFormItem>

        <template v-else-if="form.type === 'mini-program'">
          <ElFormItem label="小程序 AppID" :error="appIdError" required>
            <ElInput
              :model-value="form.params.appId ?? ''"
              clearable
              placeholder="wx 开头的小程序 AppID"
              @update:model-value="
                (value: string) => setMiniParam('appId', value)
              "
            />
          </ElFormItem>
          <ElFormItem label="小程序版本">
            <ElSelect
              :model-value="miniProgramEnv"
              class="full-width"
              @change="(value: string) => setMiniParam('envVersion', value)"
            >
              <ElOption
                v-for="item in miniProgramEnvOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="页面路径" :error="pathError" required>
            <ElInput
              v-model="form.path"
              clearable
              placeholder="pages/index/index（不要以 / 开头）"
            />
          </ElFormItem>
        </template>

        <template v-else-if="form.type === 'custom'">
          <ElFormItem label="常用页面">
            <ElSelect
              v-model="presetValue"
              class="full-width"
              filterable
              placeholder="选择常用页面，自动填入下方路径"
              @change="onPresetChange"
            >
              <ElOption
                v-for="preset in COMMON_PAGE_PRESETS"
                :key="preset.path"
                :label="preset.name"
                :value="preset.path"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="页面路径" :error="pathError" required>
            <ElInput
              v-model="form.path"
              clearable
              placeholder="以 / 开头的站内路径，如 /sub-pages/product/goods-search/index"
            />
          </ElFormItem>
        </template>

        <ElAlert
          v-else-if="form.type === 'customer-service'"
          type="info"
          :closable="false"
          show-icon
          title="无需配置：点击后进入客服会话"
        />

        <ElCollapse
          v-if="showParamsEditor"
          v-model="advancedOpen"
          class="link-editor__advanced"
        >
          <ElCollapseItem name="params" title="高级设置：附加参数">
            <ElFormItem :error="paramsError" label="参数（JSON）">
              <ElInput
                v-model="paramsText"
                :rows="4"
                type="textarea"
                placeholder="一般无需填写，仅用于给页面追加 query 参数"
              />
            </ElFormItem>
          </ElCollapseItem>
        </ElCollapse>
      </ElForm>
      <template #footer>
        <ElButton @click="dialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="submit">确定</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.link-editor,
.full-width {
  width: 100%;
}

.link-editor__advanced {
  margin-top: 4px;
  border-top: none;
}
</style>
