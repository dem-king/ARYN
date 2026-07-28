import { describe, expect, it } from 'vitest';

import { resolveResourceUrl } from './resource-url';

describe('resource URL resolution', () => {
  it('prefixes cloud local file paths with the API base URL', () => {
    expect(
      resolveResourceUrl(
        '/upms/file/local/1881232176465358849/demo.jpg',
        '/api',
      ),
    ).toBe('/api/upms/file/local/1881232176465358849/demo.jpg');
  });

  it('prefixes boot local file paths without duplicate slashes', () => {
    expect(resolveResourceUrl('/boot/file/local/1/demo.png', '/api/')).toBe(
      '/api/boot/file/local/1/demo.png',
    );
  });

  it.each([
    'https://cdn.example.com/demo.jpg',
    '//cdn.example.com/demo.jpg',
    'data:image/png;base64,abc',
    'blob:http://localhost/id',
    '/assets/demo.jpg',
  ])('keeps non-API resource URL unchanged: %s', (url) => {
    expect(resolveResourceUrl(url, '/api')).toBe(url);
  });
});
