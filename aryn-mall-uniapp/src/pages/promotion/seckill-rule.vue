<script setup lang="ts">
definePage({
  name: 'seckill-rule',
  style: {
    navigationStyle: 'custom',
    navigationBarTitleText: '限时秒杀活动规则',
  },
})

/** 规则模块：标题 + 条目；条目里的加粗片段用 {bold} 标记，渲染时拆成两段 */
const RULE_SECTIONS = [
  {
    title: '一、活动时间',
    items: [
      '每天多场整点开启，具体场次以页面展示为准',
      '每场活动持续约 2 小时，售完即止',
      '未开场次可提前预览商品，到点自动开抢',
    ],
  },
  {
    title: '二、参与条件',
    items: [
      '已登录并完成实名认证的用户均可参与',
      '每个用户每场活动限购 {1 件}（同一 SKU）',
      '秒杀商品不支持使用优惠券、不支持积分抵现，也不参与其他满减活动',
    ],
  },
  {
    title: '三、抢购流程',
    items: [
      '进入「限时秒杀」会场，选择对应场次',
      '开抢后点击商品进入详情页，点击「加入购物车」',
      '在购物车确认商品数量，提交订单并完成支付',
      '订单需在 {15 分钟内} 完成支付，超时自动取消',
    ],
    ordered: true,
  },
  {
    title: '四、库存与发货',
    items: [
      '秒杀商品限量供应，先到先得，售完即止',
      '库存以页面实时显示为准，加入购物车不代表锁定库存',
      '发货时效与普通商品一致，按靠港计划安排配送',
    ],
  },
  {
    title: '五、订单与售后',
    items: [
      '秒杀商品支持 7 天无理由退货（未拆封、不影响二次销售）',
      '因质量问题产生的退换货运费由平台承担',
      '秒杀订单不支持修改地址、不支持合并下单',
      '如遇缺货，平台将主动退款并通知用户',
    ],
  },
  {
    title: '六、其他说明',
    items: [
      '如遇不可抗力或系统故障，平台有权调整或暂停活动',
      '严禁使用外挂、脚本等作弊手段，违者取消参与资格',
      '本活动最终解释权归悦航购所有',
    ],
  },
]

/** 把「{加粗}」拆成片段，模板逐段渲染（模块加载时算一次，避免模板里反复正则） */
function toSegments(item: string) {
  const match = item.match(/^(.*?)\{(.+?)\}(.*)$/)
  if (!match)
    return [{ strong: false, text: item }]
  return [
    { strong: false, text: match[1] ?? '' },
    { strong: true, text: match[2] ?? '' },
    { strong: false, text: match[3] ?? '' },
  ].filter(segment => segment.text.length > 0)
}
</script>

<template>
  <view class="rule-page">
    <hr-navbar title="限时秒杀活动规则" :bordered="false" />

    <scroll-view scroll-y class="rule-scroll">
      <view class="rule-body">
        <view v-for="section in RULE_SECTIONS" :key="section.title" class="rule-section">
          <view class="rule-title">
            {{ section.title }}
          </view>
          <view
            v-for="(item, index) in section.items"
            :key="item"
            class="rule-item"
          >
            <text class="rule-dot">
              {{ section.ordered ? `${index + 1}.` : '•' }}
            </text>
            <text class="rule-text">
              <text
                v-for="(segment, segIndex) in toSegments(item)"
                :key="`${index}-${segIndex}`"
                :class="{ 'rule-strong': segment.strong }"
              >
                {{ segment.text }}
              </text>
            </text>
          </view>
        </view>

        <text class="rule-foot">
          如有疑问，请联系在线客服
        </text>
      </view>
    </scroll-view>
  </view>
</template>

<style lang="scss" scoped>
.rule-page {
  display: flex;
  height: 100vh;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
}

.rule-scroll {
  min-height: 0;
  flex: 1;
}

.rule-body {
  padding: 8rpx 32rpx calc(48rpx + env(safe-area-inset-bottom));
}

.rule-section {
  margin-top: 40rpx;
}

.rule-title {
  color: #1f2329;
  font-size: 30rpx;
  font-weight: 700;
  line-height: 1.5;
}

.rule-item {
  display: flex;
  margin-top: 16rpx;
  gap: 12rpx;
}

/* 小红点做视觉锚点（有序条目直接用序号） */
.rule-dot {
  flex: 0 0 auto;
  color: var(--wot-color-theme-primary, #ff2237);
  font-size: 26rpx;
  font-weight: 700;
  line-height: 1.8;
}

.rule-text {
  flex: 1;
  color: #333;
  font-size: 26rpx;
  line-height: 1.8;
}

.rule-strong {
  color: #1f2329;
  font-weight: 700;
}

.rule-foot {
  display: block;
  padding-top: 56rpx;
  color: #b6b9c0;
  font-size: 20rpx;
  text-align: center;
}
</style>
