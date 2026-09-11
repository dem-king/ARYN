<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref, toRefs } from 'vue';
import { useRouter } from 'vue-router';

import {
  Bottom,
  Delete,
  Edit,
  Plus,
  Refresh,
  Search,
  Top,
} from '@element-plus/icons-vue';
import {
  ElAlert,
  ElButton,
  ElCascader,
  ElCol,
  ElDialog,
  ElForm,
  ElFormItem,
  ElImage,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRow,
  ElSelect,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getList as getBrandList } from '#/api/product/goods-brand';
import { getPage as getCategoryTree } from '#/api/product/goods-category';
import {
  delObj,
  getPage,
  goodsShelf as goodsShelfOpt,
} from '#/api/product/goods-spu';
import {
  confirmImport,
  downloadCsvTemplate,
  getTemplate,
  parseCsvToRows,
  previewImport,
} from '#/api/product/product-import';
import { useDict } from '#/utils/dict';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const DictTag = defineAsyncComponent(
  () => import('#/components/dict-tag/index.vue'),
);

const { goods_status, goods_specs } = useDict('goods_status', 'goods_specs');
const state = reactive({
  queryParams: {
    name: '',
    status: '',
    enableSpecs: '',
    categorySecondId: '',
    brandId: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    desc: 'create_time',
  },
  tableData: [],
  active: '',
});
// 商品类目
const goodsCategoryList = ref([]);
const goodsBrandList = ref<any[]>([]);
const defaultProps = {
  label: 'name',
  value: 'id',
  children: 'children',
  emitPath: false,
};

const multipleSelection = ref<any>([]);
const showSearch = ref(true);
const $route = useRouter();
const loading = ref(false);
const queryRef = ref();
const { tableData, queryParams, page } = toRefs(state);

// 批量导入状态
const importVisible = ref(false);
const importLoading = ref(false);
const importFileName = ref('');
const importRows = ref<any[]>([]);
const previewResult = ref<any>(null);

const openImport = () => {
  importVisible.value = true;
  importRows.value = [];
  previewResult.value = null;
  importFileName.value = '';
};
const onTemplateDownload = async () => {
  const columns = await getTemplate();
  downloadCsvTemplate(columns.map((c: any) => c.title));
};
const handleImportFile = async (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  importFileName.value = file.name;
  const content = await file.text();
  importRows.value = parseCsvToRows(content);
  if (importRows.value.length === 0) {
    ElMessage.warning('未解析到数据行');
    return;
  }
  importLoading.value = true;
  try {
    previewResult.value = await previewImport({
      fileName: file.name,
      rows: importRows.value,
    });
  } finally {
    importLoading.value = false;
  }
};
const doConfirmImport = async () => {
  if (!previewResult.value?.jobId) return;
  importLoading.value = true;
  try {
    const imported = await confirmImport({
      jobId: previewResult.value.jobId,
      fileName: importFileName.value,
      rows: importRows.value,
    });
    ElMessage.success(`导入成功 ${imported} 行`);
    importVisible.value = false;
    initPage();
  } finally {
    importLoading.value = false;
  }
};

/**
 * 查询全部商品类目
 */
const getCategory = () => {
  getCategoryTree().then((response) => {
    goodsCategoryList.value = response;
  });
};
const getBrands = () => {
  getBrandList().then((response) => {
    goodsBrandList.value = response;
  });
};
const initPage = async () => {
  loading.value = true;
  const params = {
    current: page.value.currentPage,
    size: page.value.pageSize,
    desc: page.value.desc,
    verifyStatus: state.active,
  };
  await getPage(Object.assign(params, queryParams.value))
    .then((response) => {
      tableData.value = response.records;
      page.value.total = response.total;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};
/**
 * 重置搜索表单
 */
const resetQuery = () => {
  queryRef.value.resetFields();
};
/**
 * 新增按钮
 */
const add = () => {
  $route.push({ path: '/spu/form' });
};
/**
 * 修改按钮
 */
const edit = (row: any) => {
  $route.push({ path: '/spu/form', query: { id: row.id } });
};
/**
 * 删除按钮
 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该商品，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    delObj(id)
      .then(() => {
        ElMessage.success('删除成功');

        initPage();
      })
      .catch(() => {});
  });
};
/**
 * 商品上下架
 */
const goodsShelf = (status: string) => {
  if (!multipleSelection.value || multipleSelection.value.length <= 0) {
    return ElMessage.warning('请选择商品');
  }
  loading.value = true;
  const ids = multipleSelection.value.map((item: any) => item.id);
  goodsShelfOpt({ status, spuIds: ids })
    .then(() => {
      ElMessage.success('操作成功');
      loading.value = false;
      initPage();
    })
    .catch(() => {
      loading.value = false;
    });
};
/**
 * 当选择项发生变化时会触发该事件
 */
const handleSelectionChange = (val: any) => {
  multipleSelection.value = val;
};

getCategory();
getBrands();
initPage();
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索 -->
      <ElForm
        :model="queryParams"
        ref="queryRef"
        :inline="true"
        v-show="showSearch"
      >
        <ElFormItem label="商品名称" prop="name">
          <ElInput
            v-model="queryParams.name"
            clearable
            placeholder="请输入商品名称"
          />
        </ElFormItem>
        <ElFormItem label="商品类目" prop="categorySecondId">
          <ElCascader
            style="width: 200px"
            :options="goodsCategoryList"
            v-model="queryParams.categorySecondId"
            placeholder="请选择商品类目"
            clearable
            :props="defaultProps"
          />
        </ElFormItem>
        <ElFormItem label="商品品牌" prop="brandId">
          <ElSelect
            v-model="queryParams.brandId"
            clearable
            filterable
            placeholder="请选择商品品牌"
            style="width: 200px"
          >
            <ElOption
              v-for="item in goodsBrandList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="商品规格" prop="enableSpecs">
          <ElSelect
            style="width: 200px"
            v-model="state.queryParams.enableSpecs"
            clearable
            placeholder="请选择商品规格"
          >
            <ElOption
              v-for="item in goods_specs"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="商品状态" prop="status">
          <ElSelect
            style="width: 200px"
            v-model="state.queryParams.status"
            clearable
            placeholder="请选择商品状态"
          >
            <ElOption
              v-for="item in goods_status"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage" :icon="Search">
            搜索
          </ElButton>
          <ElButton @click="resetQuery" :icon="Refresh"> 重置 </ElButton>
        </ElFormItem>
      </ElForm>
      <!-- 工具栏 -->
      <div class="hx-table-toolbar mb10">
        <div>
          <ElButton
            type="primary"
            v-access:code="'product:goodsspu:add'"
            @click="add"
            :icon="Plus"
          >
            新增
          </ElButton>
          <ElButton
            type="success"
            v-access:code="'product:goodsspu:edit'"
            @click="goodsShelf('1')"
            :disabled="multipleSelection.length <= 0"
            :icon="Top"
          >
            上架
          </ElButton>
          <ElButton
            type="danger"
            v-access:code="'product:goodsspu:edit'"
            @click="goodsShelf('0')"
            :disabled="multipleSelection.length <= 0"
            :icon="Bottom"
          >
            下架
          </ElButton>
          <ElButton
            type="warning"
            v-access:code="'product:import:preview'"
            @click="openImport"
          >
            批量导入
          </ElButton>
        </div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>
      <!-- 列表 -->
      <ElTable
        v-loading="loading"
        :data="tableData"
        border
        @selection-change="handleSelectionChange"
        style="width: 100%"
      >
        <ElTableColumn type="selection" width="50" align="center" />
        <ElTableColumn prop="spu" label="商品信息" width="320">
          <template #default="scope">
            <ElRow>
              <ElCol :span="6">
                <ElImage
                  style="width: 50px; height: 50px"
                  :src="scope.row.spuUrls[0]"
                  :preview-src-list="scope.row.spuUrls"
                  :hide-on-click-modal="true"
                  fit="cover"
                  :preview-teleported="true"
                />
              </ElCol>
              <ElCol :span="18" style="float: left">
                <span class="line-clamp-2">{{ scope.row.name }}</span>
                <div
                  class="line-clamp-2"
                  style="font-size: 13px; color: #909399"
                >
                  {{ scope.row.subTitle }}
                </div>
              </ElCol>
            </ElRow>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="categoryName"
          label="商品类目"
          align="center"
          width="180"
        />
        <ElTableColumn
          prop="brandName"
          label="商品品牌"
          align="center"
          width="140"
        />
        <ElTableColumn
          prop="salesPrice"
          label="价格（元）"
          align="center"
          width="140"
        >
          <template #default="scope">
            <span style="color: red">￥{{ scope.row.salesPrice }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="status"
          label="商品状态"
          align="center"
          width="110"
        >
          <template #default="scope">
            <DictTag :options="goods_status" :value="scope.row.status" />
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="enableSpecs"
          label="规格"
          align="center"
          width="110"
        >
          <template #default="scope">
            <DictTag :options="goods_specs" :value="scope.row.enableSpecs" />
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="salesVolume"
          label="销量"
          align="center"
          width="110"
        />
        <ElTableColumn prop="stock" label="总库存" align="center" width="200" />
        <ElTableColumn
          prop="createTime"
          label="创建时间"
          align="center"
          width="180"
        />
        <ElTableColumn
          label="操作"
          align="center"
          fixed="right"
          min-width="160"
        >
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'product:goodsspu:edit'"
              @click="edit(scope.row)"
              :icon="Edit"
            >
              修改
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'product:goodsspu:del'"
              @click="del(scope.row.id)"
              :icon="Delete"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <!-- 分页 -->
      <Pagination
        :total="state.page.total"
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        @change="initPage"
      />
      <!-- 批量导入 -->
      <ElDialog
        v-model="importVisible"
        title="商品批量导入"
        width="720px"
        :close-on-click-modal="false"
      >
        <div class="mb10">
          <ElButton link type="primary" @click="onTemplateDownload">
            下载 CSV 模板
          </ElButton>
          <input
            accept=".csv"
            type="file"
            style="margin-left: 12px"
            @change="handleImportFile"
          />
        </div>
        <ElAlert
          v-if="previewResult"
          :title="`共 ${previewResult.totalRows} 行，可导入 ${previewResult.successRows} 行，错误 ${previewResult.errorRows} 行`"
          :type="previewResult.errorRows > 0 ? 'warning' : 'success'"
          :closable="false"
          class="mb10"
        />
        <ElTable
          v-if="previewResult?.errors?.length"
          :data="previewResult.errors"
          border
          max-height="300"
        >
          <ElTableColumn prop="rowNo" label="行号" width="80" align="center" />
          <ElTableColumn prop="errorType" label="错误类型" width="200" />
          <ElTableColumn prop="errorMessage" label="错误说明" />
        </ElTable>
        <template #footer>
          <ElButton @click="importVisible = false">取消</ElButton>
          <ElButton
            type="primary"
            :disabled="!previewResult?.confirmable"
            :loading="importLoading"
            @click="doConfirmImport"
          >
            确认导入
          </ElButton>
        </template>
      </ElDialog>
    </div>
  </div>
</template>
