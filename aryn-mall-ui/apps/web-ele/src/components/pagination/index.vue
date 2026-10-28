<script lang="ts" setup>
import { ElPagination } from 'element-plus';

const props = defineProps({
  current: {
    type: Number,
    default: 1,
  },
  size: {
    type: Number,
    default: 10,
  },
  total: {
    type: Number,
    default: 0,
  },
});

const emit = defineEmits(['change', 'update:size', 'update:current']);

const sizeChange = (value: number) => {
  emit('update:size', value);
  emit('change');
};
const currentChange = (value: number) => {
  emit('update:current', value);
  emit('change');
};
</script>
<template>
  <ElPagination
    class="hx-pagination"
    background
    layout="total, sizes, prev, pager, next, jumper"
    :total="props.total"
    :current-page="props.current"
    :page-size="props.size"
    @size-change="sizeChange"
    @current-change="currentChange"
  />
</template>
<style scoped>
/* 列表滚动发生在 .hx-layout-container-auto：分页钉在可视区右下角不随列表滚走。
   背景取卡片色，不支持背景模糊的环境保持不透明以免文字叠影 */
.hx-pagination {
  position: sticky;
  bottom: 0;
  z-index: 10;
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  justify-content: flex-end;

  /* 水平留 16px 间距，避免「前往 N 页」等内容贴住条边 */
  padding: 10px 16px 4px;

  /* 卡片是纵向 flex（见 ele/index.css 的 :has 规则）：数据少时把分页推到
     卡片底部（=视口底部），不跟随表格内容上浮；数据多时由 sticky bottom 兜底 */
  margin-top: auto;
  background: hsl(var(--card));

  /* 四角对称 12px，与搜索区圆角一致 */
  border-radius: 12px;

  /* 玻璃拟态：滚动内容从分页下方穿过时呈磨砂效果 */
  @supports (backdrop-filter: blur(1px)) {
    background: hsl(var(--card) / 62%);
    box-shadow: var(--el-box-shadow-light);
    backdrop-filter: blur(10px) saturate(1.4);
  }

  /* 页码、翻页箭头与下拉/跳页输入框统一圆角语言 */
  :deep(.el-pager li),
  :deep(.btn-prev),
  :deep(.btn-next),
  :deep(.el-select__wrapper),
  :deep(.el-input__wrapper) {
    border-radius: calc(var(--radius) - 2px);
  }
}
</style>
