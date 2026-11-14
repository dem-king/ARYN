import type { AppNewbieSceneVO } from '@/api/promotion'
import { ref } from 'vue'
import { getNewbieScenes, getCurrentNewbieFloor } from '@/api/promotion'
import { getMyBuyerOrgs, BUYER_ORG_AUDIT_APPROVED } from '@/api/order/buyerOrg'
import { getMyVessels } from '@/api/vessel'
import { useAuthStore } from '@/store/authStore'

/**
 * 新人专享三场景聚合。
 *
 * 船供/企业首单资格按主体算，页面本身不知道用户的主船/主主体，
 * 先取「我的船舶」「我的工厂」，把主船/已通过主体传给后端做资格判定；
 * 取不到时传空，后端返回 eligible=null，页面按「待验证」展示，不伪造结论。
 *
 * P1-FE-04：旧后端没有 /newbie/scenes 时，降级为只展示个人新人价楼层，
 * 船供/企业 tab 保留但显示「暂不可用」，不阻断首页/专享页。
 */
export function useNewbieScenes(limit = 200) {
  const scenes = ref<AppNewbieSceneVO[]>([])
  const loading = ref(false)
  const activeScene = ref<'1' | '2' | '3'>('1')
  const degraded = ref(false)

  const personalScene = ref<AppNewbieSceneVO | null>(null)
  const shipScene = ref<AppNewbieSceneVO | null>(null)
  const orgScene = ref<AppNewbieSceneVO | null>(null)

  function assign(list: AppNewbieSceneVO[]) {
    scenes.value = list
    personalScene.value = list.find(item => item.scene === '1') ?? null
    shipScene.value = list.find(item => item.scene === '2') ?? null
    orgScene.value = list.find(item => item.scene === '3') ?? null
  }

  async function resolveSubjectIds() {
    const authStore = useAuthStore()
    // 游客没有主船/主主体，直接让后端按 eligible=null 展示，避免无 token 请求触发 401
    if (!authStore.isLoggedIn)
      return { vesselId: undefined, buyerOrgId: undefined }
    let vesselId: string | undefined
    let buyerOrgId: string | undefined
    try {
      const vessels = await getMyVessels()
      vesselId = (vessels ?? []).find(item => item?.canConfirm)?.id
        ?? (vessels ?? [])[0]?.id
    }
    catch {
      // 未登录/船舶服务不可用：不阻断场景加载
    }
    try {
      const orgs = await getMyBuyerOrgs()
      buyerOrgId = (orgs ?? []).find(item =>
        item?.auditStatus === BUYER_ORG_AUDIT_APPROVED && item?.status !== '0',
      )?.id ?? (orgs ?? []).find(item => item?.status !== '0')?.id
    }
    catch {
      // 未登录/主体服务不可用：不阻断场景加载
    }
    return { vesselId, buyerOrgId }
  }

  async function load() {
    loading.value = true
    degraded.value = false
    try {
      const { vesselId, buyerOrgId } = await resolveSubjectIds()
      assign(await getNewbieScenes({ limit, vesselId, buyerOrgId }))
    }
    catch {
      degraded.value = true
      try {
        const floor = await getCurrentNewbieFloor(limit)
        assign([
          {
            scene: '1',
            activityId: floor?.activityId,
            activityName: floor?.activityName,
            activityType: '9',
            endTime: floor?.endTime,
            longTerm: floor?.longTerm,
            eligible: floor?.eligible,
            ineligibleReason: floor?.ineligibleReason,
            goodsList: floor?.goodsList ?? [],
          },
          { scene: '2' },
          { scene: '3' },
        ])
      }
      catch {
        assign([{ scene: '1' }, { scene: '2' }, { scene: '3' }])
      }
    }
    finally {
      loading.value = false
    }
  }

  function switchScene(scene: '1' | '2' | '3') {
    activeScene.value = scene
  }

  return {
    activeScene,
    degraded,
    load,
    loading,
    orgScene,
    personalScene,
    scenes,
    shipScene,
    switchScene,
  }
}
