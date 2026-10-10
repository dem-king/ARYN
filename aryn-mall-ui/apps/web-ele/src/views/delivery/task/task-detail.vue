<script lang="ts" setup>
import type { UploadUserFile } from 'element-plus';

import type { DeliveryTaskStatus } from '#/api/delivery/task';

import { ref } from 'vue';

import { Plus } from '@element-plus/icons-vue';
import {
  ElAlert,
  ElButton,
  ElCard,
  ElCol,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElIcon,
  ElImage,
  ElInput,
  ElMessage,
  ElOption,
  ElRow,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTimeline,
  ElTimelineItem,
  ElUpload,
} from 'element-plus';

import { getDeliveryStaffList } from '#/api/delivery/staff';
import {
  backfillDeliveryTaskArrive,
  closeDeliveryTask,
  getDeliveryTaskDetail,
  getDeliveryTaskEvidence,
  getDeliveryTaskLogs,
  reassignDeliveryTask,
  returnConfirmDeliveryTask,
  returnPendingDeliveryTask,
} from '#/api/delivery/task';
import { uploadFile } from '#/api/upms/upload';

import { resolveDeliveryDestination } from '../delivery-destination';

const emit = defineEmits(['initPage']);

const statusOptions: {
  label: string;
  type: 'danger' | 'info' | 'primary' | 'success' | 'warning';
  value: DeliveryTaskStatus;
}[] = [
  { value: '1', label: '待派单', type: 'primary' },
  { value: '2', label: '待取货', type: 'warning' },
  { value: '3', label: '配货中', type: 'warning' },
  { value: '4', label: '待送达', type: 'primary' },
  { value: '5', label: '已送达', type: 'success' },
  { value: '6', label: '已签收', type: 'success' },
  { value: '7', label: '已取消', type: 'info' },
  { value: '8', label: '异常', type: 'danger' },
  { value: '9', label: '待退回', type: 'danger' },
];

interface TaskDetail {
  id: string;
  taskNo: string;
  orderId: string;
  orderNo: string;
  recipientName: string;
  recipientPhone: string;
  recipientAddress?: string;
  /** 配送港口名称快照（内部配送单地址为空时的回落项） */
  portName?: string;
  /** 泊位快照（内部配送单地址为空时的回落项） */
  berth?: string;
  /** 配送船舶名称快照 */
  vesselName?: string;
  status: DeliveryTaskStatus;
  staffId?: string;
  staffName?: string;
  assignTime?: string;
  pickUpTime?: string;
  arriveTime?: string;
  departTime?: string;
  signTime?: string;
  exceptionTime?: string;
  returnPendingTime?: string;
  returnConfirmTime?: string;
  closeTime?: string;
  attemptNo?: number;
  exceptionReason?: string;
  exceptionDesc?: string;
  remark?: string;
  createTime?: string;
  itemList?: any[];
  timeline?: any[];
}

interface DeliveryStaffOption {
  id: string;
  name: string;
  phone?: string;
  status: string;
}

const drawerVisible = ref(false);
const loading = ref(false);
const detail = ref<null | TaskDetail>(null);
const evidenceList = ref<any[]>([]);
const logList = ref<any[]>([]);

const reassignVisible = ref(false);
const reassignLoading = ref(false);
const reassignStaffId = ref('');
const reassignReason = ref('');
const staffOptions = ref<DeliveryStaffOption[]>([]);

const closeVisible = ref(false);
const closeReason = ref('');
const closeLoading = ref(false);

const returnConfirmVisible = ref(false);
const returnConfirmRemark = ref('');
const returnConfirmLoading = ref(false);

// 管理端补录送达凭证（司机已送达却漏点送达时使用）
interface BackfillFile extends UploadUserFile {
  materialId?: string;
}
const backfillVisible = ref(false);
const backfillLoading = ref(false);
const backfillRemark = ref('');
const backfillFiles = ref<BackfillFile[]>([]);

const getStatusType = (status: DeliveryTaskStatus) => {
  return statusOptions.find((item) => item.value === status)?.type ?? 'info';
};

const getStatusLabel = (status: DeliveryTaskStatus) => {
  return statusOptions.find((item) => item.value === status)?.label ?? '未知';
};

const open = (id: string) => {
  drawerVisible.value = true;
  loading.value = true;
  getDeliveryTaskDetail(id)
    .then((response: any) => {
      detail.value = response;
      loading.value = false;
      loadEvidence(id);
      loadLogs(id);
    })
    .catch(() => {
      loading.value = false;
    });
};

const loadEvidence = (id: string) => {
  getDeliveryTaskEvidence(id).then((res: any) => {
    // 无 URL 快照的凭证行（素材已删/脏数据）渲染必为灰块，直接过滤
    evidenceList.value = (res || []).filter((item: any) => item?.materialUrl);
  });
};

const loadLogs = (id: string) => {
  getDeliveryTaskLogs(id).then((res: any) => {
    logList.value = res || [];
  });
};

const openReassign = () => {
  if (!detail.value) return;
  reassignStaffId.value = '';
  reassignReason.value = '';
  reassignVisible.value = true;
  getDeliveryStaffList({ status: '1' })
    .then((response: any) => {
      staffOptions.value = response || [];
    })
    .catch(() => {});
};

const confirmReassign = () => {
  if (!detail.value) return;
  if (!reassignStaffId.value) {
    ElMessage.warning('请选择新配送员');
    return;
  }
  reassignLoading.value = true;
  reassignDeliveryTask(detail.value.id, {
    staffId: reassignStaffId.value,
    reason: reassignReason.value,
  })
    .then(() => {
      ElMessage.success('改派成功');
      reassignLoading.value = false;
      reassignVisible.value = false;
      open(detail.value!.id);
      emit('initPage');
    })
    .catch(() => {
      reassignLoading.value = false;
    });
};

const openClose = () => {
  closeReason.value = '';
  closeVisible.value = true;
};

const confirmClose = () => {
  if (!detail.value) return;
  if (!closeReason.value) {
    ElMessage.warning('请输入关闭原因');
    return;
  }
  closeLoading.value = true;
  closeDeliveryTask(detail.value.id, closeReason.value)
    .then(() => {
      ElMessage.success('关闭成功');
      closeLoading.value = false;
      closeVisible.value = false;
      open(detail.value!.id);
      emit('initPage');
    })
    .catch(() => {
      closeLoading.value = false;
    });
};

const handleReturnPending = () => {
  if (!detail.value) return;
  returnPendingDeliveryTask(detail.value.id).then(() => {
    ElMessage.success('已置为待退回');
    open(detail.value!.id);
    emit('initPage');
  });
};

const openReturnConfirm = () => {
  returnConfirmRemark.value = '';
  returnConfirmVisible.value = true;
};

const confirmReturn = () => {
  if (!detail.value) return;
  returnConfirmLoading.value = true;
  returnConfirmDeliveryTask(detail.value.id, returnConfirmRemark.value)
    .then(() => {
      ElMessage.success('确认退回成功');
      returnConfirmLoading.value = false;
      returnConfirmVisible.value = false;
      open(detail.value!.id);
      emit('initPage');
    })
    .catch(() => {
      returnConfirmLoading.value = false;
    });
};

const openBackfill = () => {
  backfillRemark.value = '';
  backfillFiles.value = [];
  backfillVisible.value = true;
};

const beforeBackfillUpload = (file: any) => {
  if (!file.type?.startsWith('image/')) {
    ElMessage.error('送达凭证仅支持图片');
    return false;
  }
  if (backfillFiles.value.length >= 6) {
    ElMessage.error('送达凭证最多上传6张');
    return false;
  }
  return true;
};

const uploadBackfillFile = async (options: any) => {
  const formData = new FormData();
  formData.append('file', options.file);
  formData.append('type', '1');
  const res: any = await uploadFile(formData);
  const fileItem = backfillFiles.value.at(-1);
  if (fileItem) {
    fileItem.materialId = res?.id;
    fileItem.url = res?.url;
  }
  options.onSuccess?.(res);
  return res;
};

const confirmBackfill = () => {
  if (!detail.value) return;
  const materialIds = backfillFiles.value
    .map((item) => item.materialId)
    .filter((id): id is string => !!id);
  if (materialIds.length === 0) {
    ElMessage.warning('请至少上传 1 张送达凭证');
    return;
  }
  backfillLoading.value = true;
  backfillDeliveryTaskArrive(detail.value.id, materialIds, backfillRemark.value)
    .then(() => {
      ElMessage.success('补录送达成功');
      backfillLoading.value = false;
      backfillVisible.value = false;
      open(detail.value!.id);
      emit('initPage');
    })
    .catch(() => {
      backfillLoading.value = false;
    });
};

defineExpose({
  open,
});
</script>
<template>
  <div class="task-detail-root">
    <ElDrawer v-model="drawerVisible" title="配送任务详情" size="55%">
      <div v-if="detail" v-loading="loading" style="padding: 0 8px">
        <ElCard class="box-card">
          <template #header>
            <div class="card-header">
              <span>基本信息</span>
              <ElTag :type="getStatusType(detail.status)">
                {{ getStatusLabel(detail.status) }}
              </ElTag>
            </div>
          </template>
          <ElDescriptions :column="2" border>
            <ElDescriptionsItem label="任务编号">
              {{ detail.taskNo }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="订单号">
              {{ detail.orderNo }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="配送员">
              {{ detail.staffName ?? '—' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="尝试号">
              {{ detail.attemptNo ?? 1 }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="派单时间">
              {{ detail.assignTime ?? '—' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="取货时间">
              {{ detail.pickUpTime ?? '—' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="出发时间">
              {{ detail.departTime ?? '—' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="送达时间">
              {{ detail.arriveTime ?? '—' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="签收时间">
              {{ detail.signTime ?? '—' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="创建时间">
              {{ detail.createTime ?? '—' }}
            </ElDescriptionsItem>
          </ElDescriptions>
        </ElCard>

        <ElCard class="box-card margin-top">
          <template #header>
            <div class="card-header"><span>收货信息</span></div>
          </template>
          <ElDescriptions :column="1" border>
            <ElDescriptionsItem label="收货人">
              {{ detail.recipientName }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="联系电话">
              {{ detail.recipientPhone }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="收货地址">
              {{ resolveDeliveryDestination(detail) || '—' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem v-if="detail.vesselName" label="配送船舶">
              {{ detail.vesselName }}
            </ElDescriptionsItem>
          </ElDescriptions>
        </ElCard>

        <ElCard
          v-if="detail.status === '8' || detail.status === '9'"
          class="box-card margin-top"
        >
          <template #header>
            <div class="card-header"><span>异常信息</span></div>
          </template>
          <ElDescriptions :column="1" border>
            <ElDescriptionsItem label="异常时间">
              {{ detail.exceptionTime ?? '—' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="异常原因">
              {{ detail.exceptionReason ?? '—' }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="异常说明">
              {{ detail.exceptionDesc ?? '—' }}
            </ElDescriptionsItem>
          </ElDescriptions>
        </ElCard>

        <ElCard class="box-card margin-top">
          <template #header>
            <div class="card-header"><span>订单明细</span></div>
          </template>
          <ElTable
            :data="detail.itemList ?? []"
            border
            row-key="id"
            style="width: 100%"
          >
            <ElTableColumn prop="spuName" label="商品信息" min-width="240">
              <template #default="scope">
                <ElRow>
                  <ElCol :span="4">
                    <ElImage
                      style="width: 60px; height: 60px"
                      :src="scope.row.image"
                      fit="cover"
                      :preview-teleported="true"
                    />
                  </ElCol>
                  <ElCol :span="20">
                    <div class="overflow-line-clamp-2 name">
                      {{ scope.row.spuName }}
                    </div>
                    <p style="font-size: 12px; color: #a8abb2">
                      {{ scope.row.skuName }}
                    </p>
                  </ElCol>
                </ElRow>
              </template>
            </ElTableColumn>
            <ElTableColumn
              prop="quantity"
              label="数量"
              width="100"
              align="center"
            />
            <ElTableColumn prop="picked" label="已取" width="80" align="center">
              <template #default="{ row }">
                <ElTag :type="row.picked === '1' ? 'success' : 'info'">
                  {{ row.picked === '1' ? '已取' : '未取' }}
                </ElTag>
              </template>
            </ElTableColumn>
          </ElTable>
        </ElCard>

        <ElCard v-if="evidenceList.length > 0" class="box-card margin-top">
          <template #header>
            <div class="card-header"><span>送达凭证</span></div>
          </template>
          <div style="display: flex; flex-wrap: wrap; gap: 12px">
            <ElImage
              v-for="item in evidenceList"
              :key="item.id"
              style="width: 120px; height: 120px; border-radius: 4px"
              :src="item.materialUrl"
              fit="cover"
              :preview-teleported="true"
              :preview-src-list="evidenceList.map((e: any) => e.materialUrl)"
            />
          </div>
        </ElCard>

        <ElCard v-if="logList.length > 0" class="box-card margin-top">
          <template #header>
            <div class="card-header"><span>操作日志</span></div>
          </template>
          <ElTimeline>
            <ElTimelineItem
              v-for="log in logList"
              :key="log.id"
              :timestamp="log.createTime ?? '—'"
            >
              <ElTag size="small">{{ log.action }}</ElTag>
              <span style="margin-left: 8px">{{ log.reasonDesc ?? '' }}</span>
            </ElTimelineItem>
          </ElTimeline>
        </ElCard>
      </div>

      <template #footer>
        <div style="text-align: right">
          <ElButton @click="drawerVisible = false">关 闭</ElButton>
          <ElButton
            v-if="detail && (detail.status === '1' || detail.status === '8')"
            v-access:code="'delivery:task:reassign'"
            type="primary"
            @click="openReassign"
          >
            改派
          </ElButton>
          <ElButton
            v-if="detail && detail.status === '8'"
            v-access:code="'delivery:task:exception'"
            type="danger"
            @click="openClose"
          >
            关闭异常
          </ElButton>
          <ElButton
            v-if="detail && (detail.status === '4' || detail.status === '8')"
            v-access:code="'delivery:task:return'"
            type="warning"
            @click="handleReturnPending"
          >
            置为待退回
          </ElButton>
          <ElButton
            v-if="detail && detail.status === '9'"
            v-access:code="'delivery:task:return'"
            type="success"
            @click="openReturnConfirm"
          >
            确认退回
          </ElButton>
          <ElButton
            v-if="detail && detail.status === '4'"
            v-access:code="'delivery:task:backfill-arrive'"
            type="primary"
            @click="openBackfill"
          >
            补录送达
          </ElButton>
        </div>
      </template>
    </ElDrawer>

    <ElDialog
      v-model="reassignVisible"
      title="改派"
      width="420px"
      append-to-body
    >
      <ElForm label-width="100px">
        <ElFormItem label="新配送员">
          <ElSelect
            v-model="reassignStaffId"
            placeholder="请选择新配送员"
            filterable
            style="width: 100%"
          >
            <ElOption
              v-for="item in staffOptions"
              :key="item.id"
              :label="`${item.name}（${item.phone ?? ''}）`"
              :value="item.id"
            />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="改派原因">
          <ElInput
            v-model="reassignReason"
            type="textarea"
            :rows="3"
            placeholder="请输入改派原因（选填）"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="reassignVisible = false">取 消</ElButton>
        <ElButton
          type="primary"
          :loading="reassignLoading"
          @click="confirmReassign"
        >
          确认改派
        </ElButton>
      </template>
    </ElDialog>

    <ElDialog
      v-model="closeVisible"
      title="关闭异常任务"
      width="420px"
      append-to-body
    >
      <ElForm label-width="100px">
        <ElFormItem label="关闭原因">
          <ElInput
            v-model="closeReason"
            type="textarea"
            :rows="3"
            placeholder="请输入关闭原因"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="closeVisible = false">取 消</ElButton>
        <ElButton type="danger" :loading="closeLoading" @click="confirmClose">
          确认关闭
        </ElButton>
      </template>
    </ElDialog>

    <ElDialog
      v-model="returnConfirmVisible"
      title="确认商品退回"
      width="420px"
      append-to-body
    >
      <ElForm label-width="100px">
        <ElFormItem label="备注">
          <ElInput
            v-model="returnConfirmRemark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注（选填）"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="returnConfirmVisible = false">取 消</ElButton>
        <ElButton
          type="success"
          :loading="returnConfirmLoading"
          @click="confirmReturn"
        >
          确认退回
        </ElButton>
      </template>
    </ElDialog>

    <ElDialog
      v-model="backfillVisible"
      title="补录送达凭证"
      width="520px"
      append-to-body
      :close-on-click-modal="false"
    >
      <ElAlert
        type="warning"
        :closable="false"
        show-icon
        title="仅用于司机实际已送达却漏点「送达」的场景"
        description="补录后将视为已送达，必须上传真实送达照片作为凭证，操作会记入任务日志。"
        style="margin-bottom: 12px"
      />
      <ElForm label-width="90px">
        <ElFormItem label="送达凭证" required>
          <ElUpload
            v-model:file-list="backfillFiles"
            list-type="picture-card"
            accept="image/*"
            :limit="6"
            :before-upload="beforeBackfillUpload"
            :http-request="uploadBackfillFile"
          >
            <ElIcon><Plus /></ElIcon>
          </ElUpload>
        </ElFormItem>
        <ElFormItem label="备注">
          <ElInput
            v-model="backfillRemark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注（选填，如补录原因）"
          />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="backfillVisible = false">取 消</ElButton>
        <ElButton
          type="primary"
          :loading="backfillLoading"
          @click="confirmBackfill"
        >
          确认补录
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style lang="scss" scoped>
.margin-top {
  margin-top: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;

  .name {
    color: #409eff;
  }
}
</style>
