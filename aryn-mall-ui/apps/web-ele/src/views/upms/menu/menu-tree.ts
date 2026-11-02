export interface MenuTreeNode {
  [key: string]: any;
  applicationKey?: null | string;
  children?: MenuTreeNode[];
  component?: null | string;
  id: string;
  name?: null | string;
  path?: null | string;
  permission?: null | string;
  sort?: null | number;
  type?: null | string;
}

export interface MenuTreeFilter {
  applicationKey?: string;
  keyword?: string;
  type?: string;
}

const textOf = (value: unknown) => (typeof value === 'string' ? value : '');

function matches(node: MenuTreeNode, filter: Required<MenuTreeFilter>) {
  if (filter.type && textOf(node.type) !== filter.type) {
    return false;
  }
  if (
    filter.applicationKey &&
    textOf(node.applicationKey) !== filter.applicationKey
  ) {
    return false;
  }
  if (!filter.keyword) {
    return true;
  }
  return [node.name, node.permission, node.path, node.component].some((value) =>
    textOf(value).toLowerCase().includes(filter.keyword),
  );
}

/** 过滤后仍保留命中节点的祖先，保证树结构可读；命中数只统计节点自身 */
export function filterMenuTree(
  nodes: MenuTreeNode[],
  filter: MenuTreeFilter = {},
): { matchedCount: number; nodes: MenuTreeNode[] } {
  const normalized: Required<MenuTreeFilter> = {
    applicationKey: filter.applicationKey ?? '',
    keyword: (filter.keyword ?? '').trim().toLowerCase(),
    type: filter.type ?? '',
  };
  let matchedCount = 0;

  const walk = (list: MenuTreeNode[]): MenuTreeNode[] => {
    const result: MenuTreeNode[] = [];
    for (const node of list) {
      const children =
        Array.isArray(node.children) && node.children.length > 0
          ? walk(node.children)
          : [];
      const selfMatched = matches(node, normalized);
      if (selfMatched) {
        matchedCount += 1;
      }
      if (selfMatched || children.length > 0) {
        const filtered = { ...node };
        if (children.length > 0) {
          filtered.children = children;
        } else {
          delete filtered.children;
        }
        result.push(filtered);
      }
    }
    return result;
  };

  const filtered = walk(nodes ?? []);
  return { matchedCount, nodes: filtered };
}

export function flattenMenuTree(nodes: MenuTreeNode[]): MenuTreeNode[] {
  const flat: MenuTreeNode[] = [];
  const walk = (list: MenuTreeNode[]) => {
    for (const node of list) {
      flat.push(node);
      if (Array.isArray(node.children) && node.children.length > 0) {
        walk(node.children);
      }
    }
  };
  walk(nodes ?? []);
  return flat;
}
