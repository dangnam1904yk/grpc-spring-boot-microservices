import type { MenuNode } from '../api/types';

/** Dữ liệu cây cho TreeSelect (value) và Tree (key). */
export interface TreeOption {
  value: string;
  key: string;
  title: string;
  children: TreeOption[];
}

export function toTreeOptions(nodes: MenuNode[]): TreeOption[] {
  return nodes.map((n) => ({
    value: n.id,
    key: n.id,
    title: n.active ? n.name : `${n.name} (tắt)`,
    children: toTreeOptions(n.children),
  }));
}
