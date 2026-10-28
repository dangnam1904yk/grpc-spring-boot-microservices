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
