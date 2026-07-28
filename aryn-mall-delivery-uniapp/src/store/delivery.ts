import { defineStore } from 'pinia'
import { DeliveryApiError } from '@/api/core/instance'
import {
  checkTaskItem,
  completeTask,
  getMyTask,
  getMyTasks,
  pickupTask,
  reportTaskException,
  startPicking,
  type DeliveryTask,
  type DeliveryTaskItem,
} from '@/api/delivery'

interface EvidenceDraft {
  materialIds: string[]
  requestId: string
}

function createRequestId() {
  if (typeof globalThis.crypto?.randomUUID === 'function')
    return globalThis.crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

export function canPickup(items: Pick<DeliveryTaskItem, 'checked'>[] | undefined) {
  return Boolean(items?.length && items.every(item => item.checked === '1'))
}

export function validateDeliveryEvidence(materialIds: string[]) {
  return materialIds.length >= 1 && materialIds.length <= 6
}

export const useDeliveryStore = defineStore('delivery-workflow', {
  state: () => ({
    currentTask: null as DeliveryTask | null,
    evidenceDrafts: {} as Record<string, EvidenceDraft>,
    loading: false,
    tasks: [] as DeliveryTask[],
    total: 0,
  }),
  actions: {
    async loadTasks(status?: string) {
      this.loading = true
      try {
        const page = await getMyTasks({ current: 1, size: 50, status })
        this.tasks = page.records ?? []
        this.total = page.total ?? this.tasks.length
        return this.tasks
      }
      finally {
        this.loading = false
      }
    },
    async loadTask(id: string) {
      this.currentTask = await getMyTask(id)
      return this.currentTask
    },
    saveEvidenceDraft(taskId: string, materialIds: string[]) {
      const current = this.evidenceDrafts[taskId]
      this.evidenceDrafts[taskId] = {
        materialIds: [...materialIds],
        requestId: current?.requestId || createRequestId(),
      }
    },
    removeEvidenceDraftItem(taskId: string, index: number) {
      const draft = this.evidenceDrafts[taskId]
      if (!draft)
        return
      draft.materialIds.splice(index, 1)
    },
    async beginPicking(task: DeliveryTask) {
      await startPicking(task.id, { requestId: createRequestId(), version: task.version ?? 0 })
      return this.loadTask(task.id)
    },
    async setItemChecked(task: DeliveryTask, itemId: string, checked: boolean) {
      await checkTaskItem(task.id, itemId, {
        checked,
        requestId: createRequestId(),
        version: task.version ?? 0,
      })
      return this.loadTask(task.id)
    },
    async confirmPickup(task: DeliveryTask) {
      if (!canPickup(task.items))
        throw new Error('请先核对全部商品')
      await pickupTask(task.id, { requestId: createRequestId(), version: task.version ?? 0 })
      return this.loadTask(task.id)
    },
    async submitDelivered(taskId: string, version: number) {
      const draft = this.evidenceDrafts[taskId]
      if (!draft || !validateDeliveryEvidence(draft.materialIds))
        throw new Error('请上传 1 至 6 张送达凭证')
      await completeTask(taskId, {
        materialIds: draft.materialIds,
        requestId: draft.requestId,
        version,
      })
      delete this.evidenceDrafts[taskId]
      this.currentTask = null
    },
    async submitException(task: DeliveryTask, reasonCode: string, description: string, materialIds: string[]) {
      const requestId = createRequestId()
      await reportTaskException(task.id, {
        description,
        materialIds,
        reasonCode,
        requestId,
        version: task.version ?? 0,
      })
      this.currentTask = null
    },
    handleMutationFailure(error: unknown) {
      const code = error instanceof DeliveryApiError ? error.code : Number((error as { code?: number })?.code)
      if (code !== 403 && code !== 409)
        return false
      this.currentTask = null
      return true
    },
  },
})
