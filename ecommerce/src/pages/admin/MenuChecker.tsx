import { Tree } from 'antd';
import type { MenuNode } from '../../api/types';
import { toTreeOptions } from '../../utils/menuTree';

interface Props {
  menus: MenuNode[];
  value?: string[];
  onChange?: (value: string[]) => void;
}

/**
 * Chọn menu cho role. checkStrictly: chọn menu cha không tự chọn menu con (và ngược lại);
 * backend tự thêm menu cha của menu được gán khi dựng sidebar.
 */
export default function MenuChecker({ menus, value = [], onChange }: Props) {
  return (
    <Tree
      checkable
      checkStrictly
      defaultExpandAll
      selectable={false}
      treeData={toTreeOptions(menus)}
      checkedKeys={value}
      onCheck={(checked) => onChange?.((Array.isArray(checked) ? checked : checked.checked).map(String))}
    />
  );
}
