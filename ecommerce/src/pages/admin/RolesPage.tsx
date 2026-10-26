import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { Alert, App, Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag } from 'antd';
import { useState } from 'react';
import { menuApi, resourceApi, roleApi, type RoleInput } from '../../api/auth';
import type { Role } from '../../api/types';
import { ROLE_ADMIN, useAuth } from '../../auth/AuthContext';
import { showError } from '../../utils/errors';
import { ROLE_CODE, ROLE_CODE_MESSAGE } from '../../utils/patterns';
import { useLoader } from '../../utils/useLoader';
import MenuChecker from './MenuChecker';
import PermissionMatrix from './PermissionMatrix';

export default function RolesPage() {
  const { hasPermission, authorities, reload: reloadMine } = useAuth();
  const { message } = App.useApp();
  const { data, loading, reload } = useLoader(roleApi.list);
  // Bảng chọn quyền / cây menu cần quyền xem resource / menu; không có thì gõ tay quyền, giữ nguyên menu
  const canReadResources = hasPermission('resource:read');
  const canReadMenus = hasPermission('menu:read');
  const { data: resources } = useLoader(resourceApi.list, [], canReadResources);
  const { data: menus } = useLoader(menuApi.tree, [], canReadMenus);
  const [form] = Form.useForm<RoleInput>();
  const [editing, setEditing] = useState<Role | 'new' | null>(null);
  const [saving, setSaving] = useState(false);
  const isAdminRole = editing !== 'new' && editing?.code === ROLE_ADMIN;

  const save = async () => {
    // Field không hiện trên form (thiếu quyền xem menu, role ADMIN) không có trong values: giữ giá trị cũ
    const values: Partial<RoleInput> = await form.validateFields();
    const current = editing === 'new' || !editing ? { permissions: [], menuIds: [] } : editing;
    const input: RoleInput = {
      code: values.code,
      name: values.name ?? '',
      description: values.description ?? null,
      permissions: values.permissions ?? current.permissions,
      menuIds: values.menuIds ?? current.menuIds,
    };
    setSaving(true);
    try {
      if (editing === 'new') {
        await roleApi.create(input);
      } else if (editing) {
        await roleApi.update(editing.id, { ...input, version: editing.version });
      }
      message.success('Đã lưu. Quyền mới có hiệu lực khi user đăng nhập lại / làm mới phiên');
      setEditing(null);
      reload();
      // Role của chính mình: sidebar đổi ngay
      if (editing !== 'new' && editing && authorities?.roles.includes(editing.code)) {
        reloadMine().catch(() => undefined);
      }
    } catch (e) {
      showError(message, e, form);
    } finally {
      setSaving(false);
    }
  };

  const remove = async (role: Role) => {
    try {
      await roleApi.remove(role.id);
      message.success('Đã xoá');
      reload();
    } catch (e) {
      showError(message, e);
    }
  };

  return (
    <Card
      title="Role"
      extra={
        hasPermission('role:create') && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing('new')}>
            Thêm
          </Button>
        )
      }
    >
      <Table<Role>
        rowKey="id"
        loading={loading}
        dataSource={data}
        pagination={false}
        columns={[
          {
            title: 'Code',
            dataIndex: 'code',
            render: (code: string, role) => (
              <Space>
                {code}
                {role.builtIn && <Tag color="gold">Hệ thống</Tag>}
              </Space>
            ),
          },
          { title: 'Tên', dataIndex: 'name' },
          {
            title: 'Quyền',
            render: (_, role) =>
              role.code === ROLE_ADMIN ? <Tag color="red">Toàn quyền</Tag> : `${role.permissions.length} quyền`,
          },
          {
            title: 'Menu',
            render: (_, role) => (role.code === ROLE_ADMIN ? 'Tất cả' : `${role.menuIds.length} menu`),
          },
          {
            title: '',
            width: 100,
            render: (_, role) => (
              <Space>
                {hasPermission('role:update') && (
                  <Button size="small" icon={<EditOutlined />} onClick={() => setEditing(role)} />
                )}
                {hasPermission('role:delete') && !role.builtIn && (
                  <Popconfirm title={`Xoá role ${role.code}?`} onConfirm={() => remove(role)}>
                    <Button size="small" danger icon={<DeleteOutlined />} />
                  </Popconfirm>
                )}
              </Space>
            ),
          },
        ]}
      />
      <Modal
        title={editing === 'new' ? 'Thêm role' : `Sửa role ${editing?.code ?? ''}`}
        open={editing !== null}
        onOk={save}
        confirmLoading={saving}
        onCancel={() => setEditing(null)}
        width={760}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          preserve={false}
          initialValues={editing === 'new' ? { permissions: [], menuIds: [] } : editing ?? {}}
        >
          <Form.Item
            name="code"
            label="Code"
            extra="Không đổi được sau khi tạo"
            rules={[
              { required: true, message: 'Nhập code' },
              { pattern: ROLE_CODE, message: ROLE_CODE_MESSAGE },
            ]}
          >
            <Input disabled={editing !== 'new'} placeholder="vd. EDITOR" />
          </Form.Item>
          <Form.Item name="name" label="Tên" rules={[{ required: true, message: 'Nhập tên' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="Mô tả">
            <Input.TextArea rows={2} />
          </Form.Item>
          {isAdminRole ? (
            <Alert type="info" showIcon message="ADMIN luôn có mọi quyền và thấy mọi menu" />
          ) : (
            <>
              <Form.Item name="permissions" label="Quyền">
                {canReadResources ? (
                  <PermissionMatrix resources={resources ?? []} />
                ) : (
                  <Select mode="tags" placeholder="resource:action, vd. category:create" />
                )}
              </Form.Item>
              {canReadMenus && (
                <Form.Item name="menuIds" label="Menu">
                  <MenuChecker menus={menus ?? []} />
                </Form.Item>
              )}
            </>
          )}
        </Form>
      </Modal>
    </Card>
  );
}
