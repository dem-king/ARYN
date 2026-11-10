import { defineOverridesPreferences } from '@vben/preferences';

/**
 * @description 项目配置文件
 * 只需要覆盖项目中的一部分配置，不需要的配置不用覆盖，会自动使用默认配置
 * !!! 更改配置后请清空缓存，否则可能不生效
 */
export const overridesPreferences = defineOverridesPreferences({
  // overrides
  app: {
    /**
     * 登录失效（被顶下线 / 过期 / 被踢等）用页内弹窗原地重新登录，
     * 保留当前页面现场与未提交的表单数据，不整页跳转到登录页。
     * 详见 #/layouts/session-invalid-modal.vue。
     */
    loginExpiredMode: 'modal',
    name: import.meta.env.VITE_APP_TITLE,
  },
  /**
   * 采用 SaaS 控制台口径（Stripe/Linear）：默认不显示全站页脚，内容区直达视口底部。
   * 框架能力保留，偏好抽屉里仍可临时开启；商户端版权只在页脚开启时渲染。
   */
  footer: {
    enable: false,
  },
});
