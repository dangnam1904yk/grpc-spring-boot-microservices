import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { App, Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag } from 'antd';
import { useState } from 'react';
import { actionApi, resourceApi, type ResourceInput } from '../../api/auth';
import type { Resource } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { showError } from '../../utils/errors';
import { RESOURCE_CODE, RESOURCE_CODE_MESSAGE } from '../../utils/patterns';
import { useLoader } from '../../utils/useLoader';

export default function ResourcesPage() {
  const { hasPermission } = useAuth();
  const { message } = App.useApp();
  const { data, loading, reload } = useLoader(resourceApi.list);
  // Không có quyền xem action thì vẫn gõ code action bằng tay được
  const canReadActions = hasPermission('action:read');
  const { data: actions } = useLoader(actionApi.list, [], canReadActions);
  const [form] = Form.useForm<ResourceInput>();
  const [editing, setEditing] = useState<Resource | 'new' | null>(null);
  const [saving, setSaving] = useState(false);

  const save = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing === 'new') {
        await resourceApi.create(values);
      } else if (editing) {
        await resourceApi.update(editing.id, { ...values, version: editing.version });
      }
      message.success('Đã lưu');
      setEditing(null);
      reload();
    } catch (e) {
      showError(message, e, form);
    } finally {
      setSaving(false);
    }
  };

  const remove = async (resource: Resource) => {
    try {
      await resourceApi.remove(resource.id);
      message.success('Đã xoá');
      reload();
    } catch (e) {
      showError(message, e);
    }
  };

  return (
    <Card
      title="Resource"
      extra={
        hasPermission('resource:create') && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing('new')}>
            Thêm
          </Button>
        )
      }
    >
      <Table<Resource>
        rowKey="id"
        loading={loading}
        dataSource={data}
        pagination={false}
        columns={[
          { title: 'Code', dataIndex: 'code' },
          { title: 'Tên', dataIndex: 'name' },
          {
            title: 'Action',
            dataIndex: 'actions',
            render: (codes: string[]) => (
              <Space wrap size={4}>
                {codes.map((c) => (
                  <Tag key={c}>{c}</Tag>
                ))}
              </Space>
            ),
          },
          {
            title: '',
            width: 100,
            render: (_, resource) => (
              <Space>
                {hasPermission('resource:update') && (
                  <Button size="small" icon={<EditOutlined />} onClick={() => setEditing(resource)} />
                )}
                {hasPermission('resource:delete') && (
                  <Popconfirm
                    title={`Xoá resource ${resource.code}?`}
                    description="Quyền của resource này cũng bị gỡ khỏi mọi role"
                    onConfirm={() => remove(resource)}
                  >
                    <Button size="small" danger icon={<DeleteOutlined />} />
                  </Popconfirm>
                )}
              </Space>
            ),
          },
        ]}
      />
      <Modal
        title={editing === 'new' ? 'Thêm resource' : 'Sửa resource'}
        open={editing !== null}
        onOk={save}
        confirmLoading={saving}
        onCancel={() => setEditing(null)}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          preserve={false}
          initialValues={editing === 'new' ? { actions: [] } : editing ?? {}}
        >
          <Form.Item
            name="code"
            label="Code"
            extra="Không đổi được sau khi tạo"
            rules={[
              { required: true, message: 'Nhập code' },
              { pattern: RESOURCE_CODE, message: RESOURCE_CODE_MESSAGE },
            ]}
          >
            <Input disabled={editing !== 'new'} placeholder="vd. report" />
          </Form.Item>
          <Form.Item name="name" label="Tên" rules={[{ required: true, message: 'Nhập tên' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="Mô tả">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item
            name="actions"
            label="Action áp dụng được"
            extra={editing !== 'new' ? 'Bỏ action thì quyền tương ứng bị gỡ khỏi mọi role' : undefined}
          >
            <Select
              mode={canReadActions ? 'multiple' : 'tags'}
              options={actions?.map((a) => ({ value: a.code, label: `${a.code} (${a.name})` }))}
              placeholder="Chọn action"
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
