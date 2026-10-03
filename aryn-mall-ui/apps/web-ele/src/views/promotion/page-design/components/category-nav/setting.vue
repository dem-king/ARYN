<script lang="ts" setup>
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue';

import { CircleCloseFilled, DCaret, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInputNumber,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElSlider,
  ElSwitch,
  ElTabPane,
  ElTabs,
} from 'element-plus';
import draggable from 'vuedraggable';

import { getPage as getCategoryTree } from '#/api/product/goods-category';

const props = defineProps<{ modelValue: any }>();
const emit = defineEmits(['update:modelValue']);

const SelectMaterial = defineAsyncComponent(
  () => import('#/components/select-material/index.vue'),
);
const LinkUrl = defineAsyncComponent(
  () => import('../common/link-url/index.vue'),
);
const CommonStyle = defineAsyncComponent(
  () => import('../common/common-style/index.vue'),
);
const ColorPicker = defineAsyncComponent(
  () => import('../common/common-color-picker/index.vue'),
);

const active = ref('0');
const dragOptions = {
  animation: 300,
  group: 'description',
  disabled: false,
  ghostClass: 'ghost',
};

const defaultConfig = {
  navList: [],
  // 数据来源：static 手动配置（历史行为）；category 跟随分类（实时拉取一级分类）
  source: 'static',
  categoryMax: 8,
  showNum: 4,
  imgSize: 25,
  imgRadius: 0,
  fontColor: 'rgba(0, 0, 0, 1)',
  scrollShow: false,
  commonStyle: {
    styleTopMargin: 10,
    styleBottomMargin: 10,
    styleLeftMargin: 10,
    styleRightMargin: 10,
    styleTopPadding: 10,
    styleBottomPadding: 10,
    styleLeftPadding: 10,
    styleRightPadding: 10,
    styleLtRadius: 10,
    styleRtRadius: 10,
    styleLbRadius: 10,
    styleRbRadius: 10,
    bgColorDirection: 'to right',
    bgStartColor: 'rgba(255, 255, 255, 1)',
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
  form.value.navList.push({ url: '', text: '导航', linkUrl: '' });
};

const del = (index: number) => {
  form.value.navList.splice(index, 1);
};

// ---------- 一级分类选项（文字可选可输） ----------

interface CategoryOption {
  id: string;
  name: string;
  pic: string;
}

const categoryOptions = ref<CategoryOption[]>([]);

/** 所有分类配图集合：当前图标命中说明是自动带出的，可随重新选择覆盖 */
const categoryPicSet = computed(
  () => new Set(categoryOptions.value.map((o) => o.pic).filter(Boolean)),
);

onMounted(async () => {
  try {
    // 与预览「跟随分类」同一数据源、同一启用过滤（status '1' 为停用）
    const tree = (await getCategoryTree()) ?? [];
    categoryOptions.value = tree
      .filter((node: any) => node.status !== '1')
      .sort((a: any, b: any) => (Number(a.sort) || 0) - (Number(b.sort) || 0))
      .map((node: any) => ({
        id: String(node.id ?? ''),
        name: node.name,
        pic: node.categoryPic || '',
      }));
  } catch {
    // 拉取失败时退化为纯手动输入，不阻塞编辑器
    categoryOptions.value = [];
  }
});

/**
 * 选中真实分类名时联动链接与图片：链接为空、或本就指向某个一级分类时，
 * 直接改指所选分类（文字与链接一步对齐）；指向二级分类或非分类页的链接不动。
 * 图片同理：未配图或当前图是其他分类带出的才覆盖，手动上传的图保留。
 */
const onNavTextChange = (item: any, val: string) => {
  const matched = categoryOptions.value.find((o) => o.name === val);
  if (!matched) return;
  const link = item.linkUrl;
  const canSyncLink =
    !link ||
    (typeof link === 'object' &&
      link.type === 'category' &&
      !link.params?.categorySecondId);
  if (canSyncLink) {
    item.linkUrl = {
      type: 'category',
      targetId: matched.id,
      path: '',
      params: { categoryFirstId: matched.id, categorySecondId: '' },
    };
  }
  if (!item.url || categoryPicSet.value.has(item.url)) {
    item.url = matched.pic;
  }
};
</script>

<template>
  <div class="setting-base">
    <div class="setting-title">
      <p>分类导航</p>
      <div></div>
    </div>
    <div class="setting-form">
      <ElForm :model="form" label-width="80px">
        <ElTabs type="border-card" v-model="active">
          <ElTabPane label="组件内容" name="0">
            <ElFormItem label="数据来源">
              <ElRadioGroup v-model="form.source">
                <ElRadio value="static">手动配置</ElRadio>
                <ElRadio value="category">跟随分类</ElRadio>
              </ElRadioGroup>
            </ElFormItem>
            <template v-if="form.source === 'category'">
              <ElFormItem label="最多显示">
                <ElInputNumber
                  v-model="form.categoryMax"
                  :min="1"
                  :max="50"
                  :step="1"
                  step-strictly
                />
              </ElFormItem>
              <p class="form-tip">
                自动展示启用中的一级分类（按排序号排序），未配图的分类显示首字色块。
                分类变更后小程序端实时生效，无需重新发布装修页。
              </p>
            </template>
            <ElFormItem label="每行数量">
              <ElRadioGroup v-model="form.showNum">
                <ElRadio :value="4">4个</ElRadio>
                <ElRadio :value="5">5个</ElRadio>
              </ElRadioGroup>
            </ElFormItem>
            <template v-if="form.source !== 'category'">
              <ElFormItem label="导航设置">
                <p class="form-tip">
                  可以上下拖动更改顺序；文字支持选择一级分类或手动输入，选中分类后自动带出该分类的图片和链接
                </p>
              </ElFormItem>
              <draggable
                v-model="form.navList"
                v-bind="dragOptions"
                animation="700"
                item-key="id"
                @dragover.prevent
              >
                <template #item="{ element, index }">
                  <div class="content-item">
                    <ElIcon size="24px" style="cursor: move"><DCaret /></ElIcon>
                    <div class="nav-item-info">
                      <div class="nav-item-row">
                        <span class="nav-label">图标</span>
                        <SelectMaterial
                          v-model="element.url"
                          :can-choose-images-num="1"
                        />
                      </div>
                      <div class="nav-item-row">
                        <span class="nav-label">文字</span>
                        <ElSelect
                          v-model="element.text"
                          class="nav-text-select"
                          size="small"
                          filterable
                          allow-create
                          default-first-option
                          placeholder="选择分类或输入名称"
                          @change="onNavTextChange(element, $event)"
                        >
                          <ElOption
                            v-for="opt in categoryOptions"
                            :key="opt.id"
                            :label="opt.name"
                            :value="opt.name"
                          />
                        </ElSelect>
                      </div>
                      <div class="nav-item-row">
                        <span class="nav-label">链接</span>
                        <LinkUrl v-model="element.linkUrl" />
                      </div>
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
                <ElButton @click="add">
                  <ElIcon :size="20"><Plus /></ElIcon>添加导航
                </ElButton>
              </div>
            </template>
          </ElTabPane>
          <ElTabPane label="组件样式" name="1">
            <ElFormItem label="图标大小">
              <ElSlider
                v-model="form.imgSize"
                :min="20"
                :max="60"
                show-input
                :show-input-controls="false"
              />
            </ElFormItem>
            <ElFormItem label="图标圆角">
              <ElSlider
                v-model="form.imgRadius"
                :min="0"
                :max="30"
                show-input
                :show-input-controls="false"
              />
            </ElFormItem>
            <ElFormItem label="文字颜色">
              <ColorPicker v-model="form.fontColor" />
            </ElFormItem>
            <ElFormItem label="横向滚动">
              <ElSwitch v-model="form.scrollShow" />
            </ElFormItem>
            <h4>通用样式</h4>
            <div class="content-item">
              <CommonStyle v-model="form.commonStyle" />
            </div>
          </ElTabPane>
        </ElTabs>
      </ElForm>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '#/views/promotion/page-design/components/common/common.scss' as *;

.nav-item-info {
  flex: 1;
  padding-left: 8px;

  .nav-item-row {
    display: flex;
    gap: 8px;
    align-items: center;
    margin-bottom: 6px;

    .nav-label {
      flex-shrink: 0;
      width: 32px;
      font-size: 12px;
      color: #666;
    }

    /* 下拉在窄行里占满剩余宽度，选项过多时靠 filterable 搜索 */
    .nav-text-select {
      flex: 1;
      min-width: 0;
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
