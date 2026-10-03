<script lang="ts" setup>
import { computed, defineAsyncComponent, ref } from 'vue';

import { ArrowDown } from '@element-plus/icons-vue';
import { ElButton, ElCol, ElDialog, ElImage, ElRow } from 'element-plus';

import { useDict } from '#/utils/dict';

import { groupOrderItems } from './category-group';
import { buildOrderSummary } from './order-summary';

const props = defineProps<{
  row: any;
}>();

const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);
const { order_item_status } = useDict('order_item_status');

const groups = computed(() => groupOrderItems(props.row?.orderItemList));
const summary = computed(() => buildOrderSummary(props.row?.orderItemList));

/** 完整商品明细用弹窗展示，避免大订单把列表行撑高 */
const dialogVisible = ref(false);
</script>

<template>
  <!-- 折叠态：只展示首件商品，缩略图堆叠收口，避免大订单把列表撑高 -->
  <div v-if="summary" class="order-collapsed">
    <ElRow class="order-item">
      <ElCol :span="4">
        <ElImage
          style="width: 48px; height: 48px"
          :src="summary.firstItem.picUrl"
          :preview-src-list="[summary.firstItem.picUrl]"
          :preview-teleported="true"
          fit="cover"
        />
      </ElCol>
      <ElCol :span="15">
        <span class="name name--single">{{ summary.firstItem.spuName }}</span>
        <p class="spec--single">{{ summary.firstItem.specsInfo }}</p>
      </ElCol>
      <ElCol :span="5" class="order-item-pay">
        <span style="color: #f56c6c">
          ￥{{ summary.firstItem.paymentPrice }}
        </span>
        <p>x{{ summary.firstItem.buyQuantity }}</p>
        <DictTag
          v-if="
            summary.firstItem.status === '3' || summary.firstItem.status === '6'
          "
          :options="order_item_status"
          :value="summary.firstItem.status"
        />
      </ElCol>
    </ElRow>
    <!-- 单件商品不需要折叠条与展开入口 -->
    <div v-if="summary.collapsible" class="order-collapse-bar">
      <div class="thumb-stack">
        <ElImage
          v-for="(thumb, index) in summary.thumbnails"
          :key="index"
          class="thumb"
          :src="thumb.picUrl"
          :preview-src-list="[thumb.picUrl]"
          :preview-teleported="true"
          fit="cover"
        />
        <span v-if="summary.hiddenCount > 0" class="thumb thumb--more">
          +{{ summary.hiddenCount }}
        </span>
      </div>
      <span class="order-collapse-summary">
        共 {{ summary.totalQuantity }} 件 · {{ summary.categoryCount }} 个分类
      </span>
      <ElButton
        link
        type="primary"
        :icon="ArrowDown"
        @click="dialogVisible = true"
      >
        明细
      </ElButton>
    </div>
    <!-- 完整商品明细弹窗：分类分组与订单详情、导出 Excel 同口径 -->
    <ElDialog
      v-model="dialogVisible"
      title="订单商品明细"
      width="560px"
      append-to-body
      destroy-on-close
    >
      <div class="order-detail-dialog">
        <p class="order-detail-summary">
          共 {{ summary.totalQuantity }} 件 · {{ summary.categoryCount }} 个分类
        </p>
        <template v-for="group in groups" :key="group.key">
          <div
            class="order-category-row"
            :class="{ uncategorized: group.isUncategorized }"
          >
            {{ group.key }}
          </div>
          <ElRow
            v-for="(item, index) in group.items"
            :key="`${group.key}-${index}`"
            style="padding: 0 !important"
            class="order-item"
          >
            <ElCol :span="4">
              <ElImage
                style="width: 60px; height: 60px"
                :src="item.picUrl"
                :preview-src-list="[item.picUrl]"
                :preview-teleported="true"
                fit="cover"
              />
            </ElCol>
            <ElCol :span="15">
              <span class="name line-clamp-2">
                {{ item.spuName }}
              </span>
              <p style="font-size: 12px; color: #a8abb2">
                {{ item.specsInfo }}
              </p>
              <p>
                <span style="color: red" v-if="item.couponPrice > 0">
                  优惠券减免：-{{ item.couponPrice }}元
                </span>
              </p>
            </ElCol>
            <ElCol :span="5" style="padding-left: 20px">
              <span style="color: #f56c6c">￥{{ item.paymentPrice }}</span>
              <p>x{{ item.buyQuantity }}</p>
              <DictTag
                v-if="item.status === '3' || item.status === '6'"
                :options="order_item_status"
                :value="item.status"
              />
            </ElCol>
          </ElRow>
        </template>
      </div>
      <template #footer>
        <ElButton type="primary" @click="dialogVisible = false">关闭</ElButton>
      </template>
    </ElDialog>
  </div>
  <span v-else>—</span>
</template>

<style scoped lang="scss">
.order-category-row {
  padding: 4px 8px;
  margin: 4px 0;
  font-weight: 600;
  color: #323233;
  background: #f5f7fa;
  border-radius: 4px;

  &.uncategorized {
    font-weight: 400;
    color: #a8abb2;
  }
}

.order-item {
  margin-right: 0 !important;
  margin-left: 0 !important;

  p {
    margin: 2px 0;
  }

  .name {
    color: #409eff;
  }
}

/* 折叠态：首件商品名与规格单行截断，行高不随内容膨胀 */
.name--single,
.spec--single {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.spec--single {
  margin: 2px 0 0;
  font-size: 12px;
  color: #a8abb2;
}

.order-collapse-bar {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-top: 6px;
}

.thumb-stack {
  display: flex;
  flex-shrink: 0;
  align-items: center;

  .thumb {
    width: 28px;
    height: 28px;
    overflow: hidden;
    background: #fff;
    border: 1px solid #e5e6eb;
    border-radius: 4px;

    /* 相邻缩略图堆叠 */
    &:not(:first-child) {
      margin-left: -10px;
    }

    /* +N 计数与缩略图分离，避免互相遮挡 */
    &.thumb--more {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      margin-left: 4px;
      font-size: 11px;
      color: #4e5969;
      background: #f2f3f5;
    }
  }
}

.order-collapse-summary {
  font-size: 12px;
  color: #86909c;
}

.order-detail-dialog {
  max-height: 60vh;
  overflow-y: auto;
}

.order-detail-summary {
  margin: 0 0 8px;
  font-size: 12px;
  color: #86909c;
}
</style>
