import { defineStore } from 'pinia'
import { emptyProcurementSession, procurementEnvelope, stableProcurementRequest } from '@/utils/procurement-session'
import type { ProcurementSession } from '@/utils/procurement-session'
const key = (scope: string) => `procurement:${scope}`
export const useProcurementStore = defineStore('procurement', {
  state: () => ({ scope: '', session: emptyProcurementSession() }),
  actions: {
    bind(scope: string) {
      if (scope && scope === this.scope)
        return
      const previous = this.scope || uni.getStorageSync('procurement:scope')
      if (previous && previous !== scope)
        uni.removeStorageSync(key(previous))
      if (scope) uni.setStorageSync('procurement:scope', scope)
      else uni.removeStorageSync('procurement:scope')
      this.scope = scope
      this.session = emptyProcurementSession()
      if (scope) {
        const saved = uni.getStorageSync(key(scope))
        if (saved && typeof saved === 'object') {
          const value = saved as Partial<ProcurementSession>
          this.session = { ...emptyProcurementSession(), ...value, pending: value.pending ?? {}, edits: value.edits ?? {} }
        }
      }
    },
    save() {
      if (this.scope)
        uni.setStorageSync(key(this.scope), this.session)
    },
    request(action: string, payload: unknown) {
      const id = stableProcurementRequest(this.session, action, payload)
      this.save()
      return id
    },
    envelope<T extends Record<string, unknown>>(action: string, intent: unknown, command: T) {
      const result = procurementEnvelope(this.session, action, intent, command)
      this.save()
      return result
    },
    complete(action: string) {
      delete this.session.pending[action]
      this.save()
    },
    clear() { this.bind('') },
  },
})
