<script setup lang="ts">
import type {
  DecorationSection,
  SectionBackgroundGradient,
  SectionCondition,
  SectionConditionGroup,
  SectionConditionRule,
  SectionStyle,
} from '../schema/types';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { CircleClose, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElColorPicker,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
  ElTimePicker,
  ElTooltip,
} from 'element-plus';

import { getList as getMemberLevelList } from '#/api/user/member-level';
import { getList as getMemberTagList } from '#/api/user/member-tag';

import { cloneDesignerValue } from '../schema/clone';
import { sanitizeCondition } from '../schema/condition';
import { CARD_SECTION_PRESET } from '../schema/defaults';
import { buildSectionGradient } from '../schema/section-style';

const props = defineProps<{ section?: DecorationSection }>();

const emit = defineEmits<{
  patch: [
    patch: {
      name?: string;
      style?: Partial<SectionStyle>;
    },
    groupKey?: string,
  ];
}>();

const form = reactive({
  backgroundColor: '',
  backgroundGradient: {
    direction: 'to right',
    endColor: '',
    startColor: '',
  } as SectionBackgroundGradient,
  backgroundImage: '',
  horizontalScroll: false,
  marginX: 0,
  marginY: 0,
  name: '',
  paddingX: 0,
  paddingY: 0,
  radius: 0,
  sticky: false,
});

type BackgroundMode = 'gradient' | 'none' | 'solid';
const backgroundMode = ref<BackgroundMode>('none');
const gradientPreview = computed(
  () => buildSectionGradient(form.backgroundGradient) ?? 'transparent',
);

// ---- 条件显示：登录态三态（字符串简写） + 高级组合条件（对象） ----
type LoginScope = 'always' | 'guest' | 'login';
type RuleTypeOption = 'memberLevel' | 'timeRange' | 'userTag';

const RULE_TYPE_OPTIONS: { label: string; value: RuleTypeOption }[] = [
  { label: '会员等级', value: 'memberLevel' },
  { label: '用户标签', value: 'userTag' },
  { label: '时间段', value: 'timeRange' },
];

const loginScope = ref<LoginScope>('always');
const advanced = ref(false);
const group = ref<SectionConditionGroup>({ logic: 'and', rules: [] });

interface NamedOption {
  id: string;
  name: string;
}
const levelOptions = ref<NamedOption[]>([]);
const tagOptions = ref<NamedOption[]>([]);

function createRule(type: RuleTypeOption): SectionConditionRule {
  if (type === 'memberLevel')
    return { type: 'memberLevel', memberLevelIds: [] };
  if (type === 'userTag') return { type: 'userTag', userTagIds: [] };
  return { type: 'timeRange', startTime: '00:00', endTime: '23:59' };
}

/** 已存在的 login/guest 规则（C 端写入）在管理端按会员等级规则编辑器兜底 */
function editableRuleType(rule: SectionConditionRule): RuleTypeOption {
  if (rule.type === 'userTag') return 'userTag';
  if (rule.type === 'timeRange') return 'timeRange';
  return 'memberLevel';
}

function resolveCondition(): SectionCondition {
  // 面板里允许存在未配置完的规则，但写回文档前必须剔除（空规则会让 C 端整块不可见）
  return advanced.value ? sanitizeCondition(group.value) : loginScope.value;
}

watch(
  () => props.section,
  (section) => {
    if (!section) return;
    form.backgroundColor = section.style.backgroundColor;
    const gradient = section.style.backgroundGradient;
    form.backgroundGradient = {
      direction: gradient?.direction || 'to right',
      endColor: gradient?.endColor || '',
      startColor: gradient?.startColor || '',
    };
    // 起止色都为空且无纯色即视为无背景，兼容没有该字段的历史草稿
    const hasGradient = Boolean(
      form.backgroundGradient.startColor || form.backgroundGradient.endColor,
    );
    if (hasGradient) {
      backgroundMode.value = 'gradient';
    } else {
      backgroundMode.value = form.backgroundColor ? 'solid' : 'none';
    }
    form.backgroundImage = section.style.backgroundImage;
    form.horizontalScroll = section.style.horizontalScroll;
    form.marginX = section.style.marginX;
    form.marginY = section.style.marginY;
    form.name = section.name ?? '';
    form.paddingX = section.style.paddingX;
    form.paddingY = section.style.paddingY;
    form.radius = section.style.radius;
    form.sticky = section.style.sticky;

    const condition = section.style.condition;
    if (typeof condition === 'string') {
      advanced.value = false;
      loginScope.value =
        condition === 'guest' || condition === 'login' ? condition : 'always';
      group.value = { logic: 'and', rules: [] };
    } else {
      advanced.value = true;
      loginScope.value = 'always';
      group.value = cloneDesignerValue(condition);
    }
  },
  { immediate: true, deep: true },
);

onMounted(async () => {
  try {
    const [levels, tags] = await Promise.all([
      getMemberLevelList() as Promise<
        undefined | { id: string; levelName: string }[]
      >,
      getMemberTagList() as Promise<
        undefined | { id: string; tagName: string }[]
      >,
    ]);
    levelOptions.value = (levels ?? []).map((level) => ({
      id: level.id,
      name: level.levelName,
    }));
    tagOptions.value = (tags ?? []).map((tag) => ({
      id: tag.id,
      name: tag.tagName,
    }));
  } catch {
    // 下拉数据源加载失败时保持空选项，不阻断装修主流程
  }
});

function onAdvancedChange(enabled: boolean | number | string) {
  advanced.value = Boolean(enabled);
  if (advanced.value && group.value.rules.length === 0) {
    group.value.rules = [createRule('memberLevel')];
  }
  emitPatch('section-condition');
}

function addRule() {
  group.value.rules.push(createRule('memberLevel'));
  emitPatch('section-condition');
}

function removeRule(index: number) {
  group.value.rules.splice(index, 1);
  emitPatch('section-condition');
}

function replaceRule(rule: SectionConditionRule, type: RuleTypeOption) {
  const index = group.value.rules.indexOf(rule);
  if (index === -1) return;
  group.value.rules.splice(index, 1, createRule(type));
  emitPatch('section-condition');
}

const ruleRows = computed(() => group.value.rules);

function emitPatch(groupKey: string) {
  const style: Partial<SectionStyle> = cloneDesignerValue({
    backgroundColor: form.backgroundColor,
    backgroundGradient: form.backgroundGradient,
    backgroundImage: form.backgroundImage,
    condition: resolveCondition(),
    horizontalScroll: form.horizontalScroll,
    marginX: form.marginX,
    marginY: form.marginY,
    paddingX: form.paddingX,
    paddingY: form.paddingY,
    radius: form.radius,
    sticky: form.sticky,
  });
  emit(
    'patch',
    {
      name: form.name,
      style,
    },
    groupKey,
  );
}

/**
 * 无背景 / 纯色 / 渐变三态。
 * 无背景会同时清空纯色与渐变，让下层页面背景透出——区块背景画在页面上层，
 * 不透明时会盖住页面背景，这是运营最容易误解的一层顺序。
 */
function onBackgroundModeChange(value: boolean | number | string | undefined) {
  let mode: BackgroundMode = 'none';
  if (value === 'gradient') {
    mode = 'gradient';
  } else if (value === 'solid') {
    mode = 'solid';
  }
  backgroundMode.value = mode;
  if (mode === 'gradient') {
    if (
      !form.backgroundGradient.startColor &&
      !form.backgroundGradient.endColor
    ) {
      // 以下 hex/rgba 是写进装修文档的区块背景默认值（C 端渲染数据），
      // 不是管理端装饰色，因此保持固定色值、不换 Element 令牌
      form.backgroundGradient.startColor =
        form.backgroundColor || 'rgba(255, 255, 255, 1)';
      form.backgroundGradient.endColor = '#f5f7fa';
    }
  } else if (mode === 'solid') {
    form.backgroundGradient.startColor = '';
    form.backgroundGradient.endColor = '';
    form.backgroundColor = form.backgroundColor || '#ffffff';
  } else {
    form.backgroundGradient.startColor = '';
    form.backgroundGradient.endColor = '';
    form.backgroundColor = '';
  }
  emitPatch('section-background');
}

/** 一键套用卡片预设：通栏色带 → 带留白的圆角卡片 */
function applyCardPreset() {
  form.marginX = CARD_SECTION_PRESET.marginX;
  form.marginY = CARD_SECTION_PRESET.marginY;
  form.paddingX = CARD_SECTION_PRESET.paddingX;
  form.paddingY = CARD_SECTION_PRESET.paddingY;
  form.radius = CARD_SECTION_PRESET.radius;
  emitPatch('section-card-preset');
}
</script>

<template>
  <div v-if="section" class="section-settings">
    <ElForm label-position="top" @submit.prevent>
      <ElFormItem label="区块名称">
        <ElInput
          v-model="form.name"
          maxlength="20"
          placeholder="默认区块"
          @update:model-value="emitPatch('section-name')"
        />
      </ElFormItem>
      <ElFormItem label="区块背景色">
        <ElRadioGroup
          :model-value="backgroundMode"
          @change="onBackgroundModeChange"
        >
          <ElRadioButton value="none">无背景</ElRadioButton>
          <ElRadioButton value="solid">纯色</ElRadioButton>
          <ElRadioButton value="gradient">渐变</ElRadioButton>
        </ElRadioGroup>
        <div class="form-tip">
          区块背景画在页面背景之上：不透明时会盖住页面背景，选「无背景」才透出页面背景/主题底色
        </div>
      </ElFormItem>
      <ElFormItem v-if="backgroundMode === 'solid'" label="颜色">
        <ElColorPicker
          v-model="form.backgroundColor"
          show-alpha
          @update:model-value="emitPatch('section-background')"
        />
      </ElFormItem>
      <template v-else-if="backgroundMode === 'gradient'">
        <ElFormItem label="渐变颜色">
          <div class="gradient-colors">
            <ElColorPicker
              v-model="form.backgroundGradient.startColor"
              show-alpha
              @update:model-value="emitPatch('section-background')"
            />
            <span class="gradient-arrow">→</span>
            <ElColorPicker
              v-model="form.backgroundGradient.endColor"
              show-alpha
              @update:model-value="emitPatch('section-background')"
            />
          </div>
        </ElFormItem>
        <ElFormItem label="渐变方向">
          <ElSelect
            v-model="form.backgroundGradient.direction"
            class="gradient-direction"
            @update:model-value="emitPatch('section-background')"
          >
            <ElOption label="从左到右" value="to right" />
            <ElOption label="从上到下" value="to bottom" />
            <ElOption label="左上到右下" value="135deg" />
            <ElOption label="右上到左下" value="225deg" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="渐变预览">
          <div
            class="gradient-preview"
            :style="{ background: gradientPreview }"
          ></div>
        </ElFormItem>
      </template>
      <ElFormItem label="区块背景图">
        <ElInput
          v-model="form.backgroundImage"
          clearable
          placeholder="图片地址"
          @update:model-value="emitPatch('section-background')"
        />
        <div class="form-tip">填写后优先于背景色/渐变</div>
      </ElFormItem>
      <ElFormItem label="左右内边距（px）">
        <ElInputNumber
          v-model="form.paddingX"
          :max="60"
          :min="0"
          controls-position="right"
          @update:model-value="emitPatch('section-padding')"
        />
      </ElFormItem>
      <ElFormItem label="上下内边距（px）">
        <ElInputNumber
          v-model="form.paddingY"
          :max="60"
          :min="0"
          controls-position="right"
          @update:model-value="emitPatch('section-padding')"
        />
      </ElFormItem>
      <ElFormItem label="左右外边距（px）">
        <ElInputNumber
          v-model="form.marginX"
          :max="60"
          :min="0"
          controls-position="right"
          @update:model-value="emitPatch('section-margin')"
        />
      </ElFormItem>
      <ElFormItem label="上下外边距（px）">
        <ElInputNumber
          v-model="form.marginY"
          :max="60"
          :min="0"
          controls-position="right"
          @update:model-value="emitPatch('section-margin')"
        />
      </ElFormItem>
      <ElFormItem label="区块圆角（px）">
        <ElInputNumber
          v-model="form.radius"
          :max="60"
          :min="0"
          controls-position="right"
          @update:model-value="emitPatch('section-radius')"
        />
      </ElFormItem>
      <ElFormItem>
        <ElTooltip content="留白 + 圆角，通栏色带变成独立卡片">
          <ElButton size="small" @click="applyCardPreset">
            套用卡片样式
          </ElButton>
        </ElTooltip>
      </ElFormItem>
      <ElFormItem label="横向滚动（组件横滑排列）">
        <ElSwitch
          v-model="form.horizontalScroll"
          @update:model-value="emitPatch('section-scroll')"
        />
      </ElFormItem>
      <ElFormItem label="吸顶">
        <ElSwitch
          v-model="form.sticky"
          @update:model-value="emitPatch('section-sticky')"
        />
        <div class="form-tip">
          吸顶适合较矮的区块（如 Tab 栏）；区块过高会长期占屏、遮挡后续内容
        </div>
      </ElFormItem>
      <ElFormItem label="条件显示（登录态）">
        <ElRadioGroup
          :model-value="loginScope"
          @change="emitPatch('section-condition')"
        >
          <ElRadioButton value="always">全部用户</ElRadioButton>
          <ElRadioButton value="login">仅登录用户</ElRadioButton>
          <ElRadioButton value="guest">仅游客</ElRadioButton>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem label="高级条件组合">
        <ElSwitch :model-value="advanced" @change="onAdvancedChange" />
      </ElFormItem>
      <template v-if="advanced">
        <ElFormItem label="组合逻辑">
          <ElRadioGroup
            :model-value="group.logic"
            @change="emitPatch('section-condition')"
          >
            <ElRadioButton value="and">且（同时满足）</ElRadioButton>
            <ElRadioButton value="or">或（满足其一）</ElRadioButton>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem
          v-for="(rule, index) in ruleRows"
          :key="index"
          :label="`规则 ${index + 1}`"
        >
          <div class="rule-row">
            <ElSelect
              :model-value="editableRuleType(rule)"
              class="rule-type"
              @change="(value: RuleTypeOption) => replaceRule(rule, value)"
            >
              <ElOption
                v-for="option in RULE_TYPE_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </ElSelect>
            <ElSelect
              v-if="rule.type === 'memberLevel'"
              v-model="rule.memberLevelIds"
              class="rule-content"
              multiple
              filterable
              placeholder="选择会员等级"
              @change="emitPatch('section-condition')"
            >
              <ElOption
                v-for="option in levelOptions"
                :key="option.id"
                :label="option.name"
                :value="option.id"
              />
            </ElSelect>
            <ElSelect
              v-else-if="rule.type === 'userTag'"
              v-model="rule.userTagIds"
              class="rule-content"
              multiple
              filterable
              placeholder="选择用户标签"
              @change="emitPatch('section-condition')"
            >
              <ElOption
                v-for="option in tagOptions"
                :key="option.id"
                :label="option.name"
                :value="option.id"
              />
            </ElSelect>
            <template v-else-if="rule.type === 'timeRange'">
              <ElTimePicker
                v-model="rule.startTime"
                class="rule-time"
                format="HH:mm"
                placeholder="开始时间"
                value-format="HH:mm"
                @change="emitPatch('section-condition')"
              />
              <ElTimePicker
                v-model="rule.endTime"
                class="rule-time"
                format="HH:mm"
                placeholder="结束时间"
                value-format="HH:mm"
                @change="emitPatch('section-condition')"
              />
            </template>
            <ElTooltip content="删除规则">
              <ElButton
                :icon="CircleClose"
                aria-label="删除规则"
                circle
                text
                type="danger"
                @click="removeRule(index)"
              />
            </ElTooltip>
          </div>
        </ElFormItem>
        <ElFormItem>
          <ElButton :icon="Plus" size="small" @click="addRule">
            添加规则
          </ElButton>
        </ElFormItem>
      </template>
    </ElForm>
  </div>
  <ElEmpty v-else :image-size="64" description="请选择一个区块" />
</template>

<style scoped>
.gradient-colors {
  display: flex;
  gap: 8px;
  align-items: center;
}

.gradient-arrow {
  color: var(--el-text-color-secondary);
}

.gradient-direction {
  width: 100%;
}

.gradient-preview {
  width: 100%;
  height: 24px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.form-tip {
  width: 100%;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}

.rule-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  width: 100%;
}

.rule-type {
  flex-shrink: 0;
  width: 110px;
}

.rule-content {
  flex: 1;
  min-width: 140px;
}

.rule-time {
  width: 120px;
}
</style>
