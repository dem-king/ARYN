<script lang="ts" setup>
import { computed, defineAsyncComponent, onMounted, reactive, ref } from 'vue';
import useClipboard from 'vue-clipboard3';

import {
  CopyDocument,
  Refresh,
  RefreshLeft,
  Search,
  Ship,
  ShoppingCart,
} from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElIcon,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTag,
  ElTooltip,
} from 'element-plus';

import {
  getSharedCartDetail,
  getSharedCartPage,
  updateSharedCartMemberPermission,
} from '#/api/order/shared-cart';

const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

type TagEffect = 'dark' | 'light' | 'plain';
type TagType = 'danger' | 'info' | 'primary' | 'success' | 'warning';

interface StatusMeta {
  effect: TagEffect;
  label: string;
  type: TagType;
}

const state = reactive({
  page: { total: 0, currentPage: 1, pageSize: 10 },
  query: { cartNo: '', status: '' },
  tableData: [] as any[],
});
const loading = ref(false);

const detailVisible = ref(false);
const detailLoading = ref(false);
const detail = ref<any>({ cart: {}, members: [], items: [] });
const detailTab = ref('members');

const statusMeta: Record<string, StatusMeta> = {
  '1': { effect: 'light', label: '草稿', type: 'info' },
  '2': { effect: 'light', label: '收集中', type: 'primary' },
  '3': { effect: 'light', label: '待确认', type: 'warning' },
  '4': { effect: 'light', label: '已提交', type: 'success' },
  '5': { effect: 'plain', label: '已关闭', type: 'info' },
  '6': { effect: 'dark', label: '已完成（已送达）', type: 'success' },
};
const statusLabel: Record<string, string> = Object.fromEntries(
  Object.entries(statusMeta).map(([value, meta]) => [value, meta.label]),
);
const statusOf = (status?: string): StatusMeta =>
  statusMeta[status ?? ''] ?? {
    effect: 'light',
    label: status || '未知',
    type: 'info',
  };

const itemStatusMeta: Record<string, { label: string; type: TagType }> = {
  '1': { label: '待确认', type: 'warning' },
  '2': { label: '已确认', type: 'success' },
  '3': { label: '已移除', type: 'info' },
};
const itemStatusOf = (status?: string) =>
  itemStatusMeta[status ?? ''] ?? {
    label: status || '未知',
    type: 'info' as TagType,
  };

const memberRoleOf = (role?: string): { label: string; type: TagType } => {
  if (role === '1') return { label: '发起人', type: 'warning' };
  if (role === '3') return { label: '确认人', type: 'primary' };
  return { label: '成员', type: 'info' };
};

const members = computed<any[]>(() => detail.value.members ?? []);
const items = computed<any[]>(() => detail.value.items ?? []);
// 已移除的明细不计入数量统计，避免「申请 10 件、实际 8 件」的误读
const activeItems = computed(() =>
  items.value.filter((item: any) => item.status !== '3'),
);
const removedItemCount = computed(
  () => items.value.length - activeItems.value.length,
);
const requestedTotal = computed(() =>
  activeItems.value.reduce(
    (sum: number, item: any) => sum + Number(item.requestedQuantity ?? 0),
    0,
  ),
);
const approvedTotal = computed(() =>
  activeItems.value.reduce(
    (sum: number, item: any) => sum + Number(item.approvedQuantity ?? 0),
    0,
  ),
);
const detailVessel = computed(() => splitVesselCall(detail.value.cart ?? {}));

const hasQuery = computed(() =>
  Boolean(state.query.cartNo || state.query.status),
);

const { toClipboard } = useClipboard();

const copyText = async (text?: string, label = '内容') => {
  if (!text) return;
  try {
    await toClipboard(String(text));
    ElMessage.success(`${label}已复制`);
  } catch {
    ElMessage.error('复制失败，请手动选择复制');
  }
};

const formatDateTime = (value?: string) => {
  if (!value) return '—';
  const time = dayjs(value);
  return time.isValid() ? time.format('YYYY-MM-DD HH:mm') : String(value);
};

const formatDuration = (minutes: number) => {
  if (minutes < 60) return `${Math.max(minutes, 1)} 分钟`;
  if (minutes < 24 * 60) return `${Math.floor(minutes / 60)} 小时`;
  return `${Math.floor(minutes / (24 * 60))} 天`;
};

/** 收集截止的相对提示：仅进行中的购物车需要提示，已提交/关闭/完成只看日期 */
const expiryState = (row: any): { hint: string; tone: string } => {
  const value = row?.expiresAt;
  if (!value || ['4', '5', '6'].includes(row?.status)) {
    return { hint: '', tone: 'muted' };
  }
  const target = dayjs(value);
  if (!target.isValid()) return { hint: '', tone: 'muted' };
  const minutes = target.diff(dayjs(), 'minute');
  if (minutes <= 0) {
    return { hint: `已逾期 ${formatDuration(-minutes)}`, tone: 'danger' };
  }
  return {
    hint: `剩 ${formatDuration(minutes)}`,
    tone: minutes < 24 * 60 ? 'warning' : 'muted',
  };
};

const splitVesselCall = (row: any) => {
  const text = row?.vesselCallText ?? row?.vesselCallId ?? '';
  if (!text) return { main: '—', sub: '' };
  const [main = '', ...rest] = String(text).split(' · ');
  return { main, sub: rest.join(' · ') };
};

const ownerInitial = (name?: string) => name?.trim()?.charAt(0) || '?';

const avatarTone = (name?: string) => {
  let sum = 0;
  for (const char of name ?? '') sum += char.codePointAt(0) ?? 0;
  return `sc-avatar--${sum % 4}`;
};

const isQtyDiff = (row: any) =>
  Number(row?.approvedQuantity ?? 0) !== Number(row?.requestedQuantity ?? 0);

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

const handleSearch = () => {
  state.page.currentPage = 1;
  initPage();
};

const resetQuery = () => {
  state.query = { cartNo: '', status: '' };
  handleSearch();
};

const openDetail = (row: any) => {
  // 先用列表行填充弹窗头部，详情返回后用实体字段覆盖；列表行独有的
  // 靠港快照/发起人昵称/订单号没有在详情接口回填，保留下来供摘要展示
  detail.value = { cart: { ...row }, items: [], members: [] };
  detailTab.value = 'members';
  detailVisible.value = true;
  detailLoading.value = true;
  getSharedCartDetail(row.id)
    .then((data) => {
      detail.value = {
        ...data,
        cart: { ...row, ...data.cart },
      };
    })
    .catch(() => {
      detailVisible.value = false;
    })
    .finally(() => {
      detailLoading.value = false;
    });
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
      <header class="sc-hero">
        <div class="sc-hero-main">
          <span class="sc-hero-icon">
            <ElIcon :size="22"><ShoppingCart /></ElIcon>
          </span>
          <div class="sc-hero-copy">
            <div class="sc-hero-title">
              共享购物车
              <span class="sc-hero-count">共 {{ state.page.total }} 个</span>
            </div>
            <div class="sc-hero-sub">
              同船海员合并采购的实时清单；提交后自动生成整船订单，可在此跟进成员与明细。
            </div>
          </div>
        </div>
        <div class="sc-hero-actions">
          <ElTooltip content="刷新列表" placement="top">
            <ElButton
              :icon="Refresh"
              circle
              :loading="loading"
              @click="initPage"
            />
          </ElTooltip>
        </div>
      </header>

      <section class="sc-filter">
        <ElForm :inline="true" :model="state.query" @submit.prevent>
          <ElFormItem label="购物车编号">
            <ElInput
              v-model="state.query.cartNo"
              clearable
              placeholder="搜索编号，如 SC2118..."
              style="width: 240px"
              @clear="handleSearch"
              @keyup.enter="handleSearch"
            />
          </ElFormItem>
          <ElFormItem label="状态">
            <ElSelect
              v-model="state.query.status"
              clearable
              placeholder="全部状态"
              style="width: 160px"
              @change="handleSearch"
            >
              <ElOption
                v-for="(label, value) in statusLabel"
                :key="value"
                :label="label"
                :value="value"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem>
            <ElButton type="primary" :icon="Search" @click="handleSearch">
              搜索
            </ElButton>
            <ElButton :icon="RefreshLeft" @click="resetQuery">重置</ElButton>
          </ElFormItem>
        </ElForm>
      </section>

      <section class="sc-list">
        <div class="sc-list-head">
          <span class="sc-list-title">购物车列表</span>
          <span class="sc-list-tip">按创建时间倒序</span>
        </div>

        <ElTable
          v-loading="loading"
          class="sc-table"
          :data="state.tableData"
          row-key="id"
        >
          <ElTableColumn label="编号" min-width="200">
            <template #default="scope">
              <span class="sc-no">
                <span class="sc-mono">{{ scope.row.cartNo }}</span>
                <ElTooltip content="复制编号" placement="top">
                  <ElButton
                    class="sc-copy"
                    link
                    :icon="CopyDocument"
                    @click.stop="copyText(scope.row.cartNo, '购物车编号')"
                  />
                </ElTooltip>
              </span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="靠港计划" min-width="220">
            <template #default="scope">
              <!-- 回填船名 · 港口 泊位；远程域不可用时回落靠港 ID，不留空白 -->
              <div
                class="sc-vessel"
                :title="scope.row.vesselCallText ?? scope.row.vesselCallId"
              >
                <span class="sc-vessel-main">
                  <ElIcon class="sc-vessel-icon" :size="14"><Ship /></ElIcon>
                  {{ splitVesselCall(scope.row).main }}
                </span>
                <span
                  v-if="splitVesselCall(scope.row).sub"
                  class="sc-vessel-sub"
                >
                  {{ splitVesselCall(scope.row).sub }}
                </span>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn label="发起人" min-width="150">
            <template #default="scope">
              <!-- 服务端已按「昵称 → 用户+ID后6位」兜底（与详情页同一口径） -->
              <div v-if="scope.row.ownerName" class="sc-person">
                <span
                  class="sc-avatar"
                  :class="avatarTone(scope.row.ownerName)"
                >
                  {{ ownerInitial(scope.row.ownerName) }}
                </span>
                <span class="sc-person-name">{{ scope.row.ownerName }}</span>
              </div>
              <span v-else class="sc-muted">—</span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="状态" width="136">
            <template #default="scope">
              <ElTag
                round
                :effect="statusOf(scope.row.status).effect"
                :type="statusOf(scope.row.status).type"
              >
                <span class="sc-status">
                  <span class="sc-status-dot"></span>
                  {{ statusOf(scope.row.status).label }}
                </span>
              </ElTag>
            </template>
          </ElTableColumn>
          <ElTableColumn label="收集截止" min-width="170">
            <template #default="scope">
              <div class="sc-expiry">
                <span class="sc-expiry-main">
                  {{ formatDateTime(scope.row.expiresAt) }}
                </span>
                <span
                  v-if="expiryState(scope.row).hint"
                  class="sc-expiry-sub"
                  :class="`is-${expiryState(scope.row).tone}`"
                >
                  {{ expiryState(scope.row).hint }}
                </span>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn label="生成订单" min-width="200">
            <template #default="scope">
              <!-- 展示订单号：运营在订单模块按订单号检索，主键搜不到 -->
              <span v-if="scope.row.submitOrderNo" class="sc-no">
                <span class="sc-mono">{{ scope.row.submitOrderNo }}</span>
                <ElTooltip content="复制订单号" placement="top">
                  <ElButton
                    class="sc-copy"
                    link
                    :icon="CopyDocument"
                    @click.stop="copyText(scope.row.submitOrderNo, '订单号')"
                  />
                </ElTooltip>
              </span>
              <span v-else class="sc-muted">—</span>
            </template>
          </ElTableColumn>
          <ElTableColumn label="操作" width="90" align="center" fixed="right">
            <template #default="scope">
              <ElButton
                v-access:code="'sharedcart:get'"
                link
                type="primary"
                @click="openDetail(scope.row)"
              >
                详情
              </ElButton>
            </template>
          </ElTableColumn>
          <template #empty>
            <ElEmpty
              :image-size="80"
              :description="
                hasQuery ? '没有符合筛选条件的购物车' : '暂无共享购物车'
              "
            >
              <ElButton v-if="hasQuery" :icon="RefreshLeft" @click="resetQuery">
                清空筛选
              </ElButton>
            </ElEmpty>
          </template>
        </ElTable>

        <Pagination
          v-model:current="state.page.currentPage"
          v-model:size="state.page.pageSize"
          :total="state.page.total"
          @change="initPage"
        />
      </section>

      <ElDialog
        v-model="detailVisible"
        class="sc-detail-dialog"
        :close-on-click-modal="false"
        top="5vh"
        width="980px"
      >
        <template #header>
          <div class="sc-dialog-head">
            <div class="sc-dialog-heading">
              <span class="sc-dialog-title">共享购物车详情</span>
              <div class="sc-dialog-sub">
                <span class="sc-dialog-no sc-mono">
                  {{ detail.cart.cartNo ?? '—' }}
                </span>
                <ElTag
                  round
                  size="small"
                  :effect="statusOf(detail.cart.status).effect"
                  :type="statusOf(detail.cart.status).type"
                >
                  <span class="sc-status">
                    <span class="sc-status-dot"></span>
                    {{ statusOf(detail.cart.status).label }}
                  </span>
                </ElTag>
                <ElTooltip
                  v-if="detail.cart.cartNo"
                  content="复制购物车编号"
                  placement="top"
                >
                  <ElButton
                    class="sc-copy is-visible"
                    link
                    :icon="CopyDocument"
                    @click="copyText(detail.cart.cartNo, '购物车编号')"
                  />
                </ElTooltip>
              </div>
            </div>
          </div>
        </template>

        <div v-loading="detailLoading" class="sc-detail-body">
          <div class="sc-detail-meta">
            <div class="sc-meta-item">
              <span class="sc-meta-label">靠港计划</span>
              <span
                class="sc-meta-value"
                :title="detail.cart.vesselCallText ?? ''"
              >
                <ElIcon class="sc-vessel-icon" :size="14"><Ship /></ElIcon>
                <span class="sc-meta-text">{{ detailVessel.main }}</span>
                <span v-if="detailVessel.sub" class="sc-meta-extra">
                  {{ detailVessel.sub }}
                </span>
              </span>
            </div>
            <div class="sc-meta-item">
              <span class="sc-meta-label">发起人</span>
              <span class="sc-meta-value">
                <template v-if="detail.cart.ownerName">
                  <span
                    class="sc-avatar is-small"
                    :class="avatarTone(detail.cart.ownerName)"
                  >
                    {{ ownerInitial(detail.cart.ownerName) }}
                  </span>
                  <span class="sc-meta-text">{{ detail.cart.ownerName }}</span>
                </template>
                <span v-else class="sc-muted">—</span>
              </span>
            </div>
            <div class="sc-meta-item">
              <span class="sc-meta-label">收集截止</span>
              <span class="sc-meta-value">
                <span class="sc-expiry-main">
                  {{ formatDateTime(detail.cart.expiresAt) }}
                </span>
                <span
                  v-if="expiryState(detail.cart).hint"
                  class="sc-expiry-sub"
                  :class="`is-${expiryState(detail.cart).tone}`"
                >
                  {{ expiryState(detail.cart).hint }}
                </span>
              </span>
            </div>
            <div class="sc-meta-item">
              <span class="sc-meta-label">生成订单</span>
              <span class="sc-meta-value">
                <template v-if="detail.cart.submitOrderNo">
                  <span class="sc-mono">{{ detail.cart.submitOrderNo }}</span>
                  <ElTooltip content="复制订单号" placement="top">
                    <ElButton
                      class="sc-copy is-visible"
                      link
                      :icon="CopyDocument"
                      @click="copyText(detail.cart.submitOrderNo, '订单号')"
                    />
                  </ElTooltip>
                </template>
                <span v-else class="sc-muted">未生成订单</span>
              </span>
            </div>
          </div>

          <div class="sc-stat-grid">
            <div class="sc-stat">
              <span class="sc-stat-label">成员</span>
              <span class="sc-stat-value">
                {{ members.length }}<span class="sc-stat-unit">人</span>
              </span>
            </div>
            <div class="sc-stat is-success">
              <span class="sc-stat-label">明细项</span>
              <span class="sc-stat-value">
                {{ items.length }}<span class="sc-stat-unit">项</span>
              </span>
              <span v-if="removedItemCount > 0" class="sc-stat-sub">
                含已移除 {{ removedItemCount }} 项
              </span>
            </div>
            <div class="sc-stat is-warning">
              <span class="sc-stat-label">申请总量</span>
              <span class="sc-stat-value">
                {{ requestedTotal }}<span class="sc-stat-unit">件</span>
              </span>
            </div>
            <div class="sc-stat is-primary">
              <span class="sc-stat-label">核定总量</span>
              <span class="sc-stat-value">
                {{ approvedTotal }}<span class="sc-stat-unit">件</span>
              </span>
              <span v-if="approvedTotal !== requestedTotal" class="sc-stat-sub">
                较申请
                {{ approvedTotal > requestedTotal ? '+' : ''
                }}{{ approvedTotal - requestedTotal }} 件
              </span>
            </div>
          </div>

          <ElTabs v-model="detailTab" class="sc-tabs">
            <ElTabPane name="members">
              <template #label>
                <span class="sc-tab-label">
                  成员
                  <span class="sc-tab-badge">{{ members.length }}</span>
                </span>
              </template>
              <ElTable
                class="sc-table"
                :data="members"
                row-key="id"
                max-height="360"
              >
                <ElTableColumn label="用户" min-width="220">
                  <template #default="scope">
                    <div class="sc-person">
                      <span
                        class="sc-avatar"
                        :class="avatarTone(scope.row.displayName)"
                      >
                        {{ ownerInitial(scope.row.displayName) }}
                      </span>
                      <span class="sc-person-name">
                        {{ scope.row.displayName ?? scope.row.userId }}
                      </span>
                    </div>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="角色" width="110">
                  <template #default="scope">
                    <ElTag
                      round
                      size="small"
                      :type="memberRoleOf(scope.row.memberRole).type"
                    >
                      {{ memberRoleOf(scope.row.memberRole).label }}
                    </ElTag>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="可确认" width="100" align="center">
                  <template #default="scope">
                    <span
                      :class="
                        scope.row.canConfirm === '1' ? 'sc-yes' : 'sc-no-text'
                      "
                    >
                      {{ scope.row.canConfirm === '1' ? '可确认' : '不可' }}
                    </span>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="可加购" width="130">
                  <template #default="scope">
                    <!--
                      发起人始终可维护（服务端 requireCanEdit 直接放行），开关对他无效，
                      因此只读展示；给非发起人可切换的开关。
                    -->
                    <span v-if="scope.row.memberRole === '1'" class="sc-muted">
                      始终可加购
                    </span>
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
                <ElTableColumn label="加入时间" width="180">
                  <template #default="scope">
                    {{ formatDateTime(scope.row.joinedTime) }}
                  </template>
                </ElTableColumn>
                <template #empty>
                  <ElEmpty :image-size="72" description="暂无成员" />
                </template>
              </ElTable>
            </ElTabPane>

            <ElTabPane name="items">
              <template #label>
                <span class="sc-tab-label">
                  明细
                  <span class="sc-tab-badge">{{ items.length }}</span>
                </span>
              </template>
              <ElTable
                class="sc-table"
                :data="items"
                row-key="id"
                max-height="360"
              >
                <ElTableColumn label="商品" min-width="260">
                  <template #default="scope">
                    <div class="sc-goods">
                      <span
                        class="sc-goods-name"
                        :class="{ 'is-removed': scope.row.status === '3' }"
                      >
                        {{ scope.row.spuName ?? '—' }}
                      </span>
                      <span v-if="scope.row.specText" class="sc-goods-spec">
                        {{ scope.row.specText }}
                      </span>
                    </div>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="申请数量" width="100" align="center">
                  <template #default="scope">
                    <span class="sc-qty">
                      {{ scope.row.requestedQuantity ?? 0 }}
                    </span>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="核定数量" width="100" align="center">
                  <template #default="scope">
                    <span
                      class="sc-qty"
                      :class="{ 'is-diff': isQtyDiff(scope.row) }"
                    >
                      {{ scope.row.approvedQuantity ?? 0 }}
                    </span>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="成员备注" min-width="160">
                  <template #default="scope">
                    <span v-if="scope.row.memberRemark">
                      {{ scope.row.memberRemark }}
                    </span>
                    <span v-else class="sc-muted">—</span>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="状态" width="100">
                  <template #default="scope">
                    <ElTag
                      round
                      size="small"
                      :type="itemStatusOf(scope.row.status).type"
                    >
                      {{ itemStatusOf(scope.row.status).label }}
                    </ElTag>
                  </template>
                </ElTableColumn>
                <ElTableColumn label="来源成员" min-width="140">
                  <template #default="scope">
                    <span v-if="scope.row.contributorName">
                      {{ scope.row.contributorName }}
                    </span>
                    <span v-else class="sc-muted">—</span>
                  </template>
                </ElTableColumn>
                <template #empty>
                  <ElEmpty :image-size="72" description="暂无明细" />
                </template>
              </ElTable>
            </ElTabPane>
          </ElTabs>
        </div>

        <template #footer>
          <ElButton @click="detailVisible = false">关闭</ElButton>
        </template>
      </ElDialog>
    </div>
  </div>
</template>

<style scoped>
/* ---------- 页头 ---------- */
.sc-hero {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  margin-bottom: 12px;
  background:
    radial-gradient(
      circle at 92% 8%,
      var(--el-color-primary-light-8),
      transparent 45%
    ),
    linear-gradient(
      120deg,
      var(--el-color-primary-light-9),
      var(--el-bg-color) 62%
    );
  border: 1px solid var(--el-color-primary-light-8);
  border-radius: 12px;
}

.sc-hero-main {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
}

.sc-hero-icon {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  color: #fff;
  background: linear-gradient(
    135deg,
    var(--el-color-primary),
    var(--el-color-primary-light-3)
  );
  border-radius: 12px;
  box-shadow: 0 8px 18px var(--el-color-primary-light-7);
}

.sc-hero-copy {
  min-width: 0;
}

.sc-hero-title {
  display: flex;
  gap: 10px;
  align-items: center;
  font-size: 17px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.sc-hero-count {
  padding: 1px 8px;
  font-size: 12px;
  font-weight: 500;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-8);
  border-radius: 999px;
}

.sc-hero-sub {
  margin-top: 3px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.sc-hero-actions {
  display: flex;
  flex: none;
  gap: 8px;
  align-items: center;
}

/* ---------- 查询区 ---------- */
.sc-filter {
  padding: 14px 16px 0;
  margin-bottom: 12px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}

.sc-filter :deep(.el-form-item) {
  margin-bottom: 14px;
}

/* ---------- 列表卡片 ---------- */
.sc-list {
  padding: 14px 16px 6px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
}

.sc-list-head {
  display: flex;
  gap: 12px;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 10px;
}

.sc-list-title {
  position: relative;
  padding-left: 10px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.sc-list-title::before {
  position: absolute;
  top: 50%;
  left: 0;
  width: 3px;
  height: 14px;
  content: '';
  background: var(--el-color-primary);
  border-radius: 2px;
  transform: translateY(-50%);
}

.sc-list-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* ---------- 表格 ---------- */
.sc-table {
  --el-table-border-color: var(--el-border-color-lighter);
}

.sc-table :deep(th.el-table__cell) {
  height: 42px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
}

.sc-table :deep(.el-table__body tr:hover > td.el-table__cell) {
  background: var(--el-color-primary-light-9);
}

.sc-no {
  display: inline-flex;
  gap: 2px;
  align-items: center;
}

.sc-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.2px;
}

.sc-copy {
  width: 20px;
  height: 20px;
  opacity: 0;
  transition: opacity 0.15s;
}

.sc-copy.is-visible,
.sc-table :deep(.el-table__row:hover .sc-copy),
.sc-copy:focus-visible {
  opacity: 1;
}

.sc-vessel {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  line-height: 1.4;
}

.sc-vessel-main {
  display: flex;
  gap: 6px;
  align-items: center;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 500;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.sc-vessel-icon {
  flex: none;
  color: var(--el-color-primary);
}

.sc-vessel-sub {
  padding-left: 20px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.sc-person {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.sc-person-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sc-avatar {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  font-size: 12px;
  font-weight: 600;
  border-radius: 50%;
}

.sc-avatar.is-small {
  width: 22px;
  height: 22px;
  font-size: 11px;
}

.sc-avatar--0 {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.sc-avatar--1 {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.sc-avatar--2 {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.sc-avatar--3 {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}

.sc-status {
  display: inline-flex;
  gap: 5px;
  align-items: center;
}

.sc-status-dot {
  width: 6px;
  height: 6px;
  background: currentcolor;
  border-radius: 50%;
}

.sc-expiry {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.4;
}

.sc-expiry-main {
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-regular);
}

.sc-expiry-sub {
  font-size: 12px;
}

.sc-expiry-sub.is-muted {
  color: var(--el-text-color-secondary);
}

.sc-expiry-sub.is-warning {
  color: var(--el-color-warning);
}

.sc-expiry-sub.is-danger {
  color: var(--el-color-danger);
}

.sc-muted {
  color: var(--el-text-color-placeholder);
}

/* ---------- 详情弹窗 ---------- */
.sc-detail-dialog :deep(.el-dialog__body) {
  max-height: calc(100vh - 210px);
  padding-top: 4px;
  overflow-y: auto;
}

.sc-dialog-heading {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sc-dialog-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.sc-dialog-sub {
  display: flex;
  gap: 8px;
  align-items: center;
}

.sc-dialog-no {
  color: var(--el-text-color-secondary);
}

.sc-detail-meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 12px 16px;
  padding: 14px 16px;
  margin-bottom: 12px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.sc-meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.sc-meta-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.sc-meta-value {
  display: flex;
  gap: 6px;
  align-items: center;
  min-width: 0;
  overflow: hidden;
  font-size: 13px;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.sc-meta-text {
  overflow: hidden;
  text-overflow: ellipsis;
}

.sc-meta-extra {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.sc-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.sc-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 14px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-left: 3px solid var(--el-color-info);
  border-radius: 10px;
}

.sc-stat.is-primary {
  border-left-color: var(--el-color-primary);
}

.sc-stat.is-success {
  border-left-color: var(--el-color-success);
}

.sc-stat.is-warning {
  border-left-color: var(--el-color-warning);
}

.sc-stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.sc-stat-value {
  font-size: 20px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1.25;
  color: var(--el-text-color-primary);
}

.sc-stat-unit {
  margin-left: 3px;
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.sc-stat-sub {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
}

.sc-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.sc-tab-label {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.sc-tab-badge {
  padding: 0 6px;
  font-size: 11px;
  line-height: 16px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color);
  border-radius: 999px;
}

.sc-goods {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.4;
}

.sc-goods-name {
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.sc-goods-name.is-removed {
  color: var(--el-text-color-secondary);
  text-decoration: line-through;
}

.sc-goods-spec {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.sc-qty {
  font-variant-numeric: tabular-nums;
}

.sc-qty.is-diff {
  font-weight: 600;
  color: var(--el-color-warning);
}

.sc-yes {
  color: var(--el-color-success);
}

.sc-no-text {
  color: var(--el-text-color-placeholder);
}
</style>
