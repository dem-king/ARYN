import { describe, expect, it } from 'vitest';

import { sanitizeRichTextHtml } from './sanitize-rich-text';

describe('sanitizeRichTextHtml', () => {
  it('preserves supported rich text formatting and safe links', () => {
    const result = sanitizeRichTextHtml(
      '<p style="color: red; text-align: center">正文 <a href="https://example.com" target="_blank">详情</a></p>',
    );

    expect(result).toContain('color: red');
    expect(result).toContain('text-align: center');
    expect(result).toContain('href="https://example.com"');
    expect(result).toContain('rel="noopener noreferrer"');
  });

  it('removes executable elements, event handlers, and unsafe URLs', () => {
    const result = sanitizeRichTextHtml(
      '<script>alert(1)</script><iframe>嵌入内容</iframe><img src="javascript:alert(1)" onerror="alert(2)"><a href="java\nscript:alert(3)">链接</a>',
    );

    expect(result).not.toMatch(/script|iframe|javascript|onerror|alert/i);
    expect(result).toContain('<img>');
    expect(result).toContain('<a>链接</a>');
  });

  it('keeps allowlisted styles and drops CSS execution primitives', () => {
    const result = sanitizeRichTextHtml(
      '<p style="font-size: 16px; background-image: url(javascript:alert(1)); behavior: url(test.htc)">正文</p>',
    );

    expect(result).toContain('font-size: 16px');
    expect(result).not.toMatch(
      /background-image|behavior|javascript|url\s*\(/i,
    );
  });
});
