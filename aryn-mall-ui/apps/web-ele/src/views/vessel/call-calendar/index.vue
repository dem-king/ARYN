<script lang="ts" setup>
import type { Component } from 'vue';

import { computed, onMounted, ref, watch } from 'vue';

import {
  ArrowLeft,
  ArrowRight,
  Calendar,
  CircleCheck,
  Clock,
  RefreshLeft,
  Ship,
  Timer,
} from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import {
  ElButton,
  ElDrawer,
  ElEmpty,
  ElIcon,
  ElOption,
  ElSegmented,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
  ElTooltip,
} from 'element-plus';

import { getCallCalendar } from '#/api/promotion/ship-activity';

interface VesselCall {
  berth?: null | string;
  callId?: string;
  deliveryWindowEnd?: null | string;
  deliveryWindowStart?: null | string;
  eta?: null | string;
  etd?: null | string;
  portCode?: null | string;
  portName?: null | string;
  status: string;
  vesselId?: null | string;
  vesselName?: null | string;
}

interface CalendarCell {
  calls: VesselCall[];
  dateStr: string;
  day: number;
  inMonth: boolean;
  isToday: boolean;
  key: string;
}

interface TimelineBar {
  call: VesselCall;
  lane: number;
  left: string;
  width: string;
}

interface TimelineRow {
  bars: TimelineBar[];
  laneCount: number;
  name: string;
}

type ViewKind = 'calendar' | 'list' | 'timeline';

const WEEK_LABELS = ['一', '二', '三', '四', '五', '六', '日'];
const CHIP_LIMIT = 5;

const STATUS_META: Record<string, { label: string }> = {
  '1': { label: '计划中' },
  '2': { label: '靠泊中' },
  '3': { label: '已完成' },
  '4': { label: '已取消' },
};

const STAT_CARDS = [
  { key: 'total', label: '靠港计划', cls: 'ic-total', icon: Calendar },
  { key: 'planned', label: '计划中', cls: 'ic-planned', icon: Clock },
  { key: 'berthed', label: '靠泊中', cls: 'ic-berthed', icon: Ship },
  { key: 'done', label: '已完成', cls: 'ic-done', icon: CircleCheck },
  { key: 'today', label: '今日到港', cls: 'ic-today', icon: Timer },
] as const;

const STAT_FILTERS: Record<'berthed' | 'done' | 'planned' | 'total', string> = {
  berthed: '2',
  done: '3',
  planned: '1',
  total: '',
};

const STAT_SUBS: Record<'berthed' | 'done' | 'planned', string> = {
  berthed: '当前在港',
  done: '本月已完成',
  planned: '待到港',
};

const view = ref<ViewKind>('calendar');
const viewOptions = [
  { label: '日历', value: 'calendar' },
  { label: '时间轴', value: 'timeline' },
  { label: '列表', value: 'list' },
];

const loading = ref(false);
const month = ref(dayjs().format('YYYY-MM'));
const rawList = ref<VesselCall[]>([]);
const statusFilter = ref('');
const vesselFilter = ref('');

const drawerVisible = ref(false);
const selectedDate = ref('');

const monthLabel = computed(() =>
  dayjs(`${month.value}-01`).format('YYYY年M月'),
);
const isCurrentMonth = computed(
  () => month.value === dayjs().format('YYYY-MM'),
);
const daysInMonth = computed(() => dayjs(`${month.value}-01`).daysInMonth());

const vesselOptions = computed(() => {
  const names = new Set<string>();
  for (const item of rawList.value) {
    if (item.vesselName) names.add(item.vesselName);
  }
  return [...names].sort((a, b) => a.localeCompare(b, 'zh-CN'));
});

const filteredList = computed(() => {
  const list = rawList.value.filter(
    (item) =>
      (!statusFilter.value || item.status === statusFilter.value) &&
      (!vesselFilter.value || item.vesselName === vesselFilter.value),
  );
  return list.sort((a, b) => ts(a.eta) - ts(b.eta));
});

const stats = computed(() => {
  const active = filteredList.value.filter((item) => item.status !== '4');
  const todayStr = dayjs().format('YYYY-MM-DD');
  const todayArrivals = active.filter(
    (item) => item.eta && dayjs(item.eta).format('YYYY-MM-DD') === todayStr,
  );
  const todayNames = todayArrivals
    .slice(0, 2)
    .map((item) => item.vesselName)
    .filter(Boolean);
  return {
    berthed: active.filter((item) => item.status === '2').length,
    done: active.filter((item) => item.status === '3').length,
    planned: active.filter((item) => item.status === '1').length,
    ports: new Set(active.map((item) => item.portName).filter(Boolean)).size,
    todayCount: todayArrivals.length,
    todayExtra: Math.max(0, todayArrivals.length - 2),
    todayNames,
    total: active.length,
    vessels: new Set(active.map((item) => item.vesselName).filter(Boolean))
      .size,
  };
});

const callsByDate = computed(() => {
  const map = new Map<string, VesselCall[]>();
  for (const item of filteredList.value) {
    if (!item.eta) continue;
    const key = dayjs(item.eta).format('YYYY-MM-DD');
    if (!map.has(key)) map.set(key, []);
    map.get(key)!.push(item);
  }
  return map;
});

const calendarCells = computed(() => {
  const first = dayjs(`${month.value}-01`);
  const offset = (first.day() + 6) % 7;
  const gridStart = first.subtract(offset, 'day');
  const todayStr = dayjs().format('YYYY-MM-DD');
  const cells: CalendarCell[] = [];
  for (let i = 0; i < 42; i++) {
    const date = gridStart.add(i, 'day');
    const dateStr = date.format('YYYY-MM-DD');
    cells.push({
      calls: callsByDate.value.get(dateStr) ?? [],
      dateStr,
      day: date.date(),
      inMonth: date.month() === first.month(),
      isToday: dateStr === todayStr,
      key: dateStr,
    });
  }
  return cells;
});

const timelineRows = computed(() => {
  const byVessel = new Map<string, VesselCall[]>();
  for (const item of filteredList.value) {
    if (!item.eta) continue;
    const name = item.vesselName || '未知船舶';
    if (!byVessel.has(name)) byVessel.set(name, []);
    byVessel.get(name)!.push(item);
  }
  const rows: TimelineRow[] = [];
  for (const [name, calls] of byVessel) {
    const sorted = [...calls].sort((a, b) => ts(a.eta) - ts(b.eta));
    const laneEnds: string[] = [];
    const bars: TimelineBar[] = [];
    for (const call of sorted) {
      const start = dayjs(call.eta).format('YYYY-MM-DD');
      const end = dayjs(call.etd ?? call.eta).format('YYYY-MM-DD');
      let lane = laneEnds.findIndex((laneEnd) => start > laneEnd);
      if (lane === -1) lane = laneEnds.length;
      laneEnds[lane] = end;
      bars.push({
        call,
        lane,
        left: barLeft(call.eta!),
        width: barWidth(call),
      });
    }
    rows.push({ bars, laneCount: laneEnds.length, name });
  }
  return rows.sort((a, b) => ts(a.bars[0]?.call.eta) - ts(b.bars[0]?.call.eta));
});

const todayLeft = computed(() => {
  const today = dayjs();
  if (today.format('YYYY-MM') !== month.value) return '';
  return `${(((today.date() - 0.5) / daysInMonth.value) * 100).toFixed(2)}%`;
});

const drawerTitle = computed(() => {
  if (!selectedDate.value) return '靠港详情';
  const base = `${dayjs(selectedDate.value).format('M月D日')} 靠港详情`;
  const count = selectedDayCalls.value.length;
  return count > 0 ? `${base} · ${count} 艘` : base;
});
const selectedDayCalls = computed(() =>
  [...(callsByDate.value.get(selectedDate.value) ?? [])].sort(
    (a, b) => ts(a.eta) - ts(b.eta),
  ),
);

function ts(value?: null | string) {
  return value ? dayjs(value).valueOf() : Number.MAX_SAFE_INTEGER;
}

function barLeft(eta: string) {
  return `${(((dayjs(eta).date() - 1) / daysInMonth.value) * 100).toFixed(2)}%`;
}

function barWidth(call: VesselCall) {
  const monthEnd = dayjs(`${month.value}-01`).endOf('month');
  const start = dayjs(call.eta!);
  const end = dayjs(call.etd ?? call.eta!);
  const endDate = end.isAfter(monthEnd, 'day') ? monthEnd : end;
  const spanDays = Math.max(1, endDate.date() - start.date() + 1);
  return `${((Math.min(spanDays, daysInMonth.value) / daysInMonth.value) * 100).toFixed(2)}%`;
}

function statusLabel(status: string) {
  return STATUS_META[status]?.label ?? status;
}

function fmtDateTime(value?: null | string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—';
}

function fmtShort(value?: null | string) {
  return value ? dayjs(value).format('MM-DD HH:mm') : '—';
}

function statValue(key: 'berthed' | 'done' | 'planned' | 'today' | 'total') {
  if (key === 'today') return stats.value.todayCount;
  return stats.value[key];
}

function statSub(key: 'berthed' | 'done' | 'planned' | 'today' | 'total') {
  if (key === 'total') {
    return `${stats.value.vessels} 艘船舶 · ${stats.value.ports} 个港口`;
  }
  if (key === 'today') {
    if (stats.value.todayCount === 0) return '今日无到港安排';
    const names = stats.value.todayNames.join('、');
    return stats.value.todayExtra > 0
      ? `${names} 等${stats.value.todayCount} 艘`
      : names;
  }
  return STAT_SUBS[key];
}

function onStatClick(key: 'berthed' | 'done' | 'planned' | 'today' | 'total') {
  if (key === 'today') {
    goToday();
    return;
  }
  statusFilter.value = STAT_FILTERS[key] ?? '';
}

function isStatActive(key: string) {
  if (key === 'planned') return statusFilter.value === '1';
  if (key === 'berthed') return statusFilter.value === '2';
  if (key === 'done') return statusFilter.value === '3';
  return false;
}

function shiftMonth(delta: number) {
  month.value = dayjs(`${month.value}-01`)
    .add(delta, 'month')
    .format('YYYY-MM');
}

function goToday() {
  month.value = dayjs().format('YYYY-MM');
}

async function fetchCalls() {
  loading.value = true;
  try {
    const start = `${month.value}-01 00:00:00`;
    const end = dayjs(`${month.value}-01`)
      .endOf('month')
      .format('YYYY-MM-DD 23:59:59');
    rawList.value = await getCallCalendar(start, end);
  } finally {
    loading.value = false;
  }
}

function isWeekend(day: number) {
  const dow = dayjs(`${month.value}-01`).date(day).day();
  return dow === 0 || dow === 6;
}

function isToday(day: number) {
  return (
    dayjs(`${month.value}-01`).date(day).format('YYYY-MM-DD') ===
    dayjs().format('YYYY-MM-DD')
  );
}

function openDay(dateStr: string) {
  selectedDate.value = dateStr;
  drawerVisible.value = true;
}

watch(month, fetchCalls);
onMounted(fetchCalls);
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <div class="toolbar">
        <ElSegmented v-model="view" :options="viewOptions" />
        <div class="toolbar-filters">
          <ElSelect
            v-model="statusFilter"
            class="filter-select"
            placeholder="全部状态"
          >
            <ElOption label="全部状态" value="" />
            <ElOption
              v-for="(meta, code) in STATUS_META"
              :key="code"
              :label="meta.label"
              :value="code"
            />
          </ElSelect>
          <ElSelect
            v-model="vesselFilter"
            class="filter-select"
            placeholder="全部船舶"
          >
            <ElOption label="全部船舶" value="" />
            <ElOption
              v-for="name in vesselOptions"
              :key="name"
              :label="name"
              :value="name"
            />
          </ElSelect>
        </div>
        <div class="month-nav">
          <ElButton circle :icon="ArrowLeft" @click="shiftMonth(-1)" />
          <span class="month-title">{{ monthLabel }}</span>
          <ElButton circle :icon="ArrowRight" @click="shiftMonth(1)" />
          <ElButton
            circle
            :icon="RefreshLeft"
            title="刷新"
            @click="fetchCalls"
          />
          <ElButton v-if="!isCurrentMonth" plain @click="goToday">
            回到本月
          </ElButton>
        </div>
      </div>

      <div class="stat-grid">
        <div
          v-for="card in STAT_CARDS"
          :key="card.key"
          class="stat-card"
          :class="{ active: isStatActive(card.key) }"
          @click="onStatClick(card.key)"
        >
          <div class="stat-icon" :class="card.cls">
            <ElIcon :size="20">
              <Component :is="card.icon as Component" />
            </ElIcon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ statValue(card.key) }}</div>
            <div class="stat-label">
              {{ card.label }}
              <span class="stat-sub">{{ statSub(card.key) }}</span>
            </div>
          </div>
        </div>
      </div>

      <div v-loading="loading" class="view-body">
        <template v-if="filteredList.length > 0">
          <!-- 月历视图 -->
          <div v-if="view === 'calendar'" class="calendar">
            <div class="cal-week-header">
              <span v-for="week in WEEK_LABELS" :key="week">{{ week }}</span>
            </div>
            <div class="cal-grid">
              <div
                v-for="cell in calendarCells"
                :key="cell.key"
                class="cal-cell"
                :class="{ 'out-month': !cell.inMonth, today: cell.isToday }"
                @click="openDay(cell.dateStr)"
              >
                <div class="cal-date">
                  <span class="num">{{ cell.day }}</span>
                  <span v-if="cell.isToday" class="today-pill">今天</span>
                </div>
                <div v-if="cell.calls.length > 0" class="cal-chips">
                  <ElTooltip
                    v-for="call in cell.calls.slice(0, CHIP_LIMIT)"
                    :key="call.callId ?? `${call.vesselName}-${call.eta}`"
                    effect="dark"
                    placement="top"
                    :show-after="120"
                  >
                    <template #content>
                      <div class="chip-tip">
                        <div class="chip-tip-title">
                          {{ call.vesselName }} · {{ statusLabel(call.status) }}
                        </div>
                        <div>
                          {{ call.portName
                          }}<template v-if="call.berth">
                            · {{ call.berth }}
                          </template>
                        </div>
                        <div>到港 {{ fmtShort(call.eta) }}</div>
                        <div v-if="call.etd">离港 {{ fmtShort(call.etd) }}</div>
                        <div v-if="call.deliveryWindowStart">
                          配送 {{ fmtShort(call.deliveryWindowStart) }} ~
                          {{ fmtShort(call.deliveryWindowEnd) }}
                        </div>
                      </div>
                    </template>
                    <div
                      class="chip"
                      :class="`st-${call.status}`"
                      @click.stop="openDay(cell.dateStr)"
                    >
                      <span class="chip-vessel">{{ call.vesselName }}</span>
                      <span v-if="call.berth" class="chip-berth">{{
                        call.berth
                      }}</span>
                    </div>
                  </ElTooltip>
                  <div
                    v-if="cell.calls.length > CHIP_LIMIT"
                    class="chip-more"
                    @click.stop="openDay(cell.dateStr)"
                  >
                    +{{ cell.calls.length - CHIP_LIMIT }} 更多
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 时间轴视图 -->
          <div v-else-if="view === 'timeline'" class="timeline">
            <div class="tl-header">
              <div class="tl-vessel-col"></div>
              <div class="tl-track">
                <span
                  v-for="day in daysInMonth"
                  :key="day"
                  class="tl-day"
                  :class="{ weekend: isWeekend(day), today: isToday(day) }"
                >
                  {{ day }}
                </span>
              </div>
            </div>
            <div v-for="row in timelineRows" :key="row.name" class="tl-row">
              <div class="tl-vessel-col" :title="row.name">
                <ElIcon class="tl-ship-icon"><Ship /></ElIcon>
                <span class="tl-vessel-name">{{ row.name }}</span>
              </div>
              <div
                class="tl-track"
                :style="{
                  height: `${row.laneCount * 26 + 10}px`,
                }"
              >
                <span
                  v-for="day in daysInMonth"
                  :key="day"
                  class="tl-day bg"
                  :class="{ weekend: isWeekend(day) }"
                ></span>
                <div
                  v-for="(bar, index) in row.bars"
                  :key="`${bar.call.callId ?? ''}-${index}`"
                  class="tl-bar"
                  :class="`st-${bar.call.status}`"
                  :style="{
                    left: bar.left,
                    width: bar.width,
                    top: `${bar.lane * 26 + 4}px`,
                  }"
                >
                  <span class="tl-bar-label">
                    {{ bar.call.berth || bar.call.portName || '靠港' }}
                  </span>
                </div>
                <div
                  v-if="todayLeft"
                  class="tl-now"
                  :style="{ left: todayLeft }"
                ></div>
              </div>
            </div>
          </div>

          <!-- 列表视图 -->
          <ElTable
            v-else
            :data="filteredList"
            border
            :row-key="
              (row: VesselCall) => row.callId ?? `${row.vesselName}-${row.eta}`
            "
          >
            <ElTableColumn label="到港 ETA" width="170">
              <template #default="scope">
                <span class="cell-strong">{{
                  fmtDateTime(scope.row.eta)
                }}</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="离港 ETD" width="170">
              <template #default="scope">
                {{ fmtDateTime(scope.row.etd) }}
              </template>
            </ElTableColumn>
            <ElTableColumn prop="vesselName" label="船舶" min-width="120" />
            <ElTableColumn prop="portName" label="港口" width="110" />
            <ElTableColumn prop="berth" label="泊位" width="110">
              <template #default="scope">
                {{ scope.row.berth || '—' }}
              </template>
            </ElTableColumn>
            <ElTableColumn label="配送时间窗" min-width="220">
              <template #default="scope">
                <template v-if="scope.row.deliveryWindowStart">
                  {{ fmtDateTime(scope.row.deliveryWindowStart) }} ~
                  {{ fmtDateTime(scope.row.deliveryWindowEnd) }}
                </template>
                <span v-else class="cell-muted">—</span>
              </template>
            </ElTableColumn>
            <ElTableColumn label="状态" width="90">
              <template #default="scope">
                <ElTag
                  class="status-tag"
                  :class="`st-${scope.row.status}`"
                  size="small"
                >
                  {{ statusLabel(scope.row.status) }}
                </ElTag>
              </template>
            </ElTableColumn>
          </ElTable>
        </template>
        <ElEmpty
          v-else
          :description="
            rawList.length === 0
              ? '本月暂无靠港计划'
              : '没有符合筛选条件的靠港计划'
          "
        />
      </div>

      <!-- 单日明细抽屉 -->
      <ElDrawer v-model="drawerVisible" :title="drawerTitle" size="440px">
        <div v-if="selectedDayCalls.length === 0" class="drawer-empty">
          <ElEmpty description="当天暂无靠港计划" />
        </div>
        <div v-else class="day-cards">
          <div
            v-for="call in selectedDayCalls"
            :key="call.callId ?? `${call.vesselName}-${call.eta}`"
            class="day-card"
          >
            <div class="day-card-head">
              <span class="day-card-vessel">{{ call.vesselName }}</span>
              <ElTag
                class="status-tag"
                :class="`st-${call.status}`"
                size="small"
              >
                {{ statusLabel(call.status) }}
              </ElTag>
            </div>
            <div class="day-card-body">
              <div class="day-card-row">
                <span class="row-label">港口泊位</span>
                <span>
                  {{ call.portName || '—'
                  }}<template v-if="call.berth"> · {{ call.berth }}</template>
                </span>
              </div>
              <div class="day-card-row">
                <span class="row-label">到港 ETA</span>
                <span>{{ fmtDateTime(call.eta) }}</span>
              </div>
              <div class="day-card-row">
                <span class="row-label">离港 ETD</span>
                <span>{{ fmtDateTime(call.etd) }}</span>
              </div>
              <div class="day-card-row">
                <span class="row-label">配送时间窗</span>
                <span>
                  <template v-if="call.deliveryWindowStart">
                    {{ fmtDateTime(call.deliveryWindowStart) }} ~
                    {{ fmtDateTime(call.deliveryWindowEnd) }}
                  </template>
                  <template v-else>—</template>
                </span>
              </div>
            </div>
          </div>
        </div>
      </ElDrawer>
    </div>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.toolbar-filters {
  display: flex;
  flex: 1;
  gap: 8px;
}

.filter-select {
  width: 130px;
}

.month-nav {
  display: flex;
  gap: 8px;
  align-items: center;
}

.month-title {
  min-width: 92px;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  text-align: center;
}

/* 统计卡 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.stat-card {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  cursor: pointer;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  transition:
    box-shadow 0.2s,
    border-color 0.2s;
}

.stat-card:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: var(--el-box-shadow-light);
}

.stat-card.active {
  border-color: var(--el-color-primary);
}

.stat-icon {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
}

.ic-total {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.ic-planned {
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.ic-berthed {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.ic-done {
  color: var(--el-color-info);
  background: var(--el-color-info-light-9);
}

.ic-today {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}

.stat-info {
  min-width: 0;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--el-text-color-primary);
}

.stat-label {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.stat-sub {
  margin-left: 4px;
  color: var(--el-text-color-placeholder);
}

/* 内容区 */
.view-body {
  min-height: 320px;
  margin-top: 12px;
}

/* 状态色 */
.st-1 {
  --st: var(--el-color-primary);
  --st-bg: var(--el-color-primary-light-9);
}

.st-2 {
  --st: var(--el-color-success);
  --st-bg: var(--el-color-success-light-9);
}

.st-3 {
  --st: var(--el-color-info);
  --st-bg: var(--el-color-info-light-9);
}

.st-4 {
  --st: var(--el-color-danger);
  --st-bg: var(--el-color-danger-light-9);
}

.status-tag {
  color: var(--st);
  background: var(--st-bg);
  border-color: var(--st-bg);
}

/* 月历 */
.calendar {
  overflow-x: auto;
}

.cal-week-header {
  display: grid;
  grid-template-columns: repeat(7, minmax(128px, 1fr));
  gap: 6px;
  padding: 0 2px;
  margin-bottom: 6px;
}

.cal-week-header span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(128px, 1fr));
  gap: 6px;
}

.cal-cell {
  min-height: 96px;
  padding: 6px 8px;
  cursor: pointer;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  transition:
    background 0.15s,
    border-color 0.15s;
}

.cal-cell:hover {
  background: var(--el-fill-color-light);
}

.cal-cell.out-month {
  opacity: 0.45;
}

.cal-cell.today {
  border-color: var(--el-color-primary);
}

.cal-date {
  display: flex;
  gap: 4px;
  align-items: center;
  margin-bottom: 4px;
}

.cal-date .num {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.today-pill {
  padding: 0 6px;
  font-size: 10px;
  line-height: 16px;
  color: #fff;
  background: var(--el-color-primary);
  border-radius: 8px;
}

.cal-chips {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.chip {
  display: flex;
  gap: 4px;
  align-items: center;
  max-width: 100%;
  padding: 2px 6px;
  overflow: hidden;
  font-size: 12px;
  color: var(--st);
  white-space: nowrap;
  cursor: pointer;
  background: var(--st-bg);
  border-left: 3px solid var(--st);
  border-radius: 4px;
}

.st-4 .chip,
.chip.st-4 {
  opacity: 0.65;
}

.chip-vessel {
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 600;
}

.chip-berth {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  opacity: 0.8;
}

.chip-more {
  padding: 2px 6px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
}

.chip-more:hover {
  color: var(--el-color-primary);
}

.chip-tip {
  line-height: 1.7;
}

.chip-tip-title {
  font-weight: 600;
}

/* 时间轴 */
.timeline {
  min-width: 720px;
}

.tl-header,
.tl-row {
  display: flex;
  align-items: stretch;
}

.tl-header {
  margin-bottom: 4px;
}

.tl-row {
  margin-bottom: 4px;
}

.tl-vessel-col {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
  align-items: center;
  width: 150px;
  padding: 0 8px;
  overflow: hidden;
}

.tl-ship-icon {
  flex-shrink: 0;
  color: var(--el-color-primary);
}

.tl-vessel-name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.tl-track {
  position: relative;
  display: flex;
  flex: 1;
  min-width: 560px;
}

.tl-day {
  flex: 1 1 0;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

.tl-day.weekend {
  color: var(--el-text-color-placeholder);
}

.tl-day.today {
  font-weight: 700;
  color: var(--el-color-primary);
}

.tl-day.bg {
  border-left: 1px solid var(--el-border-color-extra-light);
}

.tl-day.bg.weekend {
  background: var(--el-fill-color-lighter);
}

.tl-bar {
  position: absolute;
  height: 20px;
  padding: 0 6px;
  overflow: hidden;
  line-height: 20px;
  color: #fff;
  white-space: nowrap;
  cursor: default;
  background: var(--st);
  border-radius: 4px;
  box-shadow: var(--el-box-shadow-light);
}

.tl-bar.st-4 {
  color: var(--st);
  background: var(--st-bg);
  border: 1px dashed var(--st);
}

.tl-bar-label {
  font-size: 11px;
}

.tl-now {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 2px;
  background: var(--el-color-danger);
  border-radius: 1px;
  opacity: 0.7;
}

/* 列表 */
.cell-strong {
  font-weight: 600;
}

.cell-muted {
  color: var(--el-text-color-placeholder);
}

/* 抽屉 */
.drawer-empty {
  padding-top: 60px;
}

.day-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.day-card {
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
}

.day-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.day-card-vessel {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.day-card-body {
  padding: 8px 14px 12px;
}

.day-card-row {
  display: flex;
  gap: 12px;
  padding: 4px 0;
  font-size: 13px;
  color: var(--el-text-color-primary);
}

.row-label {
  flex-shrink: 0;
  width: 76px;
  color: var(--el-text-color-secondary);
}
</style>
