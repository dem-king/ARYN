import { readFileSync } from 'node:fs';

import { describe, expect, it } from 'vitest';

function getElementPlusImports(relativePath: string) {
  const filename = new URL(relativePath, import.meta.url);
  const source = readFileSync(filename, 'utf8');
  const importBlock = source.match(
    /import\s*\{(?<imports>[^;]*?)\}\s*from\s*'element-plus';/,
  );
  return new Set(
    (importBlock?.groups?.imports ?? '')
      .split(',')
      .map((name) => name.trim())
      .filter(Boolean),
  );
}

describe('page designer Element Plus bindings', () => {
  it('binds the workspace tabs as imported Vue components', () => {
    const imports = getElementPlusImports('./index.vue');

    expect(imports).toContain('ElTabs');
    expect(imports).toContain('ElTabPane');
  });

  it('binds the QR expiry tag as an imported Vue component', () => {
    const imports = getElementPlusImports(
      '../page-design/components/preview-dialog.vue',
    );

    expect(imports).toContain('ElTag');
  });
});
