/**
 * 导入清单直下单的结算交接上下文（向导 → 报告页 → 结算页）。
 *
 * 「导入清单下单」不创建共享购物车：向导选定的配送目标（船供靠港 / 岸上收货地址）
 * 要穿越报告页（上传/核对）后在结算页生效，而 order-confirm 的配送上下文
 * 只认全局 shipContextStore —— 有船用户选岸上地址会被强制 way=4。
 *
 * 2026-11-07 修复：交接必须绑定**本次结算来源**（handoffId）而不是一个全局 active
 * 布尔值。普通购物车/立即购买也会走 order-confirm/createWay=2，只判断 active
 * 会把用户退出导入后的普通订单接管成船供/岸上场景。结算页只有 URL 上的
 * handoff 与 store 匹配时才应用导入上下文；不匹配的残留会被清理。
 *
 * 生命周期：
 * - begin：向导点「上传清单导入」时生成新的 handoffId 并覆盖旧交接；
 * - setImportId：报告页解析成功后绑定具体导入任务；
 * - 用户编辑备注/地址时同步回 store（remarkEdited 记录「用户已改过」，清空也生效）；
 * - 下单成功 clear；过期（24h）或普通下单入口打开时视为失效并清理；
 * - 持久化随默认插件：H5 刷新结算页仍能恢复，但必须带同一 handoff 参数。
 */
import { defineStore } from 'pinia'

import { PURCHASE_SCENE_ENTERPRISE } from '@/store/shipContextStore'

/** 交接有效期：覆盖用户上传/核对/刷新结算页的合理时长，过期不应用到普通订单 */
export const DIRECT_IMPORT_HANDOFF_TTL_MS = 24 * 60 * 60 * 1000

export interface DirectImportHandoffPayload {
  scene: string
  userAddressId?: string
  remark?: string
  vesselId?: string
  vesselCallId?: string
  vesselName?: string
  portName?: string
  berth?: string
}

export const useDirectImportOrderStore = defineStore('directImportOrder', {
  state: () => ({
    /** 是否处于「导入直下单」交接态（仅 handoffId 匹配的结算页可消费） */
    active: false,
    /** 本次交接的唯一标识：随 URL 传到结算页，防止全局残留接管普通订单 */
    handoffId: '',
    /** 报告页解析成功后绑定的导入任务 ID */
    importId: '',
    /** 购买场景：2 船供采购（way=4）；3 岸上企业采购（way=3） */
    scene: '',
    /** 岸上模式：向导选定的收货地址ID（船供模式为空，靠港走交接里的 vessel 字段） */
    userAddressId: '',
    /** 备注：结算页展示/提交的唯一来源；用户清空后为空串 */
    remark: '',
    /** 用户是否在结算页编辑过备注（含清空）：编辑后刷新不再回填向导值 */
    remarkEdited: false,
    /** 船供模式锁定的船舶与靠港（刷新/冷启动后仍能恢复，防丢配送目标） */
    vesselId: '',
    vesselCallId: '',
    vesselName: '',
    portName: '',
    berth: '',
    /** 交接创建时间（毫秒），用于 TTL 失效 */
    createdAt: 0,
  }),

  getters: {
    /** 岸上企业：按收货地址配送（way=3） */
    isShore: state => state.scene === PURCHASE_SCENE_ENTERPRISE,
    /** 交接是否已过期（没有创建时间的旧数据按过期处理） */
    isExpired: state => !state.createdAt || Date.now() - state.createdAt > DIRECT_IMPORT_HANDOFF_TTL_MS,
    /** 指定 handoff 是否就是当前有效交接：结算页只认这个，不认「有没有 active」 */
    matches: (state) => {
      return (handoffId?: string) => !!handoffId
        && state.active
        && state.handoffId === handoffId
        && !!state.createdAt
        && Date.now() - state.createdAt <= DIRECT_IMPORT_HANDOFF_TTL_MS
    },
  },

  actions: {
    begin(payload: DirectImportHandoffPayload) {
      this.active = true
      this.handoffId = createHandoffId()
      this.importId = ''
      this.scene = payload.scene
      this.userAddressId = payload.userAddressId ?? ''
      this.remark = payload.remark ?? ''
      this.remarkEdited = false
      this.vesselId = payload.vesselId ?? ''
      this.vesselCallId = payload.vesselCallId ?? ''
      this.vesselName = payload.vesselName ?? ''
      this.portName = payload.portName ?? ''
      this.berth = payload.berth ?? ''
      this.createdAt = Date.now()
    },

    setImportId(importId: string) {
      this.importId = importId
    },

    /** 用户编辑备注（空串同样落盘，避免刷新后被向导值填回） */
    setRemark(remark: string) {
      this.remark = remark ?? ''
      this.remarkEdited = true
    },

    /** 用户换收货地址：同步给结算页刷新后的恢复逻辑 */
    setUserAddressId(userAddressId: string) {
      this.userAddressId = userAddressId ?? ''
    },

    /** 结算页重新选择靠港后更新交接，保证预览与提交使用同一组船舶参数 */
    setVesselContext(payload: {
      vesselId?: string
      vesselCallId?: string
      vesselName?: string
      portName?: string
      berth?: string
    }) {
      this.vesselId = payload.vesselId ?? this.vesselId
      this.vesselCallId = payload.vesselCallId ?? this.vesselCallId
      this.vesselName = payload.vesselName ?? this.vesselName
      this.portName = payload.portName ?? this.portName
      this.berth = payload.berth ?? this.berth
    },

    clear() {
      this.active = false
      this.handoffId = ''
      this.importId = ''
      this.scene = ''
      this.userAddressId = ''
      this.remark = ''
      this.remarkEdited = false
      this.vesselId = ''
      this.vesselCallId = ''
      this.vesselName = ''
      this.portName = ''
      this.berth = ''
      this.createdAt = 0
    },
  },
})

function createHandoffId() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}
