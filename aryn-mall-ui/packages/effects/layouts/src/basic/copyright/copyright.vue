<script lang="ts" setup>
interface Props {
  companyName?: string;
  companySiteLink?: string;
  date?: string;
  icp?: string;
  icpLink?: string;
}

defineOptions({
  name: 'Copyright',
});

withDefaults(defineProps<Props>(), {
  companyName: 'Vben Admin',
  companySiteLink: '',
  date: '2024',
  icp: '',
  icpLink: '',
});
</script>

<template>
  <div
    class="text-muted-foreground flex flex-wrap items-center justify-center gap-x-2.5 gap-y-1 px-4 text-xs"
  >
    <span class="flex items-center gap-1 whitespace-nowrap">
      <span>© {{ date }}</span>
      <a
        v-if="companyName && companySiteLink"
        :href="companySiteLink"
        class="hover:text-foreground transition-colors"
        rel="noopener"
        target="_blank"
      >
        {{ companyName }}
      </a>
      <span v-else-if="companyName">{{ companyName }}</span>
    </span>

    <template v-if="icp">
      <span aria-hidden="true" class="bg-border h-3 w-px"></span>
      <a
        v-if="icpLink"
        :href="icpLink"
        class="hover:text-foreground whitespace-nowrap transition-colors"
        rel="noopener"
        target="_blank"
      >
        {{ icp }}
      </a>
      <span v-else class="whitespace-nowrap">{{ icp }}</span>
    </template>
  </div>
</template>
