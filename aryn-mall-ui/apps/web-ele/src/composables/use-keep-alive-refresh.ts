import { onActivated } from 'vue';

/**
 * keepAlive 列表页回到前台时的静默刷新。
 *
 * 背景：菜单页开启 meta.keepAlive 后，切到详情页再返回列表时组件不会重新挂载，
 * 首次查询不会重跑——查询条件/页码/数据原样保留（这正是开缓存的目的），
 * 但列表停留期间发生的数据变化也看不到。本组合式函数在页面重新激活时
 * 用**当前条件**补一次查询，既保住筛选状态又让数据保持新鲜。
 *
 * onActivated 在首次挂载后也会触发一次（紧跟 onMounted），那次必须跳过，
 * 否则列表页首进就会查两遍；页面未启用 keepAlive 时该钩子不会触发，天然无副作用。
 */
export function useKeepAliveRefresh(refresh: () => unknown) {
  let activated = false;
  onActivated(() => {
    if (!activated) {
      activated = true;
      return;
    }
    refresh();
  });
}
