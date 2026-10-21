/**
 * 全局浮层层级（z-index）唯一出处。
 *
 * 为什么需要它：小程序里 `position: fixed` 的元素各自带 z-index，谁大谁在上。
 * 页面自己写的底部弹层长期用 900/901、类目面板 999、SKU 弹窗 990、日历
 * root-portal 1000，而 wot-design-uni 的全局提示默认只有 99/100 —— 于是
 * 「弹层里的操作失败了」这件事，提示被弹层整个盖住：用户看到的就是点击无反应。
 * 2026-10-18 船舶绑定页「提交申请没反应」正是这个组合（后端返回
 * 「您有一条待审核的申请」，toast 在 900/901 下面看不见）。
 *
 * 约定：
 *  · 页面级浮层（遮罩/弹层/弹层内二级浮层）用 sheet* 三档；
 *  · 全局反馈层（toast/loading/messageBox/notify）一律高于页面浮层，
 *    且不得低于任何页面里出现的裸 z-index —— 当前最高是 1020
 *    （shared-cart-destination-sheet、delivery/trip-detail 的 wd-popup），
 *    所以反馈层从 5001 起，而不是只压到 1000；
 *  · 新增页面浮层时引用这里的常量，不要再往模板里写裸数字。
 */
export const OVERLAY_Z_INDEX = {
  /** 底部弹层的遮罩 */
  sheetMask: 900,
  /** 底部弹层本体 */
  sheet: 901,
  /** 弹层内的二级浮层（日历、下拉面板等），要压住弹层本体 */
  sheetPopover: 1000,
  /** 全局轻提示：操作结果反馈必须有最高可见性 */
  toast: 5001,
  /** 全局加载中（带遮罩），高于 toast 以免被结果提示盖住 */
  loading: 5002,
  /** 全局确认框，必须压住 toast */
  messageBox: 5003,
  /** 顶部通知条，最高 */
  notify: 5004,
} as const

export type OverlayZIndexKey = keyof typeof OVERLAY_Z_INDEX
