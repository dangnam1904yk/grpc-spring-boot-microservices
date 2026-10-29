import type { FormInstance } from 'antd';
import type { MessageInstance } from 'antd/es/message/interface';
import { ApiError } from '../api/client';

/** Hiện lỗi API: lỗi validation gắn vào field của form (nếu có form), còn lại hiện message. */
export function showError(message: MessageInstance, error: unknown, form?: FormInstance) {
  if (error instanceof ApiError) {
    if (form && error.errors) {
      form.setFields(Object.entries(error.errors).map(([name, msg]) => ({ name, errors: [msg] })));
    }
    if (error.code === 'VERSION_CONFLICT' || (error.status === 409 && !error.code)) {
      message.error('Dữ liệu đã bị người khác thay đổi, hãy tải lại rồi thử lại');
      return;
    }
    message.error(error.message);
    return;
  }
  message.error('Có lỗi xảy ra');
}
