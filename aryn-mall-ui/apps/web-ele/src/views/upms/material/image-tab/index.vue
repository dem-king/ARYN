<script lang="ts" setup>
import type { UploadProps, UploadRequestOptions } from 'element-plus';

import { defineAsyncComponent, nextTick, reactive, ref } from 'vue';
import useClipboard from 'vue-clipboard3';

import { Plus, Upload } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDrawer,
  ElImage,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElPopover,
  ElTree,
  ElUpload,
} from 'element-plus';

import { delObj, editObj, getPage } from '#/api/upms/material';
import { addObj as addObjGroup, getList } from '#/api/upms/material-group';
import { uploadFile } from '#/api/upms/upload';

// 新增props和emits
const props = defineProps({
  selectable: { type: Boolean, default: false }, // 是否可选
  maxSelect: { type: Number, default: 1 }, // 最大选择数量
  mode: { type: String, default: 'none' }, // 'dialog' 或 'none'
  showShop: { type: Boolean, default: true },
});

const emit = defineEmits(['selectChange']);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const MaterialGroup = defineAsyncComponent(
  () => import('#/views/upms/material-group/index.vue'),
);
const Preview = defineAsyncComponent(
  () => import('#/components/select-material/preview.vue'),
);
interface MaterialObj {
  id: string;
  name: string;
  url: string;
}
interface DataState {
  page: {
    asc: any;
    currentPage: number;
    desc: any;
    pageSize: number;
    total: number;
  };
  queryParams: {
    groupId: string;
  };
  treeList: any[];
  materialList: MaterialObj[];
  shopList: any[];
}
// 选中图片列表
const selectedList = ref<MaterialObj[]>([]);
const state = reactive<DataState>({
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: '',
    desc: ['create_time', 'id'],
  },
  queryParams: {
    groupId: '-1',
  },
  treeList: [],
  materialList: [],
  shopList: [],
});
const { toClipboard } = useClipboard();
const loading = ref(false);
const groupLoading = ref(false);
const groupTitle = ref('');
const groupName = ref('');
const materialGroupRef = ref();
const groupDrawer = ref(false);
const visible = ref(false);
const previewRef = ref();
const previewUrls = ref();
const previewShow = ref(false);
const defaultProps = {
  children: 'children',
  label: 'name',
  id: 'id',
};
// 选择图片逻辑
const handleSelect = (item: MaterialObj) => {
  if (!props.selectable) return;

  if (props.maxSelect === 1) {
    // 只保留当前选中的一张图片
    selectedList.value = [item];
  } else {
    const idx = selectedList.value.findIndex((i) => i.id === item.id);
    if (idx === -1) {
      if (selectedList.value.length >= props.maxSelect) {
        ElMessage.warning(`最多可选择${props.maxSelect}张图片`);
        return;
      }
      selectedList.value.push(item);
    } else {
      selectedList.value.splice(idx, 1);
    }
  }

  const urlList = selectedList.value.map((i) => i.url);
  emit('selectChange', urlList); // 返回 url 数组
};

// 判断图片是否被选中
const isSelected = (item: MaterialObj) => {
  return selectedList.value.some((i) => i.id === item.id);
};
/**
 * 文件上传
 */
const handleHttpUpload = async (options: UploadRequestOptions) => {
  const formData = new FormData();
  formData.append('file', options.file);
  formData.append('type', '1');
  formData.append('groupId', state.queryParams.groupId);

  await uploadFile(formData)
    .then(() => {})
    .catch((error) => {
      options.onError(error as any);
    });
};
/**
 * 素材分页列表
 */
const initPage = () => {
  loading.value = true;
  getPage({
    current: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
    groupId: state.queryParams.groupId || '-1',
  })
    .then((response: any) => {
      state.materialList = response.records;
      state.page.total = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};

/**
 * 修改素材
 */
const edit = (row: any) => {
  ElMessageBox.prompt('请输入素材名称', '修改素材名称', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    inputValue: row.name,
  }).then(({ value }) => {
    editObj({ id: row.id, name: value }).then(() => {
      ElMessage.success('修改成功');
      initPage();
    });
  });
};
/**
 * 删除素材
 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该素材，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    delObj(id)
      .then(() => {
        ElMessage.success('删除成功');
        initPage();
      })
      .catch(() => {
        loading.value = false;
      });
  });
};
/**
 * 上传素材前事件
 */
const beforeAvatarUpload: UploadProps['beforeUpload'] = (rawFile) => {
  const allowedTypes = [
    'image/jpeg',
    'image/png',
    'image/gif',
    'image/webp',
    'image/svg+xml',
  ];

  if (!allowedTypes.includes(rawFile.type)) {
    ElMessage.error('图片格式必须是 JPG、PNG、GIF、WEBP 或 SVG！');
    return false;
  }

  const isLt5M = rawFile.size / 1024 / 1024 <= 5;
  if (!isLt5M) {
    ElMessage.error('图片大小不能超过 5MB！');
    return false;
  }

  return true;
};
/**
 * 上传成功事件
 */
const handleSuccess: UploadProps['onSuccess'] = () => {
  ElMessage.success('上传成功');
  initPage();
};
/**
 * 上传失败事件
 */
const handleError: UploadProps['onError'] = (error) => {
  ElMessage.error(`${error}`);
};
/**
 * 素材分组列表查询
 */
const getGroup = () => {
  groupLoading.value = true;
  getList({})
    .then((response: any) => {
      state.treeList = response;
      state.treeList.unshift({ id: '-1', name: '未分组' });
      groupTitle.value = groupTitle.value ?? '未分组';
      groupLoading.value = false;
    })
    .catch(() => {
      groupLoading.value = false;
    });
};
/**
 * 切换分组
 */
const currentChange = (row: any) => {
  state.queryParams.groupId = row.id;
  groupTitle.value = row.name;
  initPage();
};
/**
 * 添加分组
 */
const addGroup = () => {
  groupLoading.value = true;
  const params = {
    name: groupName.value,
  };
  addObjGroup(params)
    .then(() => {
      getGroup();
      groupLoading.value = false;
      visible.value = false;
      groupName.value = '';
    })
    .catch(() => {
      groupLoading.value = false;
    });
};
const openGroup = () => {
  groupDrawer.value = true;
};

const onDrawerOpened = () => {
  if (
    materialGroupRef.value &&
    typeof materialGroupRef.value.initPage === 'function'
  ) {
    materialGroupRef.value.initPage();
  }
};

// 复制
const copyText = (text: string) => {
  console.error(text);
  try {
    toClipboard(text);
    ElMessage.success('复制成功');
  } catch (error) {
    console.error(error);
    ElMessage.error('复制失败');
  }
};

// 修改 preview 方法，调用 ElImage 的 showViewer
const handlePreview = async (url: string) => {
  await nextTick();
  previewUrls.value = [url];
  previewShow.value = true;
};
getGroup();
initPage();
</script>
<template>
  <div>
    <ElUpload
      action="#"
      :limit="99"
      :multiple="true"
      :show-file-list="false"
      :http-request="handleHttpUpload"
      :before-upload="beforeAvatarUpload"
      :on-success="handleSuccess"
      :on-error="handleError"
    >
      <ElButton
        type="primary"
        v-access:code="'upms:material:add'"
        :icon="Upload"
      >
        上传图片
      </ElButton>
    </ElUpload>
    <div class="material-main">
      <div class="category" v-loading="groupLoading">
        <ElTree
          :data="state.treeList"
          :props="defaultProps"
          class="category-tree"
          @current-change="currentChange"
          :highlight-current="true"
        />
        <div class="footer">
          <ElPopover :visible="visible" placement="top" :width="350">
            <div class="popover-input">
              <div style="padding: 0 10px">
                <ElInput v-model="groupName" placeholder="请输入分组名称" />
              </div>
              <div class="flex">
                <ElButton :loading="groupLoading" @click="addGroup">
                  保存
                </ElButton>
                <ElButton
                  :loading="groupLoading"
                  @click="((visible = false), (groupName = ''))"
                >
                  关闭
                </ElButton>
              </div>
            </div>
            <template #reference>
              <ElButton
                v-access:code="'upms:materialgroup:add'"
                @click="visible = true"
                :icon="Plus"
              >
                添加分组
              </ElButton>
            </template>
          </ElPopover>
          <ElButton
            v-access:code="'upms:materialgroup:page'"
            @click="openGroup"
          >
            管理分组
          </ElButton>
        </div>
      </div>
      <div class="media-list" v-loading="loading">
        <h1 class="title">{{ groupTitle }}</h1>
        <div class="image-list">
          <div
            class="image-item"
            v-for="(item, index) in state.materialList"
            :key="index"
          >
            <!-- 去掉 ElCard，直接自定义盒子 -->
            <div
              class="image-box-wrap"
              :class="{
                selectable: props.selectable,
                selected: isSelected(item) && props.mode === 'dialog',
              }"
              @click="handleSelect(item)"
            >
              <div class="image-box">
                <ElImage
                  :preview-teleported="true"
                  :src="item.url"
                  fit="cover"
                />
                <!-- 选中蒙层，仅mode为'dialog'时显示 -->
                <div
                  v-if="props.mode === 'dialog' && isSelected(item)"
                  class="selected-mask"
                >
                  <span class="selected-icon">✔</span>
                </div>
              </div>
              <div class="image-title">{{ item.name }}</div>
              <div class="image-bar" @click.stop>
                <ElButton
                  link
                  type="primary"
                  v-access:code="'upms:material:edit'"
                  @click="edit(item)"
                >
                  重命名
                </ElButton>
                <ElPopover
                  placement="bottom"
                  :width="400"
                  trigger="click"
                  :content="item.url"
                >
                  <template #reference>
                    <ElButton link type="primary">链接</ElButton>
                  </template>
                  <div class="popover-input">
                    <ElInput v-model="item.url" id="url" readonly />
                    <ElButton type="primary" @click="copyText(item.url)">
                      复制
                    </ElButton>
                  </div>
                </ElPopover>
                <ElButton link type="primary" @click="handlePreview(item.url)">
                  查看
                </ElButton>
                <ElButton
                  link
                  type="danger"
                  v-access:code="'upms:material:del'"
                  @click="del(item.id)"
                >
                  删除
                </ElButton>
              </div>
            </div>
          </div>
        </div>
        <!-- 分页 -->
        <Pagination
          :total="state.page.total"
          v-model:current="state.page.currentPage"
          v-model:size="state.page.pageSize"
          @change="initPage"
        />
      </div>
    </div>
    <!-- 素材分组管理 -->
    <ElDrawer
      size="40%"
      v-model="groupDrawer"
      direction="rtl"
      @opened="onDrawerOpened"
    >
      <template #header>
        <h4>分组管理</h4>
      </template>
      <template #default>
        <MaterialGroup ref="materialGroupRef" @init-group="getGroup" />
      </template>
    </ElDrawer>
    <Preview ref="previewRef" v-model="previewShow" :images="previewUrls" />
  </div>
</template>
<style lang="scss" scoped>
.material-main {
  display: flex;
  height: calc(max(100vh - 420px, 300px));
  min-height: 300px;
  margin-top: 15px;

  .category {
    box-sizing: border-box;
    display: flex;
    flex: none;
    flex-direction: column;
    width: 240px;
    margin-right: -1px;
    overflow: hidden;
    border: 1px solid #dcdee0;

    .category-tree {
      padding: 20px;
    }

    .footer {
      position: absolute;
      bottom: 2px;
      box-sizing: border-box;
      width: 240px;
      height: 56px;
      padding: 12px 0;
      text-align: center;
      box-shadow: 0 -1px 4px 0 rgb(0 0 0 / 10%);
    }
  }

  .media-list {
    box-sizing: border-box;
    flex: auto;
    padding: 12px;
    overflow-y: auto;
    border: 1px solid #dcdee0;

    .title {
      font-size: 16px;
      font-weight: 700;
      line-height: 24px;
      color: #323233;
    }
  }
}

.image-list {
  display: flex;
  flex-wrap: wrap;
  overflow: hidden;

  .image-item {
    position: relative;
    width: 140px;
    margin: 12px 14px 18px 0;

    .image-box-wrap {
      position: relative;
      display: flex;
      flex-direction: column;
      align-items: center;
      cursor: pointer;
      transition:
        box-shadow 0.2s,
        transform 0.2s;

      &:hover {
        transform: translateY(-2px) scale(1.03);
      }

      &.selectable {
        cursor: pointer;
        // border: 1px solid #e6e6e6;
      }
    }

    .image-box {
      position: relative;
      display: flex;
      align-items: center;
      justify-content: center;
      width: 120px;
      height: 120px;
      overflow: hidden;
      background-color: var(--el-border-color-extra-light);
      border-radius: 4px;

      img,
      .el-image {
        object-fit: cover;
      }

      .selected-mask {
        position: absolute;
        inset: 0;
        z-index: 2;
        display: flex;
        align-items: center;
        justify-content: center;
        background: rgb(0 0 0 / 50%);
        border-radius: 8px;

        .selected-icon {
          font-size: 32px;
          font-weight: bold;
          color: #fff;
        }
      }
    }

    .image-title {
      width: 100%;
      height: 20px;
      margin: 4px 0;
      overflow: hidden;
      text-overflow: ellipsis;
      font-size: 14px;
      line-height: 20px;
      color: #333;
      text-align: center;
      white-space: nowrap;
    }

    .image-bar {
      display: flex;
      justify-content: center;
      height: 20px;
      margin-top: 2px;
      font-size: 0;
      pointer-events: none;
      opacity: 0;
      transition: opacity 0.2s;

      button {
        padding: 0 1px;
        margin-left: 0;
      }
    }

    &:hover .image-bar {
      pointer-events: auto;
      opacity: 1;
    }
  }
}

.popover-input {
  display: flex;
  justify-content: space-between;
}
</style>
