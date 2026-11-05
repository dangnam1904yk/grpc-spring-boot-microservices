import { LockOutlined, MailOutlined, PhoneOutlined, UserOutlined } from '@ant-design/icons';
import { App, Button, Card, Form, Input, Typography } from 'antd';
import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { showError } from '../utils/errors';
import { PASSWORD, PASSWORD_MESSAGE, PHONE, PHONE_MESSAGE } from '../utils/patterns';

interface RegisterForm {
  email: string;
  fullName?: string;
  phoneNumber?: string;
  password: string;
  confirm: string;
}

/**
 * Form đăng ký của app: auth-service tạo tài khoản trên Keycloak, lưu user vào bảng của mình rồi trả token
 * (đăng nhập luôn).
 */
export default function RegisterPage() {
  const { user, register } = useAuth();
  const { message } = App.useApp();
  const navigate = useNavigate();
  const location = useLocation();
  const [form] = Form.useForm<RegisterForm>();
  const [submitting, setSubmitting] = useState(false);
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  if (user) {
    return <Navigate to={from} replace />;
  }

  const onFinish = async ({ confirm: _confirm, ...values }: RegisterForm) => {
    setSubmitting(true);
    try {
      await register({
        email: values.email.trim(),
        password: values.password,
        fullName: values.fullName?.trim() || null,
        phoneNumber: values.phoneNumber?.trim() || null,
      });
      message.success('Đăng ký thành công');
      navigate(from, { replace: true });
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
              { max: 255, message: 'Tối đa 255 ký tự' },
            ]}
          >
            <Input prefix={<MailOutlined />} autoComplete="email" autoFocus />
          </Form.Item>
          <Form.Item name="fullName" label="Họ tên" rules={[{ max: 255, message: 'Tối đa 255 ký tự' }]}>
            <Input prefix={<UserOutlined />} autoComplete="name" />
          </Form.Item>
          <Form.Item name="phoneNumber" label="Số điện thoại" rules={[{ pattern: PHONE, message: PHONE_MESSAGE }]}>
            <Input prefix={<PhoneOutlined />} autoComplete="tel" />
          </Form.Item>
          <Form.Item
            name="password"
            label="Mật khẩu"
            rules={[
              { required: true, message: 'Nhập mật khẩu' },
              { min: 8, max: 72, message: 'Từ 8 đến 72 ký tự' },
              { pattern: PASSWORD, message: PASSWORD_MESSAGE },
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
          Đã có tài khoản? <Link to="/login" state={location.state}>Đăng nhập</Link>
        </Typography.Paragraph>
      </Card>
    </div>
  );
}
