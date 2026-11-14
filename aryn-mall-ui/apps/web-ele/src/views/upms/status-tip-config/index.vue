<script lang="ts" setup>
import type { UploadProps, UploadRequestOptions } from 'element-plus';

import type {
  StatusTipConfig,
  StatusTipImageKey,
} from '#/api/upms/status-tip-config';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElUpload,
} from 'element-plus';

import {
  getStatusTipConfig,
  saveStatusTipConfig,
} from '#/api/upms/status-tip-config';
import { uploadFile } from '#/api/upms/upload';

defineOptions({ name: 'StatusTipConfigPage' });

type UrlField = keyof StatusTipConfig;

interface PresetMeta {
  field: UrlField;
  key: StatusTipImageKey;
  label: string;
  scene: string;
}

/**
 * 10 个图片位与 C 端实际场景一一对应：
 * 前 7 个是 wd-status-tip 预设位（项目当前用到 network/content/message/search，
 * collect/comment/halo 一并开放，避免以后新增空态又要回来加字段）；
 * 后 3 个是购物车、订单列表、秒杀会场的会场插画。
 */
const PRESETS: PresetMeta[] = [
  {
    field: 'searchUrl',
    key: 'search',
    label: '搜索无结果',
    scene: '搜索页、船供采购关键词搜不到商品',
  },
  {
    field: 'networkUrl',
    key: 'network',
    label: '网络异常',
    scene: '列表加载失败、请求超时',
  },
  {
    field: 'contentUrl',
    key: 'content',
    label: '内容为空',
    scene: '分类无商品、订单物流为空、消息为空',
  },
  {
    field: 'messageUrl',
    key: 'message',
    label: '消息为空',
    scene: '消息中心无通知/会话',
  },
  {
    field: 'emptyCartUrl',
    key: 'empty-cart',
    label: '购物车空态',
    scene: '购物车没有商品',
  },
  {
    field: 'emptyOrderUrl',
    key: 'empty-order',
    label: '订单空态',
    scene: '订单列表无订单',
  },
  {
    field: 'emptySeckillUrl',
    key: 'empty-seckill',
    label: '秒杀空态',
    scene: '秒杀会场无场次、当前场次无商品',
  },
  {
    field: 'collectUrl',
    key: 'collect',
    label: '收藏为空',
    scene: '收藏夹没有商品（预留）',
  },
  {
    field: 'commentUrl',
    key: 'comment',
    label: '评论为空',
    scene: '商品暂无评价（预留）',
  },
  {
    field: 'haloUrl',
    key: 'halo',
    label: '券/资产为空',
    scene: '优惠券、积分等资产列表为空（预留）',
  },
];

const editPermission = 'upms:statustip:edit';

const emptyForm = (): StatusTipConfig => ({
  collectUrl: '',
  commentUrl: '',
  contentUrl: '',
  emptyCartUrl: '',
  emptyOrderUrl: '',
  emptySeckillUrl: '',
  haloUrl: '',
  messageUrl: '',
  networkUrl: '',
  searchUrl: '',
});

const state = reactive({ form: emptyForm() });

const loading = ref(false);
const saving = ref(false);
const uploadingField = ref<null | UrlField>(null);

const formRef = ref<InstanceType<typeof ElForm>>();

const URL_RULE = {
  message: '请填写 http/https 图片地址或站内相对路径',
  pattern: /^(?:https?:\/\/|\/)/i,
  trigger: 'blur',
};

const rules = Object.fromEntries(
  PRESETS.map((item) => [item.field, [URL_RULE]]),
);

async function load() {
  loading.value = true;
  try {
    const config = await getStatusTipConfig();
    state.form = { ...emptyForm(), ...config };
  } catch {
    // 读取失败保留空表单：留空即内置图，保存不会把脏值写回
    state.form = emptyForm();
  } finally {
    loading.value = false;
  }
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (valid === false) return;
  saving.value = true;
  try {
    await saveStatusTipConfig({ ...state.form });
    ElMessage.success('保存成功');
    await load();
  } finally {
    saving.value = false;
  }
}

function reset() {
  void load();
}

const IMAGE_MAX_SIZE_MB = 2;

const beforeImageUpload: UploadProps['beforeUpload'] = (rawFile) => {
  if (!rawFile.type.startsWith('image/')) {
    ElMessage.error('只能上传图片文件');
    return false;
  }
  if (rawFile.size / 1024 / 1024 > IMAGE_MAX_SIZE_MB) {
    ElMessage.error(`图片大小不能超过 ${IMAGE_MAX_SIZE_MB}MB`);
    return false;
  }
  return true;
};

async function uploadImage(options: UploadRequestOptions, field: UrlField) {
  uploadingField.value = field;
  try {
    const formData = new FormData();
    formData.append('file', options.file);
    // type=1 走图片素材校验（与版权配置/商品图同一入口）
    formData.append('type', '1');
    const material = (await uploadFile(formData)) as
      undefined | { url?: string };
    state.form[field] = material?.url ?? '';
  } finally {
    uploadingField.value = null;
  }
}

function makeUploadRequest(field: UrlField) {
  return (options: UploadRequestOptions) => uploadImage(options, field);
}

void load();
</script>

<template>
  <div class="hx-layout-container status-tip-config">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <section v-loading="loading" class="st-panel">
        <header class="st-head">
          <div>
            <h3 class="st-title">空态插画配置</h3>
            <p class="st-tip">
              商城空态/失败态展示的插画，仅对当前租户生效。留空使用系统内置图；
              上传后建议 1:1 正方形、不小于 176×176，手机端会按比例缩放。
              其中购物车、订单、秒杀三张内置图随小程序包发布，配置后无需重新发版。
            </p>
          </div>
        </header>

        <ElForm
          ref="formRef"
          class="st-form"
          label-width="auto"
          :model="state.form"
          :rules="rules"
          status-icon
        >
          <div class="st-grid">
            <div v-for="item in PRESETS" :key="item.key" class="st-card">
              <div class="st-card__head">
                <span class="st-card__label">{{ item.label }}</span>
                <span class="st-card__key">{{ item.key }}</span>
              </div>
              <p class="st-card__scene">{{ item.scene }}</p>

              <ElFormItem :prop="item.field" class="st-card__form-item">
                <div class="st-image">
                  <div class="st-image__box">
                    <img
                      v-if="state.form[item.field]"
                      :alt="item.label"
                      class="st-image__preview"
                      :src="state.form[item.field]"
                    />
                    <span v-else class="st-image__empty">内置图</span>
                  </div>
                  <div class="st-image__actions">
                    <ElUpload
                      accept="image/*"
                      action="#"
                      :before-upload="beforeImageUpload"
                      :disabled="uploadingField === item.field"
                      :http-request="makeUploadRequest(item.field)"
                      :show-file-list="false"
                    >
                      <ElButton
                        :loading="uploadingField === item.field"
                        size="small"
                        type="primary"
                      >
                        {{ state.form[item.field] ? '重新上传' : '上传图片' }}
                      </ElButton>
                    </ElUpload>
                    <ElButton
                      v-if="state.form[item.field]"
                      size="small"
                      text
                      type="danger"
                      @click="state.form[item.field] = ''"
                    >
                      清除
                    </ElButton>
                  </div>
                </div>
                <ElInput
                  v-model="state.form[item.field]"
                  class="st-card__input"
                  maxlength="500"
                  placeholder="留空使用内置图，或粘贴图片地址"
                  show-word-limit
                />
              </ElFormItem>
            </div>
          </div>

          <ElFormItem>
            <ElButton
              v-access:code="editPermission"
              :loading="saving"
              type="primary"
              @click="submit"
            >
              确认
            </ElButton>
            <ElButton :disabled="saving" @click="reset">重置</ElButton>
          </ElFormItem>
        </ElForm>

        <p class="st-foot">
          保存后商城下次打开生效；已打开的页面回到前台时按 5 分钟节流刷新。
          图片与商品图共用同一套存储，不会产生额外域名配置。
        </p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.status-tip-config {
  height: 100%;
}

.st-panel {
  min-height: 100%;
  padding: 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.st-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}

.st-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.st-tip {
  max-width: 760px;
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.st-form {
  max-width: 900px;
}

.st-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 8px;
}

.st-card {
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.st-card__head {
  display: flex;
  gap: 8px;
  align-items: baseline;
  justify-content: space-between;
}

.st-card__label {
  font-size: 14px;
  font-weight: 600;
}

.st-card__key {
  font-family: monospace;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.st-card__scene {
  margin: 2px 0 10px;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.st-card__form-item {
  margin-bottom: 0;
}

.st-card__input {
  margin-top: 8px;
}

.st-image {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  width: 100%;
}

.st-image__box {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 88px;
  height: 88px;
  overflow: hidden;
  background: var(--el-fill-color-lighter);
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
}

.st-image__preview {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.st-image__empty {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.st-image__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.st-foot {
  max-width: 900px;
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 960px) {
  .st-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
