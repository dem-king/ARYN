<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';

import { Box } from '@element-plus/icons-vue';
import {
  ElAvatar,
  ElButton,
  ElCol,
  ElDivider,
  ElImage,
  ElMessage,
  ElMessageBox,
  ElRow,
  ElStep,
  ElSteps,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTimeline,
  ElTimelineItem,
} from 'element-plus';

import { getById, selffetchObj } from '#/api/order/order-info';
import { getById as getUserById } from '#/api/user/user-info';
import { useDict } from '#/utils/dict';

const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);

interface DataState {
  orderInfo: {
    couponPrice: number;
    deliverTime: string;
    deliveryWay: string;
    freightPrice: number;
    id: string;
    orderDelivery: any;
    orderItemList: any[];
    orderNo: string;
    paymentPrice: number;
    paymentTime: string;
    paymentType: string;
    payStatus: string;
    receiverTime: string;
    recipientAddress: string;
    recipientArea: string;
    recipientCity: string;
    recipientName: string;
    recipientPhone: string;
    recipientProvince: string;
    status: string;
    totalPrice: number;
  };
  userInfo: {
    avatarUrl: string;
    nickname: string;
    phone: string;
  };
  stepActive: number;
  dialog: boolean;
}
const Deliver = defineAsyncComponent(() => import('../deliver/index.vue'));
// 字典
const { pay_type, delivery_way, order_item_status } = useDict(
  'pay_type',
  'delivery_way',
  'order_item_status',
);
const state = reactive<DataState>({
  orderInfo: {
    id: '',
    orderNo: '',
    payStatus: '',
    paymentType: '',
    paymentTime: '',
    paymentPrice: 0,
    totalPrice: 0,
    freightPrice: 0,
    couponPrice: 0,
    receiverTime: '',
    deliverTime: '',
    deliveryWay: '',
    orderItemList: [],
    status: '',
    recipientName: '',
    recipientPhone: '',
    recipientProvince: '',
    recipientCity: '',
    recipientArea: '',
    recipientAddress: '',
    orderDelivery: null,
  },
  userInfo: { avatarUrl: '', phone: '', nickname: '' },
  stepActive: 0,
  dialog: false,
});
const loading = ref(false);
const refDeliver = ref();
const route = useRoute();
const getDetail = () => {
  const { id }: any = route.query;
  if (id) {
    loading.value = true;
    getById(id)
      .then((response) => {
        loading.value = false;
        state.orderInfo = response;
        const status = state.orderInfo.status;
        state.stepActive = status === '7' ? 1 : Number(status) - 1;
        getUser(response.userId);
      })
      .catch(() => {
        loading.value = false;
      });
  }
};

// 查询用户信息
const getUser = (userId: string) => {
  getUserById(userId).then((response) => {
    state.userInfo = response;
  });
};
/**
 * 发货
 */
const deliverOrder = (row: any) => {
  refDeliver.value.initPage(row.id, row.orderLogisticsId);
};
/**
 * 自提
 */
const selffetchOrder = () => {
  ElMessageBox.confirm('此操作将完成订单提货，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    selffetchObj({ id: state.orderInfo.id })
      .then(() => {
        ElMessage.success('提货成功');
        getDetail();
      })
      .catch(() => {});
  });
};
getDetail();
</script>
<template>
  <div class="hx-layout-container" v-loading="loading">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <div class="top-order-info">
        <span>订单号：{{ state.orderInfo.orderNo }}</span>
        <ElDivider v-if="state.orderInfo.paymentType" direction="vertical" />
        <DictTag
          v-if="state.orderInfo.payStatus === '1'"
          :options="pay_type"
          :value="state.orderInfo.paymentType"
        />

        <ElDivider v-if="state.orderInfo.paymentTime" direction="vertical" />

        <span v-if="state.orderInfo.paymentTime">
          付款时间：{{ state.orderInfo.paymentTime }}
        </span>

        <ElDivider v-if="state.orderInfo.receiverTime" direction="vertical" />
        <span v-if="state.orderInfo.receiverTime">
          收货时间：{{ state.orderInfo.receiverTime }}
        </span>
      </div>
      <div class="order-status-row">
        <div class="row-left">
          <div v-if="state.orderInfo.status === '1'" class="title">
            等待买家付款，超时系统自动取消并返还库存。
          </div>
          <div v-if="state.orderInfo.status === '2'">
            <div class="title">
              <div v-if="state.orderInfo.deliveryWay === '1'">
                买家已付款，等待商家发货
              </div>
              <div v-if="state.orderInfo.deliveryWay === '2'">
                买家已付款，请尽快备货
              </div>
              <p style="font-size: 14px; color: rgb(150 151 153)">
                买家已付款至待结算账户，{{
                  state.orderInfo.deliveryWay === '1'
                    ? '请尽快发货'
                    : '请尽快备货'
                }}，否则买家有权申请退款。
              </p>
            </div>
            <div>
              <ElButton
                type="primary"
                v-access:code="'order:orderinfo:deliver'"
                @click="deliverOrder(state.orderInfo)"
              >
                发货
              </ElButton>
            </div>
          </div>
          <div v-if="state.orderInfo.status === '3'">
            <div class="title">
              <div v-if="state.orderInfo.deliveryWay === '1'">
                商家已发货，等待签收。
              </div>
              <div v-if="state.orderInfo.deliveryWay === '2'">
                商家已备货，等待提货。
              </div>
              <ElButton
                type="primary"
                v-access:code="'order:orderinfo:deliver'"
                v-if="
                  state.orderInfo.status === '3' &&
                  state.orderInfo.deliveryWay === '2'
                "
                @click="selffetchOrder"
                :icon="Box"
              >
                提货
              </ElButton>
            </div>
          </div>
          <div v-if="state.orderInfo.status === '4'">
            <div class="title">交易完成</div>
          </div>
          <div v-if="state.orderInfo.status === '11'">
            <div class="title">交易已关闭</div>
          </div>
        </div>
        <div class="row-right">
          <div
            style="width: 80%"
            v-if="
              state.orderInfo.status !== '11' && state.orderInfo.status !== '6'
            "
          >
            <ElSteps :active="state.stepActive" finish-status="success">
              <ElStep title="买家待付款" />
              <ElStep
                :title="
                  state.orderInfo.deliveryWay === '1'
                    ? '商家待发货'
                    : '商家待备货'
                "
              />
              <ElStep
                :title="
                  state.orderInfo.deliveryWay === '1'
                    ? '买家待收货'
                    : '买家待提货'
                "
              />
              <ElStep title="交易完成" />
            </ElSteps>
          </div>
        </div>
      </div>
      <div class="order-info">
        <div
          class="item"
          v-if="state.orderInfo && state.orderInfo.deliveryWay === '1'"
        >
          <div class="title">收货人信息</div>
          <div class="content">
            <p>
              <span>收货人:</span>
              <span>{{ state.orderInfo.recipientName }}</span>
            </p>
            <p>
              <span>联系电话:</span>
              <span>{{ state.orderInfo.recipientPhone }}</span>
            </p>
            <p>
              <span>收货地址:</span>
              <span>
                {{ state.orderInfo.recipientProvince
                }}{{ state.orderInfo.recipientCity
                }}{{ state.orderInfo.recipientArea
                }}{{ state.orderInfo.recipientAddress }}
              </span>
            </p>
          </div>
        </div>
        <div class="item">
          <div class="title">配送信息</div>
          <div class="content">
            <p>
              <span>配送方式：</span>
              <span>
                <DictTag
                  :options="delivery_way"
                  :value="state.orderInfo.deliveryWay"
                />
              </span>
            </p>
            <p v-if="state.orderInfo.deliveryWay === '1'">
              <span v-if="state.orderInfo.deliverTime">
                发货时间：{{ state.orderInfo.deliverTime }}
              </span>
            </p>
          </div>
        </div>
        <div class="item">
          <div class="title">付款信息</div>

          <div class="content">
            <p>
              <span>实付金额:</span>
              <span style="color: red">
                ￥{{ state.orderInfo.paymentPrice }}
              </span>
            </p>
            <p v-if="state.orderInfo.paymentType">
              <span>付款方式:</span>
              <span>
                <DictTag
                  :options="pay_type"
                  :value="state.orderInfo.paymentType"
                />
              </span>
            </p>
            <p v-if="state.orderInfo.paymentTime">
              <span>付款时间:</span>
              <span v-if="state.orderInfo.paymentTime">{{
                state.orderInfo.paymentTime
              }}</span>
            </p>
          </div>
        </div>
        <div class="item">
          <div class="title">买家信息</div>
          <div class="content">
            <p>
              <span>买家:</span>
              <span v-if="state.userInfo">{{ state.userInfo.nickname }}</span>
            </p>
            <p>
              <span>手机号:</span>
              <span v-if="state.userInfo">{{ state.userInfo.phone }}</span>
            </p>
            <p>
              <span>头像:</span>
              <span v-if="state.userInfo">
                <ElAvatar :src="state.userInfo.avatarUrl" style="width: 40px" />
              </span>
            </p>
          </div>
        </div>
      </div>
      <div class="order-info logistics" v-if="state.orderInfo.orderDelivery">
        <div class="logistics-wrapper">
          <div class="logistics-info">
            <div class="item">
              <div class="content-wrapper">
                <div class="title">包裹</div>
                <div class="content">
                  <p>
                    <span>快递公司：</span>
                    <span>{{
                      state.orderInfo.orderDelivery.logisticsCompanyName
                    }}</span>
                  </p>
                  <p>
                    <span>快递单号：</span>
                    <span class="tracking-number">{{
                      state.orderInfo.orderDelivery.logisticsNo
                    }}</span>
                  </p>
                  <p>
                    <span>发货时间：</span>
                    <span>{{ state.orderInfo.orderDelivery.deliverTime }}</span>
                  </p>
                  <p>
                    <span>签收状态：</span>
                    <span
                      :class="{
                        'status-signed':
                          state.orderInfo.orderDelivery.isCheck === '1',
                      }"
                    >
                      {{
                        state.orderInfo.orderDelivery.isCheck === '1'
                          ? '已签收'
                          : '未签收'
                      }}
                    </span>
                  </p>
                </div>
                <div
                  class="logistics-track"
                  v-if="
                    state.orderInfo.orderDelivery.logisticsList &&
                    state.orderInfo.orderDelivery.logisticsList.length > 0
                  "
                >
                  <div class="title">物流轨迹</div>
                  <div class="track-content">
                    <ElTimeline style="max-width: 600px">
                      <ElTimelineItem
                        v-for="(track, trackIndex) in state.orderInfo
                          .orderDelivery.logisticsList"
                        :key="trackIndex"
                        :type="trackIndex === 0 ? 'primary' : 'info'"
                        :hollow="trackIndex === 0"
                        :timestamp="track.logisticsTime"
                      >
                        <div class="track-item">
                          <div class="track-info">
                            {{ track.logisticsContext }}
                          </div>
                        </div>
                      </ElTimelineItem>
                    </ElTimeline>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <ElTable :data="state.orderInfo.orderItemList" border>
        <ElTableColumn prop="date" label="商品信息" width="400">
          <template #default="scope">
            <div class="order-item">
              <div style="width: 350px">
                <ElRow>
                  <ElCol :span="6">
                    <ElImage
                      style="width: 60px; height: 60px"
                      :src="scope.row.picUrl"
                      fit="cover"
                      :preview-teleported="true"
                    />
                  </ElCol>
                  <ElCol :span="18">
                    <span class="overflow-line-clamp-2 name">{{
                      scope.row.spuName
                    }}</span>
                    <p>{{ scope.row.specsInfo }}</p>
                  </ElCol>
                </ElRow>
              </div>
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="salesPrice" label="单价（元）" width="150" />
        <ElTableColumn prop="buyQuantity" label="数量" width="100" />
        <ElTableColumn prop="freightPrice" label="运费（元）" width="150" />
        <ElTableColumn prop="couponPrice" label="优惠金额（元）" width="150" />
        <ElTableColumn prop="paymentPrice" label="小记（元）" />
        <ElTableColumn prop="address" label="状态">
          <template #default="scope">
            <ElTag
              v-if="
                state.orderInfo.deliveryWay === '2' && scope.row.status === '1'
              "
            >
              备货中
            </ElTag>
            <ElTag
              v-else-if="
                state.orderInfo.deliveryWay === '2' && scope.row.status === '2'
              "
            >
              待自提
            </ElTag>
            <DictTag
              v-else
              :options="order_item_status"
              :value="scope.row.status"
            />
          </template>
        </ElTableColumn>
      </ElTable>
      <!--订单金额明细-->
      <div class="amount-info">
        <div>
          <p>
            <span>商品总价：</span>
            <span>￥{{ state.orderInfo.totalPrice }}</span>
          </p>
          <p>
            <span>运费：</span>
            <span style="color: red">
              +￥{{ state.orderInfo.freightPrice }}
            </span>
          </p>
          <p>
            <span>优惠券：</span>
            <span style="color: red">-￥{{ state.orderInfo.couponPrice }}</span>
          </p>
          <p v-if="state.orderInfo.payStatus === '1'" class="pay-type">
            <span>支付方式：</span>
            <span>
              <DictTag
                :options="pay_type"
                :value="state.orderInfo.paymentType"
              />
            </span>
          </p>
          <p class="pay-price">
            <span>实收金额：</span>
            <span>￥{{ state.orderInfo.paymentPrice }}</span>
          </p>
        </div>
      </div>
      <Deliver ref="refDeliver" @handle-success="getDetail" />
    </div>
  </div>
</template>
<style scoped lang="scss">
.order-status-row {
  display: flex;
  padding: 30px;
  margin-top: 20px;
  border: 1px solid #ebedf0;

  .title {
    margin-bottom: 20px;
    font-size: 20px;
    font-weight: 700;
    line-height: 28px;
    color: #323233;
  }
}

.row-left {
  width: 33%;
  padding: 28px 16px;
  font-size: 14px;
}

.row-right {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  border-left: 1px solid #ebedf0;
}

.order-item {
  display: flex;
  justify-content: space-between;
  padding: 5px;

  .name {
    color: #409eff;
  }
}

.logistics-title {
  margin-bottom: 16px;
  font-weight: 700;
  color: #323233;
}

.top-order-info {
  display: flex;
  align-items: center;

  span {
    padding: 10px;
  }
}

.order-info {
  display: flex;
  margin-bottom: 16px;
  background-color: #f7f8fa;
  border: 1px solid #fefef0;

  .item {
    flex: 1;
    padding: 16px 12px;
    font-size: 14px;
    color: #646566;
  }

  .title {
    margin-bottom: 16px;
    font-weight: 700;
    color: #323233;
  }

  .content {
    p {
      display: flex;
      margin-bottom: 8px;
      font-size: 12px;
      color: #646566;

      span {
        &:first-child {
          width: 80px;
        }

        &:last-child {
          flex: 1;
        }
      }
    }
  }
}

.amount-info {
  display: flex;
  -ms-flex-pack: end;
  justify-content: flex-end;
  padding: 16px 32px 16px 0;
  color: #646566;

  p {
    display: flex;
    margin-bottom: 8px;
    font-size: 12px;
    color: #646566;

    &.pay-price {
      margin-top: 20px;
      font-size: 14px;
      font-weight: 600;
      color: #323233;

      span {
        &:last-child {
          font-size: 18px;
          font-weight: bold;
          color: #d40000;
        }
      }
    }

    &.pay-type {
      margin-top: 20px;
      font-size: 14px;
      font-weight: 600;
      color: #323233;

      span {
        &:last-child {
          font-size: 16px;
          font-weight: bold;
        }
      }
    }

    span {
      &:first-child {
        width: 100px;
      }

      &:last-child {
        flex: 1;
      }
    }
  }
}

.logistics {
  .logistics-wrapper {
    .logistics-info {
      .item {
        padding-bottom: 20px;
        margin-bottom: 20px;
        border-bottom: 1px solid #ebedf0;

        &:last-child {
          margin-bottom: 0;
          border-bottom: none;
        }

        // 调整内容区域布局
        .content-wrapper {
          display: flex;
          gap: 20px;

          .content {
            width: 350px;

            .title {
              margin-bottom: 15px;
            }
          }

          .logistics-track {
            flex: 1;
            padding-left: 40px;
            border-left: 1px solid #ebedf0;

            .title {
              margin-bottom: 15px;
            }

            .track-content {
              max-height: 200px;
              overflow-y: auto;
            }
          }
        }
      }
    }
  }
}
</style>
