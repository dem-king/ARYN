<script setup lang="ts">
import type { VesselCall, VesselInfo, VesselMember } from '#/api/vessel/vessel';

import { computed, ref, watch } from 'vue';

import { useAccess } from '@vben/access';

import { ElInput, ElOption, ElSelect } from 'element-plus';

import { getBuyerOrgMembers, getBuyerOrgPage } from '#/api/order/buyer-org';
import { getTradeCalendars } from '#/api/promotion/trade-calendar';
import { getById, getPage } from '#/api/user/user-info';
import {
  getVesselCalls,
  getVesselMembers,
  getVesselPage,
} from '#/api/vessel/vessel';

type Kind =
  | 'calendar'
  | 'call'
  | 'member'
  | 'org'
  | 'orgMember'
  | 'vessel'
  | 'vesselMember';
interface Option {
  value: string;
  label: string;
}
interface Member {
  id: string;
  nickname?: string;
  phone?: string;
}

const props = defineProps<{ kind: Kind; parentId?: string }>();
const emit = defineEmits<{ label: [id: string, label: string] }>();
const value = defineModel<string | undefined>();
const { hasAccessByCodes } = useAccess();
const permissions: Record<Kind, string> = {
  calendar: 'promotion:calendar:page',
  call: 'vessel:call:list',
  member: 'user:userinfo:page',
  org: 'order:buyerorg:page',
  orgMember: 'order:buyerorg:member',
  vessel: 'vessel:vessel:page',
  vesselMember: 'vessel:member:list',
};
const canSearch = computed(() => hasAccessByCodes([permissions[props.kind]]));
const remote = computed(() =>
  ['calendar', 'member', 'org', 'vessel'].includes(props.kind),
);
const fallback = ref(false);
const failed = ref(false);
const loading = ref(false);
const options = ref<Option[]>([]);
const selected = ref<Option>();
watch(selected, (option) => {
  if (option && !option.label.startsWith('已保存 · '))
    emit('label', option.value, option.label);
});
const visibleOptions = computed(() =>
  selected.value &&
  !options.value.some((option) => option.value === selected.value?.value)
    ? [selected.value, ...options.value]
    : options.value,
);
let request = 0;

function memberLabel(member: Member) {
  const tail = member.phone?.slice(-4);
  return (
    [member.nickname, tail && `尾号 ${tail}`].filter(Boolean).join(' · ') ||
    `会员 ${member.id}`
  );
}

async function search(keyword = '') {
  if (!canSearch.value || (props.kind === 'member' && !keyword.trim())) return;
  const ticket = ++request;
  const query = { current: 1, size: 30 };
  const term = keyword.trim();
  loading.value = true;
  failed.value = false;
  try {
    let rows: Option[] = [];
    switch (props.kind) {
      case 'calendar': {
        const data: {
          calendarName: string;
          endTime: string;
          id: string;
          startTime: string;
        }[] = await getTradeCalendars({ keyword: term, status: '1' });
        rows = data.map((row) => ({
          label: `${row.calendarName} · ${row.startTime.slice(0, 10)} 至 ${row.endTime.slice(0, 10)}`,
          value: row.id,
        }));
        break;
      }
      case 'call': {
        const data: VesselCall[] = props.parentId
          ? await getVesselCalls(props.parentId)
          : [];
        rows = data.flatMap((row) =>
          row.id && !['3', '4'].includes(row.status ?? '')
            ? [
                {
                  label: `${row.portName} · ${row.eta.replace('T', ' ')}`,
                  value: row.id,
                },
              ]
            : [],
        );
        break;
      }
      case 'member': {
        const data = await getPage({ ...query, phone: term, status: '0' });
        rows = ((data.records ?? []) as Member[]).map((row) => ({
          label: memberLabel(row),
          value: row.id,
        }));
        break;
      }
      case 'org': {
        const data = await getBuyerOrgPage({
          ...query,
          orgName: term,
          auditStatus: '1',
          status: '1',
        });
        rows = (data.records ?? []).map(
          (row: { id: string; orgCode?: string; orgName: string }) => ({
            label: [row.orgName, row.orgCode].filter(Boolean).join(' · '),
            value: row.id,
          }),
        );
        break;
      }
      case 'orgMember': {
        const data = props.parentId
          ? await getBuyerOrgMembers(props.parentId)
          : [];
        rows = data
          .filter((row) => row.status !== '0')
          .map((row) => ({
            label: memberLabel({
              id: row.userId,
              nickname: row.nickname,
              phone: row.phoneMasked,
            }),
            value: row.userId,
          }));
        break;
      }
      case 'vessel': {
        const data = await getVesselPage({
          ...query,
          vesselName: term,
          status: '1',
        });
        rows = ((data.records ?? []) as VesselInfo[]).flatMap((row) =>
          row.id
            ? [
                {
                  label: [row.vesselName, row.imoCode]
                    .filter(Boolean)
                    .join(' · '),
                  value: row.id,
                },
              ]
            : [],
        );
        break;
      }
      case 'vesselMember': {
        const data: VesselMember[] = props.parentId
          ? await getVesselMembers(props.parentId)
          : [];
        const active = data.filter((row) => row.status !== '0');
        for (let offset = 0; offset < active.length; offset += 8) {
          if (ticket !== request) return;
          rows.push(
            ...(await Promise.all(
              active.slice(offset, offset + 8).map(async ({ userId }) => {
                const member: Member = hasAccessByCodes(['user:userinfo:get'])
                  ? await getById(userId).catch(() => ({ id: userId }))
                  : { id: userId };
                return { label: memberLabel(member), value: userId };
              }),
            )),
          );
        }
        break;
      }
    }
    if (ticket === request) {
      options.value = rows;
      selected.value =
        rows.find((row) => row.value === value.value) ?? selected.value;
    }
  } catch {
    if (ticket === request) {
      options.value = [];
      failed.value = true;
    }
  } finally {
    if (ticket === request) loading.value = false;
  }
}

watch(
  () => [props.kind, props.parentId],
  () => {
    request++;
    loading.value = false;
    options.value = [];
    selected.value = undefined;
    failed.value = false;
    fallback.value = false;
  },
);
watch(
  value,
  (id) => {
    selected.value = id
      ? (options.value.find((option) => option.value === id) ?? {
          label:
            selected.value?.value === id
              ? selected.value.label
              : `已保存 · ${id}`,
          value: id,
        })
      : undefined;
  },
  { immediate: true },
);
</script>

<template>
  <ElSelect
    v-if="canSearch && !fallback"
    v-model="value"
    clearable
    filterable
    :remote="remote"
    :remote-method="search"
    :loading="loading"
    :placeholder="
      kind === 'member' ? '输入会员手机号查询' : '输入名称搜索或选择'
    "
    :no-data-text="failed ? '查询失败，重新打开选择框可重试' : '暂无匹配结果'"
    @visible-change="(visible) => visible && search()"
  >
    <ElOption
      v-for="item in visibleOptions"
      :key="item.value"
      :label="item.label"
      :value="item.value"
    />
  </ElSelect>
  <ElInput v-else v-model.trim="value" placeholder="输入已知编号" />
  <small v-if="!canSearch" class="hint"
    >当前账号没有此资料的查询权限，可输入已知编号。</small
  >
  <button
    v-else-if="failed || fallback"
    type="button"
    class="field-link"
    @click="fallback = !fallback"
  >
    {{ fallback ? '返回查询' : '输入已知编号' }}
  </button>
</template>

<style scoped>
.hint {
  color: var(--el-text-color-secondary);
}

.field-link {
  color: var(--el-color-primary);
  cursor: pointer;
}
</style>
