<script lang="ts" setup>
import { computed, defineAsyncComponent, ref } from 'vue';

import { ArrowDown } from '@element-plus/icons-vue';
import { ElButton, ElDialog, ElImage } from 'element-plus';

import { useDict } from '#/utils/dict';
import { formatMoney } from '#/utils/util';

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
  <!-- 折叠态：只展示首件商品，缩略图堆叠收口，避免大订单把列表行撑高 -->
  <div v-if="summary" class="order-collapsed">
    <div class="oi-goods">
      <ElImage
        class="oi-goods-pic"
        :src="summary.firstItem.picUrl"
        :preview-src-list="[summary.firstItem.picUrl]"
        :preview-teleported="true"
        fit="cover"
      />
      <div class="oi-goods-main">
        <span class="oi-goods-name" :title="summary.firstItem.spuName">
          {{ summary.firstItem.spuName }}
        </span>
        <span
          v-if="summary.firstItem.specsInfo"
          class="oi-goods-spec"
          :title="summary.firstItem.specsInfo"
        >
          {{ summary.firstItem.specsInfo }}
        </span>
      </div>
      <div class="oi-goods-side">
        <span class="oi-goods-price">
          <span class="oi-currency">¥</span>
          <span>{{ formatMoney(summary.firstItem.paymentPrice) }}</span>
        </span>
        <span class="oi-goods-qty">×{{ summary.firstItem.buyQuantity }}</span>
        <DictTag
          v-if="
            summary.firstItem.status === '3' || summary.firstItem.status === '6'
          "
          :options="order_item_status"
          :value="summary.firstItem.status"
        />
      </div>
    </div>
    <!-- 单件商品不需要折叠条与展开入口 -->
    <div v-if="summary.collapsible" class="oi-collapse">
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
      <span class="oi-collapse-summary">
        共 <b>{{ summary.totalQuantity }}</b> 件 · {{ summary.categoryCount }}
        个分类
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
      width="600px"
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
          <div
            v-for="(item, index) in group.items"
            :key="`${group.key}-${index}`"
            class="oi-dialog-item"
          >
            <ElImage
              class="oi-dialog-pic"
              :src="item.picUrl"
              :preview-src-list="[item.picUrl]"
              :preview-teleported="true"
              fit="cover"
            />
            <div class="oi-dialog-main">
              <span class="oi-dialog-name">
                {{ item.spuName }}
              </span>
              <span v-if="item.specsInfo" class="oi-dialog-spec">
                {{ item.specsInfo }}
              </span>
              <span
                v-if="Number(item.couponPrice) > 0"
                class="oi-dialog-coupon"
              >
                优惠券减免 -¥{{ formatMoney(item.couponPrice) }}
              </span>
            </div>
            <div class="oi-dialog-side">
              <span class="oi-dialog-price">
                <span class="oi-currency">¥</span>
                <span>{{ formatMoney(item.paymentPrice) }}</span>
              </span>
              <span class="oi-goods-qty">×{{ item.buyQuantity }}</span>
              <DictTag
                v-if="item.status === '3' || item.status === '6'"
                :options="order_item_status"
                :value="item.status"
              />
            </div>
          </div>
        </template>
      </div>
      <template #footer>
        <ElButton type="primary" @click="dialogVisible = false">关闭</ElButton>
      </template>
    </ElDialog>
  </div>
  <span v-else class="oi-empty">—</span>
</template>

<style scoped lang="scss">
.order-category-row {
  padding: 6px 10px;
  margin: 10px 0 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border-radius: 6px;

  &.uncategorized {
    font-weight: 400;
    color: var(--el-text-color-secondary);
  }

  &:first-of-type {
    margin-top: 4px;
  }
}

/* ---------- 折叠态：首件商品 ---------- */
.oi-goods {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  min-width: 0;
}

.oi-goods-pic {
  flex: none;
  width: 52px;
  height: 52px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.oi-goods-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  padding-top: 1px;
}

.oi-goods-name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.4;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.oi-goods-spec {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.oi-goods-side {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: 2px;
  align-items: flex-end;
  min-width: 76px;

  :deep(.el-tag) {
    height: 20px;
    padding: 0 6px;
    font-size: 11px;
  }
}

.oi-goods-price {
  display: inline-flex;
  align-items: baseline;
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1.4;
  color: var(--el-color-danger);
}

.oi-currency {
  margin-right: 1px;
  font-size: 11px;
  font-weight: 500;
}

.oi-goods-qty {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-secondary);
}

.oi-collapse {
  display: flex;
  gap: 8px;
  align-items: center;
  padding-top: 6px;
  margin-top: 8px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.oi-collapse-summary {
  flex: 1;
  font-size: 12px;
  color: var(--el-text-color-secondary);

  b {
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    color: var(--el-text-color-regular);
  }
}

.thumb-stack {
  display: flex;
  flex-shrink: 0;
  align-items: center;

  .thumb {
    width: 28px;
    height: 28px;
    overflow: hidden;
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 5px;

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
      font-variant-numeric: tabular-nums;
      color: var(--el-text-color-secondary);
      background: var(--el-fill-color-light);
    }
  }
}

/* ---------- 明细弹窗 ---------- */
.order-detail-dialog {
  max-height: 60vh;
  overflow-y: auto;
}

.order-detail-summary {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.oi-dialog-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 10px 4px;
  border-bottom: 1px solid var(--el-border-color-extra-light);

  &:last-child {
    border-bottom: 0;
  }
}

.oi-dialog-pic {
  flex: none;
  width: 56px;
  height: 56px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.oi-dialog-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.oi-dialog-name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.4;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.oi-dialog-spec {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.oi-dialog-coupon {
  font-size: 12px;
  color: var(--el-color-danger);
}

.oi-dialog-side {
  display: flex;
  flex: none;
  flex-direction: column;
  gap: 3px;
  align-items: flex-end;
  min-width: 80px;

  :deep(.el-tag) {
    height: 20px;
    padding: 0 6px;
    font-size: 11px;
  }
}

.oi-dialog-price {
  display: inline-flex;
  align-items: baseline;
  font-size: 13px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--el-color-danger);
}

.oi-empty {
  color: var(--el-text-color-placeholder);
}
</style>
