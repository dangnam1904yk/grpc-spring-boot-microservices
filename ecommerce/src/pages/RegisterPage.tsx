import { LockOutlined, MailOutlined } from '@ant-design/icons';
import { App, Button, Card, Form, Input, Typography } from 'antd';
import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { authApi } from '../api/auth';
import { useAuth } from '../auth/AuthContext';
import { showError } from '../utils/errors';

interface RegisterForm {
  email: string;
  password: string;
  confirm: string;
}

export default function RegisterPage() {
  const { user, login } = useAuth();
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [form] = Form.useForm<RegisterForm>();
  const [submitting, setSubmitting] = useState(false);

  if (user) {
    return <Navigate to="/" replace />;
  }

  // Đăng ký xong thì đăng nhập luôn
  const onFinish = async (values: RegisterForm) => {
    setSubmitting(true);
    try {
      await authApi.register(values.email, values.password);
      await login(values.email, values.password);
      message.success('Đăng ký thành công');
      navigate('/', { replace: true });
    } catch (e) {
      showError(message, e, form);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="auth-page">
      <Card className="auth-card">
        <Typography.Title level={3}>Đăng ký</Typography.Title>
        <Form form={form} layout="vertical" onFinish={onFinish} requiredMark={false}>
          <Form.Item
            name="email"
            label="Email"
            rules={[
              { required: true, message: 'Nhập email' },
              { type: 'email', message: 'Email không hợp lệ' },
            ]}
          >
            <Input prefix={<MailOutlined />} autoComplete="email" autoFocus />
          </Form.Item>
          {/* Giống validation của auth-service: 8-72 ký tự, có cả chữ và số */}
          <Form.Item
            name="password"
            label="Mật khẩu"
            rules={[
              { required: true, message: 'Nhập mật khẩu' },
              { min: 8, max: 72, message: 'Từ 8 đến 72 ký tự' },
              { pattern: /^(?=.*[A-Za-z])(?=.*\d).+$/, message: 'Phải có cả chữ và số' },
            ]}
            hasFeedback
          >
            <Input.Password prefix={<LockOutlined />} autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            name="confirm"
            label="Nhập lại mật khẩu"
            dependencies={['password']}
            hasFeedback
            rules={[
              { required: true, message: 'Nhập lại mật khẩu' },
              ({ getFieldValue }) => ({
                validator: (_, value) =>
                  !value || getFieldValue('password') === value
                    ? Promise.resolve()
                    : Promise.reject(new Error('Mật khẩu không khớp')),
              }),
            ]}
          >
            <Input.Password prefix={<LockOutlined />} autoComplete="new-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={submitting}>
            Đăng ký
          </Button>
        </Form>
        <Typography.Paragraph className="auth-switch">
          Đã có tài khoản? <Link to="/login">Đăng nhập</Link>
        </Typography.Paragraph>
      </Card>
    </div>
  );
}
