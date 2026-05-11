<script lang="ts" setup>
import { Refresh, Search } from '@element-plus/icons-vue';
import { ElButton, ElTooltip } from 'element-plus';

const props = defineProps({
  searchBtn: {
    type: Boolean,
    default: false,
  },
  refreshBtn: {
    type: Boolean,
    default: false,
  },
});
const emit = defineEmits(['search', 'refresh']);
// 搜索
const toSearch = () => {
  emit('search');
};
// 刷新
const toRefresh = () => {
  emit('refresh');
};
</script>
<template>
  <div>
    <ElTooltip effect="dark" content="搜索" placement="top">
      <span class="box-item">
        <ElButton
          circle
          @click="toSearch"
          v-if="props.searchBtn"
          :icon="Search"
        />
      </span>
    </ElTooltip>
    <ElTooltip effect="dark" content="刷新" placement="top">
      <span class="box-item">
        <ElButton
          circle
          @click="toRefresh"
          v-if="props.refreshBtn"
          :icon="Refresh"
        />
      </span>
    </ElTooltip>
  </div>
</template>
<style lang="scss" scoped>
.box-item {
  margin: 5px;
}
</style>
