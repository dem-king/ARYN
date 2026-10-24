<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElAvatar,
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElTabPane,
  ElTabs,
} from 'element-plus';

import {
  getList as getAllTags,
  getUserTags,
  tagUser,
  untagUser,
} from '#/api/user/member-tag';
import { adjustBalance, adjustPoint, getById } from '#/api/user/user-info';
import PageSection from '#/components/page/page-section.vue';
import { useDict } from '#/utils/dict';
import { formatMoney } from '#/utils/util';

const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);

const OrderRecord = defineAsyncComponent(
  () => import('./components/order-record.vue'),
);
const CouponRecord = defineAsyncComponent(
  () => import('./components/coupon-record.vue'),
);
const PointsRecord = defineAsyncComponent(
  () => import('./components/points-record.vue'),
);
const BalanceRecord = defineAsyncComponent(
  () => import('./components/balance-record.vue'),
);
const SignInRecord = defineAsyncComponent(
  () => import('./components/sign-in-record.vue'),
);

// 字典

const { user_source } = useDict('user_source');
const dialog = ref(false);
const activeName = ref('info');
const state = reactive({
  userInfo: {
    id: '',
    userNumber: '',
    avatarUrl: '',
    phone: '',
    nickname: '',
    userGrade: '',
    userSource: '',
    province: '',
    city: '',
    parentUserInfo: { avatarUrl: '', nickname: '' },
    levelName: '',
    point: 0,
    balance: 0,
  },
});
const tagOptions = ref<any[]>([]);
const selectedTagIds = ref<string[]>([]);
const originalTagIds = ref<string[]>([]);
const tagLoading = ref(false);
const tagSaving = ref(false);

const loadTags = async (userId: string) => {
  tagLoading.value = true;
  try {
    const [allTags, userTags]: any[] = await Promise.all([
      getAllTags(),
      getUserTags(userId),
    ]);
    tagOptions.value = allTags || [];
    const ids = (userTags || []).map((tag: any) => tag.id);
    selectedTagIds.value = ids;
    originalTagIds.value = ids;
  } finally {
    tagLoading.value = false;
  }
};

const saveTags = async () => {
  const toAdd = selectedTagIds.value.filter(
    (id) => !originalTagIds.value.includes(id),
  );
  const toRemove = originalTagIds.value.filter(
    (id) => !selectedTagIds.value.includes(id),
  );
  if (toAdd.length === 0 && toRemove.length === 0) {
    ElMessage.info('标签未变更');
    return;
  }
  tagSaving.value = true;
  try {
    if (toAdd.length > 0) {
      await tagUser(state.userInfo.id, toAdd);
    }
    if (toRemove.length > 0) {
      await untagUser(state.userInfo.id, toRemove);
    }
    ElMessage.success('标签保存成功');
    await loadTags(state.userInfo.id);
  } finally {
    tagSaving.value = false;
  }
};

const initPage = (userInfo: any) => {
  state.userInfo = userInfo;
  dialog.value = true;
  activeName.value = 'info';
  selectedTagIds.value = [];
  originalTagIds.value = [];
  loadTags(userInfo.id);
};

const adjustVisible = ref(false);
const adjustLoading = ref(false);
const adjustForm = reactive({
  changeType: '1',
  changePoint: 1,
  remark: '',
});

const openAdjust = () => {
  adjustForm.changeType = '1';
  adjustForm.changePoint = 1;
  adjustForm.remark = '';
  adjustVisible.value = true;
};

const submitAdjust = async () => {
  if (!adjustForm.changePoint || adjustForm.changePoint <= 0) {
    ElMessage.warning('请输入大于 0 的积分值');
    return;
  }
  if (
    adjustForm.changeType === '2' &&
    adjustForm.changePoint > (state.userInfo.point ?? 0)
  ) {
    ElMessage.warning('扣减积分不能超过当前积分余额');
    return;
  }
  adjustLoading.value = true;
  try {
    await adjustPoint({
      userId: state.userInfo.id,
      changeType: adjustForm.changeType,
      changePoint: adjustForm.changePoint,
      remark: adjustForm.remark || '管理端调整',
    });
    ElMessage.success('积分调整成功');
    adjustVisible.value = false;
    const res = await getById(state.userInfo.id);
    state.userInfo = res;
  } finally {
    adjustLoading.value = false;
  }
};
const balanceAdjustVisible = ref(false);
const balanceAdjustLoading = ref(false);
const balanceForm = reactive({
  changeType: '1',
  changeAmount: 1,
  remark: '',
});

const openBalanceAdjust = () => {
  balanceForm.changeType = '1';
  balanceForm.changeAmount = 1;
  balanceForm.remark = '';
  balanceAdjustVisible.value = true;
};

const submitBalanceAdjust = async () => {
  if (!balanceForm.changeAmount || balanceForm.changeAmount === 0) {
    ElMessage.warning('请输入非 0 的余额调整值');
    return;
  }
  if (
    balanceForm.changeType === '2' &&
    balanceForm.changeAmount > Number(state.userInfo.balance ?? 0)
  ) {
    ElMessage.warning('扣减余额不能超过当前余额');
    return;
  }
  balanceAdjustLoading.value = true;
  try {
    await adjustBalance({
      userId: state.userInfo.id,
      changeType: balanceForm.changeType,
      changeAmount: balanceForm.changeAmount,
      remark: balanceForm.remark || '管理端调整',
    });
    ElMessage.success('余额调整成功');
    balanceAdjustVisible.value = false;
    const res = await getById(state.userInfo.id);
    state.userInfo = res;
  } finally {
    balanceAdjustLoading.value = false;
  }
};

defineExpose({
  initPage,
});
</script>
<template>
  <ElDrawer v-model="dialog" title="用户明细" size="50%" direction="rtl">
    <ElTabs v-model="activeName">
      <ElTabPane label="用户信息" name="info">
        <PageSection title="基本信息" :bordered="false">
          <ElDescriptions :column="3" border>
            <ElDescriptionsItem>
              <template #label>
                <div class="cell-item">用户头像</div>
              </template>
              <ElAvatar :src="state.userInfo.avatarUrl" />
            </ElDescriptionsItem>
            <ElDescriptionsItem>
              <template #label>
                <div class="cell-item">用户手机号</div>
              </template>
              {{ state.userInfo.phone }}
            </ElDescriptionsItem>
            <ElDescriptionsItem>
              <template #label>
                <div class="cell-item">用户昵称</div>
              </template>
              {{ state.userInfo.nickname }}
            </ElDescriptionsItem>
            <ElDescriptionsItem>
              <template #label>
                <div class="cell-item">来源</div>
              </template>
              <DictTag
                :options="user_source"
                :value="state.userInfo.userSource"
              />
            </ElDescriptionsItem>
            <ElDescriptionsItem>
              <template #label>
                <div class="cell-item">所在省份</div>
              </template>
              {{ state.userInfo.province }}
            </ElDescriptionsItem>
            <ElDescriptionsItem>
              <template #label>
                <div class="cell-item">所在城市</div>
              </template>
              {{ state.userInfo.city }}
            </ElDescriptionsItem>
            <ElDescriptionsItem>
              <template #label>
                <div class="cell-item">会员等级</div>
              </template>
              {{ state.userInfo.levelName || '-' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem>
              <template #label>
                <div class="cell-item">积分余额</div>
              </template>
              <div class="flex items-center gap-2">
                <span class="hx-money">{{ state.userInfo.point ?? 0 }}</span>
                <ElButton
                  v-access:code="'user:userinfo:edit'"
                  link
                  type="primary"
                  size="small"
                  @click="openAdjust"
                >
                  调整
                </ElButton>
              </div>
            </ElDescriptionsItem>
            <ElDescriptionsItem>
              <template #label>
                <div class="cell-item">储值余额</div>
              </template>
              <div class="flex items-center gap-2">
                <span class="hx-money">
                  ¥{{ formatMoney(state.userInfo.balance) }}
                </span>
                <ElButton
                  v-access:code="'user:userinfo:edit'"
                  link
                  type="primary"
                  size="small"
                  @click="openBalanceAdjust"
                >
                  调整
                </ElButton>
              </div>
            </ElDescriptionsItem>
          </ElDescriptions>
        </PageSection>
      </ElTabPane>
      <ElTabPane label="会员标签" name="tags">
        <PageSection title="会员标签" :bordered="false">
          <ElSelect
            v-model="selectedTagIds"
            multiple
            filterable
            placeholder="请选择标签"
            style="width: 100%"
            :loading="tagLoading"
          >
            <ElOption
              v-for="tag in tagOptions"
              :key="tag.id"
              :label="
                tag.status === '1' ? `${tag.tagName}（已禁用）` : tag.tagName
              "
              :value="tag.id"
            />
          </ElSelect>
          <div class="mt-3">
            <ElButton type="primary" :loading="tagSaving" @click="saveTags">
              保存标签
            </ElButton>
          </div>
        </PageSection>
      </ElTabPane>
      <ElTabPane label="消费记录" name="order">
        <OrderRecord
          v-if="activeName === 'order'"
          :user-id="state.userInfo.id"
        />
      </ElTabPane>
      <ElTabPane label="优惠券记录" name="coupon">
        <CouponRecord
          v-if="activeName === 'coupon'"
          :user-id="state.userInfo.id"
        />
      </ElTabPane>
      <ElTabPane label="积分记录" name="points">
        <PointsRecord
          v-if="activeName === 'points'"
          :user-id="state.userInfo.id"
        />
      </ElTabPane>
      <ElTabPane label="余额记录" name="balance">
        <BalanceRecord
          v-if="activeName === 'balance'"
          :user-id="state.userInfo.id"
        />
      </ElTabPane>
      <ElTabPane label="签到记录" name="signin">
        <SignInRecord
          v-if="activeName === 'signin'"
          :user-id="state.userInfo.id"
        />
      </ElTabPane>
    </ElTabs>
  </ElDrawer>
  <ElDialog v-model="adjustVisible" title="调整积分" width="420px">
    <ElForm label-width="auto">
      <PageSection title="调整信息" :bordered="false">
        <ElFormItem label="调整类型">
          <ElRadioGroup v-model="adjustForm.changeType">
            <ElRadio value="1">增加</ElRadio>
            <ElRadio value="2">扣减</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="积分值">
          <ElInputNumber
            v-model="adjustForm.changePoint"
            :min="1"
            :precision="0"
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput
            v-model="adjustForm.remark"
            maxlength="100"
            placeholder="选填，默认“管理端调整”"
          />
        </ElFormItem>
      </PageSection>
    </ElForm>
    <template #footer>
      <div class="hx-form-footer">
        <ElButton @click="adjustVisible = false">取消</ElButton>
        <ElButton type="primary" :loading="adjustLoading" @click="submitAdjust">
          确定
        </ElButton>
      </div>
    </template>
  </ElDialog>
  <ElDialog v-model="balanceAdjustVisible" title="调整余额" width="420px">
    <ElForm label-width="auto">
      <PageSection title="调整信息" :bordered="false">
        <ElFormItem label="调整类型">
          <ElRadioGroup v-model="balanceForm.changeType">
            <ElRadio value="1">充值</ElRadio>
            <ElRadio value="2">扣减</ElRadio>
            <ElRadio value="3">调整</ElRadio>
          </ElRadioGroup>
        </ElFormItem>
        <ElFormItem label="余额值（元）">
          <ElInputNumber
            v-model="balanceForm.changeAmount"
            :min="balanceForm.changeType === '3' ? -999999 : 0.01"
            :precision="2"
            style="width: 100%"
          />
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput
            v-model="balanceForm.remark"
            maxlength="100"
            placeholder="选填，默认“管理端调整”"
          />
        </ElFormItem>
      </PageSection>
    </ElForm>
    <template #footer>
      <div class="hx-form-footer">
        <ElButton @click="balanceAdjustVisible = false">取消</ElButton>
        <ElButton
          type="primary"
          :loading="balanceAdjustLoading"
          @click="submitBalanceAdjust"
        >
          确定
        </ElButton>
      </div>
    </template>
  </ElDialog>
</template>
