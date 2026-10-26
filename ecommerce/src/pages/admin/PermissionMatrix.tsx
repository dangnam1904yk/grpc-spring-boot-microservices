import { Checkbox, Table } from 'antd';
import type { Resource } from '../../api/types';

interface Props {
  resources: Resource[];
  /** "resource:action" */
  value?: string[];
  onChange?: (value: string[]) => void;
}

/** Bảng chọn quyền cho role: mỗi dòng 1 resource, mỗi ô 1 action áp dụng được cho resource đó. */
export default function PermissionMatrix({ resources, value = [], onChange }: Props) {
  const selected = new Set(value);

  const toggle = (permissions: string[], checked: boolean) => {
    const next = new Set(selected);
    permissions.forEach((p) => (checked ? next.add(p) : next.delete(p)));
    onChange?.([...next].sort());
  };

  return (
    <Table<Resource>
      rowKey="code"
      size="small"
      pagination={false}
      dataSource={resources}
      columns={[
        {
          title: 'Resource',
          render: (_, r) => {
            const count = r.permissions.filter((p) => selected.has(p)).length;
            return (
              <Checkbox
                checked={count > 0 && count === r.permissions.length}
                indeterminate={count > 0 && count < r.permissions.length}
                disabled={r.permissions.length === 0}
                onChange={(e) => toggle(r.permissions, e.target.checked)}
              >
                {r.name} <code>{r.code}</code>
              </Checkbox>
            );
          },
        },
        {
          title: 'Action',
          render: (_, r) =>
            r.permissions.map((p, i) => (
              <Checkbox key={p} checked={selected.has(p)} onChange={(e) => toggle([p], e.target.checked)}>
                {r.actions[i]}
              </Checkbox>
            )),
        },
      ]}
    />
  );
}
