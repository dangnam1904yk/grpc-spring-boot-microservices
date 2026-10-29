import { App } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { showError } from './errors';

/**
 * Tải dữ liệu khi vào trang và khi deps đổi; reload() để tải lại sau khi thêm / sửa / xoá.
 * enabled=false (vd. thiếu quyền xem) thì không gọi API.
 */
export function useLoader<T>(load: () => Promise<T>, deps: unknown[] = [], enabled = true) {
  const { message } = App.useApp();
  const [data, setData] = useState<T | undefined>(undefined);
  const [loading, setLoading] = useState(false);

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(load, deps);

  const reload = useCallback(async () => {
    if (!enabled) {
      return;
    }
    setLoading(true);
    try {
      setData(await run());
    } catch (e) {
      showError(message, e);
    } finally {
      setLoading(false);
    }
  }, [run, enabled, message]);

  useEffect(() => {
    reload();
  }, [reload]);

  return { data, loading, reload };
}
