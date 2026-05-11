<script lang="ts" setup>
import { ref, watch } from 'vue';

import { Close, Edit } from '@element-plus/icons-vue';
import { ElIcon, ElImage } from 'element-plus';

import ImgDialog from './img-dialog/index.vue';

const props = defineProps({
  canChooseImagesNum: {
    type: Number,
    default: 1,
  },
  modelValue: {
    type: [String, Object, Array],
    default: '',
  },

  width: {
    type: Number,
    default: 80,
  },
  height: {
    type: Number,
    default: 80,
  },
});
const emit = defineEmits(['update:modelValue', 'change']);
const imageUrl = ref<any>(props.modelValue);
const imgDialogRef = ref();
const isIconIndex = ref(-1); // 是否展示icon
const addPic = () => {
  imgDialogRef.value.openDialog();
};

/**
 * 删除图片
 */
const deleteImg = (index: number) => {
  imageUrl.value.splice(index, 1);
  emit(
    'update:modelValue',
    props.canChooseImagesNum > 1 ? imageUrl.value : imageUrl.value[0],
  );
  emit(
    'change',
    props.canChooseImagesNum > 1 ? imageUrl.value : imageUrl.value[0],
  );
};
const changeImg = (images: any) => {
  imageUrl.value = images;
  emit(
    'update:modelValue',
    props.canChooseImagesNum > 1 ? imageUrl.value : imageUrl.value[0],
  );
  emit(
    'change',
    props.canChooseImagesNum > 1 ? imageUrl.value : imageUrl.value[0],
  );
};

watch(
  () => props.modelValue,
  (val) => {
    // 首先将值转为数组
    if (val) {
      const list = Array.isArray(val) ? val : val.split(',');
      imageUrl.value = list;
    } else {
      imageUrl.value = [];
    }
  },
  { deep: true, immediate: true },
);
</script>
<template>
  <div>
    <div class="material-pic-list">
      <template v-if="imageUrl && imageUrl.length > 0">
        <div
          v-for="(item, index) in imageUrl"
          :key="index"
          class="material-pic-item"
          :style="{ width: `${width}px`, height: `${height}px` }"
          @mouseover="isIconIndex = index"
          @mouseleave="isIconIndex = -1"
        >
          <!-- 蒙层和按钮 -->
          <div v-if="isIconIndex === index" class="image-overlay">
            <div class="image-action-icons">
              <ElIcon class="icon-btn" @click="addPic">
                <Edit />
              </ElIcon>
              <ElIcon class="icon-btn" @click="deleteImg(index)">
                <Close />
              </ElIcon>
            </div>
          </div>
          <ElImage
            class="image-inner"
            style="width: 100%; height: 100%"
            :src="item"
            :preview-src-list="[item]"
          />
        </div>
      </template>
      <div
        :style="{
          width: `${width}px`,
          height: `${height}px`,
          lineHeight: `${height}px`,
        }"
        v-if="!imageUrl || imageUrl.length < canChooseImagesNum"
        class="material-pic-item"
        @click="addPic"
      >
        + 图片
      </div>
    </div>
    <ImgDialog
      ref="imgDialogRef"
      :can-choose-images-num="canChooseImagesNum"
      @change-img="changeImg"
    />
  </div>
</template>
<style lang="scss" scoped>
.material-pic-list {
  display: flex;
  align-items: center;
  justify-content: flex-start;
}

.material-pic-item {
  position: relative;
  margin-right: 12px; // 新增：图片间距
  text-align: center;
  cursor: pointer;
  border: 1px solid #efefef;
  border-radius: 5px;
  transition: all 0.3s;
}

.material-pic-item:last-child {
  margin-right: 0;
}

.image-overlay {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  background-color: rgb(0 0 0 / 40%); // 蒙层颜色
  border-radius: 5px;
  transition: opacity 0.5s ease-in-out;
}

.image-action-icons {
  display: flex;
  gap: 10px;
}

.icon-btn {
  padding: 6px;
  font-size: 26px;
  color: #333;
  cursor: pointer;
  background: rgb(255 255 255 / 80%);
  border-radius: 50%;
  transition: background 0.2s;

  &:hover {
    background: rgb(255 255 255 / 100%);
  }
}

.image-inner {
  z-index: 1;
}

.material-main {
  display: flex;
  height: calc(max(100vh - 550px, 300px));
  min-height: 550px;
  margin-top: 15px;

  .category {
    box-sizing: border-box;
    display: flex;
    flex: none;
    flex-direction: column;
    width: 240px;
    margin-right: -1px;
    overflow: hidden;
    border: 1px solid #dcdee0;

    .category-tree {
      padding: 20px;
    }

    .footer {
      position: absolute;
      bottom: 2px;
      box-sizing: border-box;
      width: 240px;
      height: 56px;
      padding: 12px 0;
      text-align: center;
      box-shadow: 0 -1px 4px 0 rgb(0 0 0 / 10%);
    }
  }

  .media-list {
    box-sizing: border-box;
    flex: auto;
    padding: 12px;
    overflow-y: auto;
    border: 1px solid #dcdee0;

    .title {
      font-size: 16px;
      font-weight: 700;
      line-height: 24px;
      color: #323233;
    }
  }
}

.image-list {
  display: flex;
  flex-wrap: wrap;
  overflow: hidden;

  .image-item {
    position: relative;
    width: 160px;
    margin: 6px;

    .image-box {
      position: relative;
      box-sizing: content-box;
      width: 100%;
      height: 0;
      padding-bottom: 100%;
      overflow: hidden;
      cursor: pointer;
      background-color: #f7f8fa;
      background-repeat: no-repeat;
      background-position: 50%;
      background-size: cover;
    }

    .image-title {
      height: 20px;
      margin: 8px 0 4px;
      overflow: hidden;
      text-overflow: ellipsis;
      font-size: 14px;
      line-height: 20px;
      white-space: nowrap;
    }

    .image-bar {
      height: 20px;
      font-size: 0;

      button {
        padding: 0 3px;
        margin-left: 0;
      }
    }
  }
}

.popover-input {
  display: flex;
  justify-content: space-between;
}

.attachment-selected {
  position: absolute;
  top: 0;
  left: 0;
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  cursor: pointer;
  border: 2px solid #07d;
  opacity: 0;

  &:hover,
  &.active {
    opacity: 1;
  }

  i {
    position: absolute;
    top: 1px;
    right: 1px;
    z-index: 7;
    font-size: 12px;
    color: #fff;
  }

  span.after {
    position: absolute;
    top: 0;
    right: 0;
    z-index: 1;
    display: block;
    content: ' ';
    border: 14px solid #07d;
    border-bottom-color: transparent;
    border-left-color: transparent;
  }
}
</style>
