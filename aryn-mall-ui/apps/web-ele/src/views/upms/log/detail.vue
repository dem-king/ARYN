<script setup lang="ts">
import { reactive, ref } from 'vue';

import {
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElTag,
} from 'element-plus';

interface DataForm {
  form: {
    exMsg: string;
    location: string;
    method: string;
    requestMethod: string;
    requestTime: string;
    requestUri: string;
    status: string;
    title: string;
    userName: string;
  };
}

const dialog = ref(false);
const state = reactive<DataForm>({
  form: {
    userName: '',
    title: '',
    status: '',
    requestMethod: '',
    requestUri: '',
    requestTime: '',
    method: '',
    exMsg: '',
    location: '',
  },
});
</script>
<template>
  <ElDialog v-model="dialog" title="日志详情" width="60%">
    <ElDescriptions :column="2" border>
      <ElDescriptionsItem label="操作用户">
        {{ state.form.userName }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="操作标题">
        {{ state.form.title }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="请求状态">
        <ElTag type="success" v-if="state.form.status === '1'">成功</ElTag>
        <ElTag type="danger" v-if="state.form.status === '0'">失败</ElTag>
      </ElDescriptionsItem>
      <ElDescriptionsItem label="请求方法">
        {{ state.form.requestMethod }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="请求URI">
        {{ state.form.requestUri }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="请求时长">
        {{ state.form.requestTime }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="操作地点">
        {{ state.form.location }}
      </ElDescriptionsItem>
      <ElDescriptionsItem label="操作方法">
        {{ state.form.method }}
      </ElDescriptionsItem>
    </ElDescriptions>
    <ElDescriptions
      direction="vertical"
      :column="1"
      border
      v-if="state.form.exMsg"
    >
      <ElDescriptionsItem label="异常信息">
        {{ state.form.exMsg }}
      </ElDescriptionsItem>
    </ElDescriptions>
  </ElDialog>
</template>
