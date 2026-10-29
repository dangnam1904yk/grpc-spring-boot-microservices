import {
  CloseOutlined,
  DeleteOutlined,
  EditOutlined,
  FolderOpenOutlined,
  KeyOutlined,
  LockOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SaveOutlined,
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
  Drawer,
  Form,
  Input,
  Popconfirm,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { menuApi, permissionApi, roleApi, userApi, type PermissionInput } from '../../api/auth';
import type { Permission, Role, RoleDetail, User } from '../../api/types';
import { ROLE_ADMIN, useAuth } from '../../auth/AuthContext';
import StatCard from '../../components/StatCard';
import { menuIcon } from '../../layout/icons';
import { showError } from '../../utils/errors';
import { flattenMenus, type FlatMenu } from '../../utils/menuTree';
import { PERMISSION_CODE, PERMISSION_CODE_MESSAGE } from '../../utils/patterns';
import { useLoader } from '../../utils/useLoader';

const { Text } = Typography;

const cell = (menu: FlatMenu, permission: Permission) => `${menu.code}:${permission.code}`;

export default function PermissionsPage() {
  const { hasPermission } = useAuth();
  const { data: permissions, loading, reload } = useLoader(permissionApi.list);
  const canReadMenus = hasPermission('menu:VIEW');
  const { data: menuTree } = useLoader(menuApi.tree, [], canReadMenus);
  const [activeTab, setActiveTab] = useState('directory');

  const list = permissions ?? [];
  const flat = useMemo(() => flattenMenus(menuTree ?? []), [menuTree]);
  const systemCount = list.filter((p) => p.type === 'SYSTEM').length;

  const tabs = [
    {
      key: 'directory',
      label: (
        <span>
          <KeyOutlined /> Danh mục quyền hạn
        </span>
      ),
      children: <PermissionDirectory permissions={list} loading={loading} reload={reload} />,
    },
    ...(hasPermission('role:VIEW') && canReadMenus
      ? [
          {
            key: 'matrix',
            label: (
              <span>
                <SafetyCertificateOutlined /> Ma trận phân quyền theo menu
              </span>
            ),
            children: <PermissionMatrix permissions={list} menus={flat} active={activeTab === 'matrix'} />,
          },
        ]
      : []),
    ...(hasPermission('user:VIEW')
      ? [
          {
            key: 'users',
            label: (
              <span>
                <TeamOutlined /> Gán user vào vai trò
              </span>
            ),
            children: <UserRoleAssignment />,
          },
        ]
      : []),
  ];

  return (
    <div>
      <Row gutter={[16, 16]} className="page-stats">
        <Col xs={24} sm={12} md={6}>
          <StatCard title="TỔNG SỐ QUYỀN HẠN" value={list.length} icon={<KeyOutlined />} color="#1677ff" />
        </Col>
        <Col xs={24} sm={12} md={6}>
          <StatCard title="QUYỀN HỆ THỐNG" value={systemCount} icon={<LockOutlined />} color="#52c41a" />
        </Col>
        <Col xs={24} sm={12} md={6}>
          <StatCard title="QUYỀN TÙY CHỈNH" value={list.length - systemCount} icon={<PlusOutlined />} color="#faad14" />
        </Col>
        <Col xs={24} sm={12} md={6}>
          <StatCard title="MENU CHỨC NĂNG" value={flat.length} icon={<FolderOpenOutlined />} color="#722ed1" />
        </Col>
      </Row>
      <Card variant="borderless">
        <Tabs activeKey={activeTab} onChange={setActiveTab} size="large" items={tabs} />
      </Card>
    </div>
  );
}

// ---- Tab 1: danh mục thao tác ----

function PermissionDirectory({
  permissions,
  loading,
  reload,
}: {
  permissions: Permission[];
  loading: boolean;
  reload: () => void;
}) {
  const { hasPermission } = useAuth();
  const { message } = App.useApp();
  const [form] = Form.useForm<PermissionInput & { active: boolean }>();
  const [editing, setEditing] = useState<Permission | 'new' | null>(null);
  const [saving, setSaving] = useState(false);
  const [search, setSearch] = useState('');
  const [type, setType] = useState<'ALL' | 'SYSTEM' | 'CUSTOM'>('ALL');

  const filtered = useMemo(() => {
    const kw = search.trim().toLowerCase();
    return permissions.filter(
      (p) =>
        (!kw || [p.code, p.name, p.description ?? ''].some((v) => v.toLowerCase().includes(kw))) &&
        (type === 'ALL' || p.type === type),
    );
  }, [permissions, search, type]);

  const save = async () => {
    const { active, ...values } = await form.validateFields();
    setSaving(true);
    try {
      if (editing === 'new') {
        await permissionApi.create({ ...values, code: values.code?.toUpperCase() });
        message.success('Tạo quyền hạn mới thành công');
      } else if (editing) {
        await permissionApi.update(editing.id, {
          name: values.name,
          description: values.description,
          status: active ? 'ACTIVE' : 'INACTIVE',
          version: editing.version,
        });
        message.success('Cập nhật quyền hạn thành công');
      }
      setEditing(null);
      reload();
    } catch (e) {
      showError(message, e, form);
    } finally {
      setSaving(false);
    }
  };

  const remove = async (permission: Permission) => {
    try {
      await permissionApi.remove(permission.id);
      message.success('Đã xóa quyền hạn khỏi hệ thống');
      reload();
    } catch (e) {
      showError(message, e);
    }
  };

  const columns: ColumnsType<Permission> = [
    {
      title: 'Mã quyền',
      dataIndex: 'code',
      width: '22%',
      render: (code: string, record) => (
        <Space>
          <Tag color={record.type === 'SYSTEM' ? 'geekblue' : 'orange'} className="mono">
            {code}
          </Tag>
          {record.type === 'SYSTEM' && (
            <Tooltip title="Quyền hệ thống: code đang dùng, không xóa / không tắt được">
              <LockOutlined className="muted" />
            </Tooltip>
          )}
        </Space>
      ),
    },
    { title: 'Tên quyền hạn', dataIndex: 'name', width: '20%', render: (name: string) => <strong>{name}</strong> },
    { title: 'Mô tả', dataIndex: 'description', render: (d: string | null) => <Text type="secondary">{d}</Text> },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 140,
      render: (s: string) => <Badge status={s === 'ACTIVE' ? 'success' : 'default'} text={s === 'ACTIVE' ? 'Đang dùng' : 'Tạm tắt'} />,
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 110,
      render: (_, record) => (
        <Space>
          {hasPermission('permission:EDIT') && (
            <Tooltip title="Sửa quyền hạn">
              <Button type="text" icon={<EditOutlined />} onClick={() => setEditing(record)} />
            </Tooltip>
          )}
          {hasPermission('permission:DELETE') && record.type === 'CUSTOM' && (
            <Popconfirm
              title="Xóa quyền hạn tùy chỉnh này?"
              description={`Xóa ${record.code} sẽ thu hồi quyền này trên mọi menu khỏi mọi vai trò.`}
              onConfirm={() => remove(record)}
              okText="Xóa quyền"
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

  return (
    <>
      <Row gutter={[12, 12]} justify="space-between" className="page-toolbar">
        <Col xs={24} sm={16}>
          <Space wrap>
            <Input
              placeholder="Tìm quyền theo mã, tên, mô tả..."
              prefix={<SearchOutlined className="muted" />}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              allowClear
              style={{ width: 280 }}
            />
            <Select
              value={type}
              onChange={setType}
              style={{ width: 180 }}
              options={[
                { value: 'ALL', label: 'Tất cả phân loại' },
                { value: 'SYSTEM', label: 'Quyền hệ thống' },
                { value: 'CUSTOM', label: 'Quyền tùy chỉnh' },
              ]}
            />
            {(search || type !== 'ALL') && (
              <Button
                type="text"
                icon={<ReloadOutlined />}
                onClick={() => {
                  setSearch('');
                  setType('ALL');
                }}
              >
                Đặt lại
              </Button>
            )}
          </Space>
        </Col>
        <Col xs={24} sm={8} className="align-right">
          {hasPermission('permission:ADD') && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing('new')}>
              Định nghĩa quyền
            </Button>
          )}
        </Col>
      </Row>
      <Table<Permission>
        dataSource={filtered}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 8, showTotal: (total) => `Tổng cộng ${total} quyền hạn` }}
        locale={{ emptyText: 'Không tìm thấy quyền hạn phù hợp' }}
      />
      <Drawer
        title={editing === 'new' ? 'Định nghĩa quyền hạn mới' : `Sửa quyền hạn: ${editing?.name ?? ''}`}
        size={500}
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
            editing && editing !== 'new' ? { ...editing, active: editing.status === 'ACTIVE' } : { active: true }
          }
        >
          <Form.Item
            label="Mã quyền (thao tác)"
            name="code"
            normalize={(v: string) => v?.toUpperCase()}
            rules={[
              { required: true, message: 'Mã quyền không được bỏ trống' },
              { pattern: PERMISSION_CODE, message: PERMISSION_CODE_MESSAGE },
            ]}
            extra="Ghép với mã menu thành mã quyền, vd. order:APPROVE. Không đổi được sau khi tạo."
          >
            <Input placeholder="VD: APPROVE" disabled={editing !== 'new'} />
          </Form.Item>
          <Form.Item
            label="Tên hiển thị"
            name="name"
            rules={[
              { required: true, message: 'Tên hiển thị không được bỏ trống' },
              { min: 2, message: 'Tối thiểu 2 ký tự' },
            ]}
          >
            <Input placeholder="VD: Duyệt" />
          </Form.Item>
          <Form.Item label="Mô tả phạm vi" name="description">
            <Input.TextArea rows={4} placeholder="Quyền này cho phép làm gì..." />
          </Form.Item>
          {editing !== 'new' && (
            <Form.Item
              label="Đang dùng"
              name="active"
              valuePropName="checked"
              extra="Tắt thì quyền này không được cấp vào token của ai (trừ ADMIN)"
            >
              <Switch disabled={editing?.type === 'SYSTEM'} />
            </Form.Item>
          )}
        </Form>
      </Drawer>
    </>
  );
}

// ---- Tab 2: ma trận vai trò × menu × thao tác ----

function PermissionMatrix({
  permissions,
  menus,
  active,
}: {
  permissions: Permission[];
  menus: FlatMenu[];
  active: boolean;
}) {
  const { hasPermission, authorities, reload: reloadMine } = useAuth();
  const { message, modal } = App.useApp();
  const { data: roles } = useLoader(roleApi.list);
  const [role, setRole] = useState<RoleDetail | null>(null);
  const [cells, setCells] = useState<Set<string>>(new Set());
  const [saving, setSaving] = useState(false);
  const [search, setSearch] = useState('');
  const [menuFilter, setMenuFilter] = useState('ALL');
  const [onlyActive, setOnlyActive] = useState(true);
  const canEdit = hasPermission('role:EDIT') && role?.code !== ROLE_ADMIN;

  const dirty = useMemo(() => {
    const original = new Set(role?.permissions ?? []);
    return original.size !== cells.size || [...cells].some((c) => !original.has(c));
  }, [role, cells]);

  const loadRole = async (id: string) => {
    try {
      const detail = await roleApi.get(id);
      setRole(detail);
      setCells(new Set(detail.permissions));
    } catch (e) {
      showError(message, e);
    }
  };

  // Mặc định chọn vai trò đầu tiên không phải ADMIN
  useEffect(() => {
    if (!role && roles && roles.length > 0) {
      loadRole((roles.find((r) => r.code !== ROLE_ADMIN) ?? roles[0]).id);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roles]);

  const selectRole = (id: string) => {
    if (!dirty) {
      loadRole(id);
      return;
    }
    modal.confirm({
      title: 'Bỏ thay đổi chưa lưu?',
      content: `Ma trận của vai trò ${role?.code} có thay đổi chưa lưu.`,
      okText: 'Bỏ thay đổi',
      cancelText: 'Ở lại',
      onOk: () => loadRole(id),
    });
  };

  const columnsList = useMemo(() => {
    const kw = search.trim().toLowerCase();
    return permissions
      .filter((p) => !onlyActive || p.status === 'ACTIVE')
      .filter((p) => !kw || [p.code, p.name, p.description ?? ''].some((v) => v.toLowerCase().includes(kw)));
  }, [permissions, onlyActive, search]);
  const rows = useMemo(
    () => (menuFilter === 'ALL' ? menus : menus.filter((m) => m.id === menuFilter)),
    [menus, menuFilter],
  );

  const toggle = (keys: string[], checked: boolean) =>
    setCells((prev) => {
      const next = new Set(prev);
      keys.forEach((k) => (checked ? next.add(k) : next.delete(k)));
      return next;
    });
  const countChecked = (keys: string[]) => keys.filter((k) => cells.has(k)).length;
  const groupCheckbox = (keys: string[], label: string) => {
    const n = countChecked(keys);
    return (
      <Checkbox
        checked={keys.length > 0 && n === keys.length}
        indeterminate={n > 0 && n < keys.length}
        disabled={!canEdit}
        onChange={(e) => toggle(keys, e.target.checked)}
      >
        {label} ({n}/{keys.length})
      </Checkbox>
    );
  };

  const save = async () => {
    if (!role) return;
    setSaving(true);
    try {
      const saved = await roleApi.updatePermissions(role.id, [...cells], role.version);
      setRole(saved);
      setCells(new Set(saved.permissions));
      message.success(`Đã lưu phân quyền cho vai trò "${role.name}". Có hiệu lực khi user làm mới phiên.`);
      if (authorities?.roles.includes(role.code)) {
        reloadMine().catch(() => undefined);
      }
    } catch (e) {
      showError(message, e);
    } finally {
      setSaving(false);
    }
  };

  const allKeys = rows.flatMap((m) => columnsList.map((p) => cell(m, p)));
  const gridColumns: ColumnsType<FlatMenu> = [
    {
      title: (
        <div>
          <div className="matrix-head">Menu</div>
          {groupCheckbox(allKeys, 'Tích tất cả')}
        </div>
      ),
      key: 'menu',
      fixed: 'left',
      width: 300,
      render: (_, menu) => (
        <div>
          <Space wrap size={4}>
            {menuIcon(menu.icon)}
            <strong>{menu.name}</strong>
            <Tag color="cyan">{menu.code}</Tag>
            {menu.status === 'INACTIVE' && <Tag>tắt</Tag>}
          </Space>
          {menu.fullPath !== menu.name && <div className="matrix-path">{menu.fullPath}</div>}
          <div className="matrix-row-toggle">
            {groupCheckbox(
              columnsList.map((p) => cell(menu, p)),
              'Cả dòng',
            )}
          </div>
        </div>
      ),
    },
    ...columnsList.map((p) => ({
      title: (
        <div className="matrix-col-head">
          <Tag color="geekblue" className="mono">
            {p.code}
          </Tag>
          <div>{p.name}</div>
          {groupCheckbox(
            rows.map((m) => cell(m, p)),
            'Cột',
          )}
        </div>
      ),
      key: p.code,
      align: 'center' as const,
      width: 140,
      render: (_: unknown, menu: FlatMenu) => {
        const key = cell(menu, p);
        return (
          <Tooltip title={key}>
            <Checkbox
              checked={cells.has(key)}
              disabled={!canEdit}
              onChange={(e) => toggle([key], e.target.checked)}
              className="matrix-cell"
            />
          </Tooltip>
        );
      },
    })),
  ];

  return (
    <>
      <Card variant="borderless" className="matrix-toolbar">
        <div className="matrix-toolbar-row">
          <Space wrap>
            <span className="matrix-label">Vai trò:</span>
            <Select
              value={role?.id}
              onChange={selectRole}
              style={{ minWidth: 220 }}
              options={(roles ?? []).map((r: Role) => ({
                value: r.id,
                label: (
                  <Space size={4}>
                    <Tag color={r.code === ROLE_ADMIN ? 'red' : 'blue'}>{r.code}</Tag>
                    <strong>{r.name}</strong>
                  </Space>
                ),
              }))}
            />
            <Input
              placeholder="Tìm quyền..."
              prefix={<SearchOutlined className="muted" />}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              allowClear
              style={{ width: 180 }}
            />
            <Select
              value={menuFilter}
              onChange={setMenuFilter}
              style={{ width: 220 }}
              options={[
                { value: 'ALL', label: `Tất cả menu (${menus.length})` },
                ...menus.map((m) => ({ value: m.id, label: m.fullPath })),
              ]}
            />
            <Checkbox checked={onlyActive} onChange={(e) => setOnlyActive(e.target.checked)}>
              <Tag color="success">Chỉ quyền đang dùng</Tag>
            </Checkbox>
          </Space>
          {canEdit && (
            <Button type="primary" icon={<SaveOutlined />} loading={saving} disabled={!dirty} onClick={save}>
              Lưu phân quyền
            </Button>
          )}
        </div>
        {role?.code === ROLE_ADMIN && (
          <Text type="secondary">ADMIN luôn có mọi quyền, không cần cấp trong ma trận.</Text>
        )}
      </Card>

      <Table<FlatMenu>
        dataSource={rows}
        columns={gridColumns}
        rowKey="id"
        pagination={false}
        bordered
        size="middle"
        scroll={{ x: 'max-content', y: 650 }}
        // Ma trận là danh sách phẳng: không để Table dựng cây từ field children của menu (sẽ lặp dòng)
        expandable={{ childrenColumnName: 'treeChildren' }}
        loading={!role}
        className="matrix-table"
        locale={{ emptyText: 'Không có menu phù hợp với bộ lọc' }}
      />

      {active && dirty && canEdit && (
        <div className="floating-save-bar">
          <Space>
            <Badge status="processing" />
            <span>Có thay đổi chưa lưu trên ma trận của vai trò {role?.code}!</span>
          </Space>
          <Space>
            <Button icon={<CloseOutlined />} onClick={() => setCells(new Set(role?.permissions ?? []))}>
              Hủy bỏ
            </Button>
            <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={save}>
              Lưu ma trận
            </Button>
          </Space>
        </div>
      )}
    </>
  );
}

// ---- Tab 3: gán user vào vai trò ----

function UserRoleAssignment() {
  const { hasPermission, user: me, authorities } = useAuth();
  const { message } = App.useApp();
  const { data: users, loading, reload } = useLoader(() => userApi.list({ size: 100 }));
  const canReadRoles = hasPermission('role:VIEW');
  const { data: roles } = useLoader(roleApi.list, [], canReadRoles);
  const canEdit = hasPermission('user:EDIT');
  const iAmAdmin = authorities?.roles.includes(ROLE_ADMIN) ?? false;
  const [selectedUsers, setSelectedUsers] = useState<string[]>([]);
  const [targetRole, setTargetRole] = useState<string>();
  const [assigning, setAssigning] = useState(false);

  const roleOptions = (roles ?? []).map((r) => ({
    value: r.code,
    label: `${r.code} (${r.name})`,
    disabled: r.code === ROLE_ADMIN && !iAmAdmin,
  }));

  const setRoles = (user: User, roleCodes: string[]) =>
    userApi.update(user.id, {
      fullName: user.fullName,
      phoneNumber: user.phoneNumber,
      roles: roleCodes,
      version: user.version,
    });

  /** Thêm vai trò cho các user đã chọn (giữ vai trò cũ của họ). */
  const assign = async () => {
    if (selectedUsers.length === 0 || !targetRole) {
      message.warning('Chọn ít nhất 1 người dùng và 1 vai trò');
      return;
    }
    setAssigning(true);
    let updated = 0;
    try {
      for (const user of (users?.content ?? []).filter((u) => selectedUsers.includes(u.id))) {
        if (!user.roles.includes(targetRole)) {
          await setRoles(user, [...user.roles, targetRole]);
          updated++;
        }
      }
      message.success(`Đã gán vai trò ${targetRole} cho ${updated} người dùng. Phiên của họ bị thu hồi để nhận quyền mới.`);
      setSelectedUsers([]);
      reload();
    } catch (e) {
      showError(message, e);
      reload();
    } finally {
      setAssigning(false);
    }
  };

  const changeRoles = async (user: User, roleCodes: string[]) => {
    try {
      await setRoles(user, roleCodes);
      message.success(`Đã đổi vai trò của ${user.fullName || user.email}`);
    } catch (e) {
      showError(message, e);
    }
    reload();
  };

  const columns: ColumnsType<User> = [
    {
      title: 'Người dùng',
      dataIndex: 'email',
      render: (email: string, record) => (
        <Space>
          <UserOutlined className="primary" />
          <div>
            <strong>{record.fullName || email}</strong>
            <div className="muted small">{email}</div>
          </div>
        </Space>
      ),
    },
    { title: 'Số điện thoại', dataIndex: 'phoneNumber', render: (v: string | null) => v || '—' },
    {
      title: 'Vai trò',
      key: 'roles',
      width: 360,
      render: (_, record) =>
        canEdit && canReadRoles && record.id !== me?.id ? (
          <Select
            mode="multiple"
            value={record.roles}
            style={{ width: '100%' }}
            options={roleOptions}
            onChange={(codes: string[]) => changeRoles(record, codes)}
          />
        ) : (
          <Space wrap size={4}>
            {record.roles.map((r) => (
              <Tag key={r} color={r === ROLE_ADMIN ? 'red' : 'geekblue'}>
                {r}
              </Tag>
            ))}
          </Space>
        ),
    },
  ];

  return (
    <>
      {canEdit && canReadRoles && (
        <Card variant="borderless" className="assign-card">
          <div className="assign-title">
            <UserAddOutlined /> Gán vai trò cho người dùng
          </div>
          <Text type="secondary">
            Chọn một hoặc nhiều người dùng, rồi chọn vai trò muốn thêm. Vai trò hiện có của họ được giữ nguyên.
          </Text>
          <Row gutter={[16, 16]} align="bottom" className="assign-row">
            <Col xs={24} md={11}>
              <Select
                mode="multiple"
                showSearch
                style={{ width: '100%' }}
                placeholder="Tìm theo tên hoặc email..."
                value={selectedUsers}
                onChange={setSelectedUsers}
                optionFilterProp="label"
                maxTagCount="responsive"
                options={(users?.content ?? [])
                  .filter((u) => u.id !== me?.id)
                  .map((u) => ({ value: u.id, label: `${u.fullName || u.email} (${u.email})` }))}
              />
            </Col>
            <Col xs={24} md={7}>
              <Select
                style={{ width: '100%' }}
                placeholder="-- Chọn vai trò --"
                value={targetRole}
                onChange={setTargetRole}
                options={roleOptions}
              />
            </Col>
            <Col xs={24} md={6}>
              <Button type="primary" icon={<SaveOutlined />} block loading={assigning} onClick={assign}>
                Gán vai trò
              </Button>
            </Col>
          </Row>
        </Card>
      )}
      <Table<User>
        dataSource={users?.content}
        columns={columns}
        rowKey="id"
        loading={loading}
        pagination={{ pageSize: 10 }}
        bordered
      />
    </>
  );
}
