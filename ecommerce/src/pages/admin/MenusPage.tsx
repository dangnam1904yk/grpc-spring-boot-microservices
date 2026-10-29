import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  CheckCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  LinkOutlined,
  LockOutlined,
  MenuOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  StopOutlined,
} from '@ant-design/icons';
import {
  App,
  Badge,
  Button,
  Card,
  Checkbox,
  Col,
  Divider,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Tooltip,
  Tree,
  TreeSelect,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { DataNode } from 'antd/es/tree';
import { useMemo, useState } from 'react';
import { menuApi, roleApi, type MenuInput } from '../../api/auth';
import type { MenuNode, RoleDetail, Status } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import StatCard from '../../components/StatCard';
import { ICON_OPTIONS, menuIcon } from '../../layout/icons';
import { showError } from '../../utils/errors';
import { findMenu, flattenMenus, toTreeOptions } from '../../utils/menuTree';
import { MENU_CODE, MENU_CODE_MESSAGE } from '../../utils/patterns';
import { useLoader } from '../../utils/useLoader';

const { Text } = Typography;

/** Table hiện cây theo field children; menu lá thì bỏ children để không có nút mở rộng. */
type Row = Omit<MenuNode, 'children'> & { children?: Row[] };

function toRows(nodes: MenuNode[]): Row[] {
  return nodes.map(({ children, ...node }) => (children.length ? { ...node, children: toRows(children) } : node));
}

/** Lọc cây: giữ menu khớp điều kiện và các menu cha của nó. */
function filterTree(nodes: MenuNode[], match: (n: MenuNode) => boolean): MenuNode[] {
  return nodes.flatMap((n) => {
    const children = filterTree(n.children, match);
    return match(n) || children.length > 0 ? [{ ...n, children }] : [];
  });
}

/** null: đóng; parent: thêm menu (con) mới dưới parent; menu: sửa */
type Editing = { kind: 'new'; parent: MenuNode | null } | { kind: 'edit'; menu: MenuNode };

export default function MenusPage() {
  const { hasPermission, reload: reloadMine } = useAuth();
  const { message } = App.useApp();
  const { data, loading, reload } = useLoader(menuApi.tree);
  const menuTree = useMemo(() => data ?? [], [data]);
  const flat = useMemo(() => flattenMenus(menuTree), [menuTree]);

  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<Status | 'ALL'>('ALL');
  const [levelFilter, setLevelFilter] = useState<number | 'ALL'>('ALL');

  const [form] = Form.useForm<MenuInput>();
  const [editing, setEditing] = useState<Editing | null>(null);
  const [saving, setSaving] = useState(false);

  const [roleModalOpen, setRoleModalOpen] = useState(false);

  const stats = useMemo(
    () => ({
      total: flat.length,
      root: menuTree.length,
      active: flat.filter((m) => m.status === 'ACTIVE').length,
      inactive: flat.filter((m) => m.status === 'INACTIVE').length,
    }),
    [flat, menuTree],
  );

  const filteredTree = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    if (!kw && statusFilter === 'ALL' && levelFilter === 'ALL') {
      return menuTree;
    }
    return filterTree(
      menuTree,
      (m) =>
        (!kw || [m.name, m.code, m.url ?? ''].some((v) => v.toLowerCase().includes(kw))) &&
        (statusFilter === 'ALL' || m.status === statusFilter) &&
        (levelFilter === 'ALL' || m.level === levelFilter),
    );
  }, [menuTree, keyword, statusFilter, levelFilter]);

  const afterChange = () => {
    reload();
    // Sidebar của chính mình cũng đổi theo
    reloadMine().catch(() => undefined);
  };

  const save = async () => {
    const values = await form.validateFields();
    const input: MenuInput = { ...values, parentId: values.parentId ?? null };
    setSaving(true);
    try {
      if (editing?.kind === 'edit') {
        await menuApi.update(editing.menu.id, { ...input, version: editing.menu.version });
        message.success('Cập nhật menu thành công');
      } else {
        await menuApi.create(input);
        message.success('Tạo menu mới thành công');
      }
      setEditing(null);
      afterChange();
    } catch (e) {
      showError(message, e, form);
    } finally {
      setSaving(false);
    }
  };

  const remove = async (menu: MenuNode) => {
    try {
      await menuApi.remove(menu.id);
      message.success('Xóa menu thành công');
      afterChange();
    } catch (e) {
      showError(message, e);
    }
  };

  const toggleStatus = async (menu: MenuNode) => {
    const status: Status = menu.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    try {
      await menuApi.update(menu.id, {
        name: menu.name,
        url: menu.url,
        icon: menu.icon,
        parentId: menu.parentId,
        orderIndex: menu.orderIndex,
        status,
        version: menu.version,
      });
      message.success(`Đã đổi trạng thái thành ${status === 'ACTIVE' ? 'Hoạt động' : 'Tạm khóa'}`);
      afterChange();
    } catch (e) {
      showError(message, e);
    }
  };

  /** Đổi chỗ với menu liền kề cùng cha, rồi đánh lại thứ tự 1..n cho cả nhóm (tránh trùng orderIndex). */
  const move = async (menu: MenuNode, direction: -1 | 1) => {
    const siblings = menu.parentId ? (findMenu(menuTree, menu.parentId)?.children ?? []) : menuTree;
    const index = siblings.findIndex((s) => s.id === menu.id);
    const target = index + direction;
    if (target < 0 || target >= siblings.length) {
      message.info(direction < 0 ? 'Menu đã ở vị trí đầu tiên' : 'Menu đã ở vị trí cuối cùng');
      return;
    }
    const ordered = [...siblings];
    [ordered[index], ordered[target]] = [ordered[target], ordered[index]];
    try {
      await menuApi.reorder(ordered.map((s, i) => ({ id: s.id, orderIndex: i + 1, parentId: s.parentId })));
      message.success('Cập nhật thứ tự menu thành công');
      afterChange();
    } catch (e) {
      showError(message, e);
    }
  };

  const openCreate = (parent: MenuNode | null) => setEditing({ kind: 'new', parent });

  const initialValues: Partial<MenuInput> =
    editing?.kind === 'edit'
      ? editing.menu
      : {
          parentId: editing?.parent?.id ?? null,
          icon: 'MenuOutlined',
          status: 'ACTIVE',
          // Mặc định xếp cuối trong các menu cùng cha
          orderIndex: Math.max(0, ...(editing?.parent?.children ?? menuTree).map((s) => s.orderIndex)) + 1,
        };

  const columns: ColumnsType<Row> = [
    {
      title: 'Tên Menu',
      dataIndex: 'name',
      width: 320,
      render: (name: string, record) => (
        <Space>
          <span className="menu-icon">{menuIcon(record.icon)}</span>
          <Text strong>{name}</Text>
          {record.parentId ? <Tag color="cyan">Con</Tag> : <Tag color="blue">Gốc</Tag>}
          {record.system && (
            <Tooltip title="Menu hệ thống: code đang dùng để kiểm tra quyền, không xóa được">
              <LockOutlined className="muted" />
            </Tooltip>
          )}
        </Space>
      ),
    },
    {
      title: 'Mã (Code)',
      dataIndex: 'code',
      width: 130,
      render: (code: string) => (
        <Tag color="purple" className="mono">
          {code}
        </Tag>
      ),
    },
    {
      title: 'Đường dẫn (URL)',
      dataIndex: 'url',
      width: 170,
      render: (url: string | null) =>
        url ? (
          <Space>
            <LinkOutlined className="muted" />
            <Text code>{url}</Text>
          </Space>
        ) : (
          <Text type="secondary">—</Text>
        ),
    },
    {
      title: 'Cấp',
      dataIndex: 'level',
      width: 70,
      align: 'center',
      render: (level: number) => (
        <Badge count={`Lvl ${level}`} style={{ backgroundColor: level === 1 ? '#52c41a' : '#faad14' }} />
      ),
    },
    { title: 'Thứ tự', dataIndex: 'orderIndex', width: 70, align: 'center', render: (o: number) => <Tag>{o}</Tag> },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      width: 120,
      align: 'center',
      render: (status: Status, record) => {
        const tag = (
          <Tag
            color={status === 'ACTIVE' ? 'success' : 'error'}
            icon={status === 'ACTIVE' ? <CheckCircleOutlined /> : <StopOutlined />}
            className={hasPermission('menu:EDIT') ? 'clickable' : undefined}
            onClick={hasPermission('menu:EDIT') ? () => toggleStatus(menuNode(record)) : undefined}
          >
            {status === 'ACTIVE' ? 'Hoạt động' : 'Tạm khóa'}
          </Tag>
        );
        return hasPermission('menu:EDIT') ? <Tooltip title="Bấm để chuyển trạng thái">{tag}</Tooltip> : tag;
      },
    },
    {
      title: 'Thao tác',
      key: 'action',
      width: 160,
      align: 'center',
      render: (_, record) => {
        const menu = menuNode(record);
        return (
          <Space size="small">
            {hasPermission('menu:ADD') && (
              <Tooltip title="Thêm menu con">
                <Button type="text" size="small" icon={<PlusOutlined />} onClick={() => openCreate(menu)} />
              </Tooltip>
            )}
            {hasPermission('menu:EDIT') && (
              <>
                <Tooltip title="Sắp xếp lên">
                  <Button type="text" size="small" icon={<ArrowUpOutlined />} onClick={() => move(menu, -1)} />
                </Tooltip>
                <Tooltip title="Sắp xếp xuống">
                  <Button type="text" size="small" icon={<ArrowDownOutlined />} onClick={() => move(menu, 1)} />
                </Tooltip>
                <Tooltip title="Chỉnh sửa">
                  <Button
                    type="text"
                    size="small"
                    icon={<EditOutlined />}
                    onClick={() => setEditing({ kind: 'edit', menu })}
                  />
                </Tooltip>
              </>
            )}
            {hasPermission('menu:DELETE') && !record.system && (
              <Popconfirm
                title="Xóa menu này?"
                description="Các menu con cũng bị xóa, và bị gỡ khỏi mọi vai trò cùng các quyền cấp trên chúng."
                onConfirm={() => remove(menu)}
                okText="Xóa"
                cancelText="Hủy"
                okButtonProps={{ danger: true }}
              >
                <Tooltip title="Xóa">
                  <Button type="text" size="small" danger icon={<DeleteOutlined />} />
                </Tooltip>
              </Popconfirm>
            )}
          </Space>
        );
      },
    },
  ];

  /** Row của Table không giữ children rỗng: lấy lại node đầy đủ từ cây. */
  const menuNode = (row: Row): MenuNode => findMenu(menuTree, row.id) ?? { ...row, children: [] };

  return (
    <div>
      <Row gutter={[16, 16]} className="page-stats">
        <Col xs={12} sm={6}>
          <StatCard title="TỔNG SỐ MENU" value={stats.total} icon={<MenuOutlined />} color="#1677ff" />
        </Col>
        <Col xs={12} sm={6}>
          <StatCard title="MENU CẤP GỐC" value={stats.root} icon={<MenuOutlined />} color="#722ed1" />
        </Col>
        <Col xs={12} sm={6}>
          <StatCard title="ĐANG HOẠT ĐỘNG" value={stats.active} icon={<CheckCircleOutlined />} color="#52c41a" />
        </Col>
        <Col xs={12} sm={6}>
          <StatCard title="TẠM KHÓA" value={stats.inactive} icon={<StopOutlined />} color="#ff4d4f" />
        </Col>
      </Row>

      <Card>
        <Row justify="space-between" align="middle" gutter={[16, 16]} className="page-toolbar">
          <Col xs={24} lg={13}>
            <Space wrap>
              <Input
                placeholder="Tìm tên, mã, URL..."
                prefix={<SearchOutlined className="muted" />}
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                allowClear
                style={{ width: 240 }}
              />
              <Select
                value={statusFilter}
                onChange={setStatusFilter}
                style={{ width: 160 }}
                options={[
                  { value: 'ALL', label: 'Tất cả trạng thái' },
                  { value: 'ACTIVE', label: 'Hoạt động' },
                  { value: 'INACTIVE', label: 'Tạm khóa' },
                ]}
              />
              <Select
                value={levelFilter}
                onChange={setLevelFilter}
                style={{ width: 140 }}
                options={[
                  { value: 'ALL', label: 'Tất cả cấp' },
                  { value: 1, label: 'Cấp 1 (Gốc)' },
                  { value: 2, label: 'Cấp 2' },
                  { value: 3, label: 'Cấp 3' },
                ]}
              />
            </Space>
          </Col>
          <Col xs={24} lg={11} className="align-right">
            <Space wrap>
              {hasPermission('role:VIEW') && hasPermission('role:EDIT') && (
                <Button type="primary" ghost icon={<SafetyCertificateOutlined />} onClick={() => setRoleModalOpen(true)}>
                  Phân menu cho vai trò
                </Button>
              )}
              {hasPermission('menu:ADD') && (
                <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate(null)}>
                  Thêm menu mới
                </Button>
              )}
              <Button icon={<ReloadOutlined />} onClick={reload} loading={loading}>
                Tải lại
              </Button>
            </Space>
          </Col>
        </Row>

        <Table<Row>
          columns={columns}
          dataSource={toRows(filteredTree)}
          rowKey="id"
          loading={loading}
          pagination={false}
          bordered
          scroll={{ x: 'max-content' }}
          expandable={{ defaultExpandAllRows: true }}
          key={data ? 'loaded' : 'loading'}
        />
      </Card>

      <Modal
        title={
          <Space>
            {editing?.kind === 'edit' ? <EditOutlined /> : <PlusOutlined />}
            <span>{editing?.kind === 'edit' ? 'Chỉnh sửa menu' : 'Thêm menu mới'}</span>
          </Space>
        }
        open={editing !== null}
        onCancel={() => setEditing(null)}
        onOk={save}
        confirmLoading={saving}
        destroyOnHidden
        width={560}
      >
        <Form form={form} layout="vertical" preserve={false} initialValues={initialValues} className="modal-form">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="name" label="Tên menu" rules={[{ required: true, message: 'Vui lòng nhập tên menu' }]}>
                <Input placeholder="VD: Quản lý đơn hàng" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="code"
                label="Mã menu (Code)"
                extra="Dùng trong mã quyền (vd. order:ADD), không đổi được sau khi tạo"
                rules={[
                  { required: true, message: 'Vui lòng nhập mã menu' },
                  { pattern: MENU_CODE, message: MENU_CODE_MESSAGE },
                ]}
              >
                <Input placeholder="VD: order" disabled={editing?.kind === 'edit'} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="parentId" label="Menu cha (để trống nếu là menu gốc)">
            <TreeSelect
              showSearch
              allowClear
              treeDefaultExpandAll
              treeNodeFilterProp="title"
              placeholder="Chọn menu cha (không bắt buộc)"
              treeData={toTreeOptions(menuTree, editing?.kind === 'edit' ? editing.menu.id : undefined)}
            />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="url" label="Đường dẫn URL (route)">
                <Input placeholder="VD: /orders" prefix={<LinkOutlined className="muted" />} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="icon" label="Biểu tượng (icon)">
                <Select
                  showSearch
                  optionFilterProp="search"
                  options={ICON_OPTIONS.map((o) => ({
                    value: o.value,
                    search: `${o.label} ${o.value}`,
                    label: (
                      <Space>
                        {o.icon}
                        {o.label}
                      </Space>
                    ),
                  }))}
                />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="orderIndex" label="Thứ tự">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="status" label="Trạng thái" extra="Tạm khóa: ẩn khỏi sidebar, quyền đã cấp vẫn còn">
                <Select
                  options={[
                    { value: 'ACTIVE', label: 'Hoạt động' },
                    { value: 'INACTIVE', label: 'Tạm khóa' },
                  ]}
                />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>

      {roleModalOpen && <RoleMenuModal menuTree={menuTree} onClose={() => setRoleModalOpen(false)} />}
    </div>
  );
}

/** Chọn vai trò rồi tích các menu được hiển thị cho vai trò đó. */
function RoleMenuModal({ menuTree, onClose }: { menuTree: MenuNode[]; onClose: () => void }) {
  const { message } = App.useApp();
  const { reload: reloadMine } = useAuth();
  const { data: roles } = useLoader(roleApi.list);
  const [role, setRole] = useState<RoleDetail | null>(null);
  const [checked, setChecked] = useState<string[]>([]);
  const [loadingRole, setLoadingRole] = useState(false);
  const [saving, setSaving] = useState(false);
  const allIds = useMemo(() => flattenMenus(menuTree).map((m) => m.id), [menuTree]);

  const selectRole = async (id: string) => {
    setLoadingRole(true);
    try {
      const detail = await roleApi.get(id);
      setRole(detail);
      setChecked(detail.menuIds);
    } catch (e) {
      showError(message, e);
    } finally {
      setLoadingRole(false);
    }
  };

  const save = async () => {
    if (!role) {
      message.warning('Vui lòng chọn vai trò');
      return;
    }
    setSaving(true);
    try {
      await roleApi.updateMenus(role.id, checked, role.version);
      message.success(`Đã cập nhật menu cho vai trò ${role.code}`);
      reloadMine().catch(() => undefined);
      onClose();
    } catch (e) {
      showError(message, e);
    } finally {
      setSaving(false);
    }
  };

  const toTreeData = (nodes: MenuNode[]): DataNode[] =>
    nodes.map((n) => ({
      key: n.id,
      title: (
        <span>
          {menuIcon(n.icon)} <strong>{n.name}</strong> <Text type="secondary">({n.code})</Text>
        </span>
      ),
      children: toTreeData(n.children),
    }));

  const checkedCount = allIds.filter((id) => checked.includes(id)).length;

  return (
    <Modal
      title={
        <Space>
          <SafetyCertificateOutlined />
          <span>Phân menu hiển thị cho vai trò</span>
        </Space>
      }
      open
      onCancel={onClose}
      onOk={save}
      confirmLoading={saving}
      okText="Lưu phân quyền"
      cancelText="Hủy"
      width={600}
    >
      <Form.Item label="Vai trò" required>
        <Select
          placeholder="-- Chọn vai trò --"
          value={role?.id}
          onChange={selectRole}
          options={roles?.map((r) => ({
            value: r.id,
            label: (
              <Space>
                <Tag color={r.code === 'ADMIN' ? 'red' : 'blue'}>{r.code}</Tag>
                {r.name}
              </Space>
            ),
          }))}
        />
      </Form.Item>
      <Divider style={{ margin: '12px 0' }} />
      {loadingRole ? (
        <div className="center-box">
          <Spin description="Đang tải menu của vai trò..." />
        </div>
      ) : role ? (
        role.code === 'ADMIN' ? (
          <Text type="secondary">ADMIN luôn thấy mọi menu đang hoạt động.</Text>
        ) : (
          <>
            <div className="tree-toolbar">
              <Text strong>Tích chọn các menu được hiển thị cho vai trò này:</Text>
              <Checkbox
                checked={allIds.length > 0 && checkedCount === allIds.length}
                indeterminate={checkedCount > 0 && checkedCount < allIds.length}
                onChange={(e) => setChecked(e.target.checked ? allIds : [])}
              >
                Chọn tất cả ({checkedCount}/{allIds.length})
              </Checkbox>
            </div>
            <div className="tree-box">
              <Tree
                checkable
                defaultExpandAll
                selectable={false}
                checkedKeys={checked}
                onCheck={(keys) => setChecked((Array.isArray(keys) ? keys : keys.checked).map(String))}
                treeData={toTreeData(menuTree)}
              />
            </div>
          </>
        )
      ) : (
        <div className="center-box muted">Vui lòng chọn một vai trò ở trên để hiển thị cây menu</div>
      )}
    </Modal>
  );
}
