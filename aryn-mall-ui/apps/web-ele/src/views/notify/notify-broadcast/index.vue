<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { defineAsyncComponent, reactive, ref } from 'vue';

import { Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { getPage, sendBroadcast } from '#/api/notify/notify-broadcast';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const notifyTypeOptions = [
  { value: 1, label: '订单消息' },
  { value: 2, label: '支付消息' },
  { value: 3, label: '物流消息' },
  { value: 4, label: '营销消息' },
  { value: 5, label: '系统消息' },
  { value: 6, label: '社交消息' },
];

const targetTypeOptions = [{ value: 2, label: '指定用户' }];

const statusOptions = [
  { value: '0', label: '待发送' },
  { value: '1', label: '发送中' },
  { value: '2', label: '已完成' },
  { value: '3', label: '已取消' },
];

const state = reactive({
  queryParams: {
    title: '',
    notifyType: '' as number | string,
    status: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    desc: 'create_time',
  },
  tableData: [],
});

const showSearch = ref(true);
const loading = ref(false);

// 群发对话框
const sendDialog = ref(false);
const sendFormRef = ref<FormInstance>();
const sendForm = reactive({
  title: '',
  content: '',
  notifyType: 5,
  targetType: 2,
  targetIds: '',
  jumpType: 0,
  jumpUrl: '',
});
const sendRules = {
  title: [{ required: true, message: '请输入群发标题', trigger: 'change' }],
  content: [{ required: true, message: '请输入群发内容', trigger: 'change' }],
  notifyType: [
    { required: true, message: '请选择消息类型', trigger: 'change' },
  ],
  targetType: [{ required: true, message: '请选择目标', trigger: 'change' }],
  targetIds: [
    {
      required: true,
      message: '请输入目标用户ID（逗号分隔）',
      trigger: 'change',
    },
  ],
};
const sendLoading = ref(false);

const initPage = async () => {
  loading.value = true;
  const params = {
    current: state.page.currentPage,
    size: state.page.pageSize,
    desc: state.page.desc,
  };
  await getPage(Object.assign(params, state.queryParams))
    .then((response) => {
      state.tableData = response.records;
      state.page.total = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};

const resetQuery = () => {
  state.queryParams = { title: '', notifyType: '', status: '' };
};

const openSendDialog = () => {
  sendForm.title = '';
  sendForm.content = '';
  sendForm.notifyType = 5;
  sendForm.targetType = 2;
  sendForm.targetIds = '';
  sendForm.jumpType = 0;
  sendForm.jumpUrl = '';
  sendDialog.value = true;
};

const submitSend = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (valid) {
      sendLoading.value = true;
      // 将逗号分隔的 userId 转为 JSON 数组字符串
      const ids = sendForm.targetIds
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean);
      const payload = {
        ...sendForm,
        targetIds: JSON.stringify(ids),
      };
      sendBroadcast(payload)
        .then(() => {
          ElMessage.success('群发消息已提交');
          sendDialog.value = false;
          sendLoading.value = false;
          initPage();
        })
        .catch(() => {
          sendLoading.value = false;
        });
    }
  });
};

const notifyTypeText = (type: number) => {
  return notifyTypeOptions.find((t) => t.value === type)?.label || '-';
};

const statusText = (status: string) => {
  return statusOptions.find((s) => s.value === status)?.label || '-';
};

const statusTagType = (status: string) => {
  const map: Record<string, string> = {
    '0': 'info',
    '1': 'warning',
    '2': 'success',
    '3': 'danger',
  };
  return map[status] || 'info';
};

initPage();
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElForm :model="state.queryParams" :inline="true" v-show="showSearch">
        <ElFormItem label="标题" prop="title">
          <ElInput
            v-model="state.queryParams.title"
            clearable
            style="width: 200px"
            placeholder="请输入标题"
          />
        </ElFormItem>
        <ElFormItem label="消息类型" prop="notifyType">
          <ElSelect
            v-model="state.queryParams.notifyType"
            clearable
            style="width: 200px"
            placeholder="请选择消息类型"
          >
            <ElOption
              v-for="item in notifyTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="状态" prop="status">
          <ElSelect
            v-model="state.queryParams.status"
            clearable
            style="width: 200px"
            placeholder="请选择状态"
          >
            <ElOption
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage" :icon="Search">
            搜索
          </ElButton>
          <ElButton @click="resetQuery" :icon="Refresh">重置</ElButton>
        </ElFormItem>
      </ElForm>

      <div class="hx-table-toolbar">
        <div>
          <ElButton
            type="primary"
            @click="openSendDialog"
            v-access:code="'notify:broadcast:send'"
          >
            发起群发
          </ElButton>
        </div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>

      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn
          prop="title"
          label="群发标题"
          align="center"
          show-overflow-tooltip
        />
        <ElTableColumn prop="notifyType" label="消息类型" align="center">
          <template #default="scope">
            {{ notifyTypeText(scope.row.notifyType) }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="totalCount"
          label="总数"
          align="center"
          width="80"
        />
        <ElTableColumn
          prop="successCount"
          label="成功"
          align="center"
          width="80"
        />
        <ElTableColumn prop="status" label="状态" align="center" width="100">
          <template #default="scope">
            <ElTag :type="statusTagType(scope.row.status)">
              {{ statusText(scope.row.status) }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="sendTime" label="发送时间" width="180" />
        <ElTableColumn prop="createTime" label="创建时间" width="180" />
      </ElTable>
      <Pagination
        :total="state.page.total"
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        @change="initPage"
      />
    </div>

    <!-- 群发对话框 -->
    <ElDialog v-model="sendDialog" title="发起群发消息" width="600px">
      <ElForm
        ref="sendFormRef"
        :model="sendForm"
        :rules="sendRules"
        label-width="100px"
        v-loading="sendLoading"
      >
        <ElFormItem label="群发标题" prop="title">
          <ElInput v-model="sendForm.title" placeholder="请输入群发标题" />
        </ElFormItem>
        <ElFormItem label="群发内容" prop="content">
          <ElInput
            v-model="sendForm.content"
            type="textarea"
            :rows="5"
            placeholder="请输入群发内容"
          />
        </ElFormItem>
        <ElFormItem label="消息类型" prop="notifyType">
          <ElSelect
            v-model="sendForm.notifyType"
            placeholder="请选择消息类型"
            style="width: 100%"
          >
            <ElOption
              v-for="item in notifyTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="目标类型" prop="targetType">
          <ElSelect
            v-model="sendForm.targetType"
            placeholder="请选择目标类型"
            style="width: 100%"
          >
            <ElOption
              v-for="item in targetTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="目标用户ID" prop="targetIds">
          <ElInput
            v-model="sendForm.targetIds"
            type="textarea"
            :rows="3"
            placeholder="多个用户ID用英文逗号分隔，如：u1,u2,u3"
          />
        </ElFormItem>
        <ElFormItem label="跳转类型" prop="jumpType">
          <ElSelect
            v-model="sendForm.jumpType"
            placeholder="请选择跳转类型"
            style="width: 100%"
          >
            <ElOption :value="0" label="不跳转" />
            <ElOption :value="1" label="订单详情" />
            <ElOption :value="3" label="活动页" />
            <ElOption :value="4" label="自定义链接" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem
          label="跳转地址"
          prop="jumpUrl"
          v-if="sendForm.jumpType !== 0"
        >
          <ElInput v-model="sendForm.jumpUrl" placeholder="请输入跳转地址" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="sendDialog = false">取消</ElButton>
        <ElButton
          type="primary"
          @click="submitSend(sendFormRef)"
          :loading="sendLoading"
        >
          确认发送
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>
