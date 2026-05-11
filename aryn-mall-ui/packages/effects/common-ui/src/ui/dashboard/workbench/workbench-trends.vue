<script setup lang="ts">
import { Card, CardContent, CardHeader, CardTitle } from '@vben-core/shadcn-ui';

interface Props {
  items?: any[];
  title: string;
}

defineOptions({
  name: 'WorkbenchTrends',
});

withDefaults(defineProps<Props>(), {
  items: () => [],
});
</script>

<template>
  <Card>
    <CardHeader class="py-4">
      <CardTitle class="text-lg">{{ title }}</CardTitle>
    </CardHeader>
    <CardContent class="flex flex-wrap p-5 pt-0">
      <ul class="divide-border w-full divide-y" role="list">
        <li
          v-for="item in items"
          :key="item.id"
          class="flex justify-between gap-x-6 py-2"
        >
          <div class="flex min-w-0 items-center gap-x-4">
            <img
              v-if="item.spuUrls"
              :src="item.spuUrls[0]"
              class="size-16 flex-none"
            />
            <div class="min-w-0 flex-auto">
              <p class="text-foreground text-sm font-semibold leading-6">
                {{ item.name }}
              </p>
              <!-- eslint-disable vue/no-v-html -->
              <p
                class="text-foreground/80 *:text-primary mt-1 truncate text-xs leading-5"
              >
                {{ item.subTitle }}
              </p>
            </div>
          </div>
          <div class="hidden h-full shrink-0 sm:flex sm:flex-col sm:items-end">
            <span class="text-foreground/80 mt-6 text-xs leading-6">
              已售： {{ item.salesVolume }}
            </span>
          </div>
        </li>
      </ul>
    </CardContent>
  </Card>
</template>
