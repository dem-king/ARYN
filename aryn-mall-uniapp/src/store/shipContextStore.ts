/**
 * 船舶上下文状态管理
 *
 * 保存当前用户的购买场景、船舶、靠港计划与配送时间窗，
 * 贯穿商品列表、购物车、结算和订单详情。
 *
 * 安全约定：本地缓存仅用于恢复界面，不作为权限事实；
 * 每次结算由服务端重新校验船舶成员关系和靠港计划。
 */
import { defineStore } from 'pinia'

export const PURCHASE_SCENE_PERSONAL = '1'
export const PURCHASE_SCENE_SHIP_SUPPLY = '2'

export interface ShipContextState {
  /** 购买场景：1 海员个人购买；2 船供采购 */
  purchaseScene: string
  /** 当前船舶ID */
  vesselId: string
  /** 当前船舶名称 */
  vesselName: string
  /** 当前靠港计划ID */
  vesselCallId: string
  /** 港口编码 */
  portCode: string
  /** 港口名称 */
  portName: string
  /** 泊位 */
  berth: string
  /** 配送时间窗开始 */
  deliveryWindowStart: string
  /** 配送时间窗结束 */
  deliveryWindowEnd: string
}

export const useShipContextStore = defineStore('shipContext', {
  state: (): ShipContextState => ({
    purchaseScene: PURCHASE_SCENE_PERSONAL,
    vesselId: '',
    vesselName: '',
    vesselCallId: '',
    portCode: '',
    portName: '',
    berth: '',
    deliveryWindowStart: '',
    deliveryWindowEnd: '',
  }),

  getters: {
    /** 是否已绑定船舶上下文（内部配送结算的前置条件） */
    hasVesselContext: state => !!state.vesselId && !!state.vesselCallId,

    /** 是否船供采购场景 */
    isShipSupply: state => state.purchaseScene === PURCHASE_SCENE_SHIP_SUPPLY,

    /**
     * 结算用的内部配送上下文参数（delivery_way=4）
     */
    deliveryContextParams: (state) => {
      if (!state.vesselId || !state.vesselCallId) return null
      return {
        purchaseScene: state.purchaseScene,
        vesselId: state.vesselId,
        vesselCallId: state.vesselCallId,
      }
    },
  },

  actions: {
    /**
     * 设置当前船舶与靠港上下文（来自 /vessel/app/my-vessels + context）
     */
    setVesselContext(payload: {
      berth?: string
      deliveryWindowEnd?: string
      deliveryWindowStart?: string
      portCode?: string
      portName?: string
      vesselCallId?: string
      vesselId: string
      vesselName?: string
    }) {
      this.vesselId = payload.vesselId
      this.vesselName = payload.vesselName ?? this.vesselName
      this.vesselCallId = payload.vesselCallId ?? this.vesselCallId
      this.portCode = payload.portCode ?? this.portCode
      this.portName = payload.portName ?? this.portName
      this.berth = payload.berth ?? this.berth
      this.deliveryWindowStart = payload.deliveryWindowStart ?? this.deliveryWindowStart
      this.deliveryWindowEnd = payload.deliveryWindowEnd ?? this.deliveryWindowEnd
    },

    /**
     * 切换船舶：清空靠港上下文防止串船，靠港计划需重新加载选择
     */
    switchVessel(vesselId: string, vesselName: string) {
      if (vesselId === this.vesselId) return
      this.vesselId = vesselId
      this.vesselName = vesselName
      this.vesselCallId = ''
      this.portCode = ''
      this.portName = ''
      this.berth = ''
      this.deliveryWindowStart = ''
      this.deliveryWindowEnd = ''
    },

    /**
     * 设置靠港计划（选择港口/时间窗后调用）
     */
    setVesselCall(payload: {
      berth?: string
      deliveryWindowEnd?: string
      deliveryWindowStart?: string
      id: string
      portCode?: string
      portName?: string
    }) {
      this.vesselCallId = payload.id
      this.portCode = payload.portCode ?? this.portCode
      this.portName = payload.portName ?? this.portName
      this.berth = payload.berth ?? this.berth
      this.deliveryWindowStart = payload.deliveryWindowStart ?? this.deliveryWindowStart
      this.deliveryWindowEnd = payload.deliveryWindowEnd ?? this.deliveryWindowEnd
    },

    /**
     * 切换购买场景
     */
    setPurchaseScene(scene: string) {
      if (scene !== PURCHASE_SCENE_PERSONAL && scene !== PURCHASE_SCENE_SHIP_SUPPLY) {
        throw new Error(`不支持的购买场景：${scene}`)
      }
      this.purchaseScene = scene
    },

    /**
     * 登出清理：船舶上下文整体失效
     */
    reset() {
      this.purchaseScene = PURCHASE_SCENE_PERSONAL
      this.vesselId = ''
      this.vesselName = ''
      this.vesselCallId = ''
      this.portCode = ''
      this.portName = ''
      this.berth = ''
      this.deliveryWindowStart = ''
      this.deliveryWindowEnd = ''
    },
  },
})
