// antd 5 chính thức hỗ trợ React 16-18; bản vá này để message / Modal / hiệu ứng chạy đúng trên React 19
import '@ant-design/v5-patch-for-react-19';
import { App as AntApp, ConfigProvider } from 'antd';
import 'antd/dist/reset.css';
import viVN from 'antd/locale/vi_VN';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';
import './index.css';

const rootElement = document.getElementById('root');

if (rootElement) {
  createRoot(rootElement).render(
    <StrictMode>
      <ConfigProvider locale={viVN}>
        {/* AntApp: cung cấp message / modal theo context (App.useApp) */}
        <AntApp>
          <App />
        </AntApp>
      </ConfigProvider>
    </StrictMode>
  );
}
