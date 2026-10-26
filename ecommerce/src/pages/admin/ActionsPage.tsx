import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { App, Button, Card, Form, Input, Modal, Popconfirm, Space, Table } from 'antd';
import { useState } from 'react';
import { actionApi, type ActionInput } from '../../api/auth';
import type { Action } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { showError } from '../../utils/errors';
import { RESOURCE_CODE, RESOURCE_CODE_MESSAGE } from '../../utils/patterns';
import { useLoader } from '../../utils/useLoader';

export default function ActionsPage() {
  const { hasPermission } = useAuth();
  const { message } = App.useApp();
  const { data, loading, reload } = useLoader(actionApi.list);
  const [form] = Form.useForm<ActionInput>();
  /** null: đóng; 'new': tạo mới; còn lại: action đang sửa */
  const [editing, setEditing] = useState<Action | 'new' | null>(null);
  const [saving, setSaving] = useState(false);

  const open = (action: Action | 'new') => setEditing(action);

  const save = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing === 'new') {
        await actionApi.create(values);
      } else if (editing) {
        await actionApi.update(editing.id, { ...values, version: editing.version });
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

  const remove = async (action: Action) => {
    try {
      await actionApi.remove(action.id);
      message.success('Đã xoá');
      reload();
    } catch (e) {
      showError(message, e);
    }
  };

  return (
    <Card
      title="Action"
      extra={
        hasPermission('action:create') && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => open('new')}>
            Thêm
          </Button>
        )
      }
    >
      <Table<Action>
        rowKey="id"
        loading={loading}
        dataSource={data}
        pagination={false}
        columns={[
          { title: 'Code', dataIndex: 'code' },
          { title: 'Tên', dataIndex: 'name' },
          { title: 'Mô tả', dataIndex: 'description' },
          {
            title: '',
            width: 100,
            render: (_, action) => (
              <Space>
                {hasPermission('action:update') && (
                  <Button size="small" icon={<EditOutlined />} onClick={() => open(action)} />
                )}
                {hasPermission('action:delete') && (
                  <Popconfirm title={`Xoá action ${action.code}?`} onConfirm={() => remove(action)}>
                    <Button size="small" danger icon={<DeleteOutlined />} />
                  </Popconfirm>
                )}
              </Space>
            ),
          },
        ]}
      />
      <Modal
        title={editing === 'new' ? 'Thêm action' : 'Sửa action'}
        open={editing !== null}
        onOk={save}
        confirmLoading={saving}
        onCancel={() => setEditing(null)}
        destroyOnHidden
      >
        {/* Modal huỷ nội dung khi đóng nên Form được mount lại, initialValues áp dụng mỗi lần mở */}
        <Form form={form} layout="vertical" preserve={false} initialValues={editing === 'new' ? {} : editing ?? {}}>
          <Form.Item
            name="code"
            label="Code"
            extra="Không đổi được sau khi tạo"
            rules={[
              { required: true, message: 'Nhập code' },
              { pattern: RESOURCE_CODE, message: RESOURCE_CODE_MESSAGE },
            ]}
          >
            <Input disabled={editing !== 'new'} placeholder="vd. export" />
          </Form.Item>
          <Form.Item name="name" label="Tên" rules={[{ required: true, message: 'Nhập tên' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="Mô tả">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
