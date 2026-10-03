import type {
  PageDesignTemplateRecord,
  PageDesignType,
} from '#/api/promotion/page-design';

/**
 * 「从模板新建」的页型落位。
 *
 * page_design 允许直接创建的页型只有 微页面/商详/分类/个人中心（首页由
 * 「设为首页」流程维护，createPage 会拒绝 pageType='1'）。因此：
 *   · 首页模板('1')落为微页面('0')——微页面无组件白名单，发布后可「设为首页」；
 *   · 通用模板('2')落为微页面('0')；
 *   · 其余页型原样落位。
 */
export function targetPageTypeFor(
  templatePageType: string | undefined,
): PageDesignType {
  if (templatePageType === '3' || templatePageType === '4') {
    return templatePageType;
  }
  return '0';
}

/**
 * 合并设计器模板弹窗各页型的查询结果并去重。
 *
 * 模板列表接口按「page_type='2' OR page_type=指定值」过滤，通用模板会在
 * 多个页型的响应里重复出现，按 id 去重后按页型与 sort 稳定排序。
 */
export function mergeTenantTemplates(
  lists: PageDesignTemplateRecord[][],
): PageDesignTemplateRecord[] {
  const byId = new Map<string, PageDesignTemplateRecord>();
  for (const list of lists) {
    for (const template of list) {
      if (!byId.has(template.id)) {
        byId.set(template.id, template);
      }
    }
  }
  const pageTypeOrder: Record<string, number> = {
    '0': 0,
    '1': 1,
    '2': 4,
    '3': 2,
    '4': 3,
  };
  return [...byId.values()].sort((a, b) => {
    const byType =
      (pageTypeOrder[a.pageType] ?? 9) - (pageTypeOrder[b.pageType] ?? 9);
    if (byType !== 0) return byType;
    return (a.sort ?? 0) - (b.sort ?? 0);
  });
}
