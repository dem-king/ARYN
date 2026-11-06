<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import type { CategoryBadgeStyle } from '../goods-category/logic';

import { computed, defineAsyncComponent, reactive, ref, watch } from 'vue';

import {
  ElButton,
  ElColorPicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElImage,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElRadio,
  ElRadioGroup,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/product/goods-category-badge';

import {
  BADGE_BORDER_RADIUS_DEFAULT,
  BADGE_BORDER_RADIUS_MAX,
  BADGE_BORDER_RADIUS_MIN,
  BADGE_HEIGHT_DEFAULT,
  BADGE_HEIGHT_MAX,
  BADGE_HEIGHT_MIN,
  BADGE_ICON_URL_MAX_LENGTH,
  badgePreviewStyle,
  buildBadgeStyle,
  normalizeBadgeBorderRadius,
  normalizeBadgeHeight,
  normalizeBadgeIconUrl,
} from '../goods-category/logic';

const emit = defineEmits(['refresh']);

const SelectMaterial = defineAsyncComponent(
  () => import('#/components/select-material/index.vue'),
);

const visible = ref(false);
const loading = ref(false);
const formRef = ref<FormInstance>();

/** 默认配色沿用 C 端「荐」的现网绿，避免运营新增角标时先面对一片空白 */
const DEFAULT_BG_COLOR = '#00B578';
const DEFAULT_TEXT_COLOR = '#FFFFFF';
/** 常用色快捷值（绿/红/橙/蓝），在取色面板里一键点选 */
const BG_PRESET_COLORS = ['#00B578', '#FF4D2D', '#FA8C16', '#1677FF'];
const TEXT_PRESET_COLORS = ['#FFFFFF', '#000000'];

const form = reactive({
  id: '',
  badgeKey: '',
  badgeText: '',
  bgColor: DEFAULT_BG_COLOR,
  textColor: DEFAULT_TEXT_COLOR,
  /** 角标图片：空串 = 文字角标；非空时 C 端优先渲染图片 */
  iconUrl: '',
  /** 角标高度（rpx） */
  badgeHeight: BADGE_HEIGHT_DEFAULT,
  /** 圆角（rpx） */
  borderRadius: BADGE_BORDER_RADIUS_DEFAULT,
  sort: 0,
  status: '0',
});

/**
 * 弹窗内实时预览：有合法图片地址时展示图片角标（正方形），否则展示文字角标。
 * 文字为空时用「荐」占位；尺寸统一按 750rpx 设计稿折算成 px（rpx / 2）。
 */
const previewBadge = computed<CategoryBadgeStyle | null>(() => {
  const iconUrl = normalizeBadgeIconUrl(form.iconUrl);
  return buildBadgeStyle({
    badgeKey: form.badgeKey,
    badgeText: form.badgeText.trim() || (iconUrl ? '' : '荐'),
    bgColor: form.bgColor || DEFAULT_BG_COLOR,
    textColor: form.textColor || DEFAULT_TEXT_COLOR,
    iconUrl,
    badgeHeight: form.badgeHeight,
    borderRadius: form.borderRadius,
  });
});
const previewImageUrl = computed(() => previewBadge.value?.iconUrl ?? '');
const previewText = computed(() => previewBadge.value?.text || '荐');
const previewStyle = computed(() =>
  previewBadge.value ? badgePreviewStyle(previewBadge.value) : {},
);

type ValidatorCallback = (error?: Error) => void;

/** 角标文字：按字符（codepoint）计 1-2 个；配置了图片后文字可以留空 */
function validateBadgeText(
  _rule: any,
  value: string,
  callback: ValidatorCallback,
) {
  const text = String(value ?? '').trim();
  if (text === '' && normalizeBadgeIconUrl(form.iconUrl) === '') {
    callback(new Error('请输入角标文字，或配置角标图片'));
    return;
  }
  // 按 codepoint 计数，避免 emoji 被按 UTF-16 长度误判（与后端口径一致）
  if ([...text].length > 2) {
    callback(new Error('角标文字最多 2 个字符'));
    return;
  }
  callback();
}

/** 图片地址：允许留空；非空时必须是 http(s):// 或 / 开头且 ≤512 字符（与后端一致） */
function validateIconUrl(
  _rule: any,
  value: string,
  callback: ValidatorCallback,
) {
  const iconUrl = String(value ?? '').trim();
  if (iconUrl !== '' && normalizeBadgeIconUrl(iconUrl) === '') {
    callback(
      new Error(
        `图片地址需以 http(s):// 或 / 开头，且不超过 ${BADGE_ICON_URL_MAX_LENGTH} 个字符`,
      ),
    );
    return;
  }
  callback();
}

/** 高度/圆角：必须是合法范围内的整数（rpx） */
function createIntegerRangeValidator(label: string, min: number, max: number) {
  return (_rule: any, value: any, callback: ValidatorCallback) => {
    const numberValue = Number(value);
    const isEmpty =
      value === null ||
      value === undefined ||
      String(value).trim() === '' ||
      !Number.isFinite(numberValue);
    if (isEmpty) {
      callback(new Error(`请输入${label}（${min}-${max} rpx）`));
      return;
    }
    if (
      !Number.isInteger(numberValue) ||
      numberValue < min ||
      numberValue > max
    ) {
      callback(new Error(`${label}需为 ${min}-${max} 的整数（rpx）`));
      return;
    }
    callback();
  };
}

/** 颜色必须是 #RRGGBB（与后端正则一致） */
function createColorValidator(label: string) {
  return (_rule: any, value: string, callback: ValidatorCallback) => {
    const color = String(value ?? '').trim();
    if (!/^#[0-9a-f]{6}$/i.test(color)) {
      callback(new Error(`请选择${label}`));
      return;
    }
    callback();
  };
}

const rules = {
  badgeText: [{ validator: validateBadgeText, trigger: 'blur' }],
  iconUrl: [{ validator: validateIconUrl, trigger: 'change' }],
  bgColor: [{ validator: createColorValidator('角标底色'), trigger: 'change' }],
  textColor: [
    { validator: createColorValidator('角标字色'), trigger: 'change' },
  ],
  badgeHeight: [
    {
      validator: createIntegerRangeValidator(
        '角标高度',
        BADGE_HEIGHT_MIN,
        BADGE_HEIGHT_MAX,
      ),
      trigger: 'change',
    },
  ],
  borderRadius: [
    {
      validator: createIntegerRangeValidator(
        '圆角',
        BADGE_BORDER_RADIUS_MIN,
        BADGE_BORDER_RADIUS_MAX,
      ),
      trigger: 'change',
    },
  ],
};

/** 图片增删会改变「文字是否必填」的口径，切换后立即重校验文字与图片地址 */
watch(
  () => form.iconUrl,
  () => {
    formRef.value?.validateField('badgeText', () => {});
    formRef.value?.validateField('iconUrl', () => {});
  },
);

function reset() {
  Object.assign(form, {
    id: '',
    badgeKey: '',
    badgeText: '',
    bgColor: DEFAULT_BG_COLOR,
    textColor: DEFAULT_TEXT_COLOR,
    iconUrl: '',
    badgeHeight: BADGE_HEIGHT_DEFAULT,
    borderRadius: BADGE_BORDER_RADIUS_DEFAULT,
    sort: 0,
    status: '0',
  });
  formRef.value?.clearValidate();
}

async function open(row?: any) {
  reset();
  visible.value = true;
  if (!row?.id) {
    return;
  }
  // 先落 id：详情接口失败时也只会走「修改」，不会误变成新增
  form.id = row.id;
  try {
    const detail: any = await getById(row.id);
    // 尺寸/图片走与列表一致的归一规则，历史脏数据不会把弹窗带崩
    Object.assign(form, {
      ...detail,
      badgeText: String(detail?.badgeText ?? ''),
      iconUrl: normalizeBadgeIconUrl(detail?.iconUrl),
      badgeHeight: normalizeBadgeHeight(detail?.badgeHeight),
      borderRadius: normalizeBadgeBorderRadius(detail?.borderRadius),
    });
  } catch {
    // 请求层已提示错误，这里保留 id 让用户可以重试打开
  }
}

async function submit() {
  await formRef.value?.validate();
  loading.value = true;
  try {
    const payload = {
      ...form,
      badgeText: form.badgeText.trim(),
      iconUrl: normalizeBadgeIconUrl(form.iconUrl),
      badgeHeight: normalizeBadgeHeight(form.badgeHeight),
      borderRadius: normalizeBadgeBorderRadius(form.borderRadius),
    };
    await (form.id ? editObj(payload) : addObj(payload));
    ElMessage.success(form.id ? '修改成功' : '新增成功');
    visible.value = false;
    emit('refresh');
  } finally {
    loading.value = false;
  }
}

defineExpose({ open });
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="form.id ? '修改角标' : '新增角标'"
    width="560px"
  >
    <ElForm ref="formRef" :model="form" :rules="rules" label-width="auto">
      <ElFormItem label="角标文字" prop="badgeText">
        <ElInput v-model="form.badgeText" maxlength="2" show-word-limit />
      </ElFormItem>
      <ElFormItem label="角标图片" prop="iconUrl">
        <div class="badge-image-field">
          <SelectMaterial v-model="form.iconUrl" :can-choose-images-num="1" />
          <span class="badge-preview-field__tip">
            配置后 C 端优先渲染图片，角标文字可留空
          </span>
        </div>
      </ElFormItem>
      <ElFormItem label="角标底色" prop="bgColor">
        <div class="badge-color-field">
          <ElColorPicker
            v-model="form.bgColor"
            :clearable="false"
            :predefine="BG_PRESET_COLORS"
            color-format="hex"
          />
          <span class="badge-color-field__value">{{ form.bgColor }}</span>
        </div>
      </ElFormItem>
      <ElFormItem label="角标字色" prop="textColor">
        <div class="badge-color-field">
          <ElColorPicker
            v-model="form.textColor"
            :clearable="false"
            :predefine="TEXT_PRESET_COLORS"
            color-format="hex"
          />
          <span class="badge-color-field__value">{{ form.textColor }}</span>
        </div>
      </ElFormItem>
      <ElFormItem label="角标高度" prop="badgeHeight">
        <ElInputNumber
          v-model="form.badgeHeight"
          :min="BADGE_HEIGHT_MIN"
          :max="BADGE_HEIGHT_MAX"
          :step="1"
          :precision="0"
          controls-position="right"
        >
          <template #suffix>rpx</template>
        </ElInputNumber>
      </ElFormItem>
      <ElFormItem label="圆角" prop="borderRadius">
        <ElInputNumber
          v-model="form.borderRadius"
          :min="BADGE_BORDER_RADIUS_MIN"
          :max="BADGE_BORDER_RADIUS_MAX"
          :step="1"
          :precision="0"
          controls-position="right"
        >
          <template #suffix>rpx</template>
        </ElInputNumber>
      </ElFormItem>
      <ElFormItem label="效果预览">
        <div class="badge-preview-field">
          <ElImage
            v-if="previewImageUrl"
            class="badge-preview-image"
            :src="previewImageUrl"
            :style="previewStyle"
            :preview-src-list="[previewImageUrl]"
            :preview-teleported="true"
            fit="cover"
          />
          <span v-else class="badge-preview" :style="previewStyle">
            {{ previewText }}
          </span>
          <span class="badge-preview-field__tip">按 750rpx 设计稿等比预览</span>
        </div>
      </ElFormItem>
      <ElFormItem label="排序" prop="sort">
        <ElInputNumber
          v-model="form.sort"
          :min="0"
          :max="9999"
          controls-position="right"
        />
      </ElFormItem>
      <ElFormItem label="状态" prop="status">
        <ElRadioGroup v-model="form.status">
          <ElRadio value="0">启用</ElRadio>
          <ElRadio value="1">停用</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <div class="hx-form-footer">
        <ElButton @click="visible = false">取消</ElButton>
        <ElButton type="primary" :loading="loading" @click="submit">
          确定
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>

<style lang="scss" scoped>
.badge-color-field {
  display: flex;
  gap: 10px;
  align-items: center;
}

.badge-color-field__value {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
}

.badge-image-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
}

.badge-preview-field {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.badge-preview-field__tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.badge-preview-image {
  vertical-align: middle;
}

/* 文字角标：高度/行高/字号/左右内边距/圆角均由 badgePreviewStyle 动态下发（rpx/2） */
.badge-preview {
  display: inline-block;
  font-weight: 600;
  white-space: nowrap;
}
</style>
