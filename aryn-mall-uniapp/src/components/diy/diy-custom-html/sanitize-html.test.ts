import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import process from 'node:process'

import { describe, expect, it } from 'vitest'

import { sanitizeCustomHtmlHtml } from './sanitize-html'

describe('custom html client-side fallback sanitizer', () => {
  it('keeps plain display markup', () => {
    const html = '<div class="promo"><p>全场满减</p><a href="https://e.com/a">去看看</a></div>'

    const result = sanitizeCustomHtmlHtml(html)

    expect(result).toContain('<div class="promo">')
    expect(result).toContain('全场满减')
  })

  it.each(['script', 'style', 'iframe', 'object', 'embed', 'form', 'svg', 'math', 'template'])(
    'drops the <%s> container together with its content',
    (tag) => {
      const html = `<div>前</div><${tag}>危险内容</${tag}><div>后</div>`

      const result = sanitizeCustomHtmlHtml(html)

      expect(result).not.toContain(`<${tag}`)
      expect(result).not.toContain('危险内容')
      expect(result).toContain('前')
      expect(result).toContain('后')
    },
  )

  it('drops an unclosed dangerous container', () => {
    const result = sanitizeCustomHtmlHtml('<iframe src="//evil.example/x">')

    expect(result).not.toContain('<iframe')
  })

  it('drops an unclosed dangerous container that swallows the rest', () => {
    const result = sanitizeCustomHtmlHtml('<div>前</div><script>alert(1)')

    expect(result).not.toContain('<script')
    expect(result).not.toContain('alert(1)')
    expect(result).toContain('前')
  })

  it.each([
    '<a href="javascript:alert(1)">x</a>',
    '<a href="java\nscript:alert(1)">x</a>',
    '<a href="vbscript:msgbox(1)">x</a>',
  ])('removes executable pseudo protocols in %s', (html) => {
    const result = sanitizeCustomHtmlHtml(html)

    expect(result).not.toMatch(/java\s*script\s*:/i)
    expect(result).not.toMatch(/vb\s*script\s*:/i)
  })

  it.each([
    '<img src="x" onerror="alert(1)">',
    "<img src='x' onload='alert(1)'>",
    '<img src=x onerror=alert(1)>',
    // 属性紧贴引号/斜杠：旧实现要求前导空白，这三种都会漏过
    '<img src="x""onerror="alert(1)">',
    '<img/src=x/onerror=alert(1)>',
    // 属性名中插换行
    '<img src="x" on\nerror="alert(1)">',
  ])('removes event handler attribute in %s', (html) => {
    const result = sanitizeCustomHtmlHtml(html)

    expect(result).not.toMatch(/on\w+\s*=/i)
  })

  it('removes html comments that could hide markup', () => {
    const result = sanitizeCustomHtmlHtml('<div><!-- <script>x</script> -->ok</div>')

    expect(result).not.toContain('<!--')
    expect(result).not.toContain('<script')
  })

  it('rewrites data:text/html to a harmless type', () => {
    const result = sanitizeCustomHtmlHtml('<a href="data:text/html;base64,PHNjcmlwdD4=">x</a>')

    expect(result).not.toMatch(/data:\s*text\/html/i)
  })

  it('is empty-safe', () => {
    expect(sanitizeCustomHtmlHtml('')).toBe('')
  })
})

/**
 * 两端危险标签清单必须一致。
 *
 * C 端与管理端是两套独立实现，历史上分叉过一次：管理端删 iframe/svg/form，
 * C 端只删 script/style，于是 `<iframe>` 能穿过 C 端这一层。
 * 这份契约测试锁住两份清单，任一端增删标签都会在这里失败。
 */
describe('sanitizer tag list parity with admin implementation', () => {
  it('drops exactly the same container tags as the admin sanitizer', () => {
    const adminSource = readFileSync(
      resolve(
        process.cwd(),
        '../aryn-mall-ui/apps/web-ele/src/views/promotion/page-design/components/custom-html/sanitize-custom-html.ts',
      ),
      'utf8',
    )
    const clientSource = readFileSync(
      resolve(process.cwd(), 'src/components/diy/diy-custom-html/sanitize-html.ts'),
      'utf8',
    )

    const extractTags = (source: string, marker: string) => {
      const start = source.indexOf(marker)
      expect(start, `未找到清单 ${marker}`).toBeGreaterThan(-1)
      const block = source.slice(start, source.indexOf(']', start))
      return [...block.matchAll(/'([a-z]+)'/g)].map((match) => match[1]).sort()
    }

    const adminTags = extractTags(adminSource, 'DROP_CONTENT_TAGS')
    const clientTags = extractTags(clientSource, 'DANGEROUS_CONTAINER_TAGS')

    expect(clientTags).toEqual(adminTags)
  })
})
