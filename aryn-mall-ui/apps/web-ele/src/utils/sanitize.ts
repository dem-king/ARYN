/**
 * 轻量级 HTML XSS 过滤工具
 *
 * 用于过滤 v-html 渲染的富文本内容，移除潜在的恶意脚本。
 * 建议后续安装 DOMPurify 替代此实现以获得更完善的防护。
 */

/** 需要完全移除的标签 */
const DANGEROUS_TAGS = [
  'script',
  'iframe',
  'object',
  'embed',
  'form',
  'input',
  'style',
  'meta',
  'link',
  'base',
  'svg',
  'math',
];

/** 需要移除的属性前缀（事件处理器） */
const DANGEROUS_ATTR_PREFIX = 'on';

/** 需要过滤的属性（可能包含 javascript: 协议） */
const DANGEROUS_ATTRS = ['href', 'src', 'action', 'formaction', 'data', 'codebase'];

/**
 * 过滤 HTML 内容中的 XSS 攻击向量
 * @param html 原始 HTML 字符串
 * @returns 过滤后的安全 HTML
 */
export function sanitizeHtml(html: string): string {
  if (!html || typeof html !== 'string') {
    return '';
  }

  let result = html;

  // 1. 移除危险标签及其内容
  for (const tag of DANGEROUS_TAGS) {
    const tagRegex = new RegExp(`<${tag}[^>]*>[\\s\\S]*?<\\/${tag}>`, 'gi');
    result = result.replace(tagRegex, '');
    // 移除自闭合的危险标签
    const selfClosingRegex = new RegExp(`<${tag}[^>]*\\/?>`, 'gi');
    result = result.replace(selfClosingRegex, '');
  }

  // 2. 移除所有 on* 事件属性（onclick, onload, onerror 等）
  result = result.replace(/\son\w+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, '');

  // 3. 过滤 javascript: 协议
  for (const attr of DANGEROUS_ATTRS) {
    const attrRegex = new RegExp(
      `${attr}\\s*=\\s*("javascript:[^"]*"|'javascript:[^']*'|javascript:[^\\s>]*)`,
      'gi',
    );
    result = result.replace(attrRegex, '');
  }

  // 4. 移除 data: 协议中的潜在恶意内容（非图片的 data URI）
  result = result.replace(
    /src\s*=\s*("data:(?!image\/)[^"]*"|'data:(?!image\/)[^']*')/gi,
    '',
  );

  return result;
}
