import { beforeEach, describe, expect, it, vi } from 'vitest';

import { requestClient } from '#/api/request';

import {
  copyPage,
  createPreviewToken,
  createTemplate,
  deleteTemplate,
  getById,
  getEditor,
  getPreview,
  getTemplates,
  getVersions,
  publishPage,
  rollbackVersion,
  saveDraft,
  unpublishPage,
  updateTemplate,
} from './page-design';

vi.mock('#/api/request', () => ({
  requestClient: {
    delete: vi.fn(),
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

describe('page design transport API', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads page detail and editor draft from distinct endpoints', async () => {
    await getById('page-1');
    await getEditor('page-1');

    expect(requestClient.get).toHaveBeenNthCalledWith(
      1,
      '/promotion/pagedesign/page-1',
    );
    expect(requestClient.get).toHaveBeenNthCalledWith(
      2,
      '/promotion/pagedesign/page-1/editor',
    );
  });

  it('saves a draft with optimistic revision data', async () => {
    const draft = {
      draftRevision: 3,
      pageContent: { components: [], schemaVersion: 2 },
      pageName: 'Home',
      schemaVersion: 2,
    };

    await saveDraft('page-1', draft);

    expect(requestClient.put).toHaveBeenCalledWith(
      '/promotion/pagedesign/page-1/draft',
      draft,
    );
  });

  it('copies and changes publication state with exact endpoints', async () => {
    await copyPage('page-1');
    await publishPage('page-1', { draftRevision: 4, publishRemark: 'Ready' });
    await unpublishPage('page-1');

    expect(requestClient.post).toHaveBeenNthCalledWith(
      1,
      '/promotion/pagedesign/page-1/copy',
    );
    expect(requestClient.post).toHaveBeenNthCalledWith(
      2,
      '/promotion/pagedesign/page-1/publish',
      { draftRevision: 4, publishRemark: 'Ready' },
    );
    expect(requestClient.post).toHaveBeenNthCalledWith(
      3,
      '/promotion/pagedesign/page-1/unpublish',
    );
  });

  it('loads versions and rolls back by immutable version id', async () => {
    await getVersions('page-1');
    await rollbackVersion('page-1', 'version-2', 'Restore');

    expect(requestClient.get).toHaveBeenCalledWith(
      '/promotion/pagedesign/page-1/versions',
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/promotion/pagedesign/page-1/versions/version-2/rollback',
      { publishRemark: 'Restore' },
    );
  });

  it('requests an opaque preview token bound to the draft revision', async () => {
    await createPreviewToken('page-1', 9);

    expect(requestClient.post).toHaveBeenCalledWith(
      '/promotion/pagedesign/page-1/preview-token',
      undefined,
      { params: { draftRevision: 9 } },
    );
  });

  it('loads a public preview snapshot with the opaque token', async () => {
    await getPreview('token/with + symbols');

    expect(requestClient.get).toHaveBeenCalledWith(
      '/promotion/app/pagedesign/preview/token%2Fwith%20%2B%20symbols',
    );
  });

  it('manages tenant templates through the scoped endpoints', async () => {
    const template = {
      pageType: '2' as const,
      schemaVersion: 2,
      systemFlag: '0' as const,
      templateContent: { components: [], schemaVersion: 2 },
      templateName: 'Campaign',
      templateType: '0' as const,
    };

    await getTemplates('0');
    await createTemplate(template);
    await updateTemplate('template-1', template);
    await deleteTemplate('template-1');

    expect(requestClient.get).toHaveBeenCalledWith(
      '/promotion/pagedesign/templates',
      { params: { pageType: '0' } },
    );
    expect(requestClient.post).toHaveBeenCalledWith(
      '/promotion/pagedesign/templates',
      template,
    );
    expect(requestClient.put).toHaveBeenCalledWith(
      '/promotion/pagedesign/templates/template-1',
      template,
    );
    expect(requestClient.delete).toHaveBeenCalledWith(
      '/promotion/pagedesign/templates/template-1',
    );
  });
});
