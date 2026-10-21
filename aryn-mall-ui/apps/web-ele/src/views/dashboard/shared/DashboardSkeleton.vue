<script lang="ts" setup>
import { ElSkeleton, ElSkeletonItem } from 'element-plus';
</script>

<template>
  <div class="space-y-4">
    <div class="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-6">
      <ElSkeleton v-for="item in 6" :key="`metric-${item}`" animated>
        <template #template>
          <ElSkeletonItem
            variant="rect"
            style="height: 104px; border-radius: 8px"
          />
        </template>
      </ElSkeleton>
    </div>
    <ElSkeleton animated>
      <template #template>
        <ElSkeletonItem
          variant="rect"
          style="height: 360px; border-radius: 8px"
        />
      </template>
    </ElSkeleton>
    <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
      <ElSkeleton v-for="item in 2" :key="`panel-${item}`" animated>
        <template #template>
          <ElSkeletonItem
            variant="rect"
            style="height: 280px; border-radius: 8px"
          />
        </template>
      </ElSkeleton>
    </div>
  </div>
</template>
