<script lang="ts" setup>
import { reactive, ref } from 'vue';
import { useRoute } from 'vue-router';

import { QuestionFilled } from '@element-plus/icons-vue';
import {
  ElButton,
  ElCheckbox,
  ElCol,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElMessage,
  ElOption,
  ElRadio,
  ElRow,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTooltip,
  ElTreeSelect,
} from 'element-plus';

import { editObj, genTabledDetail } from '#/api/gen/table';
import { downloadBlobFile } from '#/utils/util';

type Column = { columnComment: string; columnName: string };
type MenuNode = { children?: MenuNode[]; menuId: string; menuName: string };

defineProps<{
  menus: MenuNode[];
}>();

const state = reactive({
  form: {
    tplCategory: 'crud',
    packageName: '',
    moduleName: '',
    businessName: '',
    className: '',
    columns: [] as Column[],
    functionAuthor: '',
    functionName: '',
    parentMenuId: '' as number | string,
    tableName: '',
    tableComment: '',
    subTableName: '',
    subTableFkName: '',
    treeCode: '',
    tplWebType: 'element-plus',
    genType: '0',
  },
});
const rules = ref({
  packageName: [
    { required: true, message: '请输入生成包路径', trigger: 'blur' },
  ],
  moduleName: [
    { required: true, message: '请输入生成模块名', trigger: 'blur' },
  ],
  businessName: [
    { required: true, message: '请输入生成业务名', trigger: 'blur' },
  ],
  functionName: [
    { required: true, message: '请输入生成功能名', trigger: 'blur' },
  ],
});
const defaultMenuProps = { label: 'menuName', children: 'children' };
const route = useRoute();
const activeName = ref('info');
const tableHeight = ref(`${document.documentElement.scrollHeight - 245}px`);
const loading = ref(false);

const getTableInfo = async () => {
  loading.value = true;
  try {
    const { tableName, dsName }: any = route.query;
    const res = await genTabledDetail(dsName, tableName);
    state.form = res;
  } finally {
    loading.value = false;
  }
};
const submitForm = async () => {
  loading.value = true;
  try {
    await editObj(state.form);
    await downloadBlobFile(
      `/gen/gen-table/gen/${state.form.tableName}`,
      null,
      'aryn.zip',
    );
    ElMessage.success('代码生成成功');
  } finally {
    loading.value = false;
  }
};
getTableInfo();
</script>

<template>
  <div class="aryn-layout-container">
    <div class="aryn-layout-container-auto aryn-layout-container-view">
      <ElForm
        :model="state.form"
        :rules="rules"
        label-width="150px"
        class="m-2"
        v-loading="loading"
      >
        <ElTabs v-model="activeName">
          <ElTabPane label="基本信息" name="info">
            <ElRow>
              <ElCol :span="12">
                <ElFormItem prop="tableName" label="表名称">
                  <ElInput v-model="state.form.tableName" />
                </ElFormItem>
              </ElCol>
              <ElCol :span="12">
                <ElFormItem prop="tableComment" label="表描述">
                  <ElInput v-model="state.form.tableComment" />
                </ElFormItem>
              </ElCol>
              <ElCol :span="12">
                <ElFormItem prop="className" label="实体类名称">
                  <ElInput v-model="state.form.className" />
                </ElFormItem>
              </ElCol>
              <ElCol :span="12">
                <ElFormItem prop="functionAuthor" label="生成作者">
                  <ElInput v-model="state.form.functionAuthor" />
                </ElFormItem>
              </ElCol>
            </ElRow>
          </ElTabPane>
          <ElTabPane label="字段信息" name="column">
            <ElTable
              :data="state.form.columns"
              row-key="columnId"
              :max-height="tableHeight"
            >
              <ElTableColumn
                label="序号"
                type="index"
                min-width="5%"
                class-name="allowDrag"
              />
              <ElTableColumn
                label="字段列名"
                prop="columnName"
                min-width="10%"
                :show-overflow-tooltip="true"
              />
              <ElTableColumn label="字段描述" min-width="10%">
                <template #default="scope">
                  <ElInput v-model="scope.row.columnComment" />
                </template>
              </ElTableColumn>
              <ElTableColumn
                label="物理类型"
                prop="columnType"
                min-width="10%"
                :show-overflow-tooltip="true"
              />
              <ElTableColumn label="Java类型" min-width="11%">
                <template #default="scope">
                  <ElSelect v-model="scope.row.javaType">
                    <ElOption label="Long" value="Long" />
                    <ElOption label="String" value="String" />
                    <ElOption label="Integer" value="Integer" />
                    <ElOption label="Double" value="Double" />
                    <ElOption label="BigDecimal" value="BigDecimal" />
                    <ElOption label="Date" value="Date" />
                    <ElOption label="Boolean" value="Boolean" />
                  </ElSelect>
                </template>
              </ElTableColumn>
              <ElTableColumn label="java属性" min-width="10%">
                <template #default="scope">
                  <ElInput v-model="scope.row.javaField" />
                </template>
              </ElTableColumn>

              <ElTableColumn label="插入" min-width="5%">
                <template #default="scope">
                  <ElCheckbox
                    true-label="1"
                    false-label="0"
                    v-model="scope.row.isInsert"
                  />
                </template>
              </ElTableColumn>
              <ElTableColumn label="编辑" min-width="5%">
                <template #default="scope">
                  <ElCheckbox
                    true-label="1"
                    false-label="0"
                    v-model="scope.row.isEdit"
                  />
                </template>
              </ElTableColumn>
              <ElTableColumn label="列表" min-width="5%">
                <template #default="scope">
                  <ElCheckbox
                    true-label="1"
                    false-label="0"
                    v-model="scope.row.isList"
                  />
                </template>
              </ElTableColumn>
              <ElTableColumn label="查询" min-width="5%">
                <template #default="scope">
                  <ElCheckbox
                    true-label="1"
                    false-label="0"
                    v-model="scope.row.isQuery"
                  />
                </template>
              </ElTableColumn>
              <ElTableColumn label="查询方式" min-width="10%">
                <template #default="scope">
                  <ElSelect v-model="scope.row.queryType">
                    <ElOption label="=" value="EQ" />
                    <ElOption label="!=" value="NE" />
                    <ElOption label=">" value="GT" />
                    <ElOption label=">=" value="GTE" />
                    <ElOption label="<" value="LT" />
                    <ElOption label="<=" value="LTE" />
                    <ElOption label="LIKE" value="LIKE" />
                    <ElOption label="BETWEEN" value="BETWEEN" />
                  </ElSelect>
                </template>
              </ElTableColumn>
              <ElTableColumn label="必填" min-width="5%">
                <template #default="scope">
                  <ElCheckbox
                    true-label="1"
                    false-label="0"
                    v-model="scope.row.isRequired"
                  />
                </template>
              </ElTableColumn>
              <ElTableColumn label="显示类型" min-width="12%">
                <template #default="scope">
                  <ElSelect v-model="scope.row.htmlType">
                    <ElOption label="文本框" value="input" />
                    <ElOption label="文本域" value="textarea" />
                    <ElOption label="下拉框" value="select" />
                    <ElOption label="单选框" value="radio" />
                    <ElOption label="复选框" value="checkbox" />
                    <ElOption label="日期控件" value="datetime" />
                  </ElSelect>
                </template>
              </ElTableColumn>
            </ElTable>
          </ElTabPane>
          <ElTabPane label="生成信息" name="gen">
            <ElRow>
              <!-- 统一使用 Element Plus 组件标签 -->
              <ElCol :span="12">
                <ElFormItem prop="packageName">
                  <template #label>
                    <span>
                      生成包路径
                      <ElTooltip
                        content="生成在哪个java包下，例如 com.ruoyi.system"
                        placement="top"
                      >
                        <ElIcon><QuestionFilled /></ElIcon>
                      </ElTooltip>
                    </span>
                  </template>
                  <ElInput v-model="state.form.packageName" />
                </ElFormItem>
              </ElCol>

              <ElCol :span="12">
                <ElFormItem prop="moduleName">
                  <template #label>
                    <span>
                      生成模块名
                      <ElTooltip
                        content="可理解为子系统名，例如 system"
                        placement="top"
                      >
                        <i class="el-icon-question"></i>
                      </ElTooltip>
                    </span>
                  </template>
                  <ElInput v-model="state.form.moduleName" />
                </ElFormItem>
              </ElCol>

              <ElCol :span="12">
                <ElFormItem prop="businessName">
                  <template #label>
                    <span>
                      生成业务名
                      <ElTooltip
                        content="可理解为功能英文名，例如 user"
                        placement="top"
                      >
                        <i class="el-icon-question"></i>
                      </ElTooltip>
                    </span>
                  </template>
                  <ElInput v-model="state.form.businessName" />
                </ElFormItem>
              </ElCol>

              <ElCol :span="12">
                <ElFormItem prop="functionName">
                  <template #label>
                    <span>
                      生成功能名
                      <ElTooltip
                        content="用作类描述，例如 用户"
                        placement="top"
                      >
                        <i class="el-icon-question"></i>
                      </ElTooltip>
                    </span>
                  </template>
                  <ElInput v-model="state.form.functionName" />
                </ElFormItem>
              </ElCol>

              <ElCol :span="12">
                <ElFormItem prop="genType" label="生成代码方式">
                  <ElRadio v-model="state.form.genType" label="0">
                    zip压缩包
                  </ElRadio>
                </ElFormItem>
              </ElCol>

              <ElCol :span="12">
                <ElFormItem>
                  <template #label>
                    <span>
                      上级菜单
                      <ElTooltip
                        content="分配到指定菜单下，例如 系统管理"
                        placement="top"
                      >
                        <ElIcon><QuestionFilled /></ElIcon>
                      </ElTooltip>
                    </span>
                  </template>
                  <!-- 替换 Treeselect 为 ElTreeSelect，匹配项目适配写法 -->
                  <ElTreeSelect
                    style="width: 100%"
                    v-model="state.form.parentMenuId"
                    :data="menus"
                    :props="defaultMenuProps"
                    node-key="menuId"
                    check-strictly
                    :render-after-expand="false"
                  />
                </ElFormItem>
              </ElCol>
            </ElRow>
          </ElTabPane>
        </ElTabs>
      </ElForm>
      <div class="mt-2 flex items-end justify-center">
        <ElButton style="width: 160px" @click="submitForm"> 返回 </ElButton>
        <ElButton
          style="width: 160px"
          type="primary"
          :disabled="loading"
          @click="submitForm"
        >
          保存并生成代码
        </ElButton>
      </div>
    </div>
  </div>
</template>
