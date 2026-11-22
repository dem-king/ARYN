<script setup lang="ts">
import type { ServiceId, UserCenterSettings } from '../schema/user-center';
import type {
  UserCenterModule,
  UserCenterPreviewMode,
} from '../schema/user-center-preview';

import { computed, defineAsyncComponent } from 'vue';

import { Close } from '@element-plus/icons-vue';
import {
  ElButton,
  ElCollapse,
  ElCollapseItem,
  ElColorPicker,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
} from 'element-plus';

import {
  normalizeUserCenter,
  userCenterOrderIds,
  userCenterServiceIds,
} from '../schema/user-center';
import {
  assetLabels,
  customIcons,
  orders,
  recommendationLabels,
  services,
  workbenches,
} from '../schema/user-center-preview';
import UserCenterOrderList from './user-center-order-list.vue';

const props = defineProps<{
  active: '' | UserCenterModule;
  modelValue?: UserCenterSettings;
  previewMode: UserCenterPreviewMode;
}>();
const emit = defineEmits<{
  'update:active': [value: '' | UserCenterModule];
  'update:modelValue': [value: undefined | UserCenterSettings];
  'update:previewMode': [value: UserCenterPreviewMode];
}>();

const SelectMaterial = defineAsyncComponent(
  () => import('#/components/select-material/index.vue'),
);
const settings = computed(() => normalizeUserCenter(props.modelValue));
const defaults = normalizeUserCenter(undefined);
const cardStyles = { default: '默认', outline: '描边', tinted: '柔色' };
const remainingServices = computed(() =>
  userCenterServiceIds.filter(
    (id) => !settings.value.services.order.includes(id),
  ),
);
const previewModes = {
  personal: '个人会员',
  guest: '游客',
  enterprise: '企业会员',
  staff: '工作台示意',
};

function update<K extends keyof UserCenterSettings>(
  key: K,
  value: UserCenterSettings[K],
) {
  emit('update:modelValue', { ...settings.value, [key]: value });
}

function patch<
  K extends
    | 'assets'
    | 'hero'
    | 'orders'
    | 'recommendations'
    | 'services'
    | 'workbenches',
>(key: K, value: Partial<UserCenterSettings[K]>) {
  update(key, { ...settings.value[key], ...value });
}

function addService(id: ServiceId | undefined) {
  if (id && remainingServices.value.includes(id)) {
    patch('services', { order: [...settings.value.services.order, id] });
  }
}

function resetActive() {
  if (props.active) update(props.active, defaults[props.active]);
}
</script>

<template>
  <div class="module-settings">
    <div class="module-settings__toolbar">
      <ElSelect
        :model-value="previewMode"
        aria-label="预览身份"
        size="small"
        @update:model-value="emit('update:previewMode', $event)"
      >
        <ElOption
          v-for="(label, value) in previewModes"
          :key="value"
          :label="label"
          :value="value"
        />
      </ElSelect>
      <ElButton
        text
        size="small"
        :disabled="!modelValue"
        @click="emit('update:modelValue', undefined)"
      >
        全部恢复默认
      </ElButton>
    </div>
    <ElForm label-position="top" size="small">
      <ElCollapse
        accordion
        :model-value="active"
        @update:model-value="
          emit('update:active', $event as UserCenterModule | '')
        "
      >
        <ElCollapseItem title="页头与会员" name="hero">
          <ElFormItem label="页头背景">
            <ElRadioGroup
              :model-value="settings.hero.appearance"
              @update:model-value="
                patch('hero', {
                  appearance:
                    $event as UserCenterSettings['hero']['appearance'],
                })
              "
            >
              <ElRadioButton value="theme">商城主题</ElRadioButton>
              <ElRadioButton value="gradient">渐变</ElRadioButton>
              <ElRadioButton value="image">图片</ElRadioButton>
            </ElRadioGroup>
          </ElFormItem>
          <div
            v-if="settings.hero.appearance === 'gradient'"
            class="module-settings__colors"
          >
            <ElFormItem label="起始色">
              <ElColorPicker
                :model-value="settings.hero.startColor"
                @update:model-value="
                  patch('hero', {
                    startColor: $event || defaults.hero.startColor,
                  })
                "
              />
            </ElFormItem>
            <ElFormItem label="结束色">
              <ElColorPicker
                :model-value="settings.hero.endColor"
                @update:model-value="
                  patch('hero', { endColor: $event || defaults.hero.endColor })
                "
              />
            </ElFormItem>
          </div>
          <ElFormItem
            v-if="settings.hero.appearance === 'image'"
            label="页头背景图"
          >
            <SelectMaterial
              :model-value="settings.hero.imageUrl"
              :can-choose-images-num="1"
              @update:model-value="patch('hero', { imageUrl: $event || '' })"
            />
          </ElFormItem>
          <ElFormItem label="会员徽章">
            <ElRadioGroup
              :model-value="settings.hero.memberStyle"
              @update:model-value="
                patch('hero', {
                  memberStyle:
                    $event as UserCenterSettings['hero']['memberStyle'],
                })
              "
            >
              <ElRadioButton value="glass">玻璃</ElRadioButton>
              <ElRadioButton value="solid">实色</ElRadioButton>
            </ElRadioGroup>
          </ElFormItem>
          <ElFormItem label="游客登录引导">
            <div class="module-settings__translations">
              <ElInput
                :model-value="settings.hero.loginHint.zh"
                placeholder="中文 · 留空使用默认文案"
                aria-label="中文登录引导"
                maxlength="72"
                @update:model-value="
                  patch('hero', {
                    loginHint: { ...settings.hero.loginHint, zh: $event },
                  })
                "
              />
              <ElInput
                :model-value="settings.hero.loginHint.en"
                placeholder="English · 默认文案"
                aria-label="英文登录引导"
                maxlength="72"
                @update:model-value="
                  patch('hero', {
                    loginHint: { ...settings.hero.loginHint, en: $event },
                  })
                "
              />
            </div>
          </ElFormItem>
          <ElFormItem label="登录后权益说明">
            <div class="module-settings__translations">
              <ElInput
                :model-value="settings.hero.memberDescription.zh"
                placeholder="中文 · 选填"
                aria-label="中文权益说明"
                maxlength="72"
                @update:model-value="
                  patch('hero', {
                    memberDescription: {
                      ...settings.hero.memberDescription,
                      zh: $event,
                    },
                  })
                "
              />
              <ElInput
                :model-value="settings.hero.memberDescription.en"
                placeholder="English · 选填"
                aria-label="英文权益说明"
                maxlength="72"
                @update:model-value="
                  patch('hero', {
                    memberDescription: {
                      ...settings.hero.memberDescription,
                      en: $event,
                    },
                  })
                "
              />
            </div>
          </ElFormItem>
        </ElCollapseItem>
        <ElCollapseItem title="个人资产" name="assets">
          <p class="module-settings__hint">仅在已登录的个人采购场景展示。</p>
          <ElFormItem label="卡片样式">
            <ElRadioGroup
              :model-value="settings.assets.style"
              @update:model-value="
                patch('assets', {
                  style: $event as UserCenterSettings['assets']['style'],
                })
              "
            >
              <ElRadioButton
                v-for="(label, value) in cardStyles"
                :key="value"
                :value="value"
              >
                {{ label }}
              </ElRadioButton>
            </ElRadioGroup>
          </ElFormItem>
          <UserCenterOrderList
            :order="settings.assets.order"
            :label-for="(id) => assetLabels[id]"
            @reorder="patch('assets', { order: $event })"
          />
        </ElCollapseItem>
        <ElCollapseItem title="我的订单" name="orders">
          <ElFormItem label="卡片标题">
            <div class="module-settings__translations">
              <ElInput
                :model-value="settings.orders.title.zh"
                placeholder="我的订单"
                aria-label="中文订单标题"
                maxlength="28"
                @update:model-value="
                  patch('orders', {
                    title: { ...settings.orders.title, zh: $event },
                  })
                "
              />
              <ElInput
                :model-value="settings.orders.title.en"
                placeholder="My Orders"
                aria-label="英文订单标题"
                maxlength="28"
                @update:model-value="
                  patch('orders', {
                    title: { ...settings.orders.title, en: $event },
                  })
                "
              />
            </div>
          </ElFormItem>
          <ElFormItem label="卡片样式">
            <ElRadioGroup
              :model-value="settings.orders.style"
              @update:model-value="
                patch('orders', {
                  style: $event as UserCenterSettings['orders']['style'],
                })
              "
            >
              <ElRadioButton
                v-for="(label, value) in cardStyles"
                :key="value"
                :value="value"
              >
                {{ label }}
              </ElRadioButton>
            </ElRadioGroup>
          </ElFormItem>
          <ElFormItem
            v-for="id in userCenterOrderIds"
            :key="id"
            :label="orders[id].label"
            class="module-settings__inline"
          >
            <ElSelect
              :model-value="settings.orders.icons[id] ?? 'default'"
              :aria-label="`${orders[id].label}图标`"
              @update:model-value="
                patch('orders', {
                  icons: { ...settings.orders.icons, [id]: $event },
                })
              "
            >
              <ElOption label="默认图标" value="default" />
              <ElOption
                v-for="(item, value) in customIcons"
                :key="value"
                :label="item.label"
                :value="value"
              />
            </ElSelect>
          </ElFormItem>
        </ElCollapseItem>
        <ElCollapseItem title="我的服务" name="services">
          <ElFormItem label="每行入口数">
            <ElRadioGroup
              :model-value="settings.services.columns"
              @update:model-value="
                patch('services', {
                  columns: $event as UserCenterSettings['services']['columns'],
                })
              "
            >
              <ElRadioButton
                v-for="columns in [3, 4, 5]"
                :key="columns"
                :value="columns"
              >
                {{ columns }} 列
              </ElRadioButton>
            </ElRadioGroup>
          </ElFormItem>
          <UserCenterOrderList
            :order="settings.services.order"
            :label-for="(id) => services[id].label"
            @reorder="patch('services', { order: $event })"
          >
            <template #default="{ id }">
              <ElSelect
                class="module-settings__icon-option"
                size="small"
                :model-value="settings.services.icons[id] ?? 'default'"
                :aria-label="`${services[id].label}图标`"
                @update:model-value="
                  patch('services', {
                    icons: { ...settings.services.icons, [id]: $event },
                  })
                "
              >
                <ElOption label="默认图标" value="default" />
                <ElOption
                  v-for="(item, value) in customIcons"
                  :key="value"
                  :label="item.label"
                  :value="value"
                />
              </ElSelect>
              <ElButton
                text
                size="small"
                :icon="Close"
                :aria-label="`移除${services[id].label}`"
                :title="`移除${services[id].label}`"
                @click="
                  patch('services', {
                    order: settings.services.order.filter(
                      (item) => item !== id,
                    ),
                  })
                "
              />
            </template>
          </UserCenterOrderList>
          <ElSelect
            v-if="remainingServices.length > 0"
            class="module-settings__add"
            placeholder="添加服务入口"
            aria-label="添加服务入口"
            :model-value="undefined"
            @update:model-value="addService"
          >
            <ElOption
              v-for="id in remainingServices"
              :key="id"
              :label="services[id].label"
              :value="id"
            />
          </ElSelect>
        </ElCollapseItem>
        <ElCollapseItem title="角色工作台" name="workbenches">
          <p class="module-settings__hint">
            入口按租户能力和用户资格展示。切换到「工作台示意」可查看布局。
          </p>
          <ElFormItem label="入口样式">
            <ElRadioGroup
              :model-value="settings.workbenches.style"
              @update:model-value="
                patch('workbenches', {
                  style: $event as UserCenterSettings['workbenches']['style'],
                })
              "
            >
              <ElRadioButton
                v-for="(label, value) in cardStyles"
                :key="value"
                :value="value"
              >
                {{ label }}
              </ElRadioButton>
            </ElRadioGroup>
          </ElFormItem>
          <UserCenterOrderList
            :order="settings.workbenches.order"
            :label-for="(id) => workbenches[id].label"
            @reorder="patch('workbenches', { order: $event })"
          >
            <template #default="{ id }">
              <ElSelect
                class="module-settings__icon-option"
                size="small"
                :model-value="settings.workbenches.icons[id] ?? 'default'"
                :aria-label="`${workbenches[id].label}图标`"
                @update:model-value="
                  patch('workbenches', {
                    icons: { ...settings.workbenches.icons, [id]: $event },
                  })
                "
              >
                <ElOption label="默认图标" value="default" />
                <ElOption
                  v-for="(item, value) in customIcons"
                  :key="value"
                  :label="item.label"
                  :value="value"
                />
              </ElSelect>
            </template>
          </UserCenterOrderList>
        </ElCollapseItem>
        <ElCollapseItem title="猜你喜欢" name="recommendations">
          <ElFormItem label="显示底部推荐">
            <ElSwitch
              :model-value="settings.recommendations.visible"
              @update:model-value="
                patch('recommendations', { visible: !!$event })
              "
            />
          </ElFormItem>
          <template v-if="settings.recommendations.visible">
            <ElFormItem label="推荐来源">
              <ElSelect
                :model-value="settings.recommendations.source"
                @update:model-value="
                  patch('recommendations', { source: $event })
                "
              >
                <ElOption
                  v-for="(label, value) in recommendationLabels"
                  :key="value"
                  :label="label"
                  :value="value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem label="推荐标题">
              <div class="module-settings__translations">
                <ElInput
                  :model-value="settings.recommendations.title.zh"
                  placeholder="猜你喜欢"
                  aria-label="中文推荐标题"
                  maxlength="28"
                  @update:model-value="
                    patch('recommendations', {
                      title: { ...settings.recommendations.title, zh: $event },
                    })
                  "
                />
                <ElInput
                  :model-value="settings.recommendations.title.en"
                  placeholder="You may also like"
                  aria-label="英文推荐标题"
                  maxlength="28"
                  @update:model-value="
                    patch('recommendations', {
                      title: { ...settings.recommendations.title, en: $event },
                    })
                  "
                />
              </div>
            </ElFormItem>
          </template>
        </ElCollapseItem>
        <ElCollapseItem title="运营内容位置" name="contentPosition">
          <ElRadioGroup
            :model-value="settings.contentPosition"
            @update:model-value="
              update(
                'contentPosition',
                $event as UserCenterSettings['contentPosition'],
              )
            "
          >
            <ElRadioButton value="beforeOrders">订单上方</ElRadioButton>
            <ElRadioButton value="afterServices">服务下方</ElRadioButton>
          </ElRadioGroup>
          <p class="module-settings__hint">
            公告、图片和其他拖入的运营组件会随装修区一起移动。
          </p>
        </ElCollapseItem>
      </ElCollapse>
      <ElButton
        v-if="active && modelValue"
        text
        class="module-settings__reset"
        @click="resetActive"
      >
        恢复此项默认
      </ElButton>
    </ElForm>
  </div>
</template>

<style scoped>
.module-settings {
  padding: 10px 14px 18px;
}

.module-settings__toolbar {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}

.module-settings__colors {
  display: flex;
  gap: 24px;
}

.module-settings__translations {
  display: grid;
  gap: 6px;
  width: 100%;
}

.module-settings__hint {
  margin: 0 0 12px;
  line-height: 1.45;
  color: var(--el-text-color-secondary);
}

.module-settings__reset {
  margin-top: 10px;
}

.module-settings__add {
  margin-top: 8px;
}

.module-settings__icon-option {
  width: 90px;
}

.module-settings__inline {
  display: grid;
  grid-template-columns: minmax(68px, 1fr) 2fr;
  align-items: center;
  margin-bottom: 8px;
}

.module-settings__inline :deep(.el-form-item__label) {
  margin: 0;
}

.module-settings :deep(.el-collapse-item__content) {
  padding-bottom: 14px;
}
</style>
