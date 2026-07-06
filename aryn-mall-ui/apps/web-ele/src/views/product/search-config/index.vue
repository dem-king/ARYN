<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus';

import {
  addHotSearch,
  addSynonym,
  deleteHotSearch,
  deleteSynonym,
  getHotSearchList,
  getSynonymList,
  rebuildSearchIndex,
  updateHotSearch,
  updateSynonym,
} from '#/api/product/search-config';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);

const activeTab = ref('synonym');
const loading = ref(false);
const rebuildLoading = ref(false);

// ==================== 同义词管理 ====================
const synonymList = ref<any[]>([]);
const synonymDialogVisible = ref(false);
const synonymFormRef = ref();
const synonymForm = reactive({
  id: '',
  wordsText: '',
});
const synonymRules = {
  wordsText: [{ required: true, message: '请输入同义词', trigger: 'blur' }],
};

async function fetchSynonymList() {
  loading.value = true;
  try {
    const res = await getSynonymList();
    synonymList.value = res || [];
  } catch {
    synonymList.value = [];
  } finally {
    loading.value = false;
  }
}

function openSynonymDialog(row?: any) {
  if (row?.id) {
    synonymForm.id = row.id;
    synonymForm.wordsText = (row.words || []).join(',');
  } else {
    synonymForm.id = '';
    synonymForm.wordsText = '';
  }
  synonymDialogVisible.value = true;
}

async function submitSynonymForm() {
  if (!synonymFormRef.value) return;
  await synonymFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return;
    loading.value = true;
    const words = synonymForm.wordsText
      .split(',')
      .map((w) => w.trim())
      .filter(Boolean);
    try {
      if (synonymForm.id) {
        await updateSynonym(synonymForm.id, { words });
        ElMessage.success('更新成功');
      } else {
        await addSynonym({ words });
        ElMessage.success('新增成功');
      }
      synonymDialogVisible.value = false;
      fetchSynonymList();
    } catch {
      // error handled by interceptor
    } finally {
      loading.value = false;
    }
  });
}

function handleDeleteSynonym(id: string) {
  ElMessageBox.confirm('此操作将删除该同义词组，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    await deleteSynonym(id);
    ElMessage.success('删除成功');
    fetchSynonymList();
  });
}

// ==================== 热搜词管理 ====================
const hotSearchData = reactive({
  queryParams: {
    enabled: '',
    word: '',
  },
  tableData: [] as any[],
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
  },
});
const hotSearchDialogVisible = ref(false);
const hotSearchFormRef = ref();
const hotSearchForm = reactive({
  enabled: true,
  id: '',
  sort: 0,
  word: '',
});
const hotSearchRules = {
  word: [{ required: true, message: '请输入热搜词', trigger: 'blur' }],
};

async function fetchHotSearchList() {
  loading.value = true;
  try {
    const res = await getHotSearchList();
    hotSearchData.tableData = res || [];
    hotSearchData.page.total = (res || []).length;
  } catch {
    hotSearchData.tableData = [];
  } finally {
    loading.value = false;
  }
}

function openHotSearchDialog(row?: any) {
  if (row?.id) {
    hotSearchForm.id = row.id;
    hotSearchForm.word = row.word;
    hotSearchForm.sort = row.sort;
    hotSearchForm.enabled = row.enabled;
  } else {
    hotSearchForm.id = '';
    hotSearchForm.word = '';
    hotSearchForm.sort = 0;
    hotSearchForm.enabled = true;
  }
  hotSearchDialogVisible.value = true;
}

async function submitHotSearchForm() {
  if (!hotSearchFormRef.value) return;
  await hotSearchFormRef.value.validate(async (valid: boolean) => {
    if (!valid) return;
    loading.value = true;
    const data = {
      enabled: hotSearchForm.enabled,
      sort: hotSearchForm.sort,
      word: hotSearchForm.word,
    };
    try {
      if (hotSearchForm.id) {
        await updateHotSearch(hotSearchForm.id, data);
        ElMessage.success('更新成功');
      } else {
        await addHotSearch(data);
        ElMessage.success('新增成功');
      }
      hotSearchDialogVisible.value = false;
      fetchHotSearchList();
    } catch {
      // error handled by interceptor
    } finally {
      loading.value = false;
    }
  });
}

function handleDeleteHotSearch(id: string) {
  ElMessageBox.confirm('此操作将删除该热搜词，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    await deleteHotSearch(id);
    ElMessage.success('删除成功');
    fetchHotSearchList();
  });
}

// ==================== 索引重建 ====================
async function handleRebuildIndex() {
  ElMessageBox.confirm('全量重建索引可能需要较长时间，是否继续？', '索引重建', {
    confirmButtonText: '确认重建',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(async () => {
    rebuildLoading.value = true;
    try {
      await rebuildSearchIndex();
      ElMessage.success('索引重建任务已提交');
    } catch {
      // error handled by interceptor
    } finally {
      rebuildLoading.value = false;
    }
  });
}

// ==================== Tab切换 ====================
function handleTabChange(tab: string) {
  if (tab === 'synonym') {
    fetchSynonymList();
  } else if (tab === 'hot') {
    fetchHotSearchList();
  }
}

// 初始化
fetchSynonymList();
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElTabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- 同义词管理 -->
        <ElTabPane label="同义词管理" name="synonym">
          <div class="hx-table-toolbar mb10">
            <div>
              <ElButton
                type="primary"
                v-access:code="'product:search-config:add'"
                @click="openSynonymDialog()"
                :icon="Plus"
              >
                新增同义词组
              </ElButton>
            </div>
            <RightToolbar :refresh-btn="true" @refresh="fetchSynonymList" />
          </div>

          <ElTable v-loading="loading" :data="synonymList" border>
            <ElTableColumn prop="id" label="ID" width="200" />
            <ElTableColumn prop="words" label="同义词" min-width="300">
              <template #default="scope">
                <ElTag
                  v-for="(word, idx) in scope.row.words"
                  :key="idx"
                  class="mr5"
                  type="info"
                >
                  {{ word }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="createdAt" label="创建时间" width="180" />
            <ElTableColumn label="操作" width="150" align="center">
              <template #default="scope">
                <ElButton
                  link
                  type="primary"
                  v-access:code="'product:search-config:edit'"
                  @click="openSynonymDialog(scope.row)"
                  :icon="Edit"
                >
                  编辑
                </ElButton>
                <ElButton
                  link
                  type="danger"
                  v-access:code="'product:search-config:del'"
                  @click="handleDeleteSynonym(scope.row.id)"
                  :icon="Delete"
                >
                  删除
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>

        <!-- 热搜词管理 -->
        <ElTabPane label="热搜词管理" name="hot">
          <div class="hx-table-toolbar mb10">
            <div>
              <ElButton
                type="primary"
                v-access:code="'product:search-config:add'"
                @click="openHotSearchDialog()"
                :icon="Plus"
              >
                新增热搜词
              </ElButton>
            </div>
            <RightToolbar :refresh-btn="true" @refresh="fetchHotSearchList" />
          </div>

          <ElTable v-loading="loading" :data="hotSearchData.tableData" border>
            <ElTableColumn prop="id" label="ID" width="200" />
            <ElTableColumn prop="word" label="热搜词" min-width="200" />
            <ElTableColumn
              prop="sort"
              label="排序"
              width="100"
              align="center"
            />
            <ElTableColumn
              prop="enabled"
              label="状态"
              width="100"
              align="center"
            >
              <template #default="scope">
                <ElTag :type="scope.row.enabled ? 'success' : 'info'">
                  {{ scope.row.enabled ? '启用' : '禁用' }}
                </ElTag>
              </template>
            </ElTableColumn>
            <ElTableColumn prop="createdAt" label="创建时间" width="180" />
            <ElTableColumn label="操作" width="150" align="center">
              <template #default="scope">
                <ElButton
                  link
                  type="primary"
                  v-access:code="'product:search-config:edit'"
                  @click="openHotSearchDialog(scope.row)"
                  :icon="Edit"
                >
                  编辑
                </ElButton>
                <ElButton
                  link
                  type="danger"
                  v-access:code="'product:search-config:del'"
                  @click="handleDeleteHotSearch(scope.row.id)"
                  :icon="Delete"
                >
                  删除
                </ElButton>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElTabPane>

        <!-- 索引管理 -->
        <ElTabPane label="索引管理" name="index">
          <div class="p-20px">
            <h3 class="mb-16px text-16px font-bold">搜索索引管理</h3>
            <p class="mb-16px text-14px text-gray-500">
              当商品数据发生较大变更时，可通过全量重建索引来确保搜索结果的准确性。重建过程可能需要数分钟，请耐心等待。
            </p>
            <ElButton
              type="danger"
              :loading="rebuildLoading"
              @click="handleRebuildIndex"
            >
              全量重建索引
            </ElButton>
          </div>
        </ElTabPane>
      </ElTabs>

      <!-- 同义词弹窗 -->
      <ElDialog
        v-model="synonymDialogVisible"
        :title="synonymForm.id ? '编辑同义词组' : '新增同义词组'"
        width="500px"
      >
        <ElForm
          ref="synonymFormRef"
          :model="synonymForm"
          label-width="100px"
          :rules="synonymRules"
        >
          <ElFormItem label="同义词" prop="wordsText">
            <ElInput
              v-model="synonymForm.wordsText"
              type="textarea"
              :rows="3"
              placeholder="请输入同义词，多个词用英文逗号分隔，如：手机,移动电话,cellphone"
            />
          </ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="synonymDialogVisible = false">取消</ElButton>
          <ElButton
            type="primary"
            :loading="loading"
            @click="submitSynonymForm"
          >
            确认
          </ElButton>
        </template>
      </ElDialog>

      <!-- 热搜词弹窗 -->
      <ElDialog
        v-model="hotSearchDialogVisible"
        :title="hotSearchForm.id ? '编辑热搜词' : '新增热搜词'"
        width="500px"
      >
        <ElForm
          ref="hotSearchFormRef"
          :model="hotSearchForm"
          label-width="100px"
          :rules="hotSearchRules"
        >
          <ElFormItem label="热搜词" prop="word">
            <ElInput v-model="hotSearchForm.word" placeholder="请输入热搜词" />
          </ElFormItem>
          <ElFormItem label="排序号" prop="sort">
            <ElInputNumber
              v-model="hotSearchForm.sort"
              :min="0"
              :max="99999"
              controls-position="right"
              style="width: 100%"
            />
          </ElFormItem>
          <ElFormItem label="是否启用" prop="enabled">
            <ElSwitch v-model="hotSearchForm.enabled" />
          </ElFormItem>
        </ElForm>
        <template #footer>
          <ElButton @click="hotSearchDialogVisible = false">取消</ElButton>
          <ElButton
            type="primary"
            :loading="loading"
            @click="submitHotSearchForm"
          >
            确认
          </ElButton>
        </template>
      </ElDialog>
    </div>
  </div>
</template>
