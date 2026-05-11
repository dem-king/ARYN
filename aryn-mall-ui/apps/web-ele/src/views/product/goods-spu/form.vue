<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  ElButton,
  ElCascader,
  ElCol,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElRadio,
  ElRadioGroup,
  ElRow,
  ElSwitch,
  ElTabPane,
  ElTabs,
} from 'element-plus';

import { getPage as getCategoryTree } from '#/api/product/goods-category';
import { getList as getSpecsList } from '#/api/product/goods-specs';
import { addObj, editObj, getById } from '#/api/product/goods-spu';
import { useDict } from '#/utils/dict';

const Editor = defineAsyncComponent(
  () => import('#/components/editor/index.vue'),
);
const SelectMaterial = defineAsyncComponent(
  () => import('#/components/select-material/index.vue'),
);
const Sku = defineAsyncComponent(() => import('#/components/sku/index.vue'));

interface DataState {
  form: {
    categoryFirstId: string;
    categoryIds: Array<string>;
    categorySecondId: string;
    description: string;
    enableSpecs: string;
    fixedFreightPrice: number;
    freightType: string;
    goodsSkus: Array<any>;
    goodsSpuSpecs: Array<any>;
    id: undefined;
    name: string;
    salesVolume: number;
    sku: {
      costPrice: number;
      originalPrice: number;
      salesPrice: number;
      status: string;
      stock: number;
      volume: number;
      weight: number;
    };
    spuUrls: Array<string>;
    status: string;
    subTitle: string;
  };
  rules: any;
  categoryTreeList: Array<any>;
  specsList: Array<any>;
  goodsSpuSpecs: Array<any>;
}
const { freight_type } = useDict('freight_type');

const active = ref(0);
const state = reactive<DataState>({
  form: {
    id: undefined,
    name: '',
    subTitle: '',
    spuUrls: [],
    salesVolume: 0,
    enableSpecs: '0',
    status: '1',
    description: '',
    goodsSkus: [],
    goodsSpuSpecs: [],
    categoryFirstId: '',
    categorySecondId: '',
    sku: {
      originalPrice: 0,
      costPrice: 0,
      salesPrice: 0,
      stock: 0,
      weight: 0,
      volume: 0,
      status: '0',
    },
    categoryIds: [],
    freightType: '0',
    fixedFreightPrice: 0,
  },
  rules: {
    name: [
      {
        required: true,
        message: '请输入商品名称',
        trigger: 'change',
      },
    ],
    spuUrls: [
      {
        required: true,
        message: '请上传商品图',
        trigger: 'change',
      },
    ],
    status: [
      {
        required: true,
        message: '请选择商品状态',
        trigger: 'change',
      },
    ],
    categoryIds: [
      {
        required: true,
        message: '请选择商品类目',
        trigger: 'change',
      },
    ],

    description: [
      {
        required: true,
        message: '请输入商品描述',
        trigger: 'change',
      },
    ],
    salesVolume: [
      {
        required: true,
        message: '请输入商品销量',
        trigger: 'change',
      },
    ],
    freightType: [
      {
        required: true,
        message: '请选择运费配置',
        trigger: 'change',
      },
    ],
    fixedFreightPrice: [
      {
        required: true,
        message: '请输入固定运费（元）',
        trigger: 'change',
      },
    ],
    'sku.originalPrice': [
      {
        required: true,
        message: '请输入商品原价（元）',
        trigger: 'change',
      },
    ],
    'sku.salesPrice': [
      {
        required: true,
        message: '请输入商品销售价（元）',
        trigger: 'change',
      },
    ],
    'sku.costPrice': [
      {
        required: true,
        message: '请输入商品成本价（元）',
        trigger: 'change',
      },
    ],
    'sku.stock': [
      {
        required: true,
        message: '请输入商品库存数量',
        trigger: 'change',
      },
    ],
  },
  categoryTreeList: [],
  specsList: [],
  goodsSpuSpecs: [],
});
const $route = useRouter();
const $useRoute = useRoute();
const loading = ref(false);
const formRef = ref();
const defaultProps = {
  label: 'name',
  value: 'id',
  children: 'children',
};

/**
 * 下一步
 */
const handleNext = () => {
  active.value++;
};
/**
 * 关闭事件
 */
const handleClose = () => {
  resetForm(formRef.value);
  $route.back();
};
/**
 * 重置表单
 */
const resetForm = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  state.form = {
    id: undefined,
    name: '',
    subTitle: '',
    spuUrls: [],
    salesVolume: 0,
    enableSpecs: '0',
    status: '1',
    description: '',
    goodsSkus: [],
    goodsSpuSpecs: [],
    categoryFirstId: '',
    categorySecondId: '',
    sku: {
      originalPrice: 0,
      costPrice: 0,
      salesPrice: 0,
      stock: 0,
      weight: 0,
      volume: 0,
      status: '0',
    },
    categoryIds: [],
    freightType: '0',
    fixedFreightPrice: 0,
  };
  loading.value = false;
  // formEl.resetFields();
};
/**
 * 提交按钮
 */
const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (valid) {
      // 单规格sku 处理
      if (state.form.enableSpecs === '0') {
        state.form.goodsSkus = [state.form.sku];
      }
      loading.value = true;
      if (state.form.id) {
        // 修改
        edit();
      } else {
        // 新增
        add();
      }
    } else {
      ElMessage.warning('请完善商品信息');
    }
  });
};
/**
 * 新增
 */
const add = () => {
  addObj(state.form)
    .then(() => {
      ElMessageBox.confirm('新增成功', '提示', {
        confirmButtonText: '继续添加',
        cancelButtonText: '返回列表',
        type: 'success',
      })
        .then(() => {
          resetForm(formRef.value);
        })
        .catch(() => {
          handleClose();
        });
    })
    .catch(() => {
      loading.value = false;
    });
};
/**
 * 修改
 */
const edit = () => {
  editObj(state.form)
    .then(() => {
      ElMessageBox.confirm('修改成功', '提示', {
        confirmButtonText: '新增商品',
        cancelButtonText: '返回列表',
        type: 'success',
      })
        .then(() => {
          resetForm(formRef.value);
        })
        .catch(() => {
          handleClose();
        });
    })
    .catch(() => {
      loading.value = false;
    });
};

/**
 * 查询全部商品类目
 */
const getCategory = () => {
  getCategoryTree().then((response) => {
    state.categoryTreeList = response;
  });
};

/**
 * 商品类目改变事件
 */
const changeCategory = (categoryIds: any) => {
  state.form.categoryFirstId = '';
  state.form.categorySecondId = '';
  if (categoryIds[0]) {
    state.form.categoryFirstId = categoryIds[0];
  }
  if (categoryIds[1]) {
    state.form.categorySecondId = categoryIds[1];
  }
};

/**
 * 查询规格数据
 */
const getSpecs = () => {
  getSpecsList({}).then((response) => {
    state.specsList = response;
  });
};
/**
 * 多规格change事件 1.开始多规格；0.关闭多规格
 */
const changeEnableSpecs = (status: any) => {
  if (status === '1') {
    getSpecs();
  } else {
    if (!state.form.sku) {
      state.form.sku = {
        originalPrice: 0,
        costPrice: 0,
        salesPrice: 0,
        stock: 0,
        weight: 0,
        volume: 0,
        status: '0',
      };
    }
  }
};
/**
 * 查询spu绑定的规格
 */
const getGoodsSpuSpecsList = (id: string) => {
  const skus = state.form.goodsSkus || [];
  const specsMap = new Map<string, any>();

  skus.forEach((sku: any) => {
    const specsArr = sku.specsArr || [];
    specsArr.forEach((spec: any) => {
      if (!spec.specsId) return;

      if (!specsMap.has(spec.specsId)) {
        specsMap.set(spec.specsId, {
          specsId: spec.specsId,
          specsName: spec.specsName,
          goodsSkuSpecsValues: [],
        });
      }

      const currentSpec = specsMap.get(spec.specsId);
      const valueExists = currentSpec.goodsSkuSpecsValues.some(
        (v: any) => v.specsValueId === spec.specsValueId,
      );

      if (!valueExists && spec.specsValueId) {
        currentSpec.goodsSkuSpecsValues.push({
          specsValueId: spec.specsValueId,
          specsValueName: spec.specsValueName,
          specsId: spec.specsId,
          specsName: spec.specsName,
          picUrl: '',
        });
      }
    });
  });

  state.goodsSpuSpecs = [...specsMap.values()];
};
/**
 * 多规格组件回调事件
 */
const getGoodsSkus = (skus: any, goodsSpuSpecs: any) => {
  state.form.goodsSkus = skus;
  state.form.goodsSpuSpecs = goodsSpuSpecs;
};

const initForm = () => {
  const { id }: any = $useRoute.query;
  state.goodsSpuSpecs = [];
  getCategory();
  if (id) {
    loading.value = true;
    // 修改
    getById(id)
      .then((response) => {
        loading.value = false;
        state.form = response;

        const categoryIds: string[] = [];

        if (response.categoryFirstId) {
          categoryIds.push(response.categoryFirstId);
        }

        if (response.categorySecondId) {
          categoryIds.push(response.categorySecondId);
        }

        state.form.categoryIds = categoryIds;
        if (response.enableSpecs === '1') {
          getSpecs();
        } else {
          state.form.sku = response.goodsSkus[0];
        }
        getGoodsSpuSpecsList(response.id);
      })
      .catch(() => {
        loading.value = false;
      });
  }
};

initForm();
</script>
<template>
  <div class="hx-layout-container goods-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <div class="goods-info" style="padding: 20px" v-loading="loading">
        <ElForm
          ref="formRef"
          class="form"
          :model="state.form"
          label-width="120px"
          :rules="state.rules"
          status-icon
        >
          <ElTabs v-model="active" class="goods-tabs">
            <ElTabPane label="基本信息" :name="0">
              <ElFormItem label="商品名称" prop="name">
                <ElInput v-model="state.form.name" />
              </ElFormItem>
              <ElFormItem label="商品子标题" prop="subTitle">
                <ElInput
                  v-model="state.form.subTitle"
                  maxlength="50"
                  type="textarea"
                  show-word-limit
                  :rows="2"
                />
              </ElFormItem>
              <ElFormItem label="商品图" prop="spuUrls">
                <SelectMaterial
                  v-model="state.form.spuUrls"
                  :can-choose-images-num="5"
                />
              </ElFormItem>

              <ElRow>
                <ElCol :span="12">
                  <ElFormItem label="商品类目" prop="categoryIds">
                    <ElCascader
                      :options="state.categoryTreeList"
                      v-model="state.form.categoryIds"
                      clearable
                      :props="defaultProps"
                      @change="changeCategory"
                    />
                  </ElFormItem>

                  <ElFormItem label="运费配置" prop="freightType">
                    <ElRadioGroup v-model="state.form.freightType">
                      <ElRadio
                        v-for="(item, index) in freight_type"
                        :value="item.value"
                        :key="index"
                      >
                        {{ item.label }}
                      </ElRadio>
                    </ElRadioGroup>
                  </ElFormItem>
                  <ElFormItem
                    label="固定运费"
                    prop="fixedFreightPrice"
                    v-if="state.form.freightType === '1'"
                  >
                    <ElInputNumber
                      v-model="state.form.fixedFreightPrice"
                      :min="0"
                      :precision="0"
                      controls-position="right"
                    />
                  </ElFormItem>
                </ElCol>
                <ElCol :span="12">
                  <ElFormItem label="商品状态" prop="status">
                    <ElRadioGroup v-model="state.form.status">
                      <ElRadio value="0">下架</ElRadio>
                      <ElRadio value="1">上架</ElRadio>
                    </ElRadioGroup>
                  </ElFormItem>
                  <ElFormItem label="商品销量" prop="salesVolume">
                    <ElInputNumber
                      v-model="state.form.salesVolume"
                      :min="0"
                      :precision="0"
                      controls-position="right"
                    />
                  </ElFormItem>
                </ElCol>
              </ElRow>
            </ElTabPane>
            <ElTabPane label="规格信息" :name="1">
              <!-- 多规格 -->
              <ElFormItem label="多规格" prop="enableSpecs">
                <ElSwitch
                  v-model="state.form.enableSpecs"
                  inline-prompt
                  active-value="1"
                  inactive-value="0"
                  active-text="是"
                  inactive-text="否"
                  @change="changeEnableSpecs"
                />
              </ElFormItem>
              <ElFormItem label="多规格" v-if="state.form.enableSpecs === '1'">
                <div style="width: 100%">
                  <Sku
                    :specs-list-prop="state.specsList"
                    :skus-data="state.form.goodsSkus"
                    :goods-spu-specs="state.goodsSpuSpecs"
                    @get-goods-skus="getGoodsSkus"
                  />
                </div>
              </ElFormItem>
              <div v-if="state.form.enableSpecs === '0'">
                <ElFormItem
                  label="商品原价（元）"
                  prop="sku.originalPrice"
                  label-width="140px"
                >
                  <ElInputNumber
                    v-model="state.form.sku.originalPrice"
                    :min="0.01"
                    :precision="2"
                    style="width: 18%"
                    controls-position="right"
                  />
                </ElFormItem>
                <ElFormItem
                  label="商品成本价（元）"
                  prop="sku.costPrice"
                  label-width="140px"
                >
                  <ElInputNumber
                    v-model="state.form.sku.costPrice"
                    :min="0.01"
                    :precision="2"
                    style="width: 18%"
                    controls-position="right"
                  />
                </ElFormItem>
                <ElFormItem
                  label="商品售价（元）"
                  prop="sku.salesPrice"
                  label-width="140px"
                >
                  <ElInputNumber
                    v-model="state.form.sku.salesPrice"
                    :min="0.01"
                    :precision="2"
                    style="width: 18%"
                    controls-position="right"
                  />
                </ElFormItem>
                <ElFormItem
                  label="商品库存"
                  prop="sku.stock"
                  label-width="140px"
                >
                  <ElInputNumber
                    v-model="state.form.sku.stock"
                    :min="0"
                    :precision="0"
                    style="width: 18%"
                    controls-position="right"
                  />
                </ElFormItem>
                <ElFormItem
                  label="重量（kg）"
                  prop="sku.weight"
                  label-width="140px"
                >
                  <ElInputNumber
                    v-model="state.form.sku.weight"
                    :min="0"
                    :precision="2"
                    style="width: 18%"
                    controls-position="right"
                  />
                </ElFormItem>
                <ElFormItem
                  label="体积（m³）"
                  prop="sku.volume"
                  label-width="140px"
                >
                  <ElInputNumber
                    v-model="state.form.sku.volume"
                    :min="0"
                    :precision="2"
                    style="width: 18%"
                    controls-position="right"
                  />
                </ElFormItem>
              </div>
            </ElTabPane>
            <ElTabPane label="商品详情" :name="2">
              <ElFormItem label="商品描述" prop="description">
                <Editor
                  v-model:get-html="state.form.description"
                  height="500px"
                  :disable="false"
                />
              </ElFormItem>
            </ElTabPane>
          </ElTabs>
        </ElForm>
        <div class="goods-footer">
          <div class="button">
            <ElButton @click="handleClose">关 闭</ElButton>
            <ElButton @click="handleNext" v-if="active < 2"> 下一步 </ElButton>
            <ElButton
              type="primary"
              :disabled="loading"
              @click="submitForm(formRef)"
            >
              确 认
            </ElButton>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<style lang="scss" scoped>
.goods-container {
  height: 100%;

  .goods-info {
    flex: 1;
    overflow: auto;

    .form {
      margin-bottom: 38px;
    }

    .goods-footer {
      position: absolute;
      right: 20px;
      bottom: 20px;
      left: 20px;
      z-index: 9;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 10px 16px;
      background-color: hsl(var(--card));
      border-top: 1px solid hsl(var(--card));

      .el-button {
        width: 100px;
      }

      .button {
        display: flex;
        justify-content: center;
      }
    }
  }
}
</style>
