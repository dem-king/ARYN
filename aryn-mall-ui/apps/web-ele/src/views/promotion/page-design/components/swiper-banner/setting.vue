<script lang="ts" setup>
import { defineAsyncComponent, ref, watch } from 'vue';

import { CircleCloseFilled, DCaret, Plus } from '@element-plus/icons-vue';
import { ElButton, ElForm, ElFormItem, ElIcon } from 'element-plus';
import draggable from 'vuedraggable';

const props = defineProps<{ modelValue: any }>();
const emit = defineEmits(['update:modelValue']);

const SelectMaterial = defineAsyncComponent(
  () => import('#/components/select-material/index.vue'),
);
const LinkUrl = defineAsyncComponent(
  () => import('../common/link-url/index.vue'),
);

const limitNum = ref(10);
const dragOptions = {
  animation: 300,
  group: 'description',
  disabled: false,
  ghostClass: 'ghost',
};

const defaultConfig = {
  imageList: [],
  interval: 3000,
  indicatorDots: true,
  indicatorColor: 'rgba(0, 0, 0, 0.3)',
  indicatorActiveColor: 'rgba(255, 255, 255, 1)',
  height: 180,
  borderRadius: 0,
  commonStyle: {
    styleTopMargin: 0,
    styleBottomMargin: 0,
    styleLeftMargin: 10,
    styleRightMargin: 10,
    styleTopPadding: 0,
    styleBottomPadding: 0,
    styleLeftPadding: 0,
    styleRightPadding: 0,
    styleLtRadius: 0,
    styleRtRadius: 0,
    styleLbRadius: 0,
    styleRbRadius: 0,
    bgColorDirection: 'to right',
    bgStartColor: '',
    bgEndColor: '',
    bgPicUrl: '',
  },
};

const form = ref({ ...defaultConfig, ...props.modelValue });

watch(
  form,
  (val) => {
    emit('update:modelValue', val);
  },
  { deep: true, immediate: true },
);

const add = () => {
  if (form.value.imageList.length < limitNum.value) {
    form.value.imageList.push({ url: '', linkUrl: '' });
  }
};

const del = (index: number) => {
  form.value.imageList.splice(index, 1);
};
</script>

<template>
  <div class="setting-base">
    <div class="setting-title">
      <p>轮播图</p>
      <div></div>
    </div>
    <div class="setting-form">
      <ElForm :model="form" label-width="80px">
        <ElFormItem label="图片设置">
          <p class="form-tip">可以上下拖动更改顺序</p>
        </ElFormItem>
        <draggable
          v-model="form.imageList"
          v-bind="dragOptions"
          animation="700"
          item-key="id"
          @dragover.prevent
        >
          <template #item="{ element, index }">
            <div class="content-item">
              <ElIcon size="24px" style="cursor: move"><DCaret /></ElIcon>
              <div class="left-img">
                <SelectMaterial
                  v-model="element.url"
                  :can-choose-images-num="1"
                />
              </div>
              <div class="img-info">
                <div class="info-top">
                  <div>
                    <p>链接配置</p>
                  </div>
                </div>
                <LinkUrl v-model="element.linkUrl" />
              </div>
              <div class="delete-btn">
                <ElButton link @click="del(index)">
                  <ElIcon><CircleCloseFilled /></ElIcon>
                </ElButton>
              </div>
            </div>
          </template>
        </draggable>
        <div class="add-btn">
          <ElButton
            @click="add"
            v-if="form.imageList && form.imageList.length < limitNum"
          >
            <ElIcon :size="20"><Plus /></ElIcon>
            添加图片（{{ form.imageList.length }}/{{ limitNum }}）
          </ElButton>
        </div>
      </ElForm>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '#/views/promotion/page-design/components/common/common.scss' as *;

.content-item {
  display: flex;
  align-items: center;
  border-radius: 4px;

  .img-info {
    padding-left: 5px;

    p {
      padding-bottom: 10px;
    }

    .info-top {
      display: flex;
      justify-content: space-between;
    }
  }
}

.add-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 10px;
}
</style>
