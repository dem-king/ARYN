import type { AsyncComponentLoader, Component } from 'vue';

import { defineAsyncComponent } from 'vue';

const viewModules = import.meta.glob('./components/**/index.vue');
const settingModules = import.meta.glob('./components/**/setting.vue');

function autoLoadComponents(
  modules: Record<string, AsyncComponentLoader>,
  suffix = '',
): Record<string, Component> {
  const map: Record<string, Component> = {};

  for (const path in modules) {
    const match = path.match(/\.\/components\/(.+)\/(index|setting)\.vue$/);
    if (match) {
      const baseName = match[1]; // 保留横杠格式，如 title-text
      const name = `${baseName}${suffix}`; // 拼接 -setting 后缀
      map[name] = defineAsyncComponent(modules[path]);
    }
  }

  return map;
}

// 暴露出去的映射对象
export const componentsMap: Record<string, Component> = {
  ...autoLoadComponents(viewModules),
  ...autoLoadComponents(settingModules, '-setting'),
};
