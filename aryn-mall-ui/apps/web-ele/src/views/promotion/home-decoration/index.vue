<script setup lang="ts">
import { ElButton, ElContainer, ElHeader, ElMain } from 'element-plus';

import ComponentPanel from './components/component-panel.vue';
import PhonePreview from './components/phone-preview.vue';
import SettingsPanel from './components/settings-panel.vue';
import { useHomeDecoration } from './use-home-decoration';

const {
  active,
  addComponent,
  clearComponent,
  componentBase,
  componentHome,
  components,
  componentTool,
  delComponent,
  downComponent,
  dragOptions,
  loading,
  onBack,
  onComponent,
  onDragStart,
  onDrop,
  onSubmit,
  selectedComponent,
  selectedId,
  showSetting,
  updateSelectedFormData,
  upComponent,
} = useHomeDecoration();
</script>

<template>
  <div class="home-decoration-page hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElContainer>
        <ElHeader>
          <div class="diy-header">
            <div class="header-left">首页装修</div>
            <div class="header-right">
              <ElButton plain @click="onBack">返回</ElButton>
              <ElButton plain :disabled="loading" @click="onSubmit">
                保存
              </ElButton>
            </div>
          </div>
        </ElHeader>
        <ElMain>
          <div v-loading="loading" class="diy-editor">
            <ComponentPanel
              v-model:active="active"
              v-model:components="components"
              :component-base="componentBase"
              :component-home="componentHome"
              :component-tool="componentTool"
              :drag-options="dragOptions"
              :selected-id="selectedId"
              @add="addComponent"
              @delete="delComponent"
              @drag-start="onDragStart"
              @drop="onDrop"
              @select="onComponent"
            />
            <PhonePreview
              v-model:components="components"
              :drag-options="dragOptions"
              :selected-id="selectedId"
              @delete="delComponent"
              @down="downComponent"
              @drop="onDrop"
              @select="onComponent"
              @up="upComponent"
            />
            <SettingsPanel
              :component="selectedComponent"
              :show-setting="showSetting"
              @clear="clearComponent"
              @update-form-data="updateSelectedFormData"
            />
          </div>
        </ElMain>
      </ElContainer>
    </div>
  </div>
</template>

<style lang="scss">
.home-decoration-page {
  height: 100%;
  padding: 0 !important;

  .hx-layout-container-view,
  .el-container {
    height: 100%;
    padding: 0 !important;
  }

  .el-header,
  .el-main {
    padding: 0 !important;
  }

  .diy-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 60px;
    padding: 0 20px;
    background-color: var(--el-color-primary);
    border-bottom: 1px solid var(--el-border-color-light);

    .header-left {
      font-size: 16px;
      font-weight: 700;
      color: var(--el-text-color-primary);
    }

    .header-right {
      display: flex;
      gap: 16px;

      .el-button {
        width: 160px;
      }
    }
  }

  .diy-editor {
    display: flex;
    height: 100%;
  }

  .component-panel {
    display: flex;
    height: 100%;
  }

  .left-panel {
    width: 230px;
    height: 100%;
    padding: 0 5px;
    overflow-y: auto;
    background-color: hsl(var(--card));
    border: 1px solid hsl(var(--overlay-content));
    box-shadow: 0 0 6px rgb(0 0 0 / 10%);

    .el-collapse {
      border: 0;
    }

    .el-collapse-item__header {
      border: 0;
    }

    .left-group {
      display: flex;
      flex-wrap: wrap;
      gap: 5px;
    }

    .group-item {
      display: flex;
      flex: 0 0 calc(100% / 3 - 4px);
      flex-direction: column;
      gap: 4px;
      align-items: center;
      padding: 8px 4px;
      color: inherit;
      cursor: pointer;
      background: transparent;
      border: 0;

      &:hover {
        background-color: #e5e5e5;
      }

      .icon {
        width: 24px;
        height: 24px;
      }
    }
  }

  .selected-component {
    width: 180px;
    height: 100%;
    overflow-y: auto;
    background-color: hsl(var(--card));
    border: 1px solid hsl(var(--overlay-content));

    .title {
      padding: 10px;
      font-weight: 700;
      border-bottom: 1px solid #e5e5e5;
    }

    .selected-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 10px;
      cursor: pointer;

      &:hover,
      &.selected-active {
        background-color: #e5e5e5;

        .selected-del {
          opacity: 1;
        }
      }
    }

    .selected-left {
      display: flex;
      align-items: center;
      min-width: 0;
    }

    .selected-title {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .selected-del {
      display: flex;
      padding: 4px;
      color: inherit;
      cursor: pointer;
      background: transparent;
      border: 0;
      opacity: 0;
    }
  }

  .middle {
    position: relative;
    flex: 1;
    height: 100%;
    overflow-y: auto;
    background: hsl(var(--overlay-content));
  }

  .middle-content {
    width: 375px;
    min-height: 667px;
    margin: 60px auto;
    background-color: #fff;
    box-shadow: 0 0 28px rgb(0 0 0 / 10%);
  }

  .content-warp {
    width: 100%;
    min-height: 667px;
    padding-bottom: 30px;
  }

  .top-mobile-nav {
    position: relative;

    .top-mobile-image {
      display: block;
      width: 375px;
      height: auto;
    }

    .top-mobile-title {
      position: absolute;
      top: 60%;
      left: 50%;
      font-size: 18px;
      font-weight: 700;
      transform: translate(-50%, -50%);
    }
  }

  .mobile-item {
    position: relative;
    width: 375px;

    &.active::before,
    &:hover::before {
      position: absolute;
      inset: 0;
      z-index: 1;
      pointer-events: none;
      content: '';
      border: 2px solid #155bd4;
    }
  }

  .preview-title {
    position: absolute;
    top: 10px;
    right: 390px;
    width: 90px;
    padding: 4px 8px;
    text-align: center;
    background-color: hsl(var(--card));
    border-radius: 2px;
    box-shadow: 0 0 2px var(--el-color-primary);
    transform: translateY(-50%);
  }

  .preview-btn-group {
    position: absolute;
    top: 0;
    right: -32px;
    z-index: 2;
    display: flex;
    flex-direction: column;
    width: 30px;
    background-color: var(--el-color-primary);
    border-radius: 5px;

    .btn-item {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 32px;
      color: #fff;
    }
  }

  .right-panel {
    width: 590px;
    height: 100%;
    overflow-y: auto;
    background-color: hsl(var(--card));
    border: 1px solid var(--el-card-border-color);
    box-shadow: 0 0 6px rgb(0 0 0 / 10%);

    .settings-content {
      display: flex;
    }

    .preview-page-config {
      padding: 20px;
    }

    .component-settings {
      width: 480px;
    }
  }

  .empty-text {
    padding: 24px;
    color: #9ca3af;
  }
}
</style>
