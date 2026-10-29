import {
  CheckCircleOutlined,
  EditOutlined,
  LockOutlined,
  MailOutlined,
  PhoneOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  StopOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import {
  Alert,
  App,
  Avatar,
  Badge,
  Button,
  Card,
  Col,
  Drawer,
  Form,
  Input,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { roleApi, userApi } from '../../api/auth';
import type { Status, User } from '../../api/types';
import { ROLE_ADMIN, useAuth } from '../../auth/AuthContext';
import StatCard from '../../components/StatCard';
import { showError } from '../../utils/errors';
import { useLoader } from '../../utils/useLoader';

const { Text } = Typography;
const PAGE_SIZE = 20;

interface UserForm {
  email: string;
  password?: string;
  fullName?: string;
  phoneNumber?: string;
  roles?: string[];
  active: boolean;
}

export default function UsersPage() {
  const { hasPermission, user: me, authorities, logout } = useAuth();
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [keyword, setKeyword] = useState('');
  const { data, loading, reload } = useLoader(() => userApi.list({ keyword, page, size: PAGE_SIZE }), [keyword, page]);
  const canReadRoles = hasPermission('role:VIEW');
  const { data: roles } = useLoader(roleApi.list, [], canReadRoles);
  const iAmAdmin = authorities?.roles.includes(ROLE_ADMIN) ?? false;

  const [roleFilter, setRoleFilter] = useState('ALL');
  const [statusFilter, setStatusFilter] = useState<Status[]>([]);
  const [form] = Form.useForm<UserForm>();
  const [editing, setEditing] = useState<User | 'new' | null>(null);
  const [saving, setSaving] = useState(false);
  const editingMe = editing !== 'new' && editing?.id === me?.id;

  const users = useMemo(() => data?.content ?? [], [data]);
  // Lọc vai trò / trạng thái trên trang hiện tại; từ khoá thì tìm ở server
  const filtered = users.filter(
    (u) =>
      (roleFilter === 'ALL' || u.roles.includes(roleFilter)) &&
      (statusFilter.length === 0 || statusFilter.includes(u.status)),
  );

  const save = async () => {
    const values = await form.validateFields();
    const status: Status = values.active ? 'ACTIVE' : 'INACTIVE';
    setSaving(true);
    try {
      if (editing === 'new') {
        await userApi.create({
          email: values.email,
          password: values.password ?? '',
          fullName: values.fullName,
          phoneNumber: values.phoneNumber,
          roles: canReadRoles ? values.roles : undefined,
          status,
        });
        message.success('Tạo tài khoản thành công');
      } else if (editing) {
        await userApi.update(editing.id, {
          fullName: values.fullName,
          phoneNumber: values.phoneNumber,
          status,
          roles: canReadRoles ? values.roles : undefined,
          version: editing.version,
        });
        // Đổi vai trò của chính mình: phiên hiện tại bị thu hồi
        if (editingMe && canReadRoles && !sameRoles(values.roles ?? [], editing.roles)) {
          setEditing(null);
          message.info('Vai trò của bạn đã đổi, hãy đăng nhập lại');
          await logout();
          navigate('/login', { replace: true });
          return;
        }
        message.success('Đã lưu. Đổi vai trò / khóa tài khoản thì phiên của người dùng bị thu hồi.');
      }
      setEditing(null);
      reload();
    } catch (e) {
      showError(message, e, form);
    } finally {
      setSaving(false);
    }
  };

  const toggleLock = async (user: User) => {
    try {
      await userApi.update(user.id, {
        fullName: user.fullName,
        phoneNumber: user.phoneNumber,
        status: user.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
        version: user.version,
      });
      message.success(user.status === 'ACTIVE' ? 'Đã khóa tài khoản' : 'Đã mở khóa tài khoản');
    } catch (e) {
      showError(message, e);
    }
    reload();
  };

  const columns: ColumnsType<User> = [
    {
      title: 'Họ tên / Email',
      dataIndex: 'email',
      render: (email: string, record) => (
        <Space>
          <Avatar className="user-avatar">{(record.fullName || email).charAt(0).toUpperCase()}</Avatar>
          <div>
            <strong>{record.fullName || '—'}</strong>
            {record.id === me?.id && <Tag className="ml-8">Bạn</Tag>}
            <div className="muted small">{email}</div>
          </div>
        </Space>
      ),
    },
    {
      title: 'Liên hệ',
      dataIndex: 'phoneNumber',
      render: (phone: string | null) =>
        phone ? (
          <Space>
            <PhoneOutlined className="muted" />
            {phone}
          </Space>
        ) : (
          <Text type="secondary">—</Text>
        ),
    },
    {
      title: 'Vai trò',
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
      dataIndex: 'status',
      render: (s: Status) => <Badge status={s === 'ACTIVE' ? 'success' : 'error'} text={s === 'ACTIVE' ? 'Hoạt động' : 'Đã khóa'} />,
    },
    {
      title: 'Ngày tạo',
      dataIndex: 'createDate',
      render: (d: string | null) => (d ? new Date(d).toLocaleDateString('vi-VN') : '—'),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 110,
      render: (_, record) =>
        hasPermission('user:EDIT') && (
          <Space>
            <Tooltip title="Sửa thông tin / vai trò">
              <Button type="text" icon={<EditOutlined />} onClick={() => setEditing(record)} />
            </Tooltip>
            {record.id !== me?.id && (
              <Tooltip title={record.status === 'ACTIVE' ? 'Khóa tài khoản' : 'Mở khóa tài khoản'}>
                <Switch size="small" checked={record.status === 'ACTIVE'} onChange={() => toggleLock(record)} />
              </Tooltip>
            )}
          </Space>
        ),
    },
  ];

  return (
    <div>
      <Row gutter={[16, 16]} className="page-stats">
        <Col xs={24} sm={12} md={6}>
          <StatCard title="TỔNG TÀI KHOẢN" value={data?.totalElements ?? 0} icon={<TeamOutlined />} color="#1677ff" />
        </Col>
        <Col xs={24} sm={12} md={6}>
          <StatCard
            title="HOẠT ĐỘNG (TRANG NÀY)"
            value={users.filter((u) => u.status === 'ACTIVE').length}
            icon={<CheckCircleOutlined />}
            color="#52c41a"
          />
        </Col>
        <Col xs={24} sm={12} md={6}>
          <StatCard
            title="ĐÃ KHÓA (TRANG NÀY)"
            value={users.filter((u) => u.status === 'INACTIVE').length}
            icon={<StopOutlined />}
            color="#ff4d4f"
          />
        </Col>
        <Col xs={24} sm={12} md={6}>
          <StatCard
            title="QUẢN TRỊ (TRANG NÀY)"
            value={users.filter((u) => u.roles.includes(ROLE_ADMIN)).length}
            icon={<SafetyCertificateOutlined />}
            color="#722ed1"
          />
        </Col>
      </Row>

      <Card variant="borderless" className="page-toolbar-card">
        <Row gutter={[16, 16]} align="middle" justify="space-between">
          <Col xs={24} lg={16}>
            <Space wrap>
              <Input.Search
                placeholder="Tìm theo email, họ tên, số điện thoại..."
                allowClear
                onSearch={(v) => {
                  setPage(0);
                  setKeyword(v.trim());
                }}
                style={{ width: 300 }}
              />
              <Select
                value={roleFilter}
                onChange={setRoleFilter}
                style={{ width: 180 }}
                options={[
                  { value: 'ALL', label: 'Tất cả vai trò' },
                  ...(roles ?? []).map((r) => ({ value: r.code, label: `${r.code} (${r.name})` })),
                ]}
              />
              <Select
                mode="multiple"
                allowClear
                placeholder="Trạng thái"
                value={statusFilter}
                onChange={setStatusFilter}
                style={{ minWidth: 180 }}
                options={[
                  { value: 'ACTIVE', label: 'Hoạt động' },
                  { value: 'INACTIVE', label: 'Đã khóa' },
                ]}
              />
              <Button icon={<ReloadOutlined />} onClick={reload}>
                Tải lại
              </Button>
            </Space>
          </Col>
          <Col xs={24} lg={8} className="align-right">
            {hasPermission('user:ADD') && (
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing('new')}>
                Thêm tài khoản
              </Button>
            )}
          </Col>
        </Row>
      </Card>

      <Card variant="borderless" styles={{ body: { padding: 0 } }}>
        <Table<User>
          rowKey="id"
          loading={loading}
          dataSource={filtered}
          columns={columns}
          pagination={{
            current: page + 1,
            pageSize: PAGE_SIZE,
            total: data?.totalElements,
            showSizeChanger: false,
            showTotal: (total) => `Tổng cộng ${total} tài khoản`,
            onChange: (p) => setPage(p - 1),
          }}
        />
      </Card>

      <Drawer
        title={editing === 'new' ? 'Thêm tài khoản mới' : `Sửa tài khoản: ${editing?.email ?? ''}`}
        size={520}
        open={editing !== null}
        onClose={() => setEditing(null)}
        destroyOnHidden
        extra={
          <Space>
            <Button onClick={() => setEditing(null)}>Hủy</Button>
            <Button type="primary" onClick={save} loading={saving}>
              Lưu lại
            </Button>
          </Space>
        }
      >
        {editingMe && (
          <Alert
            type="warning"
            showIcon
            className="mb-16"
            message="Đây là tài khoản của bạn: đổi vai trò thì bạn phải đăng nhập lại"
          />
        )}
        <Form
          form={form}
          layout="vertical"
          preserve={false}
          initialValues={
            editing && editing !== 'new'
              ? { ...editing, active: editing.status === 'ACTIVE' }
              : { active: true, roles: ['USER'] }
          }
        >
          <Form.Item
            name="email"
            label="Email (tên đăng nhập)"
            rules={[
              { required: true, message: 'Nhập email' },
              { type: 'email', message: 'Email không hợp lệ' },
            ]}
          >
            <Input prefix={<MailOutlined />} disabled={editing !== 'new'} />
          </Form.Item>
          {editing === 'new' && (
            <Form.Item
              name="password"
              label="Mật khẩu"
              rules={[
                { required: true, message: 'Nhập mật khẩu' },
                { min: 8, max: 72, message: 'Từ 8 đến 72 ký tự' },
                { pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/, message: 'Phải có cả chữ và số' },
              ]}
            >
              <Input.Password prefix={<LockOutlined />} autoComplete="new-password" />
            </Form.Item>
          )}
          <Row gutter={16}>
            <Col span={14}>
              <Form.Item name="fullName" label="Họ và tên" rules={[{ max: 255 }]}>
                <Input placeholder="Nguyễn Văn A" />
              </Form.Item>
            </Col>
            <Col span={10}>
              <Form.Item
                name="phoneNumber"
                label="Số điện thoại"
                rules={[{ pattern: /^[0-9+ .-]{0,20}$/, message: 'Số điện thoại không hợp lệ' }]}
              >
                <Input prefix={<PhoneOutlined />} />
              </Form.Item>
            </Col>
          </Row>
          {canReadRoles && (
            <Form.Item
              name="roles"
              label="Vai trò"
              extra={iAmAdmin ? 'USER luôn được giữ' : 'USER luôn được giữ. Chỉ ADMIN được cấp / thu hồi ADMIN'}
            >
              <Select
                mode="multiple"
                options={(roles ?? []).map((r) => ({
                  value: r.code,
                  label: `${r.code} (${r.name})`,
                  disabled: r.code === ROLE_ADMIN && !iAmAdmin,
                }))}
              />
            </Form.Item>
          )}
          <Form.Item name="active" label="Trạng thái" valuePropName="checked">
            <Switch checkedChildren="Hoạt động" unCheckedChildren="Đã khóa" disabled={editingMe} />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  );
}

function sameRoles(a: string[], b: string[]) {
  const withUser = (roles: string[]) => new Set([...roles, 'USER']);
  const x = withUser(a);
  const y = withUser(b);
  return x.size === y.size && [...x].every((r) => y.has(r));
}
