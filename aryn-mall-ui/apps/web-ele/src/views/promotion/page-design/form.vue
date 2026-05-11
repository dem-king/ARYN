<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  Bottom,
  CircleClose,
  DCaret,
  Delete,
  Top,
} from '@element-plus/icons-vue';
import {
  ElButton,
  ElCollapse,
  ElCollapseItem,
  ElContainer,
  ElHeader,
  ElIcon,
  ElLink,
  ElMain,
  ElMessage,
  ElMessageBox,
} from 'element-plus';
import { nanoid } from 'nanoid';
import draggable from 'vuedraggable';

import { addObj, editObj, getById } from '#/api/promotion/page-design';

import { componentsMap } from './componentsMap';

const components = ref<any[]>([]);

const selectedId = ref<null | string>(null);
const active = ref(['1', '2']);
const showSetting = ref(false);
const loading = ref(false);
const hasPrompted = ref(false);
const form = ref<any>({
  id: '',
  pageName: '微页面',
  pageContent: '',
  status: '0',
});
const router = useRouter();
const route = useRoute();
const dragOptions = {
  animation: 300,
  group: 'description',
  disabled: false,
  ghostClass: 'ghost',
};

// 工具组件
const componentTool = ref([
  {
    name: '标题栏',
    icon: '/static/title.svg',
    type: 'title-text',
  },
  { name: '富文本', icon: '/static/rich-text.svg', type: 'rich-text' },
  { name: '辅助空白', icon: '/static/gap.svg', type: 'gap' },
]);
// 基础组件
const componentBase = ref([
  { name: '公告', icon: '/static/notice.svg', type: 'notice' },
  { name: '商品', icon: '/static/goods.svg', type: 'goods' },
  { name: '图片广告', icon: '/static/image.svg', type: 'image-ad' },
  { name: '图文导航', icon: '/static/tab-nav.svg', type: 'tab-nav' },
]);
const selectedComponent = computed(() => {
  return components.value.find((c) => c?.id === selectedId.value);
});
// --- BroadcastChannel 多窗口同步部分 ---
const channel = new BroadcastChannel('page_design_form');

// 监听来自其他窗口的通知
channel.addEventListener('message', (event) => {
  if (event.data?.type === 'page_design_form') {
    const id = event.data?.id;
    // 如果当前窗口正在查看该类型，才刷新
    if (form.value.id === id && !hasPrompted.value) {
      hasPrompted.value = true; // 设置为已弹窗
      ElMessageBox.confirm(
        '此页面内容已被其他窗口修改，是否重新加载？',
        '提示',
        {
          closeOnClickModal: false,
          closeOnPressEscape: false,
          showCancelButton: false,
          confirmButtonText: 'OK',
          type: 'warning',
        },
      ).then(() => {
        initForm();
      });
    }
  }
});
// 保存成功后发送消息
const notifyOtherWindows = () => {
  channel.postMessage({
    type: 'page_design_form',
    id: form.value.id,
  });
};

// 清理
onBeforeUnmount(() => {
  channel.close();
});
// 页面初始化事件
const initForm = () => {
  hasPrompted.value = false; // 重置为可再次弹窗
  const { id } = route.query;
  showSetting.value = false;
  if (id) {
    // 编辑
    getDetail(id);
  } else {
    selectedId.value = '-1';
    // 强制更新右侧设置面板
    showSetting.value = false;
    nextTick(() => {
      showSetting.value = true;
    });
  }
};

// 查询详情
const getDetail = (id: any) => {
  loading.value = true;
  getById(id).then((response) => {
    loading.value = false;
    const data = response;
    const pageContent = JSON.parse(data.pageContent);
    if (pageContent.components && pageContent.components !== '{}') {
      components.value = pageContent.components;
    }
    form.value = data;
    form.value.pageContent = pageContent;
    selectedId.value = '-1';
    // 强制更新右侧设置面板
    showSetting.value = false;
    nextTick(() => {
      showSetting.value = true;
    });
  });
};
// 返回
const onBack = () => {
  router.push('/promotion/page-design');
};
// 提交保存事件
const onSubmit = () => {
  hasPrompted.value = false; // 重置为可再次弹窗
  if (components.value.length <= 0) {
    ElMessage.error('内容不能为空');
    return false;
  }
  const pageContent = {
    components: components.value,
  };
  const submitForm = {
    id: form.value.id,
    pageName: form.value.pageName,
    pageContent: JSON.stringify(pageContent),
    status: form.value.status,
  };
  if (form.value.id) {
    // 编辑
    onEdit(submitForm);
    notifyOtherWindows();
  } else {
    // 新增
    onAdd(submitForm);
  }
};
// 新增
const onAdd = (submitForm: any) => {
  loading.value = true;
  addObj(submitForm)
    .then((response) => {
      ElMessage.success('新增成功');
      loading.value = false;
      window.location.href = `/#/pagedesign/form?id=${response}`;
      getDetail(response);
    })
    .then(() => {
      loading.value = false;
    });
};
// 修改
const onEdit = (submitForm: any) => {
  loading.value = true;
  editObj(submitForm)
    .then(() => {
      ElMessage.success('修改成功');
      loading.value = false;
    })
    .then(() => {
      loading.value = false;
    });
};

// 清空组件
const clearComponent = () => {
  ElMessageBox.confirm('确定要清空所有组件吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    components.value = [];
    selectedId.value = '-1';
    // 强制更新右侧设置面板
    showSetting.value = false;
    nextTick(() => {
      showSetting.value = true;
    });
  });
};
const startComponent = () => {
  // 开始拖拽时的处理逻辑
};

// 添加组件
const addComponent = (item: any) => {
  const newComp = {
    title: item.name,
    id: nanoid(),
    type: item.type,
    formData: {},
  };
  components.value.push(newComp);
  selectedId.value = newComp.id;
  // 强制更新右侧设置面板
  showSetting.value = false;
  nextTick(() => {
    showSetting.value = true;
  });
};
// 选中预览组件
const onComponent = (element: any) => {
  // 强制更新右侧设置面板
  showSetting.value = false;
  nextTick(() => {
    showSetting.value = true;
  });
  selectedId.value = element.id;
};

const getComponent = (type: string) => {
  return componentsMap[type];
};

const getSettingComponent = (type: string) => {
  return componentsMap[`${type}-setting`];
};

// 预览组件删除事件
const delComponent = (index: number) => {
  ElMessageBox.confirm('此操作将删除该组件，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    components.value.splice(index, 1);
    selectedId.value = null;
    showSetting.value = false;
  });
};
// 向上移动预览组件
const upComponent = (index: number) => {
  const temp = components.value[index - 1];
  components.value[index - 1] = components.value[index];
  components.value[index] = temp;
};
// 向下移动预览组件
const downComponent = (index: number) => {
  const temp = components.value[index + 1];
  components.value[index + 1] = components.value[index];
  components.value[index] = temp;
};

// 拖拽开始事件
const onDragStart = (event: DragEvent, item: any) => {
  if (event.dataTransfer) {
    event.dataTransfer.setData('componentType', JSON.stringify(item));
  }
};

// 拖拽放置事件
const onDrop = (event: DragEvent) => {
  if (event.dataTransfer) {
    const componentData = event.dataTransfer.getData('componentType');
    if (componentData) {
      const item = JSON.parse(componentData);
      // 创建新组件对象
      const newComp = {
        title: item.name,
        id: nanoid(),
        type: item.type,
        formData: {},
      };

      // 如果有选中的组件，则插入到选中组件的后面
      if (selectedId.value) {
        const selectedIndex = components.value.findIndex(
          (c) => c?.id === selectedId.value,
        );
        components.value.splice(selectedIndex + 1, 0, newComp);
        selectedId.value = newComp.id;
        // 强制更新右侧设置面板
        showSetting.value = false;
        nextTick(() => {
          showSetting.value = true;
        });
      } else {
        // 没有选中组件，添加到末尾
        components.value.push(newComp);
        selectedId.value = newComp.id;
        // 强制更新右侧设置面板
        showSetting.value = false;
        nextTick(() => {
          showSetting.value = true;
        });
      }
    }
  }
};
initForm();
</script>

<template>
  <div class="hx-layout-container" style="padding: 0 !important">
    <div
      class="hx-layout-container-auto hx-layout-container-view"
      style="padding: 0 !important"
    >
      <ElContainer style="height: 100%">
        <ElHeader style="padding: 0 !important">
          <div class="diy-header">
            <div class="header-left">
              {{ form.pageName }}
            </div>
            <div class="header-right">
              <ElButton plain style="width: 160px" @click="onBack">
                返回
              </ElButton>
              <ElButton
                plain
                style="width: 160px"
                :disabled="loading"
                @click="onSubmit"
              >
                保存
              </ElButton>
            </div>
          </div>
        </ElHeader>
        <ElMain style="padding: 0 !important">
          <div class="diy-editor" v-loading="loading">
            <!-- 左侧组件库 -->
            <div style="display: flex">
              <div class="left-panel">
                <ElCollapse v-model="active">
                  <ElCollapseItem title="基础组件" name="1">
                    <div class="left-group">
                      <div
                        v-for="(comp, index) in componentBase"
                        class="group-item"
                        @click="addComponent(comp)"
                        :key="index"
                        draggable="true"
                        @dragstart="onDragStart($event, comp)"
                      >
                        <div>
                          <img :src="comp.icon" class="icon" />
                        </div>
                        {{ comp.name }}
                      </div>
                    </div>
                  </ElCollapseItem>
                  <ElCollapseItem title="工具组件" name="2">
                    <div class="left-group">
                      <div
                        v-for="(comp, index) in componentTool"
                        class="group-item"
                        @click="addComponent(comp)"
                        :key="index"
                        draggable="true"
                        @dragstart="onDragStart($event, comp)"
                      >
                        <div>
                          <img :src="comp.icon" class="icon" />
                        </div>
                        {{ comp.name }}
                      </div>
                    </div>
                  </ElCollapseItem>
                </ElCollapse>
                <div class="sub-btn"></div>
              </div>
              <div class="selected-component">
                <div class="title">
                  已选组件({{
                    components && components.length > 0 ? components.length : 0
                  }})
                </div>
                <draggable
                  v-model="components"
                  v-bind="dragOptions"
                  @start="startComponent"
                  animation="700"
                  item-key="id"
                  @dragover.prevent
                  @drop="onDrop"
                >
                  <template #item="{ element, index }">
                    <div
                      @click.stop="onComponent(element)"
                      class="selected-item"
                      :class="
                        selectedId && selectedId === element.id
                          ? 'selected-active'
                          : ''
                      "
                    >
                      <div class="selected-left">
                        <ElIcon><DCaret /></ElIcon>
                        <div v-if="element.title" class="selected-title">
                          {{ element.title }}
                        </div>
                      </div>
                      <div class="selected-del" @click="delComponent(index)">
                        <ElIcon><CircleClose /></ElIcon>
                      </div>
                    </div>
                  </template>
                </draggable>
              </div>
            </div>
            <!-- 修改中间预览区域 -->
            <div class="middle">
              <div class="middle-content">
                <div class="content-warp">
                  <div class="top-mobile-nav">
                    <img
                      style="width: 375px; height: 100%; object-fit: cover"
                      src="/static/mobile-top-nav.svg"
                    />
                    <div class="top-mobile-title">
                      {{ form.pageName }}
                    </div>
                  </div>
                  <draggable
                    v-model="components"
                    v-bind="dragOptions"
                    @start="startComponent"
                    animation="700"
                    item-key="id"
                    @dragover.prevent
                    @drop="onDrop"
                  >
                    <template #item="{ element, index }">
                      <div
                        @click.stop="onComponent(element)"
                        class="mobile-item"
                        :class="
                          selectedId && selectedId === element.id
                            ? 'active'
                            : ''
                        "
                      >
                        <component
                          v-if="element.id"
                          :is="getComponent(element.type)"
                          :key="element.id"
                          :show-data="element.formData"
                        />
                        <div v-if="element.title" class="preview-title">
                          {{ element.title }}
                        </div>

                        <div
                          v-if="selectedId && element.id === selectedId"
                          class="preview-btn-group"
                        >
                          <div class="btn-item">
                            <ElLink
                              style="font-size: 16px; color: #fff"
                              @click="delComponent(index)"
                            >
                              <ElIcon>
                                <Delete />
                              </ElIcon>
                            </ElLink>
                          </div>
                          <div class="btn-item">
                            <ElLink
                              style="font-size: 16px; color: #fff"
                              :disabled="index <= 0"
                              @click="upComponent(index)"
                            >
                              <ElIcon>
                                <Top />
                              </ElIcon>
                            </ElLink>
                          </div>
                          <div class="btn-item">
                            <ElLink
                              style="font-size: 16px; color: #fff"
                              :disabled="index === components.length - 1"
                              @click="downComponent(index)"
                            >
                              <ElIcon>
                                <Bottom />
                              </ElIcon>
                            </ElLink>
                          </div>
                        </div>
                      </div>
                    </template>
                  </draggable>
                </div>
              </div>
            </div>
            <!-- 右侧配置面板 -->
            <div class="right-panel">
              <div style="display: flex">
                <div class="preview-page-config">
                  <div class="config-content-warp">
                    <view class="box-item">
                      <ElButton @click="clearComponent">清空组件</ElButton>
                    </view>
                  </div>
                </div>
                <div v-if="selectedComponent" style="width: 480px">
                  <component
                    v-if="showSetting"
                    :is="getSettingComponent(selectedComponent.type)"
                    v-model="selectedComponent.formData"
                  />
                </div>
                <div v-else class="empty-text">请选择一个组件进行设置</div>
              </div>
            </div>
          </div>
        </ElMain>
      </ElContainer>
    </div>
  </div>
</template>

<style scoped lang="scss">
.diy-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 20px;
  background-color: var(--el-color-primary);
  border-bottom: 1px solid var(--el-border-color-light);

  .header-left {
    font-size: 16px;
    font-weight: bold;
    color: var(--el-text-color-primary);
  }

  .header-right {
    display: flex;
    gap: 16px;
    align-items: center;
  }
}

.diy-editor {
  display: flex;
  height: 100%;
}

.left-panel {
  width: 230px;
  height: 100%;
  padding: 0 5px;
  overflow-y: auto;
  white-space: nowrap;
  background-color: hsl(var(--card));
  border: 1px solid hsl(var(--overlay-content));
  box-shadow: 0 0 6px rgb(0 0 0 / 10%);

  .sub-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    padding-top: 20px;
  }

  .left-group {
    display: flex;
    flex-wrap: wrap;
    gap: 5px;

    .group-item {
      box-sizing: border-box;
      display: flex;

      /* 每个组件占据 1/3 的宽度 */
      flex: 0 0 calc(100% / 3 - (8px * 2 / 3));
      flex-direction: column;
      align-items: center;
      padding: 8px;
      text-align: center;
      cursor: pointer;

      .icon {
        width: 24px;
      }
    }

    .group-item:hover {
      background-color: #e5e5e5;
    }
  }
}

.selected-component {
  height: 100%;
  overflow-y: auto;
  background-color: hsl(var(--card));
  border: 1px solid hsl(var(--overlay-content));

  .title {
    padding: 10px;
    font-weight: bold;
    border-bottom: 1px solid #e5e5e5;
  }

  .selected-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px;
    cursor: pointer;
    transition: background 0.2s;

    .selected-left {
      display: flex;
      align-items: center;

      .selected-title {
        padding: 0 10px;
      }
    }

    .selected-del {
      display: flex;
      align-items: center;
      opacity: 0;
      transition: opacity 0.2s;
    }
  }

  .selected-item:hover {
    background-color: #e5e5e5;

    .selected-del {
      opacity: 1;
    }
  }

  .selected-active {
    background-color: #e5e5e5;

    .selected-del {
      opacity: 1;
    }
  }
}

.mobile-preview {
  position: relative;
  width: 375px;
  min-height: 667px;
  overflow: hidden;
  box-shadow: 0 0 20px rgb(0 0 0 / 10%);
}

.component-item {
  float: left;
  box-sizing: border-box;

  /* 保持正方形 */
  display: flex;
  align-items: center;
  justify-content: center;
  width: calc(33.33% - 8px);

  /* 每行三个，减去间距 */
  aspect-ratio: 1;
  padding: 8px;
  margin-right: 8px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: background 0.2s;

  /* 右侧间距 */
}

.component-item:nth-child(3n) {
  margin-right: 0;

  /* 第三个元素不需要右侧间距 */
}

.left-panel::after {
  clear: both;
  display: table;
  content: '';
}

.component-item:hover {
  background: #f9fafb;
}

.right-panel {
  width: 590px;
  height: 100%;
  overflow-y: auto;
  white-space: nowrap;
  background-color: hsl(var(--card));
  border: 1px solid var(--el-card-border-color);
  box-shadow: 0 0 6px rgb(0 0 0 / 10%);

  .preview-page-config {
    padding: 20px;
    // position: absolute;
    // top: 60px;
    // right: 2%;

    .config-content-warp {
      display: flex;
      flex-direction: column;

      .box-item {
        margin-bottom: 10px;
      }
    }
  }
}

.empty-text {
  color: #9ca3af;
}

:deep(.el-collapse) {
  border: none !important;
}

:deep(.el-collapse-item__header) {
  border: none !important;
}

.middle {
  position: relative;
  flex-grow: 1;
  height: 100%;
  overflow-y: auto;
  background: hsl(var(--overlay-content));

  .middle-content {
    position: relative;
    width: 375px;
    max-width: 375px;
    min-height: 667px;
    margin: 60px auto;
    background-color: var(--color-white);
    box-shadow: 0 0 28px 0 var(--color-white);

    .content-warp {
      width: 100%;
      min-height: 667px;
      padding-bottom: 30px;
      background-color: #fff;
      background-repeat: no-repeat;
      background-position: center;
      background-size: 100% 100%;

      .top-mobile-nav {
        position: relative;
        background-repeat: no-repeat;
        background-position: center;
        background-size: 100% 100%;

        .top-mobile-title {
          position: absolute;
          top: 60%;
          left: 50%;
          font-size: 18px;
          font-weight: 700;
          text-align: center;
          transform: translate(-50%, -50%);
        }

        .top-mobile-image {
          position: absolute;
          top: 40%;
          left: 10px;
        }
      }

      .mobile-item {
        position: relative;
        width: 375px;

        .preview-title {
          position: absolute;
          top: 10px;
          right: 390px;
          width: 90px;
          padding: 4px 8px;
          text-align: center;
          background-color: hsl(var(--card));
          border-radius: 2px;
          box-shadow: 0 0 2px 0 var(--el-color-primary);
          transform: translateY(-50%);
        }

        .preview-title::before,
        .preview-title::after {
          position: absolute;
          top: 50%;
          width: 0;
          height: 0;
          content: '';
          transform: translateY(-50%);
        }

        .preview-title::before {
          right: -9px; /* 比 ::after 稍微偏右一点，模拟边框 */
          border-top: 5px solid transparent;
          border-bottom: 5px solid transparent;
          border-left: 9px solid var(--el-color-primary); /* 模拟“边框颜色” */
        }

        .preview-title::after {
          right: -8px;
          border-top: 4px solid transparent;
          border-bottom: 4px solid transparent;
          border-left: 8px solid var(--el-color-primary); /* 原始颜色 */
        }

        .preview-btn-group {
          position: absolute;
          top: 0;
          right: -32px;
          width: 30px;
          height: 100px;
          text-align: center;
          background-color: var(--el-color-primary);
          border-radius: 5px;
          box-shadow: 0 0 14px 0 rgb(0 0 0 / 10%);

          .btn-item {
            padding-top: 8px;
            font-size: 20px;
          }
        }
      }

      .active {
        &::before {
          position: absolute;
          inset: 0;
          z-index: 1;
          content: '';
          border: 2px solid #155bd4;
        }
      }

      .mobile-item:hover::before {
        position: absolute;
        inset: 0;
        z-index: 1;
        content: '';
        border: 2px solid #155bd4;
      }

      .footer {
        display: flex;
        align-items: center;
        justify-content: center;
      }
    }
  }
}
</style>
