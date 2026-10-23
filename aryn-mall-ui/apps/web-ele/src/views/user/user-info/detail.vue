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
  ElRadio,
  ElRadioGroup,
  ElTabPane,
  ElTabs,
} from 'element-plus';

import { adjustPoint, getById } from '#/api/user/user-info';
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
const initPage = (userInfo: any) => {
  state.userInfo = userInfo;
  dialog.value = true;
  activeName.value = 'info';
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
              <span class="hx-money">
                ¥{{ formatMoney(state.userInfo.balance) }}
              </span>
            </ElDescriptionsItem>
          </ElDescriptions>
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
</template>
