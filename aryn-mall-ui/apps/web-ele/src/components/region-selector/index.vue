<script setup lang="ts">
import { reactive, ref, watch } from 'vue';

import { ElButton, ElCheckbox, ElCheckboxGroup, ElDialog } from 'element-plus';

interface RegionItem {
  code: string;
  name: string;
  children: RegionItem[];
  checked?: boolean;
  indeterminate?: boolean;
}
const props = withDefaults(
  defineProps<{
    disabledCities?: string[];
    modelValue: string[];
    options: RegionItem[];
  }>(),
  {
    disabledCities: () => [],
    modelValue: () => [],
    options: () => [],
  },
);

const emit = defineEmits<{
  (e: 'update:modelValue', value: string[]): void;
}>();

const state = reactive({
  dialog: false,
  selectedCities: [] as string[],
});

const selectedText = ref();

const handleProvinceChange = (province: RegionItem, checked: any) => {
  const cityCodes = province.children.map((city) => city.code);
  if (checked) {
    state.selectedCities.push(
      ...cityCodes.filter((code) => !state.selectedCities.includes(code)),
    );
  } else {
    state.selectedCities = state.selectedCities.filter(
      (code) => !cityCodes.includes(code),
    );
  }
  province.indeterminate = false;
};

const handleCitiesChange = (province: RegionItem, value: any[]) => {
  const provinceCityCodes = new Set(province.children.map((city) => city.code));
  const selectedProvinceCities = value.filter((code) =>
    provinceCityCodes.has(code),
  );

  province.checked = selectedProvinceCities.length === province.children.length;
  province.indeterminate =
    selectedProvinceCities.length > 0 &&
    selectedProvinceCities.length < province.children.length;
};
const handleRegion = () => {
  state.dialog = true;
  // 初始化省份选中状态
  props.options.forEach((province) => {
    if (province.children && province.children.length > 0) {
      const cityCodes = province.children.map((city) => city.code);
      const selectedCount = cityCodes.filter((code) =>
        state.selectedCities.includes(code),
      ).length;

      province.checked = selectedCount === province.children.length;
      province.indeterminate =
        selectedCount > 0 && selectedCount < province.children.length;
    } else {
      province.checked = false;
      province.indeterminate = false;
    }
  });
};

const onSubmit = () => {
  emit('update:modelValue', state.selectedCities);
  if (state.selectedCities.length === 0) {
    return;
  }
  const cityNames = props.options
    .filter((province) => province.children && province.children.length > 0)
    .flatMap((province) =>
      province.children
        .filter((city) => state.selectedCities.includes(city.code))
        .map((city) => city.name),
    );
  selectedText.value = cityNames.join(',');
  state.dialog = false;
};
// 监听 modelValue 变化
watch(
  () => props.modelValue,
  (newVal) => {
    state.selectedCities = [...newVal];
  },
  { immediate: true },
);
watch(
  () => props.options,
  (newVal) => {
    if (!newVal || newVal.length === 0) {
      selectedText.value = null;
    } else {
      const cityNames = props.options
        .filter((province) => province.children && province.children.length > 0)
        .flatMap((province) =>
          province.children
            .filter((city) => state.selectedCities.includes(city.code))
            .map((city) => city.name),
        );
      selectedText.value = cityNames.join(',');
    }
  },
  { immediate: true },
);
</script>

<template>
  <div>
    <div @click="handleRegion" class="select-trigger">
      {{ selectedText ?? '请选择地区' }}
    </div>
    <ElDialog
      v-model="state.dialog"
      width="80%"
      title="地区选择器"
      append-to-body
    >
      <div class="region-wrapper">
        <div
          v-for="province in props.options"
          :key="province.code"
          class="province-item"
        >
          <ElCheckbox
            v-model="province.checked"
            :indeterminate="province.indeterminate"
            @change="(val) => handleProvinceChange(province, val)"
            :disabled="
              province.children.every((city) =>
                props.disabledCities?.includes(city.code),
              )
            "
          >
            {{ province.name }}
          </ElCheckbox>
          <div class="city-list">
            <ElCheckboxGroup
              v-model="state.selectedCities"
              @change="(val) => handleCitiesChange(province, val)"
            >
              <ElCheckbox
                v-for="city in province.children"
                :key="city.code"
                :value="city.code"
                :disabled="props.disabledCities?.includes(city.code)"
              >
                {{ city.name }}
              </ElCheckbox>
            </ElCheckboxGroup>
          </div>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <ElButton @click="state.dialog = false">取消</ElButton>
          <ElButton type="primary" @click="onSubmit">确定</ElButton>
        </span>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped lang="scss">
.select-trigger {
  min-height: 20px;
  padding: 5px 0;
  cursor: pointer;
}

.region-wrapper {
  height: 500px;
  padding: 20px;
  overflow-y: auto;

  .province-item {
    padding-bottom: 15px;
    margin-bottom: 20px;
    border-bottom: 1px solid #ebeef5;

    &:last-child {
      border-bottom: none;
    }

    .city-list {
      padding-left: 25px;
      margin-top: 10px;

      .el-checkbox {
        width: 120px;
        margin-right: 15px;
        margin-bottom: 10px;
      }
    }
  }
}
</style>
