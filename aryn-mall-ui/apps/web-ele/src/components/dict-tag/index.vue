<script setup lang="ts">
import { computed } from 'vue';

import { ElTag } from 'element-plus';

type DictPrimitive = number | string;
type DictTagType =
  '' | 'danger' | 'default' | 'info' | 'primary' | 'success' | 'warning';

interface DictOption {
  label?: string;
  showClass?: DictTagType;
  value?: DictPrimitive;
}

interface Props {
  options?: Array<DictOption | DictPrimitive>;
  value?: DictPrimitive | DictPrimitive[] | null;
}

defineOptions({ name: 'DictTag' });

const props = withDefaults(defineProps<Props>(), {
  options: () => [],
  value: undefined,
});

const values = computed(() => {
  if (props.value === null || props.value === undefined) {
    return [];
  }
  return Array.isArray(props.value) ? props.value : [props.value];
});

const visibleOptions = computed(() => {
  return props.options
    .map((option) => {
      if (typeof option === 'object') {
        return {
          label: option.label ?? option.value ?? '',
          showClass: option.showClass,
          value: option.value,
        };
      }
      return { label: option, showClass: undefined, value: option };
    })
    .filter((option) =>
      values.value.some((value) => String(value) === String(option.value)),
    );
});
</script>

<template>
  <div>
    <template
      v-for="(item, index) in visibleOptions"
      :key="`${item.value}-${index}`"
    >
      <span v-if="!item.showClass || item.showClass === 'default'">
        {{ item.label }}
      </span>
      <ElTag v-else :type="item.showClass" :disable-transitions="true">
        {{ item.label }}
      </ElTag>
    </template>
  </div>
</template>

<style scoped></style>
