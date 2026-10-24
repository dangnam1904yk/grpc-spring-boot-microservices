import { useState } from 'react'
import './App.css'
import 'antd/dist/reset.css';

import { DatePicker, Space } from 'antd';


function App() {
  const [count, setCount] = useState(0)
  const [selectedDate, setSelectedDate] = useState<string | null>(null);

  const handleDateChange = (date: string | null) => {
    console.log("Ngày được chọn:", date);
    setSelectedDate(date);

    // Thêm logic tùy ý ở đây, ví dụ:
    if (date) {
      // Gọi API, kiểm tra logic, set form state, v.v.
    }
  };
  return (
    <>
      <h1>Vite + React</h1>
      <div className="card">
        <button onClick={() => setCount((count) => count + 1)}>
          count is {count}
        </button>
        <p>
          Edit <code>src/App.jsx</code> and save to test HMR
        </p>
      </div>
      <p className="read-the-docs">
        Click on the Vite and React logos to learn more
      </p>
    </>
  )
}

export default App
