import { Card, Descriptions, Empty, Space, Tag, Typography } from 'antd';
import { ROLE_ADMIN, useAuth } from '../auth/AuthContext';

export default function HomePage() {
  const { user, authorities } = useAuth();
  const isAdmin = authorities?.roles.includes(ROLE_ADMIN);

  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      <Typography.Title level={3}>Xin chào, {user?.email}</Typography.Title>
      <Card title="Quyền của bạn">
        <Descriptions column={1}>
          <Descriptions.Item label="Role">
            <Space wrap>
              {authorities?.roles.map((r) => (
                <Tag key={r} color={r === ROLE_ADMIN ? 'red' : 'blue'}>
                  {r}
                </Tag>
              ))}
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="Quyền">
            {isAdmin ? (
              <Tag color="red">Toàn quyền (ADMIN)</Tag>
            ) : authorities?.permissions.length ? (
              <Space wrap>
                {authorities.permissions.map((p) => (
                  <Tag key={p}>{p}</Tag>
                ))}
              </Space>
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa được cấp quyền nào" />
            )}
          </Descriptions.Item>
        </Descriptions>
      </Card>
    </Space>
  );
}
