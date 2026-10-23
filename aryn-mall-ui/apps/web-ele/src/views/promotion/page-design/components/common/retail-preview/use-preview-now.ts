import { onBeforeUnmount, ref } from 'vue';

/**
 * 管理端画布预览的秒级时钟。
 *
 * 秒杀/拼团/折扣楼层的倒计时需要每秒刷新；C 端由 `useCountdownTicker`
 * 提供，管理端此前没有统一实现（`countdown`、`newbie-floor` 各自写了一份
 * `setInterval`）。这里收敛成本文件一个组合式函数，供三个营销楼层复用：
 * - 首次渲染立即有值，避免倒计时先显示 `--`；
 * - 组件卸载时清理定时器，避免画布切换楼层后定时器泄漏。
 */
export function usePreviewNow() {
  const now = ref(Date.now());
  const timer = setInterval(() => {
    now.value = Date.now();
  }, 1000);
  onBeforeUnmount(() => {
    clearInterval(timer);
  });
  return now;
}
