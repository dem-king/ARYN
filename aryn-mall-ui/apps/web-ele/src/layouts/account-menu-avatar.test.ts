import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

const root = process.cwd();
const readSource = (path: string) => readFileSync(resolve(root, path), 'utf8');

const basicLayoutSource = readSource('apps/web-ele/src/layouts/basic.vue');
const userDropdownSource = readSource(
  'packages/effects/layouts/src/widgets/user-dropdown/user-dropdown.vue',
);

describe('account menu and avatar contracts', () => {
  it('removes external account menu entries and default avatar URL fallback', () => {
    expect(basicLayoutSource).not.toContain('ARYN_DOC_URL');
    expect(basicLayoutSource).not.toContain('ARYN_GITEE_URL');
    expect(basicLayoutSource).not.toContain('const menus');
    expect(basicLayoutSource).not.toContain(':menus');
    expect(basicLayoutSource).not.toContain('preferences.app.defaultAvatar');
    expect(basicLayoutSource).toContain(
      'userStore.userInfo?.avatar?.trim() || undefined',
    );
  });

  it('uses the username as the dropdown trigger when the avatar is absent', () => {
    const conditionalAvatars =
      userDropdownSource.match(/<VbenAvatar\s+v-if="avatar"/g) ?? [];

    expect(conditionalAvatars).toHaveLength(2);
    expect(userDropdownSource).toContain('<span v-else');
    expect(userDropdownSource).toContain('{{ text }}');
  });

  it('does not render avatars in account-related dialogs when absent', () => {
    const paths = [
      'packages/effects/layouts/src/widgets/lock-screen/lock-screen.vue',
      'packages/effects/layouts/src/widgets/lock-screen/lock-screen-modal.vue',
      'packages/effects/common-ui/src/ui/authentication/login-expired-modal.vue',
    ];

    for (const path of paths) {
      expect(readSource(path)).toMatch(/<VbenAvatar\s+v-if="avatar"/);
    }
  });
});
