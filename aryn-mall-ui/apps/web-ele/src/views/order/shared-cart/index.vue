<script lang="ts" setup>
import { defineAsyncComponent, onMounted, reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
} from 'element-plus';

import {
  getSharedCartDetail,
  getSharedCartPage,
  updateSharedCartMemberPermission,
} from '#/api/order/shared-cart';

const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const state = reactive({
  page: { total: 0, currentPage: 1, pageSize: 10 },
  query: { cartNo: '', status: '' },
  tableData: [] as any[],
});
const loading = ref(false);

const detailVisible = ref(false);
const detail = ref<any>({ cart: {}, members: [], items: [] });
const detailTab = ref('members');

const statusLabel: Record<string, string> = {
  '1': '草稿',
  '2': '收集中',
  '3': '待确认',
  '4': '已提交',
  '5': '已关闭',
  '6': '已完成（已送达）',
};
const itemStatusLabel: Record<string, string> = {
  '1': '待确认',
  '2': '已确认',
  '3': '已移除',
};

const initPage = async () => {
  loading.value = true;
  try {
    const response = await getSharedCartPage({
      current: state.page.currentPage,
      size: state.page.pageSize,
      cartNo: state.query.cartNo,
      status: state.query.status,
    });
    state.tableData = response.records;
    state.page.total = response.total;
  } finally {
    loading.value = false;
  }
};

const openDetail = (row: any) => {
  getSharedCartDetail(row.id)
    .then((data) => {
      detail.value = data;
      detailVisible.value = true;
    })
    .catch(() => {});
};

/** 正在提交权限开关的成员 ID：避免连点把两次请求的结果写乱 */
const permissionSaving = ref('');

/**
 * 切换成员明细维护权限。
 *
 * 收回后用本地值即时回显（不再整体重载详情，避免弹窗闪动），
 * 失败则把开关拨回原值——服务端拒绝时界面必须如实反映，不能让运营以为已生效。
 */
const toggleMemberPermission = (member: any, next: boolean) => {
  const previous = member.canEdit;
  const canEdit = next ? '1' : '0';
  member.canEdit = canEdit;
  permissionSaving.value = member.id;
  updateSharedCartMemberPermission(detail.value.cart.id, member.id, canEdit)
    .then(() => {
      ElMessage.success(
        next ? '已恢复该成员的明细维护权限' : '已收回该成员的明细维护权限',
      );
    })
    .catch(() => {
      member.canEdit = previous;
    })
    .finally(() => {
      permissionSaving.value = '';
    });
};

onMounted(initPage);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElForm :inline="true" :model="state.query">
        <ElFormItem label="购物车编号">
          <ElInput v-model="state.query.cartNo" clearable placeholder="SC..." />
        </ElFormItem>
        <ElFormItem label="状态">
          <ElSelect v-model="state.query.status" clearable style="width: 120px">
            <ElOption
              v-for="(label, value) in statusLabel"
              :key="value"
              :label="label"
              :value="value"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage">搜索</ElButton>
        </ElFormItem>
      </ElForm>

      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="cartNo" label="编号" width="200" />
        <ElTableColumn label="靠港计划" min-width="200">
          <template #default="scope">
            <!-- 回填船名 · 港口 泊位；远程域不可用时回落靠港 ID，不留空白 -->
            <span>{{
              scope.row.vesselCallText ?? scope.row.vesselCallId
            }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="发起人" min-width="140">
          <template #default="scope">
            <!-- 服务端已按「昵称 → 用户+ID后6位」兜底（与详情页同一口径） -->
            {{ scope.row.ownerName ?? '—' }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="状态" width="90">
          <template #default="scope">
            {{ statusLabel[scope.row.status] ?? scope.row.status }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="expiresAt" label="收集截止" width="170" />
        <ElTableColumn label="生成订单" min-width="180">
          <template #default="scope">
            <!-- 展示订单号：运营在订单模块按订单号检索，主键搜不到 -->
            <span>{{ scope.row.submitOrderNo ?? '—' }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="80">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'sharedcart:get'"
              @click="openDetail(scope.row)"
            >
              详情
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <Pagination
        :total="state.page.total"
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        @change="initPage"
      />

      <ElDialog v-model="detailVisible" title="共享购物车详情" width="900px">
        <ElTabs v-model="detailTab">
          <ElTabPane label="成员" name="members">
            <ElTable :data="detail.members" border>
              <ElTableColumn label="用户" min-width="160">
                <template #default="scope">
                  <span v-if="scope.row.displayName">{{
                    scope.row.displayName
                  }}</span>
                  <span v-else>{{ scope.row.userId }}</span>
                </template>
              </ElTableColumn>
              <ElTableColumn label="角色" width="110">
                <template #default="scope">
                  {{
                    scope.row.memberRole === '1'
                      ? '发起人'
                      : scope.row.memberRole === '3'
                        ? '确认人'
                        : '成员'
                  }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="可确认" width="80">
                <template #default="scope">
                  {{ scope.row.canConfirm === '1' ? '是' : '否' }}
                </template>
              </ElTableColumn>
              <ElTableColumn label="可加购" width="110">
                <template #default="scope">
                  <!--
                    发起人始终可维护（服务端 requireCanEdit 直接放行），开关对他无效，
                    因此只读展示；给非发起人可切换的开关。
                  -->
                  <span v-if="scope.row.memberRole === '1'">始终可加购</span>
                  <ElSwitch
                    v-else
                    v-access:code="'sharedcart:member:permission'"
                    :model-value="scope.row.canEdit === '1'"
                    :loading="permissionSaving === scope.row.id"
                    :disabled="permissionSaving === scope.row.id"
                    inline-prompt
                    active-text="可"
                    inactive-text="停"
                    @change="
                      (val: any) => toggleMemberPermission(scope.row, !!val)
                    "
                  />
                </template>
              </ElTableColumn>
              <ElTableColumn prop="joinedTime" label="加入时间" width="170" />
            </ElTable>
          </ElTabPane>
          <ElTabPane label="明细" name="items">
            <ElTable :data="detail.items" border>
              <ElTableColumn label="商品" min-width="240">
                <template #default="scope">
                  <div>{{ scope.row.spuName ?? '—' }}</div>
                  <div v-if="scope.row.specText" class="goods-spec-text">
                    {{ scope.row.specText }}
                  </div>
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="requestedQuantity"
                label="申请数量"
                width="90"
                align="center"
              />
              <ElTableColumn
                prop="approvedQuantity"
                label="核定数量"
                width="90"
                align="center"
              />
              <ElTableColumn
                prop="memberRemark"
                label="成员备注"
                min-width="140"
              />
              <ElTableColumn label="状态" width="90">
                <template #default="scope">
                  {{ itemStatusLabel[scope.row.status] ?? scope.row.status }}
                </template>
              </ElTableColumn>
              <ElTableColumn
                prop="contributorName"
                label="来源成员"
                min-width="120"
              />
            </ElTable>
          </ElTabPane>
        </ElTabs>
      </ElDialog>
    </div>
  </div>
</template>

<style scoped>
.goods-spec-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
