import type { CopyrightRenderConfig } from '#/api/upms/copyright-config';

import { readonly, ref } from 'vue';

import { getPublicCopyrightConfig } from '#/api/upms/copyright-config';

/**
 * 平台端登录页版权/品牌配置。
 *
 * <p>登录前没有会话也没有租户上下文，服务端固定按平台租户下发（见
 * /upms/app/copyright-config?clientType=platform）。布局层（auth.vue）与登录表单
 * （login.vue）都要用同一份值，故做成模块级单例，只拉一次。</p>
 *
 * <p>失败策略与商城主题一致：拉不到就用内置语言包文案，不为装饰性配置阻塞登录。</p>
 */
const config = ref<CopyrightRenderConfig | null>(null);
const loaded = ref(false);

let pending: null | Promise<void> = null;

export function usePublicCopyright() {
  async function ensureLoaded() {
    if (loaded.value) return;
    if (pending) return pending;
    pending = getPublicCopyrightConfig('platform')
      .then((value) => {
        config.value = value ?? null;
      })
      .catch(() => {
        // 保留空值，渲染侧回落语言包内置文案
      })
      .finally(() => {
        loaded.value = true;
        pending = null;
      });
    return pending;
  }

  return {
    config: readonly(config),
    ensureLoaded,
  };
}

/** 供测试重置模块级状态 */
export function resetPublicCopyrightForTest() {
  config.value = null;
  loaded.value = false;
  pending = null;
}
