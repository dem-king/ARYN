<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  ElAvatar,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElTabPane,
  ElTabs,
} from 'element-plus';

import { useDict } from '#/utils/dict';

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
defineExpose({
  initPage,
});
</script>
<template>
  <ElDrawer v-model="dialog" title="用户明细" size="50%" direction="rtl">
    <ElTabs v-model="activeName">
      <ElTabPane label="用户信息" name="info">
        <ElDescriptions class="margin-top" title="基本信息" :column="3" border>
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
            {{ state.userInfo.point ?? 0 }}
          </ElDescriptionsItem>
          <ElDescriptionsItem>
            <template #label>
              <div class="cell-item">储值余额</div>
            </template>
            {{ state.userInfo.balance ?? 0 }}
          </ElDescriptionsItem>
        </ElDescriptions>
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
</template>
