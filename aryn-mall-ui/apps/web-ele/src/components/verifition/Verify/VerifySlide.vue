<script type="text/babel">
import {
  computed,
  getCurrentInstance,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  toRefs,
  watch,
} from 'vue';

import { reqCheck, reqGet } from '../api/index';
/**
 * VerifySlide
 * @description 滑块
 */
import { aesEncrypt } from './../utils/ase';
import { resetSize } from './../utils/util';
//  "captchaType":"blockPuzzle",
export default {
  name: 'VerifySlide',
  props: {
    captchaType: {
      type: String,
      default: 'blockPuzzle',
    },
    type: {
      type: String,
      default: '1',
    },
    // 弹出式pop，固定fixed
    mode: {
      type: String,
      default: 'fixed',
    },
    space: {
      type: Number,
      default: 5,
    },
    explain: {
      type: String,
      default: '向右滑动完成验证',
    },
    imgSize: {
      type: Object,
      default() {
        return {
          width: '310px',
          height: '155px',
        };
      },
    },
    blockSize: {
      type: Object,
      default() {
        return {
          width: '50px',
          height: '50px',
        };
      },
    },
    barSize: {
      type: Object,
      default() {
        return {
          width: '310px',
          height: '40px',
        };
      },
    },
  },
  setup(props) {
    const { mode, captchaType, type, blockSize, explain } = toRefs(props);
    const { proxy } = getCurrentInstance();
    const backImgBase = ref(''); // 验证码背景图片
    const backToken = ref(''); // 后端返回的唯一token值
    const blockBackImgBase = ref(''); // 验证滑块的背景图片
    const finishText = ref('');
    const isEnd = ref(false); // 是够验证完成
    const left = ref(0);
    const leftBarWidth = ref(undefined);
    const passFlag = ref(''); // 是否通过的标识
    const secretKey = ref(''); // 后端返回的ase加密秘钥
    const setSize = reactive({
      imgHeight: 0,
      imgWidth: 0,
      barHeight: 0,
      barWidth: 0,
    });
    const showRefresh = ref(true);
    const startLeft = ref(0);
    const state = ref('idle'); // idle | moving | success | error
    const status = ref(false); // 鼠标状态
    const text = ref('');
    const tipWords = ref('');
    const top = ref(0);
    const moveBlockLeft = ref(undefined);
    const transitionLeft = ref('');

    const handleMove = (event) => move(event);
    const handleEnd = () => end();
    const preventSelection = (event) => event.preventDefault();

    const barArea = computed(() => {
      return proxy.$el.querySelector('.verify-bar-area');
    });
    function init() {
      text.value = explain.value;
      getPictrue();
      nextTick(() => {
        const { imgHeight, imgWidth, barHeight, barWidth } = resetSize(proxy);
        setSize.imgHeight = imgHeight;
        setSize.imgWidth = imgWidth;
        setSize.barHeight = barHeight;
        setSize.barWidth = barWidth;
        proxy.$parent.$emit('ready', proxy);
      });

      window.removeEventListener('touchmove', handleMove);
      window.removeEventListener('mousemove', handleMove);

      // 鼠标松开
      window.removeEventListener('touchend', handleEnd);
      window.removeEventListener('mouseup', handleEnd);

      window.addEventListener('touchmove', handleMove);
      window.addEventListener('mousemove', handleMove);

      // 鼠标松开
      window.addEventListener('touchend', handleEnd);
      window.addEventListener('mouseup', handleEnd);
    }
    watch(type, () => {
      init();
    });
    onMounted(() => {
      // 禁止拖拽
      init();
      proxy.$el.addEventListener('selectstart', preventSelection);
    });
    onBeforeUnmount(() => {
      window.removeEventListener('touchmove', handleMove);
      window.removeEventListener('mousemove', handleMove);
      window.removeEventListener('touchend', handleEnd);
      window.removeEventListener('mouseup', handleEnd);
      proxy.$el?.removeEventListener('selectstart', preventSelection);
    });
    // 鼠标按下
    function start(e) {
      e = e || window.event;
      const x = e.touches ? e.touches[0].pageX : e.clientX;
      startLeft.value = Math.floor(
        x - barArea.value.getBoundingClientRect().left,
      );
      if (!isEnd.value) {
        text.value = '';
        state.value = 'moving';
        e.stopPropagation();
        status.value = true;
      }
    }
    // 鼠标移动
    function move(e) {
      e = e || window.event;
      if (status.value && !isEnd.value) {
        const x = e.touches ? e.touches[0].pageX : e.clientX;
        const bar_area_left = barArea.value.getBoundingClientRect().left;
        let move_block_left = x - bar_area_left; // 小方块相对于父元素的left值
        if (
          move_block_left >=
          barArea.value.offsetWidth -
            Number.parseInt(Number.parseInt(blockSize.value.width) / 2) -
            2
        ) {
          move_block_left =
            barArea.value.offsetWidth -
            Number.parseInt(Number.parseInt(blockSize.value.width) / 2) -
            2;
        }
        if (move_block_left <= 0) {
          move_block_left = Number.parseInt(
            Number.parseInt(blockSize.value.width) / 2,
          );
        }
        // 拖动后小方块的left值
        moveBlockLeft.value = `${move_block_left - startLeft.value}px`;
        leftBarWidth.value = `${move_block_left - startLeft.value}px`;
      }
    }

    // 鼠标松开
    function end() {
      // 判断是否重合
      if (status.value && !isEnd.value) {
        let moveLeftDistance = Number.parseInt(
          (moveBlockLeft.value || '').replace('px', ''),
        );
        moveLeftDistance =
          (moveLeftDistance * 310) / Number.parseInt(setSize.imgWidth);
        const data = {
          captchaType: captchaType.value,
          pointJson: secretKey.value
            ? aesEncrypt(
                JSON.stringify({ x: moveLeftDistance, y: 5 }),
                secretKey.value,
              )
            : JSON.stringify({ x: moveLeftDistance, y: 5 }),
          token: backToken.value,
        };
        reqCheck(data).then((response) => {
          const res = response;
          if (res.repCode === '0000') {
            state.value = 'success';
            showRefresh.value = false;
            isEnd.value = true;
            if (mode.value === 'pop') {
              setTimeout(() => {
                proxy.$parent.clickShow = false;
                refresh();
              }, 1500);
            }
            passFlag.value = true;
            tipWords.value = '验证通过';
            const captchaVerification = secretKey.value
              ? aesEncrypt(
                  `${backToken.value}---${JSON.stringify({
                    x: moveLeftDistance,
                    y: 5,
                  })}`,
                  secretKey.value,
                )
              : `${backToken.value}---${JSON.stringify({
                  x: moveLeftDistance,
                  y: 5,
                })}`;
            setTimeout(() => {
              tipWords.value = '';
              proxy.$parent.closeBox();
              proxy.$parent.$emit('success', { captchaVerification });
            }, 1000);
          } else {
            state.value = 'error';
            passFlag.value = false;
            setTimeout(() => {
              refresh();
            }, 1000);
            proxy.$parent.$emit('error', proxy);
            tipWords.value = '验证失败';
            setTimeout(() => {
              tipWords.value = '';
            }, 1000);
          }
        });
        status.value = false;
      }
    }

    const refresh = () => {
      showRefresh.value = true;
      finishText.value = '';

      transitionLeft.value = 'left .3s';
      moveBlockLeft.value = 0;

      leftBarWidth.value = undefined;
      state.value = 'idle';
      isEnd.value = false;

      getPictrue();
      setTimeout(() => {
        transitionLeft.value = '';
        text.value = explain.value;
      }, 300);
    };

    // 请求背景图片和验证图片
    function getPictrue() {
      const data = {
        captchaType: captchaType.value,
      };
      reqGet(data).then((response) => {
        const res = response;

        if (res.repCode === '0000') {
          backImgBase.value = res.repData.originalImageBase64;
          blockBackImgBase.value = res.repData.jigsawImageBase64;
          backToken.value = res.repData.token;
          secretKey.value = res.repData.secretKey;
        } else {
          tipWords.value = res.repMsg;
        }
      });
    }
    return {
      secretKey, // 后端返回的ase加密秘钥
      passFlag, // 是否通过的标识
      backImgBase, // 验证码背景图片
      blockBackImgBase, // 验证滑块的背景图片
      backToken, // 后端返回的唯一token值
      tipWords,
      text,
      finishText,
      setSize,
      top,
      left,
      moveBlockLeft,
      leftBarWidth,
      state,
      status, // 鼠标状态
      isEnd, // 是够验证完成
      showRefresh,
      transitionLeft,
      barArea,
      refresh,
      start,
    };
  },
};
</script>
<template>
  <div style="position: relative">
    <div
      v-if="type === '2'"
      class="verify-img-out"
      :style="{ height: `${parseInt(setSize.imgHeight) + space}px` }"
    >
      <div
        class="verify-img-panel"
        :style="{ width: setSize.imgWidth, height: setSize.imgHeight }"
      >
        <img
          :src="`data:image/png;base64,${backImgBase}`"
          alt="验证码背景"
          draggable="false"
          style="display: block; width: 100%; height: 100%"
        />
        <button
          v-show="showRefresh"
          type="button"
          class="verify-refresh"
          aria-label="换一张"
          @click="refresh"
        >
          <svg aria-hidden="true" viewBox="0 0 24 24">
            <path d="M21 12a9 9 0 1 1-2.64-6.36" />
            <path d="M21 3v6h-6" />
          </svg>
        </button>
        <transition name="tips">
          <span
            class="verify-tips"
            v-if="tipWords"
            :class="passFlag ? 'suc-bg' : 'err-bg'"
            >{{ tipWords }}</span
          >
        </transition>
      </div>
    </div>
    <!-- 公共部分 -->
    <div
      class="verify-bar-area"
      :style="{
        width: setSize.imgWidth,
        height: barSize.height,
        'line-height': barSize.height,
      }"
    >
      <span class="verify-msg" v-text="text"></span>
      <div
        class="verify-left-bar"
        :class="`is-${state}`"
        :style="{
          width: leftBarWidth !== undefined ? leftBarWidth : barSize.height,
          height: barSize.height,
        }"
      >
        <span class="verify-msg" v-text="finishText"></span>
        <div
          class="verify-move-block"
          :class="`is-${state}`"
          @touchstart="start"
          @mousedown="start"
          :style="{
            width: barSize.height,
            height: barSize.height,
            left: moveBlockLeft,
            transition: transitionLeft,
          }"
        >
          <svg
            v-if="state === 'success'"
            class="verify-icon"
            aria-hidden="true"
            viewBox="0 0 24 24"
          >
            <path d="m5 12 4 4 10-10" />
          </svg>
          <svg
            v-else-if="state === 'error'"
            class="verify-icon"
            aria-hidden="true"
            viewBox="0 0 24 24"
          >
            <path d="M6 6l12 12M18 6 6 18" />
          </svg>
          <svg
            v-else
            class="verify-icon"
            aria-hidden="true"
            viewBox="0 0 24 24"
          >
            <path d="m9 6 6 6-6 6" />
          </svg>
          <div
            v-if="type === '2'"
            class="verify-sub-block"
            :style="{
              width: `${Math.floor((parseInt(setSize.imgWidth) * 47) / 310)}px`,
              height: setSize.imgHeight,
              top: `-${parseInt(setSize.imgHeight) + space}px`,
              'background-size': `${setSize.imgWidth} ${setSize.imgHeight}`,
            }"
          >
            <img
              :src="`data:image/png;base64,${blockBackImgBase}`"
              alt=""
              draggable="false"
              style="
                display: block;
                width: 100%;
                height: 100%;
                -webkit-user-drag: none;
              "
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
