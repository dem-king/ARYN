<script lang="ts" setup>
import { $t } from '@vben/locales';

import { VbenSpinner } from '@vben-core/shadcn-ui';

import { useContentSpinner } from './use-content-spinner';

defineOptions({ name: 'LayoutContentSpinner' });

const { spinning } = useContentSpinner();
</script>
<template>
  <VbenSpinner :spinning="spinning" :text="$t('common.loading')" />
</template>
