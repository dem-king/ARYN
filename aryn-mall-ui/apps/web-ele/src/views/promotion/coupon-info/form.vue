<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElButton,
  ElDatePicker,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElImage,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElRadio,
  ElRadioGroup,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getByIds } from '#/api/product/goods-spu';
import { addObj, editObj, getById } from '#/api/promotion/coupon-info';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);
const SelectGoods = defineAsyncComponent(
  () => import('#/components/select-goods/index.vue'),
);

interface DataState {
  form: {
    amount: number;
    couponGoodsList: Array<any>;
    couponName: string;
    couponType: string;
    datatimes: Array<any>;
    discount: number;
    id: string | undefined;
    receiveCount: number;
    receiveEndedAt: string;
    receiveStartedAt: string;
    status: string;
    threshold: number;
    totalNum: number;
    useDescription: string;
    useRange: string;
  };
  rules: any;
}
// 字典
const { status } = useDict('status');
const state = reactive<DataState>({
  form: {
    id: undefined,
    couponName: '',
    status: '',
    useRange: '',
    amount: 0,
    discount: 0,
    threshold: 0,
    totalNum: 0,
    receiveCount: 0,
    useDescription: '',
    datatimes: [],
    couponType: '1',
    couponGoodsList: [],
    receiveStartedAt: '',
    receiveEndedAt: '',
  },
  rules: {
    couponName: [
      {
        required: true,
        message: '请输入优惠券名称',
        trigger: 'change',
      },
    ],
    status: [
      {
        required: true,
        message: '请选择优惠券状态',
        trigger: 'change',
      },
    ],

    datatimes: [
      {
        required: true,
        message: '请选择有效时间',
        trigger: 'change',
      },
    ],
    couponType: [
      {
        required: true,
        message: '请选择优惠形式',
        trigger: 'change',
      },
    ],
    discount: [
      {
        required: true,
        message: '请输入折扣',
        trigger: 'change',
      },
    ],
    amount: [
      {
        required: true,
        message: '请输入优惠金额（元）',
        trigger: 'change',
      },
    ],
    threshold: [
      {
        required: true,
        message: '请输入使用门槛（元）',
        trigger: 'change',
      },
    ],
    totalCount: [
      {
        required: true,
        message: '请输入发行数量',
        trigger: 'change',
      },
    ],
    useRange: [
      {
        required: true,
        message: '请选择可用范围',
        trigger: 'change',
      },
    ],
    useDescription: [
      {
        required: true,
        message: '请输入使用说明',
        trigger: 'change',
      },
    ],
  },
});
const selectGoods = ref();
const loading = ref(false);
const formRef = ref();
const dialog = ref(false);
/**
 * 关闭事件
 */
const handleClose = () => {
  resetForm(formRef.value);
};
/**
 * 重置表单
 */
const resetForm = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  state.form.id = undefined;
  state.form.couponGoodsList = [];
  loading.value = false;
  dialog.value = false;
  formEl.resetFields();
};
/**
 * 提交按钮
 */
const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (valid) {
      state.form.receiveStartedAt = state.form.datatimes[0];
      state.form.receiveEndedAt = state.form.datatimes[1];
      loading.value = true;
      if (state.form.id) {
        // 修改
        edit();
      } else {
        // 新增
        add();
      }
    }
  });
};
/**
 * 新增
 */
const add = () => {
  addObj(state.form)
    .then(() => {
      resetForm(formRef.value);
      ElMessage.success('新增成功');
      emit('initPage');
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
      resetForm(formRef.value);
      ElMessage.success('修改成功');
      emit('initPage');
    })
    .catch(() => {
      loading.value = false;
    });
};
const initForm = (row: any) => {
  dialog.value = true;
  if (row && row.id) {
    getDetail(row.id);
  }
};
const getDetail = (id: string) => {
  loading.value = true;
  // 修改
  getById(id)
    .then((response) => {
      loading.value = false;
      state.form = response;
      state.form.datatimes = [
        state.form.receiveStartedAt,
        state.form.receiveEndedAt,
      ];
      if (state.form.useRange === '2') {
        // 指定商品查询
        const spuIds = state.form.couponGoodsList.map((v) => v.spuId);
        getByIds(spuIds).then((res) => {
          const spuList = res;
          state.form.couponGoodsList.forEach((v) => {
            const spu = spuList.find((obj: any) => obj.id === v.spuId);
            if (spu) {
              v.spuUrls = spu.spuUrls;
              v.spuName = spu.name;
            }
          });
        });
      }
    })
    .catch(() => {
      loading.value = false;
    });
};
/**
 * 选择商品
 */
const selectSpu = () => {
  selectGoods.value.initPage();
};
/**
 * 删除
 * @param index
 */
const del = (index: number) => {
  state.form.couponGoodsList.splice(index, 1);
};
const spuCurrent = (spuList: any) => {
  if (spuList) {
    const goodsSpuList: Array<any> = [];
    spuList.forEach((item: any) => {
      goodsSpuList.push({
        spuId: item.id,
        spuName: item.name,
        spuUrls: item.spuUrls,
      });
    });
    state.form.couponGoodsList = goodsSpuList;
  }
};

defineExpose({
  initForm,
});
</script>
<template>
  <ElDrawer
    v-model="dialog"
    :title="state.form.id ? '修改优惠券' : '新增优惠券'"
    :before-close="handleClose"
    size="50%"
  >
    <ElForm
      ref="formRef"
      class="form"
      :model="state.form"
      label-width="140px"
      :rules="state.rules"
      status-icon
      label-position="left"
      v-loading="loading"
    >
      <ElFormItem label="优惠券名称" prop="couponName">
        <ElInput
          v-model="state.form.couponName"
          maxlength="50"
          show-word-limit
        />
      </ElFormItem>
      <ElFormItem label="有效时间" prop="datatimes">
        <ElDatePicker
          v-model="state.form.datatimes"
          :disabled="!!state.form.id"
          type="datetimerange"
          range-separator="-"
          start-placeholder="活动开始时间"
          end-placeholder="活动结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </ElFormItem>
      <ElFormItem label="优惠形式" prop="couponType">
        <ElRadioGroup
          v-model="state.form.couponType"
          :disabled="!!state.form.id"
        >
          <ElRadio value="1">指定金额</ElRadio>
          <ElRadio value="2">折扣</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem
        label="优惠金额"
        prop="amount"
        v-if="state.form.couponType === '1'"
      >
        <ElInputNumber
          v-model="state.form.amount"
          :disabled="!!state.form.id"
          :min="0.01"
          :precision="2"
          :controls="false"
        />
      </ElFormItem>
      <ElFormItem
        label="折扣"
        prop="discount"
        v-if="state.form.couponType === '2'"
      >
        <ElInputNumber
          v-model="state.form.discount"
          :disabled="!!state.form.id"
          :min="0.1"
          :max="9.9"
          :precision="1"
          :controls="false"
        />
      </ElFormItem>
      <ElFormItem label="使用门槛" prop="threshold">
        <div style="display: flex">
          <span style="padding: 0 8px">满：</span>
          <ElInputNumber
            v-model="state.form.threshold"
            :disabled="!!state.form.id"
            :precision="2"
            :controls="false"
          />
          <span style="width: 120px; padding: 0 8px">可以使用</span>
        </div>
        <div>
          <p style="color: #f56c6c">0元表示无门槛</p>
        </div>
      </ElFormItem>
      <ElFormItem label="发行数量" prop="totalNum">
        <ElInputNumber
          v-model="state.form.totalNum"
          :disabled="!!state.form.id"
          :min="1"
          :precision="0"
          :controls="false"
        />
      </ElFormItem>
      <ElFormItem label="领取限制" prop="receiveCount">
        <div style="display: flex">
          <span style="width: 120px; padding: 0 8px">每人限领：</span>
          <ElInputNumber
            v-model="state.form.receiveCount"
            :disabled="!!state.form.id"
            :precision="0"
            :controls="false"
          />
          <span style="width: 120px; padding: 0 8px">张</span>
        </div>
      </ElFormItem>
      <ElFormItem label="使用说明" prop="useDescription">
        <ElInput
          v-model="state.form.useDescription"
          maxlength="100"
          placeholder="请输入使用范围"
          show-word-limit
          type="textarea"
        />
      </ElFormItem>
      <ElFormItem label="可用范围" prop="useRange">
        <ElRadioGroup v-model="state.form.useRange" :disabled="!!state.form.id">
          <ElRadio value="1">全部商品</ElRadio>
          <ElRadio value="2">指定商品</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
      <ElFormItem
        label="指定商品"
        prop="spuId"
        v-if="state.form.useRange === '2'"
      >
        <ElButton type="success" v-if="!state.form.id" link @click="selectSpu">
          选择商品
        </ElButton>
        <ElTable
          v-if="state.form.couponGoodsList"
          :data="state.form.couponGoodsList"
          style="width: 100%"
        >
          <ElTableColumn prop="spuUrls" label="图片" width="300">
            <template #default="scope">
              <ElImage
                v-if="scope.row.spuUrls"
                style="width: 50px; height: 50px"
                :src="scope.row.spuUrls[0]"
                :preview-src-list="scope.row.spuUrls"
                fit="cover"
                :preview-teleported="true"
              />
            </template>
          </ElTableColumn>
          <ElTableColumn prop="spuName" label="商品名" show-overflow-tooltip />
          <ElTableColumn label="操作" width="200" align="center">
            <template #default="scope">
              <ElButton
                :disabled="!!state.form.id"
                link
                type="danger"
                icon="delete"
                @click="del(scope.row.index)"
              >
                删除
              </ElButton>
            </template>
          </ElTableColumn>
        </ElTable>

        <SelectGoods
          ref="selectGoods"
          :limit-num="10"
          @current-row="spuCurrent"
        />
      </ElFormItem>
      <ElFormItem label="启用状态" prop="status">
        <ElRadioGroup v-model="state.form.status">
          <ElRadio v-for="item in status" :key="item.value" :value="item.value">
            {{ item.label }}
          </ElRadio>
        </ElRadioGroup>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <span class="dialog-footer">
        <ElButton @click="handleClose">关 闭</ElButton>
        <ElButton
          type="primary"
          :loading="loading"
          @click="submitForm(formRef)"
        >
          确 认
        </ElButton>
      </span>
    </template>
  </ElDrawer>
</template>
<style lang="scss" scoped></style>
