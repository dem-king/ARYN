import { ARYN_PLATFORM_TENANT_ID } from '@vben/constants';

import { describe, expect, it } from 'vitest';

import { resolveAdminBrand } from './tenant-brand';

describe('admin tenant brand', () => {
  it('keeps the platform brand for the platform tenant', () => {
    expect(
      resolveAdminBrand(ARYN_PLATFORM_TENANT_ID, {
        logoUrl: '/tenant.png',
        name: '系统租户',
      }),
    ).toEqual({});
  });

  it('keeps the platform brand when no tenant is selected', () => {
    expect(
      resolveAdminBrand('', { logoUrl: '/tenant.png', name: '悦航购' }),
    ).toEqual({});
  });

  it('uses the tenant name and trim logo for tenant accounts', () => {
    expect(
      resolveAdminBrand('1590229800633634816', {
        logoUrl: ' /static/tenant-logo.png ',
        name: ' 悦航购 ',
      }),
    ).toEqual({ logo: '/static/tenant-logo.png', name: '悦航购' });
  });

  it('falls back to the platform brand when tenant fields are missing', () => {
    const brand = resolveAdminBrand('1590229800633634816', {
      logoUrl: '',
      name: '   ',
    });

    expect(brand.logo).toBeUndefined();
    expect(brand.name).toBeUndefined();
  });

  it('falls back to the platform brand when shop info is unavailable', () => {
    expect(resolveAdminBrand('1590229800633634816', null)).toEqual({});
  });
});
