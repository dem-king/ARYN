<script lang="ts" setup>
import type { VbenFormSchema } from '#/adapter/form';

import { computed, ref } from 'vue';

import { useVbenDrawer } from '@vben/common-ui';
import { getPopupContainer } from '@vben/utils';

import { breakpointsTailwind, useBreakpoints } from '@vueuse/core';

import { useVbenForm, z } from '#/adapter/form';
import {
  addObj as createMenu,
  getList as getMenuList,
  SystemMenuApi,
  editObj as updateMenu,
} from '#/api/upms/menu';
import { $t } from '#/locales';
import { useDict } from '#/utils/dict';

const emit = defineEmits<{
  success: [];
}>();

const { application_key, menu_type } = useDict('application_key', 'menu_type');
function getMenuListWithRoot() {
  return getMenuList().then((res) => {
    const menu = {
      id: '0',
      name: '顶级菜单',
      children: [],
      parentId: '0',
    };
    res.unshift(menu);
    return res;
  });
}
const formData = ref<any>();
const titleSuffix = ref<string>();
const schema: VbenFormSchema[] = [
  {
    component: 'Input',
    fieldName: 'name',
    label: '菜单名称',
    rules: 'required',
  },
  {
    component: 'ApiTreeSelect',
    componentProps: {
      api: getMenuListWithRoot,
      class: 'w-full',
      // filterTreeNode(input: string, node: Recordable<any>) {
      //   if (!input || input.length === 0) {
      //     return true;
      //   }
      //   const title: string = node.name ?? '';
      //   if (!title) return false;
      //   return title.includes(input) || $t(title).includes(input);
      // },
      getPopupContainer,
      labelField: 'name',
      showSearch: true,
      checkStrictly: true,
      valueField: 'id',
      childrenField: 'children',
      allowClear: true,
    },
    fieldName: 'parentId',
    label: '上级菜单',
    rules: 'required',
  },

  {
    component: 'RadioGroup',
    componentProps: {
      buttonStyle: 'solid',
      options: menu_type,
      optionType: 'button',
    },
    defaultValue: '0',
    fieldName: 'type',
    label: '菜单类型',
    rules: 'required',
  },
  {
    component: 'Input',
    fieldName: 'path',
    label: '菜单编码',
    dependencies: {
      show: (values) => {
        return ['0'].includes(values.type);
      },
      triggerFields: ['type'],
    },
    rules: z
      .string()
      .min(2, $t('ui.formRules.minLength', ['菜单编码', 2]))
      .max(100, $t('ui.formRules.maxLength', ['菜单编码', 100]))
      .refine(
        (value: string) => {
          return value.startsWith('/');
        },
        $t('ui.formRules.startWith', ['菜单编码', '/']),
      )
      .refine(
        async () => {
          return true;
        },
        (value) => ({
          message: $t('ui.formRules.alreadyExists', ['菜单编码', value]),
        }),
      ),
  },
  {
    component: 'IconPicker',
    componentProps: {
      prefix: 'carbon',
    },
    dependencies: {
      show: (values) => {
        return ['0'].includes(values.type);
      },
      triggerFields: ['type'],
    },
    fieldName: 'icon',
    label: '菜单icon',
  },

  {
    component: 'Input',
    fieldName: 'component',
    label: '菜单路径',
    dependencies: {
      show: (values) => {
        return ['0'].includes(values.type);
      },
      triggerFields: ['type'],
    },
  },
  {
    component: 'Input',
    dependencies: {
      rules: (values) => {
        return values.type === '1' ? 'required' : null;
      },
      show: (values) => {
        return ['1'].includes(values.type);
      },
      triggerFields: ['type'],
    },
    fieldName: 'permission',
    label: '菜单权限',
  },

  {
    component: 'Select',
    componentProps: {
      allowClear: true,
      class: 'w-full',
      options: application_key,
    },
    fieldName: 'applicationKey',
    label: '应用系统',
    rules: 'required',
  },
  {
    component: 'InputNumber',
    fieldName: 'sort',
    label: '排序序号',
    defaultValue: 1,
    rules: 'required',
  },
];

const breakpoints = useBreakpoints(breakpointsTailwind);
const isHorizontal = computed(() => breakpoints.greaterOrEqual('md').value);

const [Form, formApi] = useVbenForm({
  commonConfig: {
    colon: true,
    formItemClass: 'col-span-2 md:col-span-1',
  },
  schema,
  showDefaultActions: false,
  wrapperClass: 'grid-cols-2 gap-x-4',
});

const [Drawer, drawerApi] = useVbenDrawer({
  onConfirm: onSubmit,
  onOpenChange(isOpen) {
    if (isOpen) {
      const data = drawerApi.getData<SystemMenuApi.SystemMenu>();
      if (data?.type === 'link') {
        data.linkSrc = data.meta?.link;
      } else if (data?.type === 'embedded') {
        data.linkSrc = data.meta?.iframeSrc;
      }
      if (data) {
        formData.value = data;
        formApi.setValues(formData.value);
        titleSuffix.value = formData.value.meta?.title
          ? $t(formData.value.meta.title)
          : '';
      } else {
        formApi.resetForm();
        titleSuffix.value = '';
      }
    }
  },
});

async function onSubmit() {
  const { valid } = await formApi.validate();
  if (valid) {
    drawerApi.lock();
    const data =
      await formApi.getValues<
        Omit<SystemMenuApi.SystemMenu, 'children' | 'id'>
      >();
    if (data.type === 'link') {
      data.meta = { ...data.meta, link: data.linkSrc };
    } else if (data.type === 'embedded') {
      data.meta = { ...data.meta, iframeSrc: data.linkSrc };
    }
    delete data.linkSrc;
    try {
      await (formData.value?.id
        ? updateMenu(
            Object.assign(
              {
                id: formData.value.id,
              },
              data,
            ),
          )
        : createMenu(data));
      drawerApi.close();
      emit('success');
    } finally {
      drawerApi.unlock();
    }
  }
}
const getDrawerTitle = computed(() =>
  formData.value?.id
    ? $t('ui.actionTitle.edit', ['菜单'])
    : $t('ui.actionTitle.create', ['菜单']),
);
</script>
<template>
  <Drawer class="w-full max-w-[800px]" :title="getDrawerTitle">
    <Form class="mx-4" :layout="isHorizontal ? 'horizontal' : 'vertical'" />
  </Drawer>
</template>
