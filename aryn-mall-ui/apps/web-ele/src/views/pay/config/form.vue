<script lang="ts" setup>
import type {
  PayCheckResult,
  PayConfigRoute,
  PayMaterial,
  PayRevision,
} from '#/api/pay/config';

import { computed, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import {
  ElAlert,
  ElButton,
  ElCol,
  ElDialog,
  ElDivider,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElRow,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  addObj,
  checkRevision,
  createRevision,
  disableConfig,
  editObj,
  getById,
  listMaterials,
  listRevisions,
  publishRevision,
} from '#/api/pay/config';
import CertUpload from '#/components/cert-upload/index.vue';
import PageSection from '#/components/page/page-section.vue';
import { useDict } from '#/utils/dict';

const emit = defineEmits(['initPage']);
const router = useRouter();
const { pay_terminal_type } = useDict('pay_terminal_type');

const PURPOSE_META: Record<
  string,
  { channel: string; hint: string; label: string }
> = {
  WX_API_V3_KEY: {
    channel: '1',
    label: '微信 APIv3 密钥',
    hint: '32 字节；仅加密保存，不回显',
  },
  WX_MERCHANT_PRIVATE_KEY: {
    channel: '1',
    label: '商户 API 私钥',
    hint: 'apiclient_key.pem；仅加密保存',
  },
  WX_MERCHANT_CERT: {
    channel: '1',
    label: '商户 API 证书',
    hint: 'apiclient_cert.pem；用于提取序列号与配对检查',
  },
  WX_PUBLIC_KEY: {
    channel: '1',
    label: '微信支付公钥',
    hint: 'PUB_KEY_ID 对应的公钥 PEM',
  },
  ALI_APP_PRIVATE_KEY: {
    channel: '2',
    label: '支付宝应用私钥',
    hint: '仅加密保存，不回显',
  },
  ALI_APP_CERT: {
    channel: '2',
    label: '应用公钥证书',
    hint: 'appCertPublicKey.crt',
  },
  ALI_PUBLIC_CERT: { channel: '2', label: '支付宝公钥证书', hint: '用于验签' },
  ALI_ROOT_CERT: {
    channel: '2',
    label: '支付宝根证书',
    hint: 'alipayRootCert.crt',
  },
};

const REQUIRED: Record<string, string[]> = {
  '1': [
    'WX_API_V3_KEY',
    'WX_MERCHANT_PRIVATE_KEY',
    'WX_MERCHANT_CERT',
    'WX_PUBLIC_KEY',
  ],
  '2': [
    'ALI_APP_PRIVATE_KEY',
    'ALI_APP_CERT',
    'ALI_PUBLIC_CERT',
    'ALI_ROOT_CERT',
  ],
};

const state = reactive({
  form: {
    id: '',
    type: '1',
    terminalType: '0',
    appId: '',
    mchId: '',
  },
  revision: {
    appId: '',
    mchId: '',
    environment: 'prod',
    wxPublicKeyId: '',
    buyerIdentityMode: '',
  },
});
const dialog = ref(false);
const loading = ref(false);
const config = ref<null | PayConfigRoute>(null);
const materials = ref<PayMaterial[]>([]);
const revisions = ref<PayRevision[]>([]);
const uploadPurpose = ref('WX_API_V3_KEY');
const selectedMaterials = reactive<Record<string, string>>({});
const checkDialog = ref(false);
const checkResult = ref<null | PayCheckResult>(null);

const purposes = computed(() =>
  Object.entries(PURPOSE_META)
    .filter(([, meta]) => meta.channel === state.form.type)
    .map(([value, meta]) => ({ value, ...meta })),
);
const requiredPurposes = computed(() => REQUIRED[state.form.type] || []);
const terminalOptions = computed(() => {
  const allowed = new Set(['0', '1', '2']);
  return (pay_terminal_type?.value || []).filter((item: any) =>
    allowed.has(String(item.value)),
  );
});
const isAlipayMini = computed(
  () => state.form.type === '2' && state.form.terminalType === '0',
);
const activeRevision = computed(() =>
  revisions.value.find((item) => item.id === config.value?.activeRevisionId),
);
const canSaveRoute = computed(() =>
  Boolean(
    state.form.type &&
    state.form.terminalType &&
    state.form.appId &&
    state.form.mchId,
  ),
);

watch(
  () => state.form.type,
  () => {
    if (
      !terminalOptions.value.some(
        (item: any) => String(item.value) === state.form.terminalType,
      )
    ) {
      state.form.terminalType = state.form.type === '2' ? '1' : '0';
    }
    uploadPurpose.value = purposes.value[0]?.value || 'WX_API_V3_KEY';
    state.revision.environment =
      state.form.type === '1' ? 'prod' : state.revision.environment;
    if (!isAlipayMini.value) {
      state.revision.buyerIdentityMode = '';
    } else if (!state.revision.buyerIdentityMode) {
      state.revision.buyerIdentityMode = 'user_id';
    }
  },
);

watch(
  () => state.form.terminalType,
  () => {
    if (!isAlipayMini.value) {
      state.revision.buyerIdentityMode = '';
    } else if (!state.revision.buyerIdentityMode) {
      state.revision.buyerIdentityMode = 'user_id';
    }
  },
);

function initForm(row?: any, options?: { focus?: string }) {
  dialog.value = true;
  state.form = {
    id: row?.id || '',
    type: row?.type || '1',
    terminalType: row?.terminalType || (row?.type === '2' ? '1' : '0'),
    appId: row?.appId || '',
    mchId: row?.mchId || '',
  };
  state.revision = {
    appId: row?.appId || '',
    mchId: row?.mchId || '',
    environment: 'prod',
    wxPublicKeyId: '',
    buyerIdentityMode:
      row?.type === '2' && row?.terminalType === '0' ? 'user_id' : '',
  };
  config.value = null;
  materials.value = [];
  revisions.value = [];
  Object.keys(selectedMaterials).forEach(
    (key) => delete selectedMaterials[key],
  );
  uploadPurpose.value = purposes.value[0]?.value || 'WX_API_V3_KEY';
  if (row?.id) {
    void load(row.id, options);
  }
}

async function load(id: string, options?: { focus?: string }) {
  loading.value = true;
  try {
    const detail = await getById(id);
    config.value = detail;
    state.form.id = detail.id || id;
    state.form.type = detail.type;
    state.form.terminalType = detail.terminalType;
    state.form.appId = detail.appId;
    state.form.mchId = detail.mchId;
    await Promise.all([loadMaterials(), loadRevisions()]);
    state.revision.appId = activeRevision.value?.appId || detail.appId;
    state.revision.mchId = activeRevision.value?.mchId || detail.mchId;
    state.revision.wxPublicKeyId = activeRevision.value?.wxPublicKeyId || '';
    state.revision.buyerIdentityMode =
      activeRevision.value?.buyerIdentityMode ||
      (isAlipayMini.value ? 'user_id' : '');
    state.revision.environment = activeRevision.value?.environment || 'prod';
    if (options?.focus === 'revisions') {
      ElMessage.info('变更记录在弹窗底部的身份版本表中查看');
    }
  } finally {
    loading.value = false;
  }
}

async function loadMaterials() {
  if (!state.form.id) return;
  materials.value = await listMaterials(state.form.id);
  for (const purpose of requiredPurposes.value) {
    if (selectedMaterials[purpose]) continue;
    const latest = materials.value.find((item) => item.purpose === purpose);
    if (latest) selectedMaterials[purpose] = latest.materialId;
  }
}

async function loadRevisions() {
  if (!state.form.id) return;
  revisions.value = await listRevisions(state.form.id);
}

async function saveRoute() {
  if (!canSaveRoute.value) {
    ElMessage.warning('请填写渠道、终端、应用ID与商户号');
    return;
  }
  loading.value = true;
  try {
    if (state.form.id) {
      await editObj({ ...state.form });
      ElMessage.success('路由已保存');
      await load(state.form.id);
    } else {
      const id: any = await addObj({ ...state.form });
      if (!id) throw new Error('创建配置失败');
      state.form.id = id;
      ElMessage.success('路由已创建，请继续上传资料');
      await load(id);
      emit('initPage');
    }
  } finally {
    loading.value = false;
  }
}

async function onUploaded(uploaded: any) {
  if (uploaded?.purpose) {
    selectedMaterials[uploaded.purpose] = uploaded.materialId;
  }
  if (uploaded?.keyPairMatched === true) {
    ElMessage.success('资料已保存，证书与私钥配对通过');
  } else if (uploaded?.keyPairMatched === false) {
    ElMessage.warning('资料已保存，但证书与私钥未配对');
  }
  await loadMaterials();
}

function materialOptions(purpose: string) {
  return materials.value.filter((item) => item.purpose === purpose);
}

async function onCreateRevision() {
  if (!state.form.id) return;
  if (!state.revision.appId || !state.revision.mchId) {
    ElMessage.warning('请填写身份版本的应用ID与商户号');
    return;
  }
  if (state.form.type === '1' && !state.revision.wxPublicKeyId) {
    ElMessage.warning('微信支付必须填写 PUB_KEY_ID_ 开头的公钥ID');
    return;
  }
  if (isAlipayMini.value && !state.revision.buyerIdentityMode) {
    ElMessage.warning('支付宝小程序必须选择买家身份模式');
    return;
  }
  const refs = Object.entries(selectedMaterials)
    .filter(
      ([purpose, materialId]) =>
        requiredPurposes.value.includes(purpose) && materialId,
    )
    .map(([purpose, materialId]) => ({ purpose, materialId }));
  loading.value = true;
  try {
    await createRevision(state.form.id, {
      appId: state.revision.appId,
      mchId: state.revision.mchId,
      environment: state.revision.environment,
      wxPublicKeyId: state.revision.wxPublicKeyId,
      buyerIdentityMode: isAlipayMini.value
        ? state.revision.buyerIdentityMode
        : undefined,
      materials: refs,
    });
    ElMessage.success('身份版本已创建（草稿）');
    await loadRevisions();
  } finally {
    loading.value = false;
  }
}

async function onPublish(revision: any) {
  await ElMessageBox.confirm(
    '启用后新支付将使用该身份版本；历史订单仍按原交易身份处理。是否启用?',
    '启用身份版本',
    { type: 'warning' },
  );
  await publishRevision(
    state.form.id,
    revision.id,
    config.value?.configVersion,
  );
  ElMessage.success('已启用');
  await load(state.form.id);
  emit('initPage');
}

async function onDisable() {
  await ElMessageBox.confirm(
    '停用只禁止新发起，历史回调/退款仍可处理。是否停用?',
    '停用支付渠道',
    {
      type: 'warning',
    },
  );
  await disableConfig(state.form.id, config.value?.configVersion);
  ElMessage.success('已停用');
  await load(state.form.id);
  emit('initPage');
}

async function onCheck(revision: any) {
  checkResult.value = await checkRevision(state.form.id, revision.id);
  checkDialog.value = true;
}

function shortFingerprint(value?: string) {
  return value ? `${value.slice(0, 12)}…` : '-';
}

function isSensitive(purpose: string) {
  return [
    'ALI_APP_PRIVATE_KEY',
    'WX_API_V3_KEY',
    'WX_MERCHANT_PRIVATE_KEY',
  ].includes(purpose);
}

function purposeLabel(purpose: string) {
  return PURPOSE_META[purpose]?.label || purpose;
}

function close() {
  dialog.value = false;
  loading.value = false;
}

defineExpose({ initForm });
</script>

<template>
  <ElDialog
    v-model="dialog"
    :title="state.form.id ? '支付配置' : '新增支付配置'"
    width="72%"
    :before-close="close"
    class="pay-config-dialog"
  >
    <div class="form-container">
      <PageSection title="基本信息" description="支付渠道、终端与应用/商户身份">
        <ElAlert
          type="info"
          :closable="false"
          show-icon
          title="支付回调基础地址由订单配置统一维护"
          class="field-alert"
        >
          <template #default>
            不使用第二套通知地址；如需修改请前往
            <ElButton link type="primary" @click="router.push('/order/config')">
              订单配置
            </ElButton>
            （遵守原权限）。
          </template>
        </ElAlert>
        <ElAlert
          type="warning"
          :closable="false"
          show-icon
          title="小程序 AppID 必须与支付应用 ID 一致"
          class="field-alert"
        >
          <template #default>
            微信/支付宝小程序的 AppID
            与身份模式在三方账号维护；支付宝私钥与证书只存在支付材料中，不在三方账号重复存储。
            <ElButton
              link
              type="primary"
              @click="router.push('/user/social-account')"
            >
              查看三方账号
            </ElButton>
          </template>
        </ElAlert>

        <ElForm :model="state.form" label-width="110px">
          <ElRow :gutter="20">
            <ElCol :span="12">
              <ElFormItem label="支付渠道">
                <ElRadioGroup
                  v-model="state.form.type"
                  :disabled="Boolean(state.form.id && config?.activeRevisionId)"
                >
                  <ElRadio value="1">微信支付</ElRadio>
                  <ElRadio value="2">支付宝</ElRadio>
                </ElRadioGroup>
              </ElFormItem>
            </ElCol>
            <ElCol :span="12">
              <ElFormItem label="支付终端">
                <ElRadioGroup
                  v-model="state.form.terminalType"
                  :disabled="Boolean(state.form.id && config?.activeRevisionId)"
                >
                  <ElRadio
                    v-for="item in terminalOptions"
                    :key="item.value"
                    :value="String(item.value)"
                  >
                    {{ item.label }}
                  </ElRadio>
                </ElRadioGroup>
                <div v-if="state.form.type === '2'" class="field-hint">
                  支付宝小程序必须在身份版本中显式选择买家身份模式，并与三方账号保持一致。
                </div>
                <div v-if="state.form.type === '1'" class="field-hint">
                  微信支持小程序、App 与外部浏览器
                  H5；微信内网页请使用小程序支付。
                </div>
              </ElFormItem>
            </ElCol>
          </ElRow>
          <ElRow :gutter="20">
            <ElCol :span="12">
              <ElFormItem label="应用ID">
                <ElInput
                  v-model="state.form.appId"
                  :disabled="Boolean(state.form.id && config?.activeRevisionId)"
                  placeholder="微信 AppID / 支付宝 AppID"
                />
              </ElFormItem>
            </ElCol>
            <ElCol :span="12">
              <ElFormItem label="商户号/PID">
                <ElInput
                  v-model="state.form.mchId"
                  :disabled="Boolean(state.form.id && config?.activeRevisionId)"
                  placeholder="微信商户号 / 支付宝收款方 PID"
                />
              </ElFormItem>
            </ElCol>
          </ElRow>
          <ElButton
            type="primary"
            :loading="loading"
            v-access:code="
              state.form.id ? 'pay:payconfig:edit' : 'pay:payconfig:add'
            "
            @click="saveRoute"
          >
            {{ state.form.id ? '保存路由' : '创建路由' }}
          </ElButton>
          <span v-if="state.form.id" class="field-hint ml-2">
            路由ID：{{ state.form.id }}；启用状态：{{
              config?.enabled === '1' ? '启用' : '停用'
            }}
          </span>
        </ElForm>
      </PageSection>

      <PageSection
        v-if="state.form.id"
        title="密钥与证书"
        description="资料加密入库；私钥不回显、不可下载，不接受服务器路径"
      >
        <div class="upload-row">
          <ElSelect v-model="uploadPurpose" class="purpose-select">
            <ElOption
              v-for="purpose in purposes"
              :key="purpose.value"
              :label="purpose.label"
              :value="purpose.value"
            />
          </ElSelect>
          <CertUpload
            v-access:code="'pay:payconfig:material'"
            :config-id="state.form.id"
            :purpose="uploadPurpose"
            @success="onUploaded"
          />
          <span class="field-hint">{{
            PURPOSE_META[uploadPurpose]?.hint
          }}</span>
        </div>
        <ElTable :data="materials" border empty-text="尚未上传资料">
          <ElTableColumn label="用途" min-width="170">
            <template #default="scope">
              {{ purposeLabel(scope.row.purpose) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="指纹" min-width="150">
            <template #default="scope">
              {{ shortFingerprint(scope.row.fingerprint) }}
            </template>
          </ElTableColumn>
          <ElTableColumn label="有效期" min-width="190">
            <template #default="scope">
              <span v-if="scope.row.certNotAfter">
                {{ scope.row.certNotBefore || '-' }} ~
                {{ scope.row.certNotAfter }}
              </span>
              <span v-else>—</span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="状态" width="130">
            <template #default="scope">
              <ElTag v-if="isSensitive(scope.row.purpose)" type="warning">
                已加密，不回显
              </ElTag>
              <ElTag v-else type="success">可校验</ElTag>
            </template>
          </ElTableColumn>
        </ElTable>
      </PageSection>

      <PageSection
        v-if="state.form.id"
        title="身份版本与启用"
        description="换商户必须新建身份版本；同商户换证可新建版本后启用"
      >
        <ElAlert
          v-if="state.form.type === '2' && state.form.terminalType === '0'"
          type="info"
          :closable="false"
          show-icon
          title="支付宝小程序需真机验证后才可视为可用"
          class="field-alert"
        >
          <template #default>
            买家身份模式必须与三方账号一致；本地检查只证明材料内部一致，不代表商户签约、应用授权或真机支付已通过。没有真实应用资料时请保持停用。
          </template>
        </ElAlert>
        <ElTable :data="revisions" border empty-text="尚未创建身份版本">
          <ElTableColumn prop="revisionNo" label="#" width="50" />
          <ElTableColumn label="应用/商户" min-width="190">
            <template #default="scope">
              <div>{{ scope.row.appId }}</div>
              <div class="field-hint">
                {{ scope.row.mchId }} / {{ scope.row.environment }}
                <template v-if="scope.row.buyerIdentityMode">
                  / 买家身份：{{ scope.row.buyerIdentityMode }}
                </template>
                <template
                  v-else-if="
                    state.form.type === '2' && state.form.terminalType === '0'
                  "
                >
                  / <span class="field-warning">买家身份模式未配置</span>
                </template>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn prop="createTime" label="创建时间" width="170" />
          <ElTableColumn label="状态" width="150">
            <template #default="scope">
              <ElTag
                v-if="scope.row.id === config?.activeRevisionId"
                type="success"
              >
                生效中
              </ElTag>
              <ElTag v-else type="info">草稿</ElTag>
              <ElTag
                :type="scope.row.checkStatus === '1' ? 'success' : 'warning'"
              >
                {{ scope.row.checkStatus === '1' ? '本地通过' : '未检查' }}
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="操作" width="240" fixed="right">
            <template #default="scope">
              <ElButton
                link
                v-access:code="'pay:payconfig:check'"
                @click="onCheck(scope.row)"
              >
                检查
              </ElButton>
              <ElButton
                link
                type="primary"
                v-access:code="'pay:payconfig:enable'"
                :disabled="scope.row.id === config?.activeRevisionId"
                @click="onPublish(scope.row)"
              >
                启用
              </ElButton>
            </template>
          </ElTableColumn>
        </ElTable>
        <div class="field-hint mt-2">
          {{ activeRevision?.checkSummary || '启用前请先完成本地检查' }}
        </div>

        <ElDivider content-position="left">新建身份版本（草稿）</ElDivider>
        <ElForm :model="state.revision" label-width="120px">
          <ElRow :gutter="20">
            <ElCol :span="12">
              <ElFormItem label="应用ID">
                <ElInput v-model="state.revision.appId" />
              </ElFormItem>
            </ElCol>
            <ElCol :span="12">
              <ElFormItem label="商户号/PID">
                <ElInput v-model="state.revision.mchId" />
              </ElFormItem>
            </ElCol>
          </ElRow>
          <ElRow :gutter="20">
            <ElCol :span="12">
              <ElFormItem label="环境">
                <ElSelect v-model="state.revision.environment">
                  <ElOption label="生产 prod" value="prod" />
                  <ElOption
                    v-if="state.form.type === '2'"
                    label="沙箱 sandbox"
                    value="sandbox"
                  />
                </ElSelect>
              </ElFormItem>
            </ElCol>
            <ElCol v-if="state.form.type === '1'" :span="12">
              <ElFormItem label="微信公钥ID">
                <ElInput
                  v-model="state.revision.wxPublicKeyId"
                  placeholder="PUB_KEY_ID_..."
                />
              </ElFormItem>
            </ElCol>
            <ElCol v-if="isAlipayMini" :span="12">
              <ElFormItem label="买家身份模式">
                <ElSelect
                  v-model="state.revision.buyerIdentityMode"
                  placeholder="请选择"
                >
                  <ElOption label="user_id（支付宝 UID）" value="user_id" />
                  <ElOption label="open_id（支付宝 OpenID）" value="open_id" />
                </ElSelect>
              </ElFormItem>
            </ElCol>
          </ElRow>
          <ElFormItem
            v-for="purpose in requiredPurposes"
            :key="purpose"
            :label="purposeLabel(purpose)"
          >
            <ElSelect
              v-model="selectedMaterials[purpose]"
              placeholder="选择已上传材料"
              class="purpose-select"
            >
              <ElOption
                v-for="material in materialOptions(purpose)"
                :key="material.materialId"
                :label="`${shortFingerprint(material.fingerprint)} ${material.certSerialNo || ''}`"
                :value="material.materialId"
              />
            </ElSelect>
          </ElFormItem>
          <ElButton
            type="primary"
            :loading="loading"
            v-access:code="'pay:payconfig:material'"
            @click="onCreateRevision"
          >
            创建草稿
          </ElButton>
          <ElButton
            v-if="config?.enabled === '1'"
            type="danger"
            plain
            v-access:code="'pay:payconfig:enable'"
            @click="onDisable"
          >
            停用当前渠道
          </ElButton>
        </ElForm>
      </PageSection>
    </div>

    <template #footer>
      <div class="hx-form-footer">
        <ElButton @click="close">关闭</ElButton>
      </div>
    </template>

    <ElDialog
      v-model="checkDialog"
      title="本地资料检查"
      width="620px"
      append-to-body
    >
      <ElAlert
        v-if="checkResult"
        :title="checkResult.passed ? '本地检查通过' : '本地检查未通过'"
        :type="checkResult.passed ? 'success' : 'warning'"
        :closable="false"
        show-icon
      />
      <p class="field-hint mt-2">
        证明等级：{{
          checkResult?.proofLevel
        }}。仅证明材料内部一致，不代表平台签约、域名或真实支付已通过。
      </p>
      <ElTable v-if="checkResult" :data="checkResult.items" border>
        <ElTableColumn prop="name" label="检查项" min-width="160" />
        <ElTableColumn label="结果" width="90">
          <template #default="scope">
            <ElTag :type="scope.row.passed ? 'success' : 'danger'">
              {{ scope.row.passed ? '通过' : '失败' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="message" label="说明" min-width="220" />
      </ElTable>
    </ElDialog>
  </ElDialog>
</template>

<style scoped>
.form-container {
  max-height: 72vh;
  padding: 0 16px;
  overflow-y: auto;
}

.field-alert {
  margin-bottom: 12px;
}

.field-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.field-warning {
  color: var(--el-color-warning);
}

.upload-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}

.purpose-select {
  width: 280px;
}

.ml-2 {
  margin-left: 8px;
}

.mt-2 {
  margin-top: 8px;
}

.pay-config-dialog {
  min-width: 860px;
}

@media (max-width: 900px) {
  .pay-config-dialog {
    min-width: auto;
  }
}
</style>
