import {
  initPreferences,
  preferences,
  updatePreferences,
} from '@vben/preferences';
import { unmountGlobalLoading } from '@vben/utils';

import { overridesPreferences } from './preferences';

import('element-plus/dist/index.css');

/**
 * 应用初始化完成之后再进行页面加载渲染
 */
async function initApplication() {
  // name用于指定项目唯一标识
  // 用于区分不同项目的偏好设置以及存储数据的key前缀以及其他一些需要隔离的数据
  const env = import.meta.env.PROD ? 'prod' : 'dev';
  const appVersion = import.meta.env.VITE_APP_VERSION;
  const namespace = `${import.meta.env.VITE_APP_NAMESPACE}-${appVersion}-${env}`;

  // app偏好设置初始化
  await initPreferences({
    namespace,
    overrides: overridesPreferences,
  });

  // 一期强制启用「登录失效页内弹窗」：
  // Vben 的偏好缓存优先于代码默认值，老用户缓存里仍是 loginExpiredMode='page'，
  // 仅靠 preferences.ts 覆盖会让升级后的用户继续静默登出（见需求 README §11 风险表）。
  // 这里是代码级迁移，保证被顶下线/过期/被踢都走 #/layouts/session-invalid-modal.vue。
  if (preferences.app.loginExpiredMode !== 'modal') {
    updatePreferences({ app: { loginExpiredMode: 'modal' } });
  }

  // 启动应用并挂载
  // vue应用主要逻辑及视图
  const { bootstrap } = await import('./bootstrap');
  await bootstrap(namespace);

  // 移除并销毁loading
  unmountGlobalLoading();
}

initApplication();
