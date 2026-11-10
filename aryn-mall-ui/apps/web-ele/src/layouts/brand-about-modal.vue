<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { preferences } from '@vben/preferences';

defineOptions({ name: 'BrandAboutModal' });

interface BrandAboutInfo {
  logo?: string;
  name?: string;
}

const [Modal, modalApi] = useVbenModal({
  destroyOnClose: true,
  onOpenChange(isOpen) {
    if (isOpen) {
      brand.value = modalApi.getData<BrandAboutInfo>() ?? {};
    }
  },
});

const brand = ref<BrandAboutInfo>({});
const copyright = computed(() => preferences.copyright);
const year = computed(
  () => copyright.value.date || String(new Date().getFullYear()),
);
const version = import.meta.env.VITE_APP_VERSION;
</script>

<template>
  <Modal
    :footer="false"
    :fullscreen-button="false"
    :header="false"
    :title="`关于${brand.name || copyright.companyName}`"
    centered
    class="sm:w-[400px]"
  >
    <div class="brand-about">
      <img
        v-if="brand.logo"
        :alt="brand.name || copyright.companyName"
        class="brand-about__logo"
        :src="brand.logo"
      />
      <div class="brand-about__name">
        {{ brand.name || copyright.companyName || '悦航购' }}
      </div>
      <div class="brand-about__meta">
        <span>商家工作台</span>
        <template v-if="version">
          <span aria-hidden="true" class="brand-about__dot"></span>
          <span class="brand-about__version">v{{ version }}</span>
        </template>
      </div>

      <div class="brand-about__divider"></div>

      <div class="brand-about__copyright">
        © {{ year }} {{ copyright.companyName }}
      </div>
      <div
        v-if="copyright.icp || copyright.companySiteLink"
        class="brand-about__links"
      >
        <template v-if="copyright.icp">
          <a
            v-if="copyright.icpLink"
            :href="copyright.icpLink"
            rel="noopener"
            target="_blank"
          >
            {{ copyright.icp }}
          </a>
          <span v-else>{{ copyright.icp }}</span>
        </template>
        <span
          v-if="copyright.icp && copyright.companySiteLink"
          aria-hidden="true"
          class="brand-about__dot"
        ></span>
        <a
          v-if="copyright.companySiteLink"
          :href="copyright.companySiteLink"
          rel="noopener"
          target="_blank"
        >
          官方网站
        </a>
      </div>
    </div>
  </Modal>
</template>

<style lang="scss" scoped>
.brand-about {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 28px 16px;
  text-align: center;
}

.brand-about__logo {
  width: 48px;
  height: 48px;
  object-fit: contain;
  border-radius: 12px;
}

.brand-about__name {
  margin-top: 14px;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.brand-about__meta {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.brand-about__version {
  padding: 1px 6px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-lighter);
  border-radius: 999px;
}

.brand-about__dot {
  width: 3px;
  height: 3px;
  background: var(--el-border-color);
  border-radius: 50%;
}

.brand-about__divider {
  width: 100%;
  height: 1px;
  margin: 20px 0 16px;
  background: var(--el-border-color-lighter);
}

.brand-about__copyright {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.brand-about__links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: center;
  margin-top: 8px;
  font-size: 12px;
}

.brand-about__links a {
  color: var(--el-text-color-secondary);
  transition: color 0.2s;
}

.brand-about__links a:hover {
  color: var(--el-color-primary);
}
</style>
