<script setup lang="ts">
import { reactive, ref } from 'vue';

import {
  ElCard,
  ElCol,
  ElDescriptions,
  ElDescriptionsItem,
  ElRow,
  ElTable,
  ElTableColumn,
} from 'element-plus';

import { getServer } from '#/api/upms/sys-server';

interface Jvm {
  total: number;
  used: number;
  free: number;
  usage: number;
  name: string;
  version: string;
  startTime: string;
  runTime: string;
  home: string;
}
interface Sys {
  computerName: string;
  osName: string;
  computerIp: string;
  osArch: string;
  userDir: string;
}
interface DataState {
  cpuList: Array<any>;
  jvm: Jvm;
  memList: Array<any>;
  sys: Sys;
  sysFiles: Array<any>;
}

const state = reactive<DataState>({
  cpuList: [],
  jvm: {
    total: 0,
    used: 0,
    free: 0,
    usage: 0,
    name: '',
    version: '',
    startTime: '',
    runTime: '',
    home: '',
  },
  memList: [],
  sys: {
    computerName: '',
    osName: '',
    computerIp: '',
    osArch: '',
    userDir: '',
  },
  sysFiles: [],
});

const loading = ref(false);

const initPage = () => {
  loading.value = true;
  getServer()
    .then((response) => {
      // const { cpu, mem, jvm, sys, sysFiles } = response;
      state.sysFiles = response.sysFiles;
      state.jvm = response.jvm;
      state.sys = response.sys;

      state.cpuList = [
        { attribute: '核心数', value: response.cpu.cpuNum },
        { attribute: '用户使用率', value: response.cpu.used },
        { attribute: '系统使用率', value: response.cpu.sys },
        { attribute: '当前空闲率', value: response.cpu.free },
      ];

      state.memList = [
        {
          attribute: '总内存',
          memValue: response.mem.total,
          jvmValue: state.jvm.total,
        },
        {
          attribute: '已用内存',
          memValue: response.mem.used,
          jvmValue: state.jvm.used,
        },
        {
          attribute: '剩余内存',
          memValue: response.mem.free,
          jvmValue: state.jvm.free,
        },
        {
          attribute: '使用率',
          memValue: response.mem.usage,
          jvmValue: state.jvm.usage,
        },
      ];

      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};

initPage();
</script>

<template>
  <div class="hx-layout-container" v-loading="loading">
    <div>
      <div>
        <ElRow>
          <ElCol :span="12">
            <ElCard class="box-card">
              <template #header>
                <div class="card-header">
                  <span>CPU</span>
                </div>
              </template>
              <ElTable :data="state.cpuList">
                <ElTableColumn prop="attribute" label="属性" />
                <ElTableColumn prop="value" label="值" />
              </ElTable>
            </ElCard>
          </ElCol>
          <ElCol :span="12">
            <ElCard class="box-card">
              <template #header>
                <div class="card-header">
                  <span>内存</span>
                </div>
              </template>
              <ElTable :data="state.memList">
                <ElTableColumn prop="attribute" label="属性" />
                <ElTableColumn prop="memValue" label="内存" />
                <ElTableColumn prop="jvmValue" label="JVM" />
              </ElTable>
            </ElCard>
          </ElCol>
        </ElRow>
        <ElCard class="box-card">
          <template #header>
            <div class="card-header">
              <span>服务器信息</span>
            </div>
          </template>
          <ElDescriptions :column="2" border>
            <ElDescriptionsItem label="服务器名称">
              {{ state.sys.computerName }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="操作系统">
              {{ state.sys.osName }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="服务器IP">
              {{ state.sys.computerIp }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="系统架构">
              {{ state.sys.osArch }}
            </ElDescriptionsItem>
          </ElDescriptions>
        </ElCard>
        <ElCard class="box-card">
          <template #header>
            <div class="card-header">
              <span>Java虚拟机信息</span>
            </div>
          </template>
          <ElDescriptions :column="2" border>
            <ElDescriptionsItem label="Java名称">
              {{ state.jvm.name }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="Java版本">
              {{ state.jvm.version }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="启动时间">
              {{ state.jvm.startTime }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="运行时长">
              {{ state.jvm.runTime }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="安装路径">
              {{ state.jvm.home }}
            </ElDescriptionsItem>
            <ElDescriptionsItem label="项目路径">
              {{ state.sys.userDir }}
            </ElDescriptionsItem>
          </ElDescriptions>
        </ElCard>
        <ElCard class="box-card">
          <template #header>
            <div class="card-header">
              <span>磁盘状态</span>
            </div>
          </template>
          <ElTable :data="state.sysFiles">
            <ElTableColumn prop="dirName" label="盘符路径" />
            <ElTableColumn prop="typeName" label="文件系统" />
            <ElTableColumn prop="sysTypeName" label="盘符类型" />
            <ElTableColumn prop="total" label="总大小" />
            <ElTableColumn prop="free" label="可用大小" />
            <ElTableColumn prop="used" label="已用大小" />
            <ElTableColumn prop="usage" label="已用百分比" />
          </ElTable>
        </ElCard>
      </div>
    </div>
  </div>
</template>
<style lang="scss" scoped>
.box-card {
  margin-top: 10px;
}

.hx-layout-container {
  overflow-y: auto;
}
</style>
