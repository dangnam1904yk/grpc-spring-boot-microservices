import type { MenuNode } from '../api/types';

/** Một menu trong danh sách phẳng, kèm đường dẫn đầy đủ "Cha › Con". */
export interface FlatMenu extends MenuNode {
  fullPath: string;
}

/** Duyệt cây theo thứ tự hiển thị. */
export function flattenMenus(nodes: MenuNode[], parentPath = ''): FlatMenu[] {
  return nodes.flatMap((node) => {
    const fullPath = parentPath ? `${parentPath} › ${node.name}` : node.name;
    return [{ ...node, fullPath }, ...flattenMenus(node.children, fullPath)];
  });
}

export function findMenu(nodes: MenuNode[], id: string): MenuNode | undefined {
  for (const node of nodes) {
    if (node.id === id) return node;
    const found = findMenu(node.children, id);
    if (found) return found;
  }
  return undefined;
}

/** Dữ liệu cây cho TreeSelect (value), bỏ qua menu exclude và nhánh con của nó (không chọn được làm menu cha). */
export interface TreeOption {
  value: string;
  title: string;
  children: TreeOption[];
}

export function toTreeOptions(nodes: MenuNode[], exclude?: string): TreeOption[] {
  return nodes
    .filter((n) => n.id !== exclude)
    .map((n) => ({
      value: n.id,
      title: n.status === 'ACTIVE' ? `${n.name} (${n.code})` : `${n.name} (${n.code}) – tắt`,
      children: toTreeOptions(n.children, exclude),
    }));
}
