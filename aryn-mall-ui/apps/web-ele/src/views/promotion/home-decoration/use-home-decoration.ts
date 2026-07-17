import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  shallowRef,
} from 'vue';
import { useRouter } from 'vue-router';

import { ElMessage, ElMessageBox } from 'element-plus';
import { nanoid } from 'nanoid';

import { editObj, getHomeDesign } from '#/api/promotion/page-design';

export interface DecorationComponent {
  formData: Record<string, any>;
  id: string;
  title: string;
  type: string;
}

export interface PaletteComponent {
  icon: string;
  name: string;
  type: string;
}

interface HomeDesignForm {
  id: string;
  pageContent: unknown;
  pageName: string;
  status: string;
}

const dragOptions = {
  animation: 300,
  disabled: false,
  ghostClass: 'ghost',
  group: 'description',
};

const componentHome: PaletteComponent[] = [
  { name: '搜索框', icon: '/static/search-bar.svg', type: 'search-bar' },
  { name: '轮播图', icon: '/static/swiper-banner.svg', type: 'swiper-banner' },
  { name: '分类导航', icon: '/static/category-nav.svg', type: 'category-nav' },
  {
    name: '优惠券领取',
    icon: '/static/coupon-receive.svg',
    type: 'coupon-receive',
  },
];

const componentTool: PaletteComponent[] = [
  { name: '标题栏', icon: '/static/title.svg', type: 'title-text' },
  { name: '富文本', icon: '/static/rich-text.svg', type: 'rich-text' },
  { name: '辅助空白', icon: '/static/gap.svg', type: 'gap' },
];

const componentBase: PaletteComponent[] = [
  { name: '公告', icon: '/static/notice.svg', type: 'notice' },
  { name: '商品', icon: '/static/goods.svg', type: 'goods' },
  { name: '图片广告', icon: '/static/image.svg', type: 'image-ad' },
  { name: '图文导航', icon: '/static/tab-nav.svg', type: 'tab-nav' },
];

export function useHomeDecoration() {
  const router = useRouter();
  const components = ref<DecorationComponent[]>([]);
  const selectedId = shallowRef<null | string>(null);
  const active = ref(['1', '2', '3']);
  const showSetting = shallowRef(false);
  const loading = shallowRef(false);
  const hasPrompted = shallowRef(false);
  const form = ref<HomeDesignForm>({
    id: '',
    pageContent: '',
    pageName: '首页',
    status: '0',
  });
  const channel =
    typeof BroadcastChannel === 'undefined'
      ? null
      : new BroadcastChannel('page_design_form');

  const selectedComponent = computed(() =>
    components.value.find((component) => component.id === selectedId.value),
  );

  function refreshSettingPanel() {
    showSetting.value = false;
    nextTick(() => {
      showSetting.value = true;
    });
  }

  async function initForm() {
    hasPrompted.value = false;
    showSetting.value = false;
    loading.value = true;
    try {
      const data = await getHomeDesign();
      const pageContent =
        typeof data.pageContent === 'string'
          ? JSON.parse(data.pageContent || '{"components":[]}')
          : (data.pageContent ?? { components: [] });
      components.value = Array.isArray(pageContent.components)
        ? pageContent.components
        : [];
      form.value = {
        ...data,
        pageContent,
      };
      selectedId.value = null;
      refreshSettingPanel();
    } finally {
      loading.value = false;
    }
  }

  function notifyOtherWindows() {
    channel?.postMessage({
      id: form.value.id,
      type: 'page_design_form',
    });
  }

  async function onSubmit() {
    hasPrompted.value = false;
    if (components.value.length === 0) {
      ElMessage.error('内容不能为空');
      return;
    }

    loading.value = true;
    try {
      if (!form.value.id) {
        const homeDesign = await getHomeDesign();
        form.value.id = homeDesign.id;
      }
      await editObj({
        id: form.value.id,
        pageContent: JSON.stringify({ components: components.value }),
        pageName: form.value.pageName,
        status: form.value.status,
      });
      ElMessage.success('修改成功');
      notifyOtherWindows();
    } finally {
      loading.value = false;
    }
  }

  function onBack() {
    router.push('/promotion/page-design');
  }

  function createComponent(item: PaletteComponent): DecorationComponent {
    return {
      formData: {},
      id: nanoid(),
      title: item.name,
      type: item.type,
    };
  }

  function addComponent(item: PaletteComponent) {
    const component = createComponent(item);
    components.value.push(component);
    selectedId.value = component.id;
    refreshSettingPanel();
  }

  function onComponent(component: DecorationComponent) {
    selectedId.value = component.id;
    refreshSettingPanel();
  }

  async function clearComponent() {
    await ElMessageBox.confirm('确定要清空所有组件吗？', '提示', {
      cancelButtonText: '取消',
      confirmButtonText: '确定',
      type: 'warning',
    });
    components.value = [];
    selectedId.value = null;
    refreshSettingPanel();
  }

  async function delComponent(index: number) {
    await ElMessageBox.confirm('此操作将删除该组件，是否继续？', '提示', {
      cancelButtonText: '取消',
      confirmButtonText: '确认',
      type: 'warning',
    });
    components.value.splice(index, 1);
    selectedId.value = null;
    showSetting.value = false;
  }

  function upComponent(index: number) {
    if (index <= 0) return;
    const [component] = components.value.splice(index, 1);
    if (component) components.value.splice(index - 1, 0, component);
  }

  function downComponent(index: number) {
    if (index >= components.value.length - 1) return;
    const [component] = components.value.splice(index, 1);
    if (component) components.value.splice(index + 1, 0, component);
  }

  function onDragStart(event: DragEvent, item: PaletteComponent) {
    event.dataTransfer?.setData('componentType', JSON.stringify(item));
  }

  function onDrop(event: DragEvent) {
    const componentData = event.dataTransfer?.getData('componentType');
    if (!componentData) return;

    const component = createComponent(JSON.parse(componentData));
    const selectedIndex = components.value.findIndex(
      (item) => item.id === selectedId.value,
    );
    if (selectedIndex === -1) {
      components.value.push(component);
    } else {
      components.value.splice(selectedIndex + 1, 0, component);
    }
    selectedId.value = component.id;
    refreshSettingPanel();
  }

  function updateSelectedFormData(formData: Record<string, any>) {
    if (selectedComponent.value) {
      selectedComponent.value.formData = formData;
    }
  }

  channel?.addEventListener('message', (event) => {
    if (
      event.data?.type !== 'page_design_form' ||
      event.data?.id !== form.value.id ||
      hasPrompted.value
    ) {
      return;
    }
    hasPrompted.value = true;
    ElMessageBox.confirm('此页面内容已被其他窗口修改，是否重新加载？', '提示', {
      closeOnClickModal: false,
      closeOnPressEscape: false,
      confirmButtonText: 'OK',
      showCancelButton: false,
      type: 'warning',
    }).then(initForm);
  });

  onMounted(initForm);
  onBeforeUnmount(() => channel?.close());

  return {
    active,
    addComponent,
    clearComponent,
    componentBase,
    componentHome,
    components,
    componentTool,
    delComponent,
    downComponent,
    dragOptions,
    loading,
    onBack,
    onComponent,
    onDragStart,
    onDrop,
    onSubmit,
    selectedComponent,
    selectedId,
    showSetting,
    updateSelectedFormData,
    upComponent,
  };
}
