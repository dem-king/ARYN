/**
 * 自定义 HTML 组件的前端 XSS 净化。
 *
 * 与富文本（sanitize-rich-text）不同：自定义 HTML 允许商户输入较自由的标签结构，
 * 因此这里采用「黑名单」策略——保留常见标签与样式，只移除可执行脚本与危险注入点：
 *  1. 直接删除 script / style / iframe / object / embed / svg / form / template 等危险容器；
 *  2. 移除所有 on* 事件属性（onerror / onload / onclick …）；
 *  3. 将 href / src / poster 等 URL 收敛到安全协议，剔除 javascript: / data: 脚本；
 *  4. 过滤 style 中的 expression / url() / @import / behavior 等危险声明。
 *
 * 注意：这是「渲染前」的第一道防护，保存到 schema 前会再次调用本函数净化 html 字段。
 */

/** 这些标签及其内部内容整体删除 */
const DROP_CONTENT_TAGS = new Set([
  'embed',
  'form',
  'iframe',
  'math',
  'object',
  'script',
  'style',
  'svg',
  'template',
]);

const URL_ATTRIBUTES = new Set([
  'action',
  'formaction',
  'href',
  'poster',
  'src',
]);
const MEDIA_URL_ATTRIBUTES = new Set(['poster', 'src']);
const SAFE_PROTOCOLS = new Set(['http:', 'https:', 'mailto:', 'tel:']);
const SAFE_DATA_IMAGE =
  /^data:image\/(?:gif|jpe?g|png|webp);base64,[a-z+/=\s]+$/i;

const UNSAFE_STYLE_VALUE =
  /expression|javascript|url\s*\(|@import|behavior|-moz-binding/i;

function isSafeDataImage(value: string) {
  return SAFE_DATA_IMAGE.test(value);
}

function isSafeUrl(value: string, media: boolean) {
  const trimmedValue = value.trim();
  const compactValue = [...trimmedValue]
    .filter((character) => {
      const codePoint = character.codePointAt(0) ?? 0;
      return codePoint > 32 && (codePoint < 127 || codePoint > 159);
    })
    .join('');

  if (!compactValue) return false;
  if (media && isSafeDataImage(compactValue)) return true;
  // 站内锚点 / 相对路径放行
  if (/^(?:#|\.{0,2}\/)/.test(compactValue)) return true;

  try {
    const url = new URL(compactValue, 'https://sanitizer.invalid');
    return (
      SAFE_PROTOCOLS.has(url.protocol) &&
      (!media || !['mailto:', 'tel:'].includes(url.protocol))
    );
  } catch {
    return false;
  }
}

function sanitizeStyle(value: string, document: Document) {
  const parsedStyle = document.createElement('span').style;
  parsedStyle.cssText = value;
  const declarations: string[] = [];

  for (let index = 0; index < parsedStyle.length; index += 1) {
    const property = parsedStyle.item(index).toLowerCase();
    const propertyValue = parsedStyle.getPropertyValue(property).trim();
    if (propertyValue && !UNSAFE_STYLE_VALUE.test(propertyValue)) {
      declarations.push(`${property}: ${propertyValue}`);
    }
  }

  return declarations.join('; ');
}

function sanitizeAttributes(element: Element, document: Document) {
  const attributes = [...element.attributes];

  for (const attribute of attributes) {
    const name = attribute.name.toLowerCase();

    // 1. 移除所有事件处理器属性 on*（onerror/onload/onclick…）
    if (name.startsWith('on')) {
      element.removeAttribute(attribute.name);
      continue;
    }

    // 2. 校验 URL 类属性协议
    if (
      URL_ATTRIBUTES.has(name) &&
      !isSafeUrl(attribute.value, MEDIA_URL_ATTRIBUTES.has(name))
    ) {
      element.removeAttribute(attribute.name);
      continue;
    }

    // 3. 过滤 style 中的危险声明
    if (name === 'style') {
      const sanitizedStyle = sanitizeStyle(attribute.value, document);
      if (sanitizedStyle) element.setAttribute('style', sanitizedStyle);
      else element.removeAttribute(attribute.name);
    }
  }

  if (element.getAttribute('target') === '_blank') {
    element.setAttribute('rel', 'noopener noreferrer');
  }
}

/** 净化自定义 HTML，返回可安全 v-html 渲染的字符串。 */
export function sanitizeCustomHtml(html: string): string {
  if (!html) return '';
  const document = new DOMParser().parseFromString(html, 'text/html');
  const elements = [...document.body.querySelectorAll('*')];

  for (const element of elements) {
    const tagName = element.tagName.toLowerCase();
    if (DROP_CONTENT_TAGS.has(tagName)) {
      element.remove();
      continue;
    }
    sanitizeAttributes(element, document);
  }

  return document.body.innerHTML;
}
