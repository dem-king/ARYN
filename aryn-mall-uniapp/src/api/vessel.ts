/**
 * 船舶上下文相关 API
 * 船舶域服务（Cloud 网关域：/vessel；Boot 模式由 rewriteBootUrl 改写）
 */
import { alovaInstance } from '@/api/core/instance'

/** 船舶档案（C 端返回本人作为成员的在营船舶） */
export function getMyVessels() {
  return alovaInstance.Get<any[]>('/vessel/app/my-vessels')
}

/** 船舶可用靠港计划（排除已过期/已完成/已取消） */
export function getVesselCalls(vesselId: string) {
  return alovaInstance.Get<any[]>(`/vessel/app/${vesselId}/calls`)
}

/** 船舶配送上下文（下一靠港、港口、泊位、时间窗） */
export function getVesselContext(vesselId: string) {
  return alovaInstance.Get<any>('/vessel/app/context', {
    params: { vesselId },
  })
}
