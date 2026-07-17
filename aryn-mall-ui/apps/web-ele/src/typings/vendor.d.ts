declare module 'element-plus/es/components/*/style/css';

declare module '@wangeditor/editor-for-vue' {
  import type { DefineComponent } from 'vue';

  export const Editor: DefineComponent;
  export const Toolbar: DefineComponent;
}

declare module '#/components/verifition/index.vue' {
  import type { DefineComponent } from 'vue';

  const component: DefineComponent;
  export default component;
}
