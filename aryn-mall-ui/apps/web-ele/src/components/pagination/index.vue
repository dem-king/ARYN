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
    style="padding-top: 10px"
    background
    layout="total, sizes, prev, pager, next, jumper"
    :total="props.total"
    :current-page="props.current"
    :page-size="props.size"
    @size-change="sizeChange"
    @current-change="currentChange"
  />
</template>
