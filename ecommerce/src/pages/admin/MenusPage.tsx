import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import {
  App,
  Button,
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  TreeSelect,
} from 'antd';
import { useState } from 'react';
import { menuApi, type MenuInput } from '../../api/auth';
import type { Menu, MenuNode } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { MENU_ICONS, menuIcon } from '../../layout/icons';
import { showError } from '../../utils/errors';
import { toTreeOptions } from '../../utils/menuTree';
import { MENU_CODE, MENU_CODE_MESSAGE } from '../../utils/patterns';
import { useLoader } from '../../utils/useLoader';

/** Table hiện cây theo field children; menu lá thì bỏ children để không có nút mở rộng. */
type Row = Omit<MenuNode, 'children'> & { children?: Row[] };

function toRows(nodes: MenuNode[]): Row[] {
  return nodes.map(({ children, ...node }) => (children.length ? { ...node, children: toRows(children) } : node));
}

const ICON_OPTIONS = Object.keys(MENU_ICONS).map((name) => ({
  value: name,
  label: (
    <Space>
      {MENU_ICONS[name]}
      {name}
    </Space>
  ),
}));

/** 'new' kèm parentId: thêm menu (con) mới; Menu: sửa */
type Editing = { kind: 'new'; parentId: string | null } | { kind: 'edit'; menu: Menu };

export default function MenusPage() {
  const { hasPermission, reload: reloadMine } = useAuth();
  const { message } = App.useApp();
  const { data, loading, reload } = useLoader(menuApi.tree);
  const [form] = Form.useForm<MenuInput>();
  const [editing, setEditing] = useState<Editing | null>(null);
  const [saving, setSaving] = useState(false);

  // Cây trả về không có version / parentId: lấy chi tiết trước khi sửa
  const openEdit = async (id: string) => {
    try {
      setEditing({ kind: 'edit', menu: await menuApi.get(id) });
    } catch (e) {
      showError(message, e);
    }
  };

  const afterChange = () => {
    reload();
    // Sidebar của chính mình cũng đổi theo
    reloadMine().catch(() => undefined);
  };

  const save = async () => {
    const values = await form.validateFields();
    const input = { ...values, parentId: values.parentId ?? null, sortOrder: values.sortOrder ?? 0 };
    setSaving(true);
    try {
      if (editing?.kind === 'new') {
        await menuApi.create(input);
      } else if (editing) {
        await menuApi.update(editing.menu.id, { ...input, version: editing.menu.version });
      }
      message.success('Đã lưu');
      setEditing(null);
      afterChange();
    } catch (e) {
      showError(message, e, form);
    } finally {
      setSaving(false);
    }
  };

  const remove = async (id: string) => {
    try {
      await menuApi.remove(id);
      message.success('Đã xoá');
      afterChange();
    } catch (e) {
      showError(message, e);
    }
  };

  const initialValues: Partial<MenuInput> =
    editing?.kind === 'edit'
      ? editing.menu
      : { parentId: editing?.parentId ?? null, sortOrder: 0, active: true };

  return (
    <Card
      title="Menu"
      extra={
        hasPermission('menu:create') && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({ kind: 'new', parentId: null })}>
            Thêm
          </Button>
        )
      }
    >
      <Table<Row>
        rowKey="id"
        loading={loading}
        dataSource={toRows(data ?? [])}
        pagination={false}
        expandable={{ defaultExpandAllRows: true }}
        key={data ? 'loaded' : 'loading'}
        columns={[
          {
            title: 'Tên',
            dataIndex: 'name',
            render: (name: string, row) => (
              <Space>
                {menuIcon(row.icon)}
                {name}
              </Space>
            ),
          },
          { title: 'Code', dataIndex: 'code' },
          { title: 'Đường dẫn', dataIndex: 'path' },
          { title: 'Thứ tự', dataIndex: 'sortOrder', width: 80 },
          {
            title: 'Trạng thái',
            dataIndex: 'active',
            width: 100,
            render: (active: boolean) => (active ? <Tag color="green">Bật</Tag> : <Tag>Tắt</Tag>),
          },
          {
            title: '',
            width: 130,
            render: (_, row) => (
              <Space>
                {hasPermission('menu:create') && (
                  <Button
                    size="small"
                    icon={<PlusOutlined />}
                    title="Thêm menu con"
                    onClick={() => setEditing({ kind: 'new', parentId: row.id })}
                  />
                )}
                {hasPermission('menu:update') && (
                  <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(row.id)} />
                )}
                {hasPermission('menu:delete') && (
                  <Popconfirm title={`Xoá menu ${row.name}?`} onConfirm={() => remove(row.id)}>
                    <Button size="small" danger icon={<DeleteOutlined />} />
                  </Popconfirm>
                )}
              </Space>
            ),
          },
        ]}
      />
      <Modal
        title={editing?.kind === 'edit' ? 'Sửa menu' : 'Thêm menu'}
        open={editing !== null}
        onOk={save}
        confirmLoading={saving}
        onCancel={() => setEditing(null)}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" preserve={false} initialValues={initialValues}>
          <Form.Item
            name="code"
            label="Code"
            extra="Không đổi được sau khi tạo"
            rules={[
              { required: true, message: 'Nhập code' },
              { pattern: MENU_CODE, message: MENU_CODE_MESSAGE },
            ]}
          >
            <Input disabled={editing?.kind === 'edit'} placeholder="vd. catalog.categories" />
          </Form.Item>
          <Form.Item name="name" label="Tên" rules={[{ required: true, message: 'Nhập tên' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="path" label="Đường dẫn" extra="Để trống nếu là menu nhóm">
            <Input placeholder="vd. /categories" />
          </Form.Item>
          <Form.Item name="icon" label="Icon">
            <Select allowClear options={ICON_OPTIONS} />
          </Form.Item>
          <Form.Item name="parentId" label="Menu cha">
            <TreeSelect allowClear treeDefaultExpandAll treeData={toTreeOptions(data ?? [])} placeholder="(gốc)" />
          </Form.Item>
          <Space size="large">
            <Form.Item name="sortOrder" label="Thứ tự">
              <InputNumber />
            </Form.Item>
            <Form.Item name="active" label="Bật" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
        </Form>
      </Modal>
    </Card>
  );
}
