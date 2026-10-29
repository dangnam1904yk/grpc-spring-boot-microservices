import { Card, Statistic } from 'antd';
import type { ReactNode } from 'react';

interface Props {
  title: string;
  value: number;
  icon: ReactNode;
  /** Màu viền trái và icon */
  color: string;
}

/** Thẻ thống kê ở đầu các trang quản trị. */
export default function StatCard({ title, value, icon, color }: Props) {
  return (
    <Card variant="borderless" className="stat-card" style={{ borderLeftColor: color }}>
      <Statistic
        title={<span className="stat-card-title">{title}</span>}
        value={value}
        prefix={<span style={{ color, marginRight: 8 }}>{icon}</span>}
        styles={{ content: { fontWeight: 'bold' } }}
      />
    </Card>
  );
}
