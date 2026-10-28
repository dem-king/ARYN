<script lang="ts" setup>
/**
 * 船舶绑定申请审核。
 *
 * 业务背景：销售业务员地推时经常遇到「船还没录入系统」的情况，
 * 而用户比运营更早接触到船。因此审核通过时支持三种落地方式：
 *   1. 选择系统内已有船舶；
 *   2. 填写新船名称——服务端先建船再绑定（覆盖上述场景）；
 *   3. 都不填——服务端按申请的 IMO/呼号、船名依次匹配在营船舶，匹配不到会返回可执行的提示。
 *
 * 审核员的核心困惑是「我不知道这条船在不在系统里」：打开弹窗时即调用预检接口，
 * 把「同 IMO/呼号」「同名」「相近船名」「无匹配」的结论摆在最上面并预选审核方式，
 * 审核员只需确认或推翻，不必靠提交失败来试。IMO/呼号是船的终身唯一标识，
 * 是「认船不认名」的强键——船名写歪时只有它能认出同一条船。
 */
import {
  computed,
  defineAsyncComponent,
  onMounted,
  reactive,
  ref,
  watch,
} from 'vue';

import {
  ElAlert,
  ElButton,
  ElCheckbox,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import {
  approveBindApply,
  getBindApplyPage,
  getBindApplyPrecheck,
  getBindApplyVesselOptions,
  rejectBindApply,
  unbindVesselMemberByApply,
} from '#/api/vessel/vessel';
import PageFilterPanel from '#/components/page/page-filter-panel.vue';
import { useKeepAliveRefresh } from '#/composables/use-keep-alive-refresh';

import {
  isSameVesselIdentity,
  VESSEL_TYPE_DEFAULT,
  VESSEL_TYPE_OPTIONS,
} from '../constants';

const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const state = reactive({
  page: { total: 0, currentPage: 1, pageSize: 10 },
  query: { status: '1', vesselName: '' },
  tableData: [] as any[],
});
const loading = ref(false);
const showSearch = ref(true);

const statusLabel: Record<string, string> = {
  '1': '待审核',
  '2': '已通过',
  '3': '已驳回',
  '4': '已撤回',
};
const statusTag: Record<string, string> = {
  '1': 'warning',
  '2': 'success',
  '3': 'danger',
  '4': 'info',
};
const roleLabel: Record<string, string> = {
  '2': '普通船员',
  '4': '业务员',
};

const initPage = async () => {
  loading.value = true;
  try {
    const response = await getBindApplyPage({
      current: state.page.currentPage,
      size: state.page.pageSize,
      status: state.query.status,
      vesselName: state.query.vesselName,
    });
    state.tableData = response.records;
    state.page.total = response.total;
  } finally {
    loading.value = false;
  }
};

// ---------------------------------------------------------------------------
// 审核通过
// ---------------------------------------------------------------------------

const approveVisible = ref(false);
const approving = ref(false);
const prechecking = ref(false);
const currentApply = ref<any>({});
/** existing=选已有船 new=建新船 auto=按 IMO/呼号与船名自动匹配 */
const approveMode = ref('auto');
const approveForm = reactive<any>({
  matchedVesselId: '',
  newVesselName: '',
  newVesselImo: '',
  newVesselCallSign: '',
  newVesselType: VESSEL_TYPE_DEFAULT,
  memberRole: '',
  auditRemark: '',
  confirmDuplicateName: false,
  // 申请船与所选船身份键全不命中时的显式背书位；换船 / 换审核方式后由 watch 重置
  confirmVesselMismatch: false,
});
const vesselOptions = ref<any[]>([]);
/** 预检结果：打开弹窗时告诉审核员「申请里的船在不在系统内」 */
const precheck = ref<any>(null);
/** 新建分支命中的同名船只（服务端也会拦，这里提前给出确认入口） */
const duplicateNameHit = ref<any[]>([]);

/** 最近一次选中的船舶对象；远程检索整体替换列表时用它兜住选中项 */
const selectedVesselCache = ref<any>(null);

/**
 * 当前选中的船舶对象。优先在候选列表里找；预检预选的船已补进 vesselOptions，
 * 因此正常路径都能解析到。找不到返回 null —— 由 vesselMismatch 统一按「不一致」处理。
 *
 * 远程检索会整体替换 `vesselOptions`，选中项可能暂时不在列表里；若此时直接
 * 回落到 null，一致性判定会误报「不一致」。缓存最近一次选中项兜住这个窗口。
 */
const selectedVessel = computed<any>(() => {
  const id = approveForm.matchedVesselId;
  if (!id) {
    return null;
  }
  return (
    vesselOptions.value.find((item: any) => item.id === id) ??
    (selectedVesselCache.value?.id === id ? selectedVesselCache.value : null)
  );
});

/** 从当前选项里记住选中项（选中瞬间它一定在列表里） */
const rememberSelectedVessel = () => {
  const found = vesselOptions.value.find(
    (item: any) => item.id === approveForm.matchedVesselId,
  );
  if (found) {
    selectedVesselCache.value = found;
  }
};

// 选中项变化即刷新缓存，并作废上一次的「确认绑定到该船」。
// 同一处还负责清「确认绑定到该船」：运营在 8 号上勾了确认、又改选 7 号时，
// 勾选若跟着带过去，等于确认过的不是这条船——拦截会被绕过。
watch(
  () => approveForm.matchedVesselId,
  (value, previous) => {
    if (value !== previous) {
      approveForm.confirmVesselMismatch = false;
    }
    rememberSelectedVessel();
  },
);

// 换审核方式即作废上一次的「确认绑定到该船」：new/auto 分支没有这次确认的语义
watch(approveMode, (mode, previous) => {
  if (mode !== previous) {
    approveForm.confirmVesselMismatch = false;
  }
});

/**
 * 申请船与所选船是否不一致（仅 existing 模式且已选船时有意义）。
 *
 * 判据与后端 `resolveVesselForApprove` 完全同源（`isSameVesselIdentity`）：
 * IMO 相等 || 呼号相等 || 归一化船名相等 → 一致。
 * 选了船、但船对象解析不到时**按不一致处理**（保守）：信息不足就不敢放行，
 * 宁可让运营多勾一次确认，也不能把「悦航8号」静默绑到「悦航7号」。
 */
const vesselMismatch = computed(() => {
  if (approveMode.value !== 'existing' || !approveForm.matchedVesselId) {
    return false;
  }
  return !isSameVesselIdentity(currentApply.value, selectedVessel.value);
});

/** 不一致提示里的两个船名，取界面上的真实文案 */
const mismatchApplyName = computed(
  () => currentApply.value.applyVesselName || '未填写',
);
const mismatchTargetName = computed(
  () =>
    selectedVessel.value?.vesselName ?? approveForm.matchedVesselId ?? '未选择',
);

const optionLabel = (vessel: any) => {
  const identity = [vessel.imoCode, vessel.callSign]
    .filter(Boolean)
    .join(' / ');
  return identity ? `${vessel.vesselName}（${identity}）` : vessel.vesselName;
};

const candidateTagType = (reason?: string) => {
  if (reason === 'IMO一致' || reason === '呼号一致') return 'success';
  if (reason === '船名相同') return 'warning';
  return 'info';
};

/**
 * 选中候选船：切到「绑定已有船舶」并预填，避免审核员再手动搜一遍。
 *
 * 直接赋值即可：确认位重置与选中项缓存都收敛在下方两个 watch 里，
 * 下拉 v-model 与候选点选走同一条路径，不会出现「一处重置、一处没重置」。
 */
const pickCandidate = (vessel: any) => {
  approveMode.value = 'existing';
  approveForm.matchedVesselId = vessel.id;
};

const openApprove = async (row: any) => {
  currentApply.value = row;
  precheck.value = null;
  duplicateNameHit.value = [];
  // 申请单已把 IMO 与呼号拆成两个字段，各自原样预填。
  // 这里不再按「7 位纯数字猜 IMO」：那条启发式会把 7 位呼号错落进 IMO 列，
  // 而 IMO 是唯一键级的认船强键，猜错的代价是建出错误档案。
  Object.assign(approveForm, {
    matchedVesselId: '',
    newVesselName: row.applyVesselName ?? '',
    newVesselImo: row.applyVesselImo ?? '',
    newVesselCallSign: row.applyVesselCallSign ?? '',
    newVesselType: VESSEL_TYPE_DEFAULT,
    memberRole: row.applyRole ?? '2',
    auditRemark: '',
    confirmDuplicateName: false,
    confirmVesselMismatch: false,
  });
  // 未选出建议前先默认自动匹配（最坏结果只是报错重来，不会静默绑错）
  approveMode.value = 'auto';
  approveVisible.value = true;

  // 预检与候选列表并发：预检失败不阻断审核，退化为原来的手动选择
  prechecking.value = true;
  try {
    const [precheckResult, options] = await Promise.all([
      getBindApplyPrecheck(row.id),
      getBindApplyVesselOptions(),
    ]);
    precheck.value = precheckResult ?? null;
    vesselOptions.value = options ?? [];
    // 预选的船若不在默认候选里，下拉会显示成裸 ID：把预检命中的船补进选项，
    // 保证 select 永远能解析出船名
    for (const candidate of precheckResult?.candidates ?? []) {
      if (
        candidate.id &&
        !vesselOptions.value.some((item: any) => item.id === candidate.id)
      ) {
        vesselOptions.value = [candidate, ...vesselOptions.value];
      }
    }
    if (precheckResult?.suggestedMode) {
      approveMode.value = precheckResult.suggestedMode;
      if (precheckResult.suggestedVesselId) {
        approveForm.matchedVesselId = precheckResult.suggestedVesselId;
      }
    }
  } catch {
    vesselOptions.value = [];
  } finally {
    prechecking.value = false;
  }
};

/** 候选下拉的远程检索：船多的租户不必在写死的 200 条里翻找 */
const searchVesselOptions = async (keyword: string) => {
  try {
    vesselOptions.value = (await getBindApplyVesselOptions(keyword)) ?? [];
  } catch {
    vesselOptions.value = [];
  }
};

/**
 * 核同名。resetConfirm=true 表示船名刚变过，上一次的重名确认不该继续算数
 * （否则改了名也能带着旧勾选提交，等于没拦）。
 */
const checkDuplicateName = async (resetConfirm = true) => {
  const name = (approveForm.newVesselName ?? '').trim();
  if (approveMode.value !== 'new' || !name) {
    duplicateNameHit.value = [];
    return;
  }
  if (resetConfirm) {
    approveForm.confirmDuplicateName = false;
  }
  try {
    const options = await getBindApplyVesselOptions(name);
    // 候选接口是模糊匹配，这里只认完全同名的
    duplicateNameHit.value = (options ?? []).filter(
      (v) => v.vesselName === name,
    );
  } catch {
    duplicateNameHit.value = [];
  }
};

const submitApprove = async () => {
  const payload: any = {
    auditRemark: approveForm.auditRemark,
    memberRole: approveForm.memberRole,
  };
  if (approveMode.value === 'existing') {
    if (!approveForm.matchedVesselId) {
      ElMessage.warning('请选择要绑定的船舶');
      return;
    }
    // 跨船绑定拦截：申请船与所选船身份键（IMO/呼号/归一化船名）全不命中时，
    // 必须显式勾选确认 + 填写审核意见。前端拦一道是为了「提交前就拦住」——
    // 服务端也会拦，但只让运营在点「确认通过」后吃一句报错，等于把拦截做成了惊吓。
    if (vesselMismatch.value) {
      if (!approveForm.confirmVesselMismatch) {
        ElMessage.warning(
          `申请船名「${mismatchApplyName.value}」与所选船舶「${mismatchTargetName.value}」不一致，请先勾选「确认绑定到该船」`,
        );
        return;
      }
      if (!(approveForm.auditRemark ?? '').trim()) {
        ElMessage.warning(
          '申请船名与所选船舶不一致时，必须填写审核意见说明原因',
        );
        return;
      }
    }
    // 无论一致与否都带上确认位：服务端按它决定成员来源
    // （APPLY_AUDIT / APPLY_AUDIT_MISMATCH），缺省等于「未确认」，不一致时会被拒绝
    payload.confirmVesselMismatch = approveForm.confirmVesselMismatch;
    payload.matchedVesselId = approveForm.matchedVesselId;
  } else if (approveMode.value === 'new') {
    if (!approveForm.newVesselName) {
      ElMessage.warning('请填写新船名称');
      return;
    }
    // 提交前再核一次同名：船名是申请单预填的，审核员可能没碰过输入框、
    // 没触发 blur，此时服务端会拦「需勾选确认」但勾选框还没渲染出来，
    // 审核员会被一句「请勾选」卡死。这里先查出来把勾选框亮出来。
    await checkDuplicateName(false);
    if (
      duplicateNameHit.value.length > 0 &&
      !approveForm.confirmDuplicateName
    ) {
      ElMessage.warning(
        '系统内已存在同名在营船舶，请确认是另一条船后勾选确认，或改用「绑定已有船舶」',
      );
      return;
    }
    payload.newVesselName = approveForm.newVesselName;
    payload.newVesselImo = approveForm.newVesselImo;
    payload.newVesselCallSign = approveForm.newVesselCallSign;
    payload.newVesselType = approveForm.newVesselType;
    payload.confirmDuplicateName = approveForm.confirmDuplicateName;
  }
  // auto 模式两个字段都不传，由服务端按 IMO/呼号与船名匹配

  approving.value = true;
  approveBindApply(currentApply.value.id, payload)
    .then(() => {
      ElMessage.success('审核通过');
      approveVisible.value = false;
      initPage();
    })
    .catch(() => {})
    .finally(() => {
      approving.value = false;
    });
};

// ---------------------------------------------------------------------------
// 驳回
// ---------------------------------------------------------------------------

const submitReject = (row: any) => {
  ElMessageBox.prompt('驳回后申请人可重新提交，请填写驳回原因', '驳回申请', {
    confirmButtonText: '确认驳回',
    cancelButtonText: '取消',
    inputPlaceholder: '例如：船名无法核实，请补充 IMO 或呼号',
    inputValidator: (value: string) =>
      (value && value.trim().length > 0) || '驳回必须填写原因',
  })
    .then(({ value }) =>
      rejectBindApply(row.id, { auditRemark: value }).then(() => {
        ElMessage.success('已驳回');
        initPage();
      }),
    )
    .catch(() => {});
};

// ---------------------------------------------------------------------------
// 解绑（误绑纠错）
// ---------------------------------------------------------------------------

/**
 * 已通过的申请此前没有任何管理端退路：绑错了只能让用户自己去 C 端退出，
 * 而用户往往正是不会操作的那个人。这里给运营一个入口。
 */
const unbindSubmit = (row: any) => {
  if (!row.matchedVesselId) {
    ElMessage.warning('该申请没有绑定船舶，无需解绑');
    return;
  }
  ElMessageBox.prompt(
    `将把 ${row.userNickname ?? row.userId} 从「${row.matchedVesselName ?? row.matchedVesselId}」移出。原因会随站内信告知本人。`,
    '解绑成员',
    {
      confirmButtonText: '确认解绑',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：审核时绑错了船',
      inputValidator: (value: string) =>
        (value && value.trim().length > 0) || '解绑必须填写原因',
    },
  )
    .then(({ value }) =>
      unbindVesselMemberByApply({
        vesselId: row.matchedVesselId,
        userId: row.userId,
        reason: value,
      }).then(() => {
        ElMessage.success('已解绑');
        initPage();
      }),
    )
    .catch(() => {});
};

onMounted(initPage);

useKeepAliveRefresh(initPage);
</script>
<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索 -->
      <PageFilterPanel
        :model="state.query"
        :visible="showSearch"
        @reset="initPage"
        @search="initPage"
      >
        <ElFormItem label="状态" prop="status">
          <ElSelect v-model="state.query.status">
            <ElOption label="待审核" value="1" />
            <ElOption label="已通过" value="2" />
            <ElOption label="已驳回" value="3" />
            <ElOption label="已撤回" value="4" />
            <ElOption label="全部" value="" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="船舶名称" prop="vesselName">
          <ElInput
            v-model="state.query.vesselName"
            clearable
            placeholder="按申请填写船名搜索"
            @keyup.enter="initPage"
          />
        </ElFormItem>
      </PageFilterPanel>

      <ElTable
        v-loading="loading"
        class="hx-data-table"
        :data="state.tableData"
        row-key="id"
        border
        empty-text="暂无绑定申请"
      >
        <ElTableColumn prop="applyNo" label="申请单号" min-width="180" />
        <ElTableColumn label="申请人" min-width="150">
          <template #default="scope">
            <div>{{ scope.row.userNickname ?? scope.row.userId }}</div>
            <div class="apply-user-phone">
              {{ scope.row.userPhone ?? '—' }}
            </div>
          </template>
        </ElTableColumn>
        <ElTableColumn label="身份" width="100" align="center">
          <template #default="scope">
            {{ roleLabel[scope.row.applyRole] ?? scope.row.applyRole }}
          </template>
        </ElTableColumn>
        <ElTableColumn
          prop="applyVesselName"
          label="申请船名"
          min-width="130"
        />
        <ElTableColumn prop="applyVesselImo" label="IMO" width="120" />
        <ElTableColumn prop="applyVesselCallSign" label="呼号" width="110" />
        <ElTableColumn prop="applyPortName" label="常靠港" width="110" />
        <ElTableColumn prop="realName" label="姓名" width="100" />
        <ElTableColumn prop="phone" label="联系电话" width="130" />
        <ElTableColumn label="状态" width="90" align="center">
          <template #default="scope">
            <ElTag :type="(statusTag[scope.row.status] as any) ?? 'info'">
              {{ statusLabel[scope.row.status] ?? scope.row.status }}
            </ElTag>
          </template>
        </ElTableColumn>
        <!-- 绑定船舶：申请船与最终绑定船不一致时把两者都摆出来。
             此前这一列只显示 matchedVesselName，审核员看到「悦航7号」
             根本无从知道申请人填的是「悦航8号」——误绑就藏在这一格背后。 -->
        <ElTableColumn label="绑定船舶" min-width="200">
          <template #default="scope">
            <template v-if="scope.row.vesselMismatch">
              <div>
                <span class="mismatch-name">
                  {{ scope.row.applyVesselName ?? '—' }}
                </span>
                <span class="mismatch-arrow">→</span>
                <span class="mismatch-target">
                  {{ scope.row.matchedVesselName ?? '—' }}
                </span>
              </div>
              <ElTag size="small" type="warning" class="mt-1">
                船名不一致
              </ElTag>
            </template>
            <span v-else>{{ scope.row.matchedVesselName ?? '—' }}</span>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="auditRemark" label="审核意见" min-width="150" />
        <ElTableColumn prop="createTime" label="提交时间" width="170" />
        <ElTableColumn label="操作" width="150" align="center" fixed="right">
          <template #default="scope">
            <template v-if="scope.row.status === '1'">
              <ElButton
                v-access:code="'vessel:bindapply:audit'"
                link
                type="primary"
                @click="openApprove(scope.row)"
              >
                通过
              </ElButton>
              <ElButton
                v-access:code="'vessel:bindapply:audit'"
                link
                type="danger"
                @click="submitReject(scope.row)"
              >
                驳回
              </ElButton>
            </template>
            <!-- 已通过、且本人当前仍在船上才可解绑：
                 解绑后申请单仍是「已通过」，只按状态渲染会留下点了必报错的按钮 -->
            <ElButton
              v-else-if="scope.row.memberActive"
              v-access:code="'vessel:bindapply:audit'"
              link
              type="danger"
              @click="unbindSubmit(scope.row)"
            >
              解绑
            </ElButton>
            <span v-else class="apply-handled">
              {{ scope.row.status === '2' ? '已解绑' : '已处理' }}
            </span>
          </template>
        </ElTableColumn>
      </ElTable>

      <Pagination
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        :total="state.page.total"
        @change="initPage"
      />

      <!-- 审核通过 -->
      <ElDialog v-model="approveVisible" title="审核通过" width="680px">
        <ElAlert
          :title="`申请船名：${currentApply.applyVesselName ?? ''} 申请人：${currentApply.userNickname ?? currentApply.userId ?? ''}`"
          :closable="false"
          class="mb-3"
        />

        <!-- 预检结论：审核员最需要回答的问题是「这条船在不在系统里」 -->
        <ElAlert
          v-if="prechecking"
          title="正在核对该船是否已在系统内…"
          type="info"
          :closable="false"
          class="mb-3"
        />
        <template v-else-if="precheck">
          <ElAlert
            :title="precheck.suggestion"
            :type="precheck.suggestedMode === 'new' ? 'info' : 'success'"
            :closable="false"
            class="mb-3"
          />
          <div v-if="precheck.candidates?.length" class="mb-3">
            <div class="precheck-hint mb-1 text-xs">
              可能相关的在营船舶（点击即选中）
            </div>
            <div class="flex flex-wrap gap-2">
              <div
                v-for="candidate in precheck.candidates"
                :key="candidate.id"
                class="candidate-chip cursor-pointer rounded px-2 py-1"
                :class="{
                  'candidate-chip--active':
                    approveForm.matchedVesselId === candidate.id,
                }"
                @click="pickCandidate(candidate)"
              >
                <span class="text-sm font-medium">{{
                  candidate.vesselName
                }}</span>
                <span
                  v-if="candidate.imoCode || candidate.callSign"
                  class="candidate-identity ml-1 text-xs"
                >
                  {{
                    [candidate.imoCode, candidate.callSign]
                      .filter(Boolean)
                      .join(' / ')
                  }}
                </span>
                <ElTag
                  v-if="candidate.matchReason"
                  size="small"
                  :type="candidateTagType(candidate.matchReason) as any"
                  class="ml-1"
                >
                  {{ candidate.matchReason }}
                </ElTag>
              </div>
            </div>
          </div>
        </template>

        <ElRadioGroup v-model="approveMode" class="mb-3">
          <ElRadio value="existing">绑定已有船舶</ElRadio>
          <ElRadio value="new">新建船舶后绑定</ElRadio>
          <ElRadio value="auto">按船名自动匹配</ElRadio>
        </ElRadioGroup>

        <ElForm label-width="auto">
          <ElFormItem
            v-if="approveMode === 'existing'"
            label="选择船舶"
            required
          >
            <ElSelect
              v-model="approveForm.matchedVesselId"
              filterable
              remote
              :remote-method="searchVesselOptions"
              placeholder="输入船名/IMO/呼号搜索在营船舶"
              style="width: 100%"
            >
              <ElOption
                v-for="vessel in vesselOptions"
                :key="vessel.id"
                :label="optionLabel(vessel)"
                :value="vessel.id"
              />
            </ElSelect>
          </ElFormItem>

          <!-- 跨船绑定拦截：申请船与所选船身份键全不命中时，把两条船的名字
               并排摆出来，并要运营显式勾选确认 + 填写审核意见。
               此前这一步完全没有——「悦航8号」就这么静默绑到了「悦航7号」。 -->
          <ElFormItem v-if="approveMode === 'existing' && vesselMismatch">
            <ElAlert type="warning" :closable="false" show-icon class="w-full">
              <template #title>
                申请船名「{{ mismatchApplyName }}」与所选船舶「{{
                  mismatchTargetName
                }}」不一致。确认是同一条船才可继续，并请在审核意见中说明原因
              </template>
              <ElCheckbox
                v-model="approveForm.confirmVesselMismatch"
                class="mt-2"
              >
                确认绑定到该船（申请船名与所选船舶不一致）
              </ElCheckbox>
            </ElAlert>
          </ElFormItem>

          <template v-if="approveMode === 'new'">
            <ElFormItem label="新船名称" required>
              <ElInput
                v-model="approveForm.newVesselName"
                @blur="checkDuplicateName()"
              />
            </ElFormItem>
            <ElFormItem label="IMO 编号">
              <ElInput v-model="approveForm.newVesselImo" />
            </ElFormItem>
            <ElFormItem label="呼号">
              <ElInput v-model="approveForm.newVesselCallSign" />
            </ElFormItem>
            <ElFormItem label="船舶类型">
              <!-- 此前 newVesselType 写死 '5'：新建的船一律归「其他」，报表船型维度失真。
                   这里让审核员按申请信息选，缺省仍是「其他」。 -->
              <ElSelect v-model="approveForm.newVesselType" style="width: 100%">
                <ElOption
                  v-for="item in VESSEL_TYPE_OPTIONS"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </ElSelect>
            </ElFormItem>
            <ElFormItem v-if="duplicateNameHit.length > 0" label=" ">
              <ElAlert
                type="warning"
                :closable="false"
                show-icon
                class="w-full"
              >
                <template #title>
                  系统内已存在同名在营船舶「{{
                    duplicateNameHit[0].vesselName
                  }}」{{
                    duplicateNameHit[0].imoCode
                      ? `（IMO ${duplicateNameHit[0].imoCode}）`
                      : ''
                  }}。确认是另一条船才可新建，否则请改选「绑定已有船舶」。
                </template>
                <ElCheckbox v-model="approveForm.confirmDuplicateName">
                  确认新建同名船舶
                </ElCheckbox>
              </ElAlert>
            </ElFormItem>
          </template>

          <ElAlert
            v-if="approveMode === 'auto'"
            title="将按申请单的 IMO/呼号（船名写歪也能认出）与船名依次在系统内匹配在营船舶；匹配不到会提示，需改用前两种方式。"
            type="info"
            :closable="false"
            class="mb-3"
          />

          <ElFormItem label="绑定角色">
            <ElSelect v-model="approveForm.memberRole" style="width: 100%">
              <ElOption label="普通船员" value="2" />
              <ElOption label="采购确认人" value="3" />
              <ElOption label="业务员" value="4" />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="审核意见">
            <ElInput
              v-model="approveForm.auditRemark"
              type="textarea"
              :rows="2"
            />
          </ElFormItem>
        </ElForm>

        <template #footer>
          <div class="hx-form-footer">
            <ElButton @click="approveVisible = false">取消</ElButton>
            <ElButton
              type="primary"
              :loading="approving"
              @click="submitApprove"
            >
              确定
            </ElButton>
          </div>
        </template>
      </ElDialog>
    </div>
  </div>
</template>

<style scoped lang="scss">
/* 申请人手机号：次要信息弱化 */
.apply-user-phone {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 已处理状态文案 */
.apply-handled {
  color: var(--el-text-color-secondary);
}

/* 「申请船名 → 实际绑定船名」渲染：来源弱化、目标提示色 */
.mismatch-name {
  color: var(--el-text-color-secondary);
}

.mismatch-arrow {
  padding: 0 4px;
  color: var(--el-text-color-placeholder);
}

.mismatch-target {
  font-weight: 500;
  color: var(--el-color-warning);
}

/* 预检结论里的候选船舶提示 */
.precheck-hint {
  color: var(--el-text-color-secondary);
}

.candidate-chip {
  background: var(--el-fill-color-light);
}

.candidate-chip--active {
  background: var(--el-color-primary-light-9);
  box-shadow: 0 0 0 1px var(--el-color-primary);
}

.candidate-identity {
  color: var(--el-text-color-secondary);
}
</style>
