import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const projectRoot = fileURLToPath(new URL('..', import.meta.url))

function source(relativePath: string) {
  return readFileSync(resolve(projectRoot, relativePath), 'utf8')
}

describe('mobile review contracts', () => {
  it('does not log route or user details', () => {
    const routerSource = source('src/router/index.ts')
    expect(routerSource).not.toMatch(/console\.(?:error|log|warn)/)
  })

  it('enables WeChat request-domain validation', () => {
    expect(source('manifest.config.ts')).toContain('urlCheck: true')
  })

  it('applies token and Boot URL validation at integration boundaries', () => {
    const authSource = source('src/store/authStore.ts')
    const requestSource = source('src/api/core/instance.ts')

    expect(authSource.match(/requireTokenValue\(response\)/g)).toHaveLength(3)
    expect(requestSource).toContain('rewriteBootUrl(method.url, openBoot)')
    expect(requestSource).toContain('delete method.config.headers.skipToken')
    expect(requestSource).not.toContain('VITE_OPEN_BOOT === \'true\'')
  })

  it('removes named event handlers during the page unload lifecycle', () => {
    const confirmSource = source('src/sub-pages/order/order-confirm/index.vue')
    const listSource = source('src/sub-pages/order/order-list/index.vue')

    expect(confirmSource).toContain('onUnload(() =>')
    expect(confirmSource).toContain(
      'uni.$off(\'update:selectedAddress\', handleSelectedAddressUpdate)',
    )
    expect(confirmSource).not.toContain('uni.$once(\'beforeUnload\'')

    expect(listSource).toContain('onUnload(() =>')
    expect(listSource).toContain('uni.$off(\'refresh\', handleRefresh)')
    expect(listSource).not.toContain('uni.$once(\'beforeUnload\'')
  })
})
