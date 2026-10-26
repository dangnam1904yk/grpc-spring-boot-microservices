import { EditOutlined } from '@ant-design/icons';
import { Alert, App, Button, Card, Form, Modal, Select, Space, Table, Tag } from 'antd';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi, roleApi } from '../../api/auth';
import type { User } from '../../api/types';
import { ROLE_ADMIN, useAuth } from '../../auth/AuthContext';
import { showError } from '../../utils/errors';
import { useLoader } from '../../utils/useLoader';

const PAGE_SIZE = 20;

export default function UsersPage() {
  const { hasPermission, user: me, authorities, logout } = useAuth();
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const { data, loading, reload } = useLoader(() => authApi.listUsers(page, PAGE_SIZE), [page]);
  const canReadRoles = hasPermission('role:read');
  const { data: roles } = useLoader(roleApi.list, [], canReadRoles);
  const [form] = Form.useForm<{ roles: string[] }>();
  const [editing, setEditing] = useState<User | null>(null);
  const [saving, setSaving] = useState(false);
  const iAmAdmin = authorities?.roles.includes(ROLE_ADMIN) ?? false;
  const editingMe = editing?.id === me?.id;

  const save = async () => {
    if (!editing) {
      return;
    }
    const { roles: selected } = await form.validateFields();
    setSaving(true);
    try {
      await authApi.updateUserRoles(editing.id, selected);
      setEditing(null);
      // Đổi role thì mọi phiên của user đó bị thu hồi, kể cả phiên hiện tại nếu sửa chính mình
      if (editingMe) {
        message.info('Role của bạn đã đổi, hãy đăng nhập lại');
        await logout();
        navigate('/login', { replace: true });
        return;
      }
      message.success('Đã lưu. User phải đăng nhập lại để nhận quyền mới');
      reload();
    } catch (e) {
      showError(message, e, form);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card title="Tài khoản">
      <Table<User>
        rowKey="id"
        loading={loading}
        dataSource={data?.content}
        pagination={{
          current: page + 1,
          pageSize: PAGE_SIZE,
          total: data?.totalElements,
          showSizeChanger: false,
          onChange: (p) => setPage(p - 1),
        }}
        columns={[
          { title: 'Email', dataIndex: 'email' },
          {
            title: 'Role',
            dataIndex: 'roles',
            render: (codes: string[]) => (
              <Space wrap size={4}>
                {codes.map((c) => (
                  <Tag key={c} color={c === ROLE_ADMIN ? 'red' : 'blue'}>
                    {c}
                  </Tag>
                ))}
              </Space>
            ),
          },
          {
            title: 'Trạng thái',
            dataIndex: 'enabled',
            render: (enabled: boolean) => (enabled ? <Tag color="green">Hoạt động</Tag> : <Tag>Bị khoá</Tag>),
          },
          {
            title: '',
            width: 60,
            render: (_, user) =>
              hasPermission('user:update') && (
                <Button size="small" icon={<EditOutlined />} title="Đổi role" onClick={() => setEditing(user)} />
              ),
          },
        ]}
      />
      <Modal
        title={`Role của ${editing?.email ?? ''}`}
        open={editing !== null}
        onOk={save}
        confirmLoading={saving}
        onCancel={() => setEditing(null)}
        destroyOnHidden
      >
        {editingMe && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
            message="Đây là tài khoản của bạn: lưu xong bạn sẽ phải đăng nhập lại"
          />
        )}
        <Form form={form} layout="vertical" preserve={false} initialValues={{ roles: editing?.roles ?? [] }}>
          <Form.Item
            name="roles"
            label="Role"
            extra={iAmAdmin ? 'USER luôn được giữ' : 'USER luôn được giữ. Chỉ ADMIN được cấp / thu hồi role ADMIN'}
            rules={[{ required: true, message: 'Chọn ít nhất 1 role' }]}
          >
            <Select
              mode={canReadRoles ? 'multiple' : 'tags'}
              options={roles?.map((r) => ({
                value: r.code,
                label: `${r.code} (${r.name})`,
                disabled: r.code === ROLE_ADMIN && !iAmAdmin,
              }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
