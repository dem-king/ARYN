const ALLOWED_TAGS = new Set([
  'a',
  'audio',
  'b',
  'blockquote',
  'br',
  'code',
  'col',
  'colgroup',
  'del',
  'div',
  'em',
  'figcaption',
  'figure',
  'h1',
  'h2',
  'h3',
  'h4',
  'h5',
  'h6',
  'hr',
  'i',
  'img',
  'li',
  'ol',
  'p',
  'pre',
  's',
  'source',
  'span',
  'strike',
  'strong',
  'sub',
  'sup',
  'table',
  'tbody',
  'td',
  'tfoot',
  'th',
  'thead',
  'tr',
  'u',
  'ul',
  'video',
]);

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

const GLOBAL_ATTRIBUTES = new Set(['class', 'dir', 'lang', 'style', 'title']);
const TAG_ATTRIBUTES: Record<string, Set<string>> = {
  a: new Set(['href', 'rel', 'target']),
  audio: new Set(['controls', 'preload', 'src']),
  col: new Set(['span', 'width']),
  img: new Set(['alt', 'height', 'src', 'title', 'width']),
  source: new Set(['src', 'type']),
  td: new Set(['colspan', 'rowspan']),
  th: new Set(['colspan', 'rowspan', 'scope']),
  video: new Set(['controls', 'height', 'poster', 'preload', 'src', 'width']),
};

const URL_ATTRIBUTES = new Set(['href', 'poster', 'src']);
const MEDIA_URL_ATTRIBUTES = new Set(['poster', 'src']);
const SAFE_PROTOCOLS = new Set(['http:', 'https:', 'mailto:', 'tel:']);
const SAFE_STYLE_PROPERTIES = new Set([
  'background-color',
  'border',
  'border-color',
  'border-radius',
  'border-style',
  'border-width',
  'color',
  'font-family',
  'font-size',
  'font-style',
  'font-weight',
  'height',
  'letter-spacing',
  'line-height',
  'margin',
  'margin-bottom',
  'margin-left',
  'margin-right',
  'margin-top',
  'max-width',
  'padding',
  'padding-bottom',
  'padding-left',
  'padding-right',
  'padding-top',
  'text-align',
  'text-decoration',
  'text-indent',
  'width',
  'word-break',
]);
const UNSAFE_STYLE_VALUE =
  /expression|javascript|url\s*\(|@import|behavior|-moz-binding/i;
const SAFE_DATA_IMAGE =
  /^data:image\/(gif|jpe?g|png|webp);base64,[a-z\d+/=\s]+$/i;

function isSafeDataImage(value: string) {
  const match = SAFE_DATA_IMAGE.exec(value);
  return !!match?.[1];
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
    if (
      SAFE_STYLE_PROPERTIES.has(property) &&
      propertyValue &&
      !UNSAFE_STYLE_VALUE.test(propertyValue)
    ) {
      declarations.push(`${property}: ${propertyValue}`);
    }
  }

  return declarations.join('; ');
}

function sanitizeAttributes(element: Element, document: Document) {
  const tagName = element.tagName.toLowerCase();
  const tagAttributes = TAG_ATTRIBUTES[tagName] ?? new Set<string>();
  const attributes = [...element.attributes];

  for (const attribute of attributes) {
    const name = attribute.name.toLowerCase();
    if (!GLOBAL_ATTRIBUTES.has(name) && !tagAttributes.has(name)) {
      element.removeAttribute(attribute.name);
      continue;
    }

    if (
      URL_ATTRIBUTES.has(name) &&
      !isSafeUrl(attribute.value, MEDIA_URL_ATTRIBUTES.has(name))
    ) {
      element.removeAttribute(attribute.name);
      continue;
    }

    if (name === 'style') {
      const sanitizedStyle = sanitizeStyle(attribute.value, document);
      if (sanitizedStyle) element.setAttribute('style', sanitizedStyle);
      else element.removeAttribute('style');
    }
  }

  if (tagName === 'a' && element.getAttribute('target') === '_blank') {
    element.setAttribute('rel', 'noopener noreferrer');
  }
}

export function sanitizeRichTextHtml(html: string) {
  const document = new DOMParser().parseFromString(html, 'text/html');
  const elements = [...document.body.querySelectorAll('*')];

  for (const element of elements) {
    const tagName = element.tagName.toLowerCase();
    if (DROP_CONTENT_TAGS.has(tagName)) {
      element.remove();
      continue;
    }
    if (!ALLOWED_TAGS.has(tagName)) {
      element.replaceWith(...element.childNodes);
      continue;
    }
    sanitizeAttributes(element, document);
  }

  return document.body.innerHTML;
}
