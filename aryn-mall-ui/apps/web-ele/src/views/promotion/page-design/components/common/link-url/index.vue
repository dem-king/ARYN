<script lang="ts" setup>
import { defineAsyncComponent, ref, watch } from 'vue';

import { ArrowRightBold, CircleClose } from '@element-plus/icons-vue';
import {
  ElAside,
  ElButton,
  ElContainer,
  ElDialog,
  ElIcon,
  ElInput,
  ElMain,
  ElMenu,
  ElMenuItem,
  ElTooltip,
} from 'element-plus';

interface Value {
  name: string;
  value: string;
}
interface Props {
  modelValue?: null | Value;
  placeholder?: string;
}
const props = defineProps<Props>();
const emit = defineEmits(['update:modelValue']);
const linkList = defineAsyncComponent(() => import('./link-list.vue'));
const linkGoods = defineAsyncComponent(() => import('./link-goods.vue'));
const linkDiyPage = defineAsyncComponent(() => import('./link-diy-page.vue'));

// 定义子组件向父组件传值/事件
const linkValue = ref(props.modelValue);
const linkName = ref(props.modelValue?.name);
const linkObj = ref();
const dialog = ref(false);
const selectedMenuType = ref('mall');
const baseMenu = ref([
  { name: '商城页面', type: 'mall' },
  { name: '商品页面', type: 'goods' },
  { name: 'DIY页面', type: 'diy' },
]);
const height = ref(window.screen.availHeight - 400);
const onsubmit = () => {
  if (linkObj.value) {
    linkValue.value = linkObj.value;
    linkName.value = linkObj.value.name;
    emit('update:modelValue', linkValue.value);
  }
  dialog.value = false;
};
// 删除
const delLink = () => {
  linkName.value = undefined;
  linkValue.value = null;
};
watch(
  () => props.modelValue,
  () => {
    linkValue.value = props.modelValue;
    linkName.value = props.modelValue?.name;
  },
);
</script>
<template>
  <div class="link-url-container">
    <ElTooltip
      class="box-item"
      effect="dark"
      :content="linkName"
      placement="top"
    >
      <ElInput
        v-model="linkName"
        :placeholder="placeholder"
        readonly
        @click="dialog = true"
        style="width: 100%"
      >
        <template #append>
          <ElIcon v-if="linkValue && linkValue.name" @click="delLink">
            <CircleClose />
          </ElIcon>
          <ElIcon v-else @click="dialog = true">
            <ArrowRightBold />
          </ElIcon>
        </template>
      </ElInput>
    </ElTooltip>

    <ElDialog v-model="dialog" title="内容选择" width="70%">
      <ElContainer>
        <ElContainer>
          <ElAside width="220px">
            <ElMenu default-active="mall">
              <ElMenuItem
                :index="item.type"
                v-for="(item, index) in baseMenu"
                :key="index"
                @click="selectedMenuType = item.type"
              >
                <template #title>{{ item.name }}</template>
              </ElMenuItem>
            </ElMenu>
          </ElAside>
          <ElMain :style="{ height: `${height}px` }">
            <template v-if="selectedMenuType === 'mall'">
              <linkList v-model="linkObj" />
            </template>
            <template v-if="selectedMenuType === 'category'"> </template>
            <template v-if="selectedMenuType === 'goods'">
              <linkGoods v-model="linkObj" />
            </template>
            <template v-if="selectedMenuType === 'diy'">
              <linkDiyPage v-model="linkObj" />
            </template>
          </ElMain>
        </ElContainer>
      </ElContainer>

      <template #footer>
        <div class="dialog-footer">
          <ElButton type="primary" @click="onsubmit"> 确认 </ElButton>
        </div>
      </template>
    </ElDialog>
  </div>
</template>
<style lang="scss" scoped>
.link-url-container {
  display: flex;
  align-items: center;
  width: 100%;

  .link-url-icon {
    font-size: 14px;
  }
}

:deep(.el-input-group__append) {
  cursor: pointer;
}
</style>
