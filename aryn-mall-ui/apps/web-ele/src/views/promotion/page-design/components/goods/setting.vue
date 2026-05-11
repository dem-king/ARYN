<script lang="ts" setup>
import { defineAsyncComponent, ref, watch } from 'vue';

import { CircleCloseFilled } from '@element-plus/icons-vue';
import {
  ElButton,
  ElCheckbox,
  ElForm,
  ElFormItem,
  ElIcon,
  ElImage,
  ElInput,
  ElRadio,
  ElRadioGroup,
  ElSlider,
  ElSwitch,
  ElTabPane,
  ElTabs,
} from 'element-plus';
import draggable from 'vuedraggable';

// 用于接收外部 v-model
const props = defineProps<{ modelValue: any }>();
const emit = defineEmits(['update:modelValue']);

const SelectGoods = defineAsyncComponent(
  () => import('#/components/select-goods/index.vue'),
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
// 设置默认值
const defaultConfig = {
  showType: '1',
  showDesc: false, // 是否显示商品描述
  descSize: 14, // 商品描述字体大小
  descColor: 'rgba(0, 0, 0, 1)', // 商品描述字体颜色
  descStyle: '0', // 商品描述字体样式 0.正常 1.加粗
  showName: true,
  nameSize: 14, // 商品名称字体大小
  nameColor: 'rgba(0, 0, 0, 1)', // 商品名称字体颜色
  nameStyle: '0', // 商品名称字体样式 0.正常 1.加粗
  showTag: false,
  showSalesPrice: true,
  salesPriceSize: 14, // 商品售价字体大小
  salesPriceColor: 'rgba(255, 0, 0, 1)', // 商品售价字体颜色
  salesPriceStyle: '0', // 商品售价字体样式 0.正常 1.加粗
  showOriginalPrice: false,
  showSalesVolume: false,
  salesVolumeSize: 14, // 商品销量字体大小
  salesVolumeColor: 'rgba(0, 0, 0, 1)', // 商品销量字体颜色
  salesVolumeStyle: '0', // 商品销量字体样式 0.正常 1.加粗
  showStock: false,
  stockSize: 14, // 商品库存字体大小
  stockColor: 'rgba(0, 0, 0, 1)', // 商品库存字体颜色
  stockStyle: '0', // 商品库存字体样式 0.正常 1.加粗
  buyBtnColor: 'rgba(255, 0, 0, 1)', // 购买按钮字体颜色
  buyBtnSize: 14, // 购买按钮字体大小
  buyBtnStyle: '1',
  showBuyBtn: true,
  buyBtnText: '购买',
  goodsList: [],
  goodsCommonStyle: {
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
  commonStyle: {
    styleTopMargin: 0,
    styleBottomMargin: 0,
    styleLeftMargin: 0,
    styleRightMargin: 0,
    styleTopPadding: 0,
    styleBottomPadding: 0,
    styleLeftPadding: 0,
    styleRightPadding: 0,
    styleLtRadius: 0,
    styleRtRadius: 0,
    styleLbRadius: 0,
    styleRbRadius: 0,
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
// 定义变量内容

const selectGoodsRef = ref();

const spuCurrent = (goodsList: any) => {
  form.value.goodsList = [...form.value.goodsList, ...goodsList] as any;
};
const chooseGoods = () => {
  selectGoodsRef.value.initPage();
};
const deleteGoods = (index: number) => {
  form.value.goodsList.splice(index, 1);
  // ElMessageBox.confirm('确定删除吗？', '提示', {
  // 	confirmButtonText: '确定',
  // 	cancelButtonText: '取消',
  // 	type: 'warning',
  // }).then(() => {

  // });
};
</script>

<template>
  <div class="setting-base">
    <div class="setting-title">
      <p>商品</p>
      <div></div>
    </div>
    <div class="setting-form">
      <ElForm :model="form" label-width="80px">
        <ElTabs type="border-card" v-model="active">
          <ElTabPane label="组件内容" name="0">
            <ElFormItem label="列表样式">
              <ElRadioGroup v-model="form.showType">
                <ElRadio value="1">大图模式 </ElRadio>
                <ElRadio value="2">一行两个</ElRadio>
                <ElRadio value="3">一行三个</ElRadio>
              </ElRadioGroup>
            </ElFormItem>

            <ElFormItem label="显示内容">
              <ElCheckbox v-model="form.showName" label="商品名称" />
              <ElCheckbox v-model="form.showDesc" label="商品描述" />
              <ElCheckbox v-model="form.showTag" label="商品标签" />
              <ElCheckbox v-model="form.showSalesPrice" label="商品售价" />
              <ElCheckbox v-model="form.showOriginalPrice" label="商品原价" />
              <ElCheckbox v-model="form.showSalesVolume" label="商品销量" />
              <ElCheckbox v-model="form.showStock" label="商品库存" />
            </ElFormItem>
            <ElFormItem label="购买按钮">
              <ElSwitch v-model="form.showBuyBtn" />
            </ElFormItem>
            <ElFormItem>
              <ElRadioGroup v-model="form.buyBtnStyle" v-if="form.showBuyBtn">
                <ElRadio value="1">样式一 </ElRadio>
                <ElRadio value="2">样式二</ElRadio>
                <ElRadio value="3">样式三</ElRadio>
              </ElRadioGroup>
            </ElFormItem>
            <ElFormItem v-if="form.showBuyBtn && form.buyBtnStyle === '3'">
              <ElInput v-model="form.buyBtnText" />
            </ElFormItem>
            <ElFormItem label="商品设置">
              <p class="form-tip">商品可以上下拖动更改顺序</p>
            </ElFormItem>
            <draggable
              v-model="form.goodsList"
              v-bind="dragOptions"
              animation="700"
              item-key="id"
              @dragover.prevent
            >
              <template #item="{ element, index }">
                <div class="goods-list">
                  <div class="goods-item">
                    <ElImage
                      :src="element.spuUrls[0]"
                      style="width: 40px; height: 40px; object-fit: cover"
                    />
                    <div>{{ element.name }}</div>
                  </div>
                  <div class="delete-btn">
                    <ElButton link @click="deleteGoods(index)">
                      <ElIcon><CircleCloseFilled /></ElIcon>
                    </ElButton>
                  </div>
                </div>
              </template>
            </draggable>
            <ElButton style="width: 100%" @click="chooseGoods">添加</ElButton>
          </ElTabPane>
          <ElTabPane label="组件样式" name="1">
            <div v-if="form.showName">
              <h4>商品名称</h4>
              <div class="content-item">
                <ElFormItem label="字体颜色">
                  <ColorPicker v-model="form.nameColor" />
                </ElFormItem>
                <ElFormItem label="字体样式">
                  <ElRadioGroup v-model="form.nameStyle">
                    <ElRadio value="0">正常</ElRadio>
                    <ElRadio value="1">加粗</ElRadio>
                  </ElRadioGroup>
                </ElFormItem>
                <ElFormItem label="字体大小">
                  <ElSlider
                    v-model="form.nameSize"
                    show-input
                    :show-input-controls="false"
                    :min="0"
                  />
                </ElFormItem>
              </div>
            </div>
            <div v-if="form.showDesc">
              <h4>商品描述</h4>
              <div class="content-item">
                <ElFormItem label="字体颜色">
                  <ColorPicker v-model="form.descColor" />
                </ElFormItem>
                <ElFormItem label="字体样式">
                  <ElRadioGroup v-model="form.descStyle">
                    <ElRadio value="0">正常</ElRadio>
                    <ElRadio value="1">加粗</ElRadio>
                  </ElRadioGroup>
                </ElFormItem>
                <ElFormItem label="字体大小">
                  <ElSlider
                    v-model="form.descSize"
                    show-input
                    :show-input-controls="false"
                    :min="0"
                  />
                </ElFormItem>
              </div>
            </div>
            <div v-if="form.showSalesPrice">
              <h4>商品售价</h4>
              <div class="content-item">
                <ElFormItem label="字体颜色">
                  <ColorPicker v-model="form.salesPriceColor" />
                </ElFormItem>
                <ElFormItem label="字体样式">
                  <ElRadioGroup v-model="form.salesPriceStyle">
                    <ElRadio value="0">正常</ElRadio>
                    <ElRadio value="1">加粗</ElRadio>
                  </ElRadioGroup>
                </ElFormItem>
                <ElFormItem label="字体大小">
                  <ElSlider
                    v-model="form.salesPriceSize"
                    show-input
                    :show-input-controls="false"
                    :min="0"
                  />
                </ElFormItem>
              </div>
            </div>
            <div v-if="form.showSalesVolume">
              <h4>商品销量</h4>
              <div class="content-item">
                <ElFormItem label="字体颜色">
                  <ColorPicker v-model="form.salesVolumeColor" />
                </ElFormItem>
                <ElFormItem label="字体样式">
                  <ElRadioGroup v-model="form.salesVolumeStyle">
                    <ElRadio value="0">正常</ElRadio>
                    <ElRadio value="1">加粗</ElRadio>
                  </ElRadioGroup>
                </ElFormItem>
                <ElFormItem label="字体大小">
                  <ElSlider
                    v-model="form.salesVolumeSize"
                    show-input
                    :show-input-controls="false"
                    :min="0"
                  />
                </ElFormItem>
              </div>
            </div>
            <div v-if="form.showStock">
              <h4>商品库存</h4>
              <div class="content-item">
                <ElFormItem label="字体颜色">
                  <ColorPicker v-model="form.stockColor" />
                </ElFormItem>
                <ElFormItem label="字体样式">
                  <ElRadioGroup v-model="form.stockStyle">
                    <ElRadio value="0">正常</ElRadio>
                    <ElRadio value="1">加粗</ElRadio>
                  </ElRadioGroup>
                </ElFormItem>
                <ElFormItem label="字体大小">
                  <ElSlider
                    v-model="form.stockSize"
                    show-input
                    :show-input-controls="false"
                    :min="0"
                  />
                </ElFormItem>
              </div>
            </div>
            <div v-if="form.showBuyBtn">
              <h4>购买按钮</h4>
              <div class="content-item">
                <ElFormItem label="字体颜色">
                  <ColorPicker v-model="form.buyBtnColor" />
                </ElFormItem>
                <ElFormItem label="字体大小">
                  <ElSlider
                    v-model="form.buyBtnSize"
                    show-input
                    :show-input-controls="false"
                    :min="0"
                  />
                </ElFormItem>
              </div>
            </div>
            <div>
              <h4>商品样式</h4>
              <div class="content-item">
                <CommonStyle v-model="form.goodsCommonStyle" />
              </div>
            </div>
            <h4>通用样式</h4>
            <div class="content-item">
              <CommonStyle v-model="form.commonStyle" />
            </div>
          </ElTabPane>
        </ElTabs>
      </ElForm>
    </div>
    <SelectGoods
      ref="selectGoodsRef"
      :limit-num="20"
      @current-row="spuCurrent"
    />
  </div>
</template>
<style scoped lang="scss">
@use '#/views/promotion/page-design/components/common/common.scss' as *;

.goods-list {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px;
  margin-bottom: 10px;
  border: 1px dashed #dcdfe6;
  border-radius: 4px;

  :hover {
    cursor: move;
  }

  .goods-item {
    display: flex;
    flex: 1;
    gap: 8px;
    align-items: center;
    min-width: 0; // 确保flex子项可以被压缩

    .el-image {
      flex: none;
      object-fit: cover;
      border-radius: 4px;
    }

    div {
      display: -webkit-box;
      min-width: 0; // 确保文本可以被压缩
      padding-right: 8px;
      overflow: hidden;
      text-overflow: ellipsis;
      -webkit-line-clamp: 2;
      font-size: 14px;
      line-height: 1.4;
      color: var(--el-text-color-primary);
      word-break: break-all;
      -webkit-box-orient: vertical;
    }
  }

  .delete-btn {
    flex: none;
    margin-left: auto;
  }
}

.choose-goods {
  position: relative;
  width: 60px;
  height: 60px;
  line-height: 60px;
  text-align: center;

  &:hover {
    cursor: pointer;
  }
}
</style>
