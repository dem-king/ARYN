<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import {
  computed,
  onActivated,
  onDeactivated,
  onMounted,
  onUnmounted,
  reactive,
  ref,
  watch,
} from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import {
  Coffee,
  Coin,
  Cpu,
  FolderOpened,
  Location,
  Monitor,
  Refresh,
  TrendCharts,
} from '@element-plus/icons-vue';
import { ElAlert, ElButton, ElIcon, ElSwitch } from 'element-plus';

import { getServer } from '#/api/upms/sys-server';

import MetricGauge from './components/MetricGauge.vue';

interface CpuInfo {
  cpuNum: number;
  free: number;
  sys: number;
  total: number;
  used: number;
  wait: number;
}

interface MemInfo {
  free: number;
  total: number;
  usage: number;
  used: number;
}

interface JvmInfo extends MemInfo {
  home: string;
  max: number;
  name: string;
  runTime: string;
  startTime: string;
  version: string;
}

interface SysInfo {
  computerIp: string;
  computerName: string;
  osArch: string;
  osName: string;
  userDir: string;
}

interface SysFileInfo {
  dirName: string;
  free: string;
  sysTypeName: string;
  total: string;
  typeName: string;
  usage: number;
  used: string;
}

interface ServerData {
  cpu: CpuInfo;
  jvm: JvmInfo;
  mem: MemInfo;
  sys: SysInfo;
  sysFiles: SysFileInfo[];
}

interface Sample {
  cpu: number;
  jvm: number;
  mem: number;
  time: string;
}

type Tone = 'danger' | 'success' | 'warning';

const AUTO_REFRESH_SECONDS = 15;
const MAX_SAMPLES = 40;

const state = reactive<ServerData>({
  cpu: { cpuNum: 0, free: 100, sys: 0, total: 0, used: 0, wait: 0 },
  jvm: {
    free: 0,
    home: '',
    max: 0,
    name: '',
    runTime: '',
    startTime: '',
    total: 0,
    usage: 0,
    used: 0,
    version: '',
  },
  mem: { free: 0, total: 0, usage: 0, used: 0 },
  sys: {
    computerIp: '',
    computerName: '',
    osArch: '',
    osName: '',
    userDir: '',
  },
  sysFiles: [],
});

const loading = ref(false);
const refreshing = ref(false);
const manualRefreshing = ref(false);
const hasLoaded = ref(false);
const autoRefresh = ref(true);
const loadError = ref('');
const lastSync = ref<Date>();
const samples = ref<Sample[]>([]);
const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);
type ChartOption = Parameters<typeof renderEcharts>[0];

let refreshTimer: number | undefined;

const clampPercent = (value: number | string | undefined): number => {
  const num = Number(value);
  if (!Number.isFinite(num)) {
    return 0;
  }
  return Math.min(100, Math.max(0, num));
};

const cpuUsage = computed(() =>
  hasLoaded.value ? clampPercent(100 - state.cpu.free) : 0,
);
const memUsage = computed(() => clampPercent(state.mem.usage));
const jvmUsage = computed(() => clampPercent(state.jvm.usage));

const parseSize = (value: string | undefined): number => {
  if (!value) {
    return 0;
  }
  const match = /([\d.]+)\s*(B|KB|MB|GB|TB)/i.exec(value.trim());
  if (!match) {
    return 0;
  }
  const amount = Number(match[1]);
  if (!Number.isFinite(amount)) {
    return 0;
  }
  const units: Record<string, number> = {
    B: 1,
    GB: 1024 ** 3,
    KB: 1024,
    MB: 1024 ** 2,
    TB: 1024 ** 4,
  };
  const unit = (match[2] ?? '').toUpperCase();
  return amount * (units[unit] ?? 0);
};

const diskTotals = computed(() => {
  const total = state.sysFiles.reduce(
    (sum, item) => sum + parseSize(item.total),
    0,
  );
  const used = state.sysFiles.reduce(
    (sum, item) => sum + parseSize(item.used),
    0,
  );
  return { total, used };
});

const diskAggregateUsage = computed(() => {
  const { total, used } = diskTotals.value;
  return total > 0 ? clampPercent((used / total) * 100) : 0;
});

const diskUsage = computed(() => {
  if (state.sysFiles.length === 0) {
    return 0;
  }
  return clampPercent(Math.max(...state.sysFiles.map((item) => item.usage)));
});

const peakDisk = computed<SysFileInfo | undefined>(() => {
  let peak: SysFileInfo | undefined;
  for (const item of state.sysFiles) {
    if (!peak || item.usage > peak.usage) {
      peak = item;
    }
  }
  return peak;
});

const peakUsage = computed(() =>
  Math.max(cpuUsage.value, memUsage.value, jvmUsage.value, diskUsage.value),
);

const overall = computed<{ label: string; tone: Tone }>(() => {
  if (peakUsage.value >= 88) {
    return { label: '资源告警', tone: 'danger' };
  }
  if (peakUsage.value >= 72) {
    return { label: '负载偏高', tone: 'warning' };
  }
  return { label: '运行正常', tone: 'success' };
});

const formatBytes = (bytes: number): string => {
  if (!Number.isFinite(bytes) || bytes <= 0) {
    return '--';
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let value = bytes;
  let index = 0;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index += 1;
  }
  return `${value.toFixed(value >= 100 ? 0 : 1)} ${units[index] ?? 'B'}`;
};

const formatGigabytes = (value: number | undefined): string => {
  const num = Number(value);
  if (!Number.isFinite(num) || num <= 0) {
    return '--';
  }
  return `${num.toFixed(num >= 100 ? 0 : 1)} GB`;
};

const formatMegabytes = (value: number | undefined): string => {
  const num = Number(value);
  if (!Number.isFinite(num) || num <= 0) {
    return '--';
  }
  return num >= 1024 ? `${(num / 1024).toFixed(1)} GB` : `${num.toFixed(0)} MB`;
};

const formatPercentValue = (value: number | string | undefined): string => {
  return clampPercent(value).toFixed(1);
};

const formatUptime = (raw: string | undefined): string => {
  const minutes = Number(raw);
  if (!raw || !Number.isFinite(minutes)) {
    return '--';
  }
  if (minutes >= 60 * 24) {
    return `${Math.floor(minutes / (60 * 24))} 天 ${Math.floor((minutes % (60 * 24)) / 60)} 小时`;
  }
  if (minutes >= 60) {
    return `${Math.floor(minutes / 60)} 小时 ${Math.floor(minutes % 60)} 分`;
  }
  return `${Math.floor(minutes)} 分钟`;
};

const uptimeText = computed(() => formatUptime(state.jvm.runTime));
const lastSyncText = computed(() =>
  lastSync.value
    ? lastSync.value.toLocaleTimeString('zh-CN', { hour12: false })
    : '--:--:--',
);

const cpuCaption = computed(() =>
  state.cpu.cpuNum ? `${state.cpu.cpuNum} 核` : '-- 核',
);
const memoryCaption = computed(
  () => `总内存 ${formatGigabytes(state.mem.total)}`,
);
const jvmCaption = computed(() => `已分配 ${formatMegabytes(state.jvm.total)}`);
const diskCaption = computed(() => {
  if (state.sysFiles.length === 0) {
    return '暂无磁盘数据';
  }
  return diskTotals.value.total > 0
    ? `总容量 ${formatBytes(diskTotals.value.total)}`
    : `${state.sysFiles.length} 个挂载点`;
});
const memoryDetail = computed(
  () =>
    `已用 ${formatGigabytes(state.mem.used)} · 可用 ${formatGigabytes(state.mem.free)}`,
);
const jvmDetail = computed(
  () =>
    `已用 ${formatMegabytes(state.jvm.used)} · 上限 ${formatMegabytes(state.jvm.max)}`,
);
const diskDetail = computed(() => {
  if (!peakDisk.value) {
    return '暂无磁盘数据';
  }
  return `最高 ${peakDisk.value.dirName} · 已用 ${peakDisk.value.used}`;
});

const diskBarBackground = (usage: number): string => {
  if (usage >= 88) {
    return 'linear-gradient(90deg, #fb7185, #ef4444)';
  }
  if (usage >= 72) {
    return 'linear-gradient(90deg, #fbbf24, #f97316)';
  }
  return 'linear-gradient(90deg, #22d3ee, #3b82f6)';
};

const diskBarClass = (usage: number): string => {
  if (usage >= 88) {
    return 'is-danger';
  }
  if (usage >= 72) {
    return 'is-warning';
  }
  return '';
};

const buildSeries = (
  name: string,
  data: number[],
  from: string,
  to: string,
) => ({
  areaStyle: {
    color: {
      colorStops: [
        { color: `${from}3d`, offset: 0 },
        { color: `${to}00`, offset: 1 },
      ],
      type: 'linear' as const,
      x: 0,
      x2: 0,
      y: 0,
      y2: 1,
    },
  },
  data,
  emphasis: { focus: 'series' as const },
  itemStyle: { color: from },
  lineStyle: { color: from, width: 2 },
  name,
  showSymbol: true,
  smooth: true,
  symbol: 'circle',
  symbolSize: 3,
  type: 'line' as const,
});

const renderChart = (): void => {
  const times = samples.value.map((item) => item.time);
  const option: ChartOption = {
    grid: { bottom: 0, containLabel: true, left: 4, right: 26, top: 42 },
    legend: {
      icon: 'roundRect',
      itemGap: 18,
      itemHeight: 4,
      itemWidth: 12,
      right: 0,
      textStyle: { color: '#94a3b8', fontSize: 12 },
      top: 0,
    },
    series: [
      buildSeries(
        'CPU',
        samples.value.map((item) => item.cpu),
        '#22d3ee',
        '#0ea5e9',
      ),
      buildSeries(
        '内存',
        samples.value.map((item) => item.mem),
        '#a78bfa',
        '#6366f1',
      ),
      buildSeries(
        'JVM 堆',
        samples.value.map((item) => item.jvm),
        '#34d399',
        '#10b981',
      ),
    ],
    tooltip: {
      axisPointer: {
        lineStyle: { color: 'rgba(56, 189, 248, 0.35)' },
        type: 'line',
      },
      backgroundColor: 'rgba(7, 20, 40, 0.92)',
      borderColor: 'rgba(56, 189, 248, 0.35)',
      textStyle: { color: '#e2e8f0' },
      trigger: 'axis',
    },
    xAxis: {
      axisLabel: { color: '#94a3b8', fontSize: 11, hideOverlap: true },
      axisLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.25)' } },
      axisTick: { show: false },
      boundaryGap: false,
      data: times,
      type: 'category',
    },
    yAxis: {
      axisLabel: {
        color: '#94a3b8',
        fontSize: 11,
        formatter: '{value}%',
      },
      max: 100,
      min: 0,
      splitLine: {
        lineStyle: { color: 'rgba(148, 163, 184, 0.14)', type: 'dashed' },
      },
      splitNumber: 4,
      type: 'value',
    },
  };
  void renderEcharts(option);
};

const pushSample = (): void => {
  samples.value.push({
    cpu: cpuUsage.value,
    jvm: jvmUsage.value,
    mem: memUsage.value,
    time: new Date().toLocaleTimeString('zh-CN', { hour12: false }),
  });
  if (samples.value.length > MAX_SAMPLES) {
    samples.value.shift();
  }
};

const stopTimer = (): void => {
  if (refreshTimer !== undefined) {
    window.clearInterval(refreshTimer);
    refreshTimer = undefined;
  }
};

const startTimer = (): void => {
  stopTimer();
  if (!autoRefresh.value) {
    return;
  }
  refreshTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible') {
      void fetchServer();
    }
  }, AUTO_REFRESH_SECONDS * 1000);
};

async function fetchServer(manual = false): Promise<void> {
  if (loading.value || refreshing.value) {
    return;
  }
  if (manual) {
    manualRefreshing.value = true;
  }
  if (hasLoaded.value) {
    refreshing.value = true;
  } else {
    loading.value = true;
  }
  try {
    const response = (await getServer()) as ServerData;
    state.cpu = response.cpu;
    state.jvm = response.jvm;
    state.mem = response.mem;
    state.sys = response.sys;
    state.sysFiles = response.sysFiles ?? [];
    hasLoaded.value = true;
    loadError.value = '';
    lastSync.value = new Date();
    pushSample();
  } catch {
    loadError.value = '服务监控数据获取失败，请稍后重试。';
  } finally {
    loading.value = false;
    refreshing.value = false;
    manualRefreshing.value = false;
  }
}

watch(samples, renderChart, { deep: true });
watch(autoRefresh, startTimer);

onMounted(() => {
  renderChart();
  void fetchServer();
  startTimer();
});

// keepAlive 缓存后离开页面不会 unmount，轮询必须挂到激活态上，
// 否则切去其它页面后仍会在后台按间隔请求
onDeactivated(stopTimer);
onActivated(startTimer);
onUnmounted(stopTimer);
</script>

<template>
  <div v-loading="loading" class="hx-layout-container">
    <div class="monitor-page">
      <ElAlert
        v-if="loadError"
        :title="loadError"
        closable
        show-icon
        type="error"
        @close="loadError = ''"
      />

      <section class="monitor-hero">
        <div class="hero-inner">
          <div class="hero-main">
            <div class="hero-identity">
              <div class="hero-badge" :class="`is-${overall.tone}`">
                <span class="pulse-dot"></span>
                LIVE · {{ overall.label }}
              </div>
              <h1 class="hero-host font-mono">
                {{ state.sys.computerName || 'UNKNOWN HOST' }}
              </h1>
              <p class="hero-sub">服务节点实时资源监控 · SERVER TELEMETRY</p>
              <div class="hero-chips">
                <span class="hero-chip">
                  <ElIcon :size="13"><Location /></ElIcon>
                  {{ state.sys.computerIp || '--' }}
                </span>
                <span class="hero-chip">
                  <ElIcon :size="13"><Monitor /></ElIcon>
                  {{ state.sys.osName || '--' }} /
                  {{ state.sys.osArch || '--' }}
                </span>
              </div>
            </div>

            <div class="hero-actions">
              <div class="sync-meta">
                <span class="meta-label">LAST SYNC</span>
                <span class="meta-value font-mono tabular-nums">
                  {{ lastSyncText }}
                </span>
              </div>
              <ElButton
                class="hero-refresh"
                :icon="Refresh"
                :loading="manualRefreshing"
                @click="fetchServer(true)"
              >
                刷新
              </ElButton>
              <label class="auto-refresh">
                <ElSwitch v-model="autoRefresh" size="small" />
                <span>{{ AUTO_REFRESH_SECONDS }}s 自动刷新</span>
              </label>
            </div>
          </div>

          <div class="hero-stats">
            <div class="hero-stat">
              <span class="stat-label">UPTIME / 运行时长</span>
              <strong class="stat-value font-mono">{{ uptimeText }}</strong>
              <span class="stat-hint">
                启动于 {{ state.jvm.startTime || '--' }}
              </span>
            </div>
            <div class="hero-stat">
              <span class="stat-label">PEAK LOAD / 资源峰值</span>
              <strong
                class="stat-value font-mono tabular-nums"
                :class="`tone-${overall.tone}`"
              >
                {{ peakUsage.toFixed(1) }}%
              </strong>
              <span class="stat-hint">CPU / 内存 / JVM / 磁盘 最高占用</span>
            </div>
            <div class="hero-stat">
              <span class="stat-label">DISK / 磁盘挂载</span>
              <strong class="stat-value font-mono">
                {{ state.sysFiles.length }} <small>个</small>
              </strong>
              <span class="stat-hint">{{ diskCaption }}</span>
            </div>
          </div>
        </div>
      </section>

      <div class="metric-grid">
        <MetricGauge
          accent="#22d3ee"
          accent-end="#3b82f6"
          :caption="cpuCaption"
          :icon="Cpu"
          :percent="cpuUsage"
          title="CPU 使用率"
        >
          <template #detail>
            <div class="cpu-breakdown">
              <span>
                用户
                <b class="font-mono"
                  >{{ formatPercentValue(state.cpu.used) }}%</b
                >
              </span>
              <span>
                系统
                <b class="font-mono"
                  >{{ formatPercentValue(state.cpu.sys) }}%</b
                >
              </span>
              <span>
                空闲
                <b class="font-mono"
                  >{{ formatPercentValue(state.cpu.free) }}%</b
                >
              </span>
            </div>
          </template>
        </MetricGauge>

        <MetricGauge
          accent="#a78bfa"
          accent-end="#6366f1"
          :caption="memoryCaption"
          :icon="Coin"
          :percent="memUsage"
          title="内存使用率"
        >
          <template #detail>
            <div class="metric-line">
              <span>{{ memoryDetail }}</span>
            </div>
          </template>
        </MetricGauge>

        <MetricGauge
          accent="#34d399"
          accent-end="#10b981"
          :caption="jvmCaption"
          :icon="Coffee"
          :percent="jvmUsage"
          title="JVM 堆内存"
        >
          <template #detail>
            <div class="metric-line">
              <span>{{ jvmDetail }}</span>
            </div>
          </template>
        </MetricGauge>

        <MetricGauge
          accent="#fbbf24"
          accent-end="#f97316"
          :caption="diskCaption"
          :icon="FolderOpened"
          :percent="diskUsage"
          title="磁盘占用率"
        >
          <template #detail>
            <div class="metric-line">
              <span>{{ diskDetail }}</span>
            </div>
          </template>
        </MetricGauge>
      </div>

      <div class="main-grid">
        <div class="panel trend-panel">
          <div class="panel-head">
            <div class="panel-title">
              <span class="title-bar"></span>
              <ElIcon class="title-icon" :size="16"><TrendCharts /></ElIcon>
              <span>实时资源曲线</span>
            </div>
            <span class="panel-sub">
              最近 {{ samples.length }} 次采样 · {{ AUTO_REFRESH_SECONDS }}s /
              次
            </span>
          </div>
          <div class="chart-wrap">
            <EchartsUI ref="chartRef" height="268px" />
            <div v-if="samples.length < 2" class="chart-hint">
              正在积累实时采样…
            </div>
          </div>
        </div>

        <div class="panel env-panel">
          <div class="panel-head">
            <div class="panel-title">
              <span class="title-bar"></span>
              <ElIcon class="title-icon" :size="16"><Monitor /></ElIcon>
              <span>运行环境</span>
            </div>
          </div>

          <div class="env-block">
            <div class="env-block-title">
              <ElIcon :size="13"><Monitor /></ElIcon>
              服务器
            </div>
            <div class="env-row">
              <span class="env-label">主机名称</span>
              <span class="env-value font-mono" :title="state.sys.computerName">
                {{ state.sys.computerName || '--' }}
              </span>
            </div>
            <div class="env-row">
              <span class="env-label">IP 地址</span>
              <span class="env-value font-mono">{{
                state.sys.computerIp || '--'
              }}</span>
            </div>
            <div class="env-row">
              <span class="env-label">操作系统</span>
              <span class="env-value font-mono">
                {{ state.sys.osName || '--' }} / {{ state.sys.osArch || '--' }}
              </span>
            </div>
            <div class="env-row">
              <span class="env-label">项目路径</span>
              <span class="env-value font-mono" :title="state.sys.userDir">
                {{ state.sys.userDir || '--' }}
              </span>
            </div>
          </div>

          <div class="env-block">
            <div class="env-block-title">
              <ElIcon :size="13"><Coffee /></ElIcon>
              Java 虚拟机
            </div>
            <div class="env-row">
              <span class="env-label">Java 名称</span>
              <span class="env-value font-mono" :title="state.jvm.name">
                {{ state.jvm.name || '--' }}
              </span>
            </div>
            <div class="env-row">
              <span class="env-label">Java 版本</span>
              <span class="env-value font-mono">{{
                state.jvm.version || '--'
              }}</span>
            </div>
            <div class="env-row">
              <span class="env-label">启动时间</span>
              <span class="env-value font-mono tabular-nums">
                {{ state.jvm.startTime || '--' }}
              </span>
            </div>
            <div class="env-row">
              <span class="env-label">安装路径</span>
              <span class="env-value font-mono" :title="state.jvm.home">
                {{ state.jvm.home || '--' }}
              </span>
            </div>
            <div class="env-row">
              <span class="env-label">最大内存</span>
              <span class="env-value font-mono">
                {{ formatMegabytes(state.jvm.max) }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <div class="panel disk-panel">
        <div class="panel-head">
          <div class="panel-title">
            <span class="title-bar"></span>
            <ElIcon class="title-icon" :size="16"><FolderOpened /></ElIcon>
            <span>磁盘存储</span>
          </div>
          <span class="panel-sub">
            {{ state.sysFiles.length }} 个挂载点 · 整体占用
            {{ formatPercentValue(diskAggregateUsage) }}%
          </span>
        </div>

        <div v-if="state.sysFiles.length > 0" class="disk-grid">
          <div
            v-for="disk in state.sysFiles"
            :key="disk.dirName"
            class="disk-card"
          >
            <div class="disk-head">
              <span class="disk-mount" :title="disk.dirName">
                <ElIcon :size="14"><FolderOpened /></ElIcon>
                {{ disk.dirName }}
              </span>
              <span class="disk-fs">{{
                disk.sysTypeName || disk.typeName || '--'
              }}</span>
            </div>
            <div class="disk-type" :title="disk.typeName">
              {{ disk.typeName || '本地文件系统' }}
            </div>
            <div class="disk-numbers">
              <span
                class="disk-percent font-mono tabular-nums"
                :class="diskBarClass(disk.usage)"
              >
                {{ formatPercentValue(disk.usage) }}%
              </span>
              <span class="disk-capacity font-mono">
                {{ disk.used }} / {{ disk.total }}
              </span>
            </div>
            <div class="disk-bar">
              <div
                class="disk-bar-inner"
                :style="{
                  background: diskBarBackground(disk.usage),
                  width: `${clampPercent(disk.usage)}%`,
                }"
              ></div>
            </div>
            <div class="disk-foot">
              <span>可用 {{ disk.free }}</span>
              <span>已用 {{ disk.used }}</span>
            </div>
          </div>
        </div>
        <div v-else class="disk-empty">暂无磁盘数据</div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.monitor-page {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 16px;
  min-height: 0;
  padding-right: 2px;
  overflow-y: auto;
}

.monitor-hero {
  position: relative;
  flex: none;
  padding: 22px 24px 0;
  overflow: hidden;
  color: #e2e8f0;
  background: radial-gradient(
    120% 150% at 88% 0%,
    #0f3a5f 0%,
    #08233d 46%,
    #051526 100%
  );
  border: 1px solid rgb(56 189 248 / 18%);
  border-radius: 16px;
  box-shadow: 0 18px 40px -30px rgb(2 20 40 / 90%);

  &::before {
    position: absolute;
    inset: 0;
    pointer-events: none;
    content: '';
    background-image:
      linear-gradient(rgb(125 211 252 / 6%) 1px, transparent 1px),
      linear-gradient(90deg, rgb(125 211 252 / 6%) 1px, transparent 1px);
    background-size: 34px 34px;
    mask-image: linear-gradient(to bottom, rgb(0 0 0 / 90%), transparent 85%);
  }

  &::after {
    position: absolute;
    top: -140px;
    right: 6%;
    width: 340px;
    height: 340px;
    pointer-events: none;
    content: '';
    background: radial-gradient(circle, rgb(34 211 238 / 22%), transparent 65%);
    filter: blur(8px);
  }
}

.hero-inner {
  position: relative;
  z-index: 1;
}

.hero-main {
  display: flex;
  gap: 24px;
  align-items: flex-start;
  justify-content: space-between;
}

.hero-identity {
  min-width: 0;
}

.hero-badge {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  padding: 4px 11px;
  font-size: 11px;
  font-weight: 500;
  color: #6ee7b7;
  letter-spacing: 0.08em;
  background: rgb(16 185 129 / 12%);
  border: 1px solid rgb(52 211 153 / 28%);
  border-radius: 999px;

  &.is-warning {
    color: #fcd34d;
    background: rgb(245 158 11 / 14%);
    border-color: rgb(251 191 36 / 32%);
  }

  &.is-danger {
    color: #fda4af;
    background: rgb(244 63 94 / 14%);
    border-color: rgb(251 113 133 / 35%);
  }
}

.pulse-dot {
  width: 7px;
  height: 7px;
  background: currentcolor;
  border-radius: 50%;
  animation: pulse-ring 2s cubic-bezier(0.66, 0, 0, 1) infinite;
}

@keyframes pulse-ring {
  0% {
    box-shadow: 0 0 0 0 color-mix(in srgb, currentcolor 55%, transparent);
  }

  70% {
    box-shadow: 0 0 0 7px transparent;
  }

  100% {
    box-shadow: 0 0 0 0 transparent;
  }
}

.hero-host {
  margin: 10px 0 4px;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.2;
  color: #f8fafc;
  letter-spacing: -0.01em;
}

.hero-sub {
  font-size: 11px;
  color: #94a3b8;
  letter-spacing: 0.08em;
}

.hero-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.hero-chip {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 4px 9px;
  font-family: ui-monospace, 'SF Mono', menlo, consolas, monospace;
  font-size: 12px;
  color: #bae6fd;
  background: rgb(8 47 73 / 55%);
  border: 1px solid rgb(56 189 248 / 18%);
  border-radius: 7px;
}

.hero-actions {
  display: flex;
  flex: none;
  gap: 14px;
  align-items: center;
  padding-top: 4px;
}

.sync-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  text-align: right;
}

.meta-label {
  font-size: 10px;
  color: #7dd3fc;
  letter-spacing: 0.16em;
}

.meta-value {
  font-size: 15px;
  font-weight: 600;
  color: #e2e8f0;
}

.hero-refresh.el-button {
  color: #bae6fd;
  background: rgb(56 189 248 / 10%);
  border-color: rgb(56 189 248 / 35%);

  &:hover,
  &:focus {
    color: #e0f2fe;
    background: rgb(56 189 248 / 18%);
    border-color: rgb(56 189 248 / 55%);
  }
}

.auto-refresh {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 12px;
  color: #94a3b8;

  :deep(.el-switch) {
    --el-switch-off-color: rgb(148 163 184 / 35%);
    --el-switch-on-color: #22d3ee;
  }
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: 18px;
  border-top: 1px solid rgb(56 189 248 / 14%);
}

.hero-stat {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 14px 18px 16px 0;

  & + & {
    padding-left: 18px;
    border-left: 1px solid rgb(56 189 248 / 12%);
  }
}

.stat-label {
  font-size: 10px;
  color: #7dd3fc;
  letter-spacing: 0.14em;
}

.stat-value {
  font-size: 19px;
  font-weight: 600;
  color: #f8fafc;

  small {
    font-size: 12px;
    color: #94a3b8;
  }
}

.tone-success {
  color: #34d399;
}

.tone-warning {
  color: #fbbf24;
}

.tone-danger {
  color: #fb7185;
}

.stat-hint {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  color: #64748b;
  white-space: nowrap;
}

.metric-grid {
  display: grid;
  flex: none;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.cpu-breakdown {
  display: flex;
  gap: 8px;
  justify-content: space-between;

  b {
    font-weight: 600;
    color: var(--el-text-color-regular);
  }
}

.metric-line {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.main-grid {
  display: grid;
  flex: none;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: 16px;
}

.panel {
  padding: 16px 18px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 14px;
  box-shadow: 0 1px 2px rgb(15 23 42 / 4%);
}

.panel-head {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.panel-title {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.title-bar {
  width: 3px;
  height: 14px;
  background: linear-gradient(180deg, #22d3ee, #3b82f6);
  border-radius: 2px;
}

.title-icon {
  color: #0ea5e9;
}

.panel-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.chart-wrap {
  position: relative;
}

.chart-hint {
  position: absolute;
  right: 0;
  bottom: 10px;
  left: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
  pointer-events: none;
}

.env-panel {
  display: flex;
  flex-direction: column;
}

.env-block + .env-block {
  padding-top: 12px;
  margin-top: 12px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.env-block-title {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--el-color-primary);
  letter-spacing: 0.06em;
}

.env-row {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 5px 0;
  font-size: 12px;
}

.env-label {
  flex: none;
  color: var(--el-text-color-secondary);
}

.env-value {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--el-text-color-regular);
  text-align: right;
  white-space: nowrap;
}

.disk-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 12px;
}

.disk-card {
  padding: 14px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  transition:
    border-color 0.2s ease,
    transform 0.2s ease;

  &:hover {
    border-color: var(--el-color-primary-light-5);
    transform: translateY(-1px);
  }
}

.disk-head {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.disk-mount {
  display: flex;
  gap: 6px;
  align-items: center;
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: ui-monospace, 'SF Mono', menlo, consolas, monospace;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.disk-fs {
  flex: none;
  padding: 1px 7px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 5px;
}

.disk-type {
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.disk-numbers {
  display: flex;
  gap: 8px;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 10px;
}

.disk-percent {
  font-size: 20px;
  font-weight: 700;
  color: var(--el-text-color-primary);

  &.is-warning {
    color: var(--el-color-warning);
  }

  &.is-danger {
    color: var(--el-color-danger);
  }
}

.disk-capacity {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.disk-bar {
  height: 6px;
  margin-top: 8px;
  overflow: hidden;
  background: var(--el-fill-color);
  border-radius: 999px;
}

.disk-bar-inner {
  height: 100%;
  border-radius: inherit;
  transition: width 0.8s cubic-bezier(0.22, 1, 0.36, 1);
}

.disk-foot {
  display: flex;
  gap: 8px;
  justify-content: space-between;
  margin-top: 6px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.disk-empty {
  padding: 24px 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

@media (max-width: 1280px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .main-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 960px) {
  .hero-main {
    flex-direction: column;
  }

  .hero-actions {
    flex-wrap: wrap;
    padding-top: 0;
  }

  .hero-stats {
    grid-template-columns: minmax(0, 1fr);
  }

  .hero-stat {
    padding: 12px 0;

    & + & {
      padding-left: 0;
      border-top: 1px solid rgb(56 189 248 / 12%);
      border-left: none;
    }
  }
}

@media (max-width: 720px) {
  .metric-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
