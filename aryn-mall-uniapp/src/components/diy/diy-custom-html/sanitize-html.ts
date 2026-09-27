/**
 * 自定义 HTML 渲染前的客户端兜底过滤。
 *
 * 真正的防护层在管理后台保存前（DOMParser 黑名单净化）与后端发布校验
 * （命中危险特征即阻断发布）。这里只是最后一道粗过滤：小程序端没有 DOMParser，
 * `mp-html` 自带过滤又依赖其解析行为，因此保留一份独立的正则实现。
 *
 * 危险标签清单与管理端 `custom-html/sanitize-custom-html.ts` 的
 * `DROP_CONTENT_TAGS` **必须保持一致**——两侧不同步会让弱的一端成为绕过通道。
 */

/**
 * 危险容器标签：连同内容整块删除。
 *
 * iframe/object/embed/form/svg/math/template 这些管理端已删除，
 * 早期实现这里只删 script/style，导致 `<iframe src=...>` 能穿过 C 端这一层。
 */
const DANGEROUS_CONTAINER_TAGS = [
  'embed',
  'form',
  'iframe',
  'math',
  'object',
  'script',
  'style',
  'svg',
  'template',
]

/**
 * 删除某标签的整块内容。
 *
 * 先删成对标签（含内容）；再处理没有闭合标签的写法——此时无法确定内容边界，
 * 按浏览器「读到 EOF 自动闭合」的语义把该标签之后的内容一并丢弃。
 * 与仅删除开标签相比，这样不会把 `<script>alert(1)` 里的 `alert(1)` 留成可见文本，
 * 也与管理端 DOMParser 的解析结果（自动闭合后整块移除）保持一致。
 */
function dropContainerTag(html: string, tag: string): string {
  const paired = new RegExp(`<${tag}\\b[\\s\\S]*?<\\/${tag}\\s*>`, 'gi')
  const result = html.replace(paired, '')
  const orphan = new RegExp(`<${tag}\\b`, 'i')
  const match = orphan.exec(result)
  return match ? result.slice(0, match.index) : result
}

export function sanitizeCustomHtmlHtml(html: string): string {
  if (!html)
    return ''
  let result = html

  for (const tag of DANGEROUS_CONTAINER_TAGS) {
    result = dropContainerTag(result, tag)
  }

  // 移除 <!-- 注释 -->（防止注释里隐藏的事件属性被后续解析还原）
  result = result.replace(/<!--[\s\S]*?-->/g, '')

  // 移除伪协议。`\s*` 覆盖 `java\nscript:` 这类插入空白的写法
  // （浏览器解析 URL 前会先剔除空白字符，因此这种写法照常执行）。
  result = result.replace(/java\s*script\s*:/gi, '')
  result = result.replace(/vb\s*script\s*:/gi, '')
  result = result.replace(/data:\s*text\/html/gi, 'data:text/plain')

  // 移除事件处理属性：onerror= onload= onclick= 等 on*=。
  //
  // 刻意**不**要求属性前必须是空白：旧实现写 `/\son\w+\s*=/`，
  // `"onerror=..."`（紧跟引号）与 `<img/src=x/onerror=...>`（紧跟斜杠）
  // 都匹配不上，而这两种写法浏览器都认。
  // 这里改为把前导分隔符一起匹配再原样写回，既覆盖上述写法，
  // 又不会误伤 URL 里恰好出现的 `on…=`（如 `?action=`，`on` 后无字符直接跟 `=`）。
  const attributeStart = String.raw`([\s/"'])`
  result = result.replace(
    new RegExp(`${attributeStart}on\\w+\\s*=\\s*"[^"]*"`, 'gi'),
    '$1',
  )
  result = result.replace(
    new RegExp(`${attributeStart}on\\w+\\s*=\\s*'[^']*'`, 'gi'),
    '$1',
  )
  result = result.replace(
    new RegExp(`${attributeStart}on\\w+\\s*=\\s*[^\\s>]+`, 'gi'),
    '$1',
  )

  return result
}
