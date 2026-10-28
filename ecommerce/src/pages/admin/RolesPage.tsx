import {
  CheckOutlined,
  DeleteOutlined,
  EditOutlined,
  KeyOutlined,
  LockOutlined,
  MenuOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  TeamOutlined,
  UserAddOutlined,
  UserOutlined,
} from '@ant-design/icons';
import {
  App,
  Badge,
  Button,
  Card,
  Checkbox,
  Col,
  Divider,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
  Tooltip,
  Tree,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { DataNode } from 'antd/es/tree';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { menuApi, roleApi, userApi, type RoleInput } from '../../api/auth';
import type { MenuNode, Role, RoleDetail, Status } from '../../api/types';
import { ROLE_ADMIN, useAuth } from '../../auth/AuthContext';
import StatCard from '../../components/StatCard';
import { menuIcon } from '../../layout/icons';
import { showError } from '../../utils/errors';
import { flattenMenus } from '../../utils/menuTree';
import { ROLE_CODE, ROLE_CODE_MESSAGE } from '../../utils/patterns';
import { useLoader } from '../../utils/useLoader';

const { Text } = Typography;

const ROLE_COLORS: Record<string, string> = { ADMIN: 'red', USER: 'blue' };

function roleTag(code: string) {
  return (
    <Tag color={ROLE_COLORS[code] ?? 'cyan'} className="mono">
      {code}
    </Tag>
  );
}

function toTreeData(nodes: MenuNode[]): DataNode[] {
  return nodes.map((n) => ({
    key: n.id,
    title: (
      <span>
        {menuIcon(n.icon)} <strong>{n.name}</strong> <Text type="secondary">({n.code})</Text>
      </span>
    ),
    children: toTreeData(n.children),
  }));
}

interface RoleForm {
  code: string;
  name: string;
  description?: string;
  active: boolean;
  userIds?: string[];
}

export default function RolesPage() {
  const { hasPermission, user: me, logout } = useAuth();
  const { message } = App.useApp();
  const navigate = useNavigate();
  const { data, loading, reload } = useLoader(roleApi.list);
  const canListUsers = hasPermission('user:VIEW');
  const canEditUsers = canListUsers && hasPermission('user:EDIT');
  const canReadMenus = hasPermission('menu:VIEW');
  const { data: users } = useLoader(() => userApi.list({ size: 100 }), [], canListUsers);
  const { data: menuTree } = useLoader(menuApi.tree, [], canReadMenus);
  const allMenuIds = useMemo(() => flattenMenus(menuTree ?? []).map((m) => m.id), [menuTree]);

  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<Status[]>([]);

  const [form] = Form.useForm<RoleForm>();
  /** null: đóng; 'new': thêm; RoleDetail: sửa */
  const [editing, setEditing] = useState<RoleDetail | 'new' | null>(null);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [menuKeys, setMenuKeys] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const [assignOpen, setAssignOpen] = useState(false);

  const roles = useMemo(() => data ?? [], [data]);
  const filtered = useMemo(() => {
    const kw = search.trim().toLowerCase();
    return roles.filter(
      (r) =>
        (!kw || [r.code, r.name, r.description ?? ''].some((v) => v.toLowerCase().includes(kw))) &&
        (statusFilter.length === 0 || statusFilter.includes(r.status)),
    );
  }, [roles, search, statusFilter]);

  const openCreate = () => {
    setMenuKeys([]);
    setEditing('new');
  };

  const openEdit = async (role: Role) => {
    setLoadingDetail(true);
    try {
      const detail = await roleApi.get(role.id);
      setMenuKeys(detail.menuIds);
      setEditing(detail);
    } catch (e) {
      showError(message, e);
    } finally {
      setLoadingDetail(false);
    }
  };

  const save = async () => {
    const values = await form.validateFields();
    const input: RoleInput = {
      code: values.code,
      name: values.name,
      description: values.description,
      status: values.active ? 'ACTIVE' : 'INACTIVE',
      // Field không hiện trên form (thiếu quyền) thì không gửi = giữ nguyên
      menuIds: canReadMenus ? menuKeys : undefined,
      userIds: canEditUsers ? values.userIds : undefined,
    };
    const touchedMe = (ids: string[] | undefined, before: string[]) =>
      !!me && ids !== undefined && ids.includes(me.id) !== before.includes(me.id);
    setSaving(true);
    try {
      if (editing === 'new') {
        await roleApi.create(input);
        message.success('Đã tạo vai trò');
      } else if (editing) {
        await roleApi.update(editing.id, { ...input, version: editing.version });
        message.success('Đã lưu vai trò. Quyền / menu mới có hiệu lực khi user làm mới phiên.');
      }
      setEditing(null);
      reload();
      // Thêm / gỡ chính mình khỏi vai trò: phiên của mình bị thu hồi
      if (touchedMe(input.userIds, editing === 'new' || !editing ? [] : editing.userIds)) {
        message.info('Vai trò của bạn đã đổi, hãy đăng nhập lại');
        await logout();
        navigate('/login', { replace: true });
      }
    } catch (e) {
      showError(message, e, form);
    } finally {
      setSaving(false);
    }
  };

  const toggleStatus = async (role: Role) => {
    try {
      await roleApi.update(role.id, {
        name: role.name,
        description: role.description,
        status: role.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
        version: role.version,
      });
      message.success(role.status === 'ACTIVE' ? 'Đã tạm dừng vai trò' : 'Đã kích hoạt vai trò');
    } catch (e) {
      showError(message, e);
    }
    reload();
  };

  const remove = async (role: Role) => {
    try {
      await roleApi.remove(role.id);
      message.success('Đã xóa vai trò');
      reload();
    } catch (e) {
      showError(message, e);
    }
  };

  const columns: ColumnsType<Role> = [
    { title: 'Mã vai trò', dataIndex: 'code', render: (code: string) => roleTag(code) },
    {
      title: 'Tên vai trò',
      dataIndex: 'name',
      render: (name: string, record) => (
        <Space>
          <strong>{name}</strong>
          {record.system && (
            <Tooltip title="Vai trò hệ thống: không xóa / không tạm dừng được">
              <Tag icon={<LockOutlined />}>Hệ thống</Tag>
            </Tooltip>
          )}
        </Space>
      ),
    },
    { title: 'Mô tả', dataIndex: 'description', width: '28%', render: (d: string | null) => <Text type="secondary">{d}</Text> },
    {
      title: 'Số tài khoản',
      dataIndex: 'accountCount',
      render: (n: number) => (
        <Space>
          <TeamOutlined className="muted" />
          <Badge count={n} showZero color="#1677ff" />
        </Space>
      ),
    },
    {
      title: 'Quyền / Menu',
      key: 'counts',
      render: (_, record) =>
        record.code === ROLE_ADMIN ? (
          <Tag color="red">Toàn quyền</Tag>
        ) : (
          <Space size={4}>
            <Tag color="geekblue">{record.permissionCount} quyền</Tag>
            <Tag>{record.menuCount} menu</Tag>
          </Space>
        ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      render: (s: Status) => <Badge status={s === 'ACTIVE' ? 'success' : 'default'} text={s === 'ACTIVE' ? 'Đang kích hoạt' : 'Tạm dừng'} />,
    },
    {
      title: 'Thao tác',
      key: 'actions',
      fixed: 'right',
      render: (_, record) => (
        <Space>
          {hasPermission('role:EDIT') && (
            <Tooltip title="Cấu hình & sửa vai trò">
              <Button type="text" icon={<EditOutlined />} onClick={() => openEdit(record)} />
            </Tooltip>
          )}
          {hasPermission('role:EDIT') && !record.system && (
            <Tooltip title={record.status === 'ACTIVE' ? 'Tạm dừng vai trò' : 'Kích hoạt vai trò'}>
              <Switch size="small" checked={record.status === 'ACTIVE'} onChange={() => toggleStatus(record)} />
            </Tooltip>
          )}
          {hasPermission('role:DELETE') && !record.system && (
            <Popconfirm
              title="Xóa vai trò?"
              description={`Vai trò ${record.name} sẽ bị gỡ khỏi ${record.accountCount} tài khoản (phiên của họ bị thu hồi).`}
              onConfirm={() => remove(record)}
              okText="Xóa vai trò"
              cancelText="Hủy"
              okButtonProps={{ danger: true }}
            >
              <Button type="text" danger icon={<DeleteOutlined />} />
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  const isSystem = editing !== 'new' && !!editing?.system;
  // Mọi tài khoản luôn có vai trò USER: không sửa thành viên của USER
  const showMembers = canEditUsers && !(editing !== 'new' && editing?.code === 'USER');
  const checkedCount = allMenuIds.filter((id) => menuKeys.includes(id)).length;

  return (
    <Spin spinning={loadingDetail}>
      <Row gutter={[16, 16]} className="page-stats">
        <Col xs={24} sm={12} md={6}>
          <StatCard title="TỔNG VAI TRÒ" value={roles.length} icon={<SafetyCertificateOutlined />} color="#1677ff" />
        </Col>
        <Col xs={24} sm={12} md={6}>
          <StatCard
            title="ĐANG HOẠT ĐỘNG"
            value={roles.filter((r) => r.status === 'ACTIVE').length}
            icon={<CheckOutlined />}
            color="#52c41a"
          />
        </Col>
        <Col xs={24} sm={12} md={6}>
          <StatCard title="VAI TRÒ HỆ THỐNG" value={roles.filter((r) => r.system).length} icon={<LockOutlined />} color="#faad14" />
        </Col>
        <Col xs={24} sm={12} md={6}>
          <StatCard
            title="LƯỢT GÁN TÀI KHOẢN"
            value={roles.reduce((sum, r) => sum + r.accountCount, 0)}
            icon={<TeamOutlined />}
            color="#13c2c2"
          />
        </Col>
      </Row>

      <Card variant="borderless" className="page-toolbar-card">
        <Row gutter={[16, 16]} align="middle" justify="space-between">
          <Col xs={24} sm={14}>
            <Space wrap>
              <Input
                placeholder="Tìm vai trò theo mã, tên, mô tả..."
                prefix={<SearchOutlined className="muted" />}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                allowClear
                style={{ width: 280 }}
              />
              <Select
                mode="multiple"
                allowClear
                placeholder="Trạng thái"
                value={statusFilter}
                onChange={setStatusFilter}
                style={{ minWidth: 200 }}
                options={[
                  { value: 'ACTIVE', label: 'Đang hoạt động' },
                  { value: 'INACTIVE', label: 'Tạm dừng' },
                ]}
              />
              {(search || statusFilter.length > 0) && (
                <Button
                  type="text"
                  icon={<ReloadOutlined />}
                  onClick={() => {
                    setSearch('');
                    setStatusFilter([]);
                  }}
                >
                  Đặt lại
                </Button>
              )}
            </Space>
          </Col>
          <Col xs={24} sm={10} className="align-right">
            <Space wrap>
              {canEditUsers && (
                <Button icon={<UserAddOutlined />} onClick={() => setAssignOpen(true)}>
                  Gán user vào vai trò
                </Button>
              )}
              {hasPermission('role:ADD') && (
                <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                  Thêm vai trò
                </Button>
              )}
            </Space>
          </Col>
        </Row>
      </Card>

      <Card variant="borderless" styles={{ body: { padding: 0 } }}>
        <Table<Role>
          dataSource={filtered}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={false}
          locale={{ emptyText: 'Không tìm thấy vai trò phù hợp' }}
        />
      </Card>

      <Drawer
        title={editing === 'new' ? 'Thêm vai trò mới' : `Cấu hình vai trò: ${editing?.name ?? ''}`}
        size={640}
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
        <Form
          form={form}
          layout="vertical"
          preserve={false}
          initialValues={
            editing && editing !== 'new'
              ? { ...editing, active: editing.status === 'ACTIVE' }
              : { active: true, userIds: [] }
          }
        >
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="Mã vai trò"
                name="code"
                normalize={(v: string) => v?.toUpperCase()}
                rules={[
                  { required: true, message: 'Mã vai trò không được bỏ trống' },
                  { pattern: ROLE_CODE, message: ROLE_CODE_MESSAGE },
                ]}
              >
                <Input placeholder="VD: ACCOUNTANT" disabled={editing !== 'new'} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="Tên vai trò" name="name" rules={[{ required: true, message: 'Tên vai trò không được bỏ trống' }]}>
                <Input placeholder="Nhân viên kế toán" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item label="Mô tả" name="description">
            <Input.TextArea rows={3} placeholder="Trách nhiệm, phạm vi của vai trò..." />
          </Form.Item>
          <Form.Item
            label="Trạng thái hoạt động"
            name="active"
            valuePropName="checked"
            extra={
              isSystem
                ? 'Vai trò hệ thống luôn ở trạng thái hoạt động'
                : 'Tạm dừng vai trò: tài khoản thuộc vai trò này không còn quyền / menu của nó'
            }
          >
            <Switch disabled={isSystem} checkedChildren="Hoạt động" unCheckedChildren="Tạm dừng" />
          </Form.Item>

          {showMembers && (
            <Form.Item
              label={
                <span>
                  <UserOutlined /> Người dùng thuộc vai trò này
                </span>
              }
              name="userIds"
              extra="Thêm / gỡ người dùng thì phiên của họ bị thu hồi để nhận quyền mới"
            >
              <Select
                mode="multiple"
                showSearch
                placeholder="Tìm theo tên hoặc email..."
                optionFilterProp="label"
                maxTagCount="responsive"
                options={(users?.content ?? []).map((u) => ({
                  value: u.id,
                  label: `${u.fullName || u.email} (${u.email})`,
                }))}
              />
            </Form.Item>
          )}

          {canReadMenus && (
            <>
              <Divider>
                <MenuOutlined /> Menu hiển thị cho vai trò
              </Divider>
              {editing !== 'new' && editing?.code === ROLE_ADMIN ? (
                <Text type="secondary">ADMIN luôn thấy mọi menu đang hoạt động.</Text>
              ) : (
                <>
                  <div className="tree-toolbar">
                    <Text type="secondary">Tích chọn các menu được hiển thị:</Text>
                    <Checkbox
                      checked={allMenuIds.length > 0 && checkedCount === allMenuIds.length}
                      indeterminate={checkedCount > 0 && checkedCount < allMenuIds.length}
                      onChange={(e) => setMenuKeys(e.target.checked ? allMenuIds : [])}
                    >
                      Chọn tất cả ({checkedCount}/{allMenuIds.length})
                    </Checkbox>
                  </div>
                  <div className="tree-box">
                    <Tree
                      checkable
                      defaultExpandAll
                      selectable={false}
                      checkedKeys={menuKeys}
                      onCheck={(keys) => setMenuKeys((Array.isArray(keys) ? keys : keys.checked).map(String))}
                      treeData={toTreeData(menuTree ?? [])}
                    />
                  </div>
                </>
              )}
            </>
          )}

          <Divider>
            <KeyOutlined /> Quyền hạn
          </Divider>
          {editing !== 'new' && editing?.code === ROLE_ADMIN ? (
            <Text type="secondary">ADMIN luôn có mọi quyền.</Text>
          ) : (
            <Space orientation="vertical" style={{ width: '100%' }}>
              {editing && editing !== 'new' && editing.permissions.length > 0 ? (
                <Space wrap size={4}>
                  {editing.permissions.map((p) => (
                    <Tag key={p} color="geekblue">
                      {p}
                    </Tag>
                  ))}
                </Space>
              ) : (
                <Text type="secondary">Chưa được cấp quyền nào.</Text>
              )}
              {hasPermission('permission:VIEW') && (
                <Button icon={<SafetyCertificateOutlined />} onClick={() => navigate('/admin/permissions')}>
                  Cấp quyền ở Ma trận phân quyền
                </Button>
              )}
            </Space>
          )}
        </Form>
      </Drawer>

      {assignOpen && <AssignUsersModal roles={roles} onClose={() => setAssignOpen(false)} onDone={reload} />}
    </Spin>
  );
}

/** Thêm một vai trò cho nhiều người dùng (giữ vai trò cũ của họ). */
function AssignUsersModal({ roles, onClose, onDone }: { roles: Role[]; onClose: () => void; onDone: () => void }) {
  const { message } = App.useApp();
  const { user: me, authorities } = useAuth();
  const { data: users } = useLoader(() => userApi.list({ size: 100 }));
  const [selected, setSelected] = useState<string[]>([]);
  const [role, setRole] = useState<string>();
  const [saving, setSaving] = useState(false);
  const iAmAdmin = authorities?.roles.includes(ROLE_ADMIN) ?? false;

  const assign = async () => {
    if (selected.length === 0 || !role) {
      message.warning('Chọn ít nhất 1 người dùng và 1 vai trò');
      return;
    }
    setSaving(true);
    let updated = 0;
    try {
      for (const user of (users?.content ?? []).filter((u) => selected.includes(u.id))) {
        if (!user.roles.includes(role)) {
          await userApi.update(user.id, {
            fullName: user.fullName,
            phoneNumber: user.phoneNumber,
            roles: [...user.roles, role],
            version: user.version,
          });
          updated++;
        }
      }
      message.success(`Đã gán vai trò ${role} cho ${updated} người dùng`);
      onDone();
      onClose();
    } catch (e) {
      showError(message, e);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      title={
        <Space>
          <UserAddOutlined />
          <span>Gán người dùng vào vai trò</span>
        </Space>
      }
      open
      onCancel={onClose}
      onOk={assign}
      confirmLoading={saving}
      okText="Gán vai trò"
      cancelText="Hủy"
      width={650}
    >
      <Form layout="vertical">
        <Form.Item label="1. Chọn người dùng" required>
          <Select
            mode="multiple"
            showSearch
            placeholder="Tìm theo tên hoặc email..."
            value={selected}
            onChange={setSelected}
            optionFilterProp="label"
            maxTagCount="responsive"
            options={(users?.content ?? [])
              .filter((u) => u.id !== me?.id)
              .map((u) => ({ value: u.id, label: `${u.fullName || u.email} (${u.email})` }))}
          />
        </Form.Item>
        <Form.Item label="2. Chọn vai trò muốn gán" required>
          <Select
            placeholder="-- Chọn vai trò --"
            value={role}
            onChange={setRole}
            options={roles.map((r) => ({
              value: r.code,
              label: `${r.code} (${r.name})`,
              disabled: r.code === ROLE_ADMIN && !iAmAdmin,
            }))}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
