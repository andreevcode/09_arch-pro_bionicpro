import React, { useState } from 'react';

// Интерфейс для данных отчета из OLAP
interface ReportData {
  reportDate: string;
  totalActions: number;
  avgResponseMs: number;
  maxNoiseLevel: number;
  batteryDrain: number;
  errors: number;
}

interface ReportPageProps {
  authenticated: boolean;
  user: { username: string } | null; // Добавляем пропс пользователя
}

const ReportPage: React.FC<ReportPageProps> = ({ authenticated, user }) => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reports, setReports] = useState<ReportData[] | null>(null); // Состояние для данных таблицы

  // Состояния для дат с предзаполненными значениями
  const [startDate, setStartDate] = useState('2026-02-20');
  const [endDate, setEndDate] = useState('2026-02-22');

  // Вход теперь — это просто редирект на автоматически создаваемый эндпоинт Spring Security
  const handleLogin = () => {
    window.location.href = `${process.env.REACT_APP_BFF_URL}/oauth2/authorization/keycloak`;
  };

  const downloadReport = async () => {
    if (!user) return; // Не отправляем запрос, если данных пользователя еще нет

    try {
      setLoading(true);
      setError(null);
      setReports(null); // Сбрасываем старые данные перед новым запросом

      // Формируем параметры запроса для новой ручки v1 используя состояния дат
      const params = new URLSearchParams({
        user_id: user.username,
        start_date: startDate,
        end_date: endDate
      });

      // Делаем запрос в новую ручку v1 через BFF
      const response = await fetch(`${process.env.REACT_APP_BFF_URL}/api/v1/reports?${params.toString()}`, {
        // Кука JSESSIONID прикрепится автоматически благодаря этому флагу
        credentials: 'include'
      });

      if (response.status === 401) {
        setError("Сессия истекла. Пожалуйста, войдите снова.");
        return;
      }

      // Проверка на права доступа (403 Forbidden)
      if (response.status === 403) {
        setError("У вас нет прав для просмотра этого отчета.");
        return;
      }

      console.log("Статус запроса к BFF:", response.status);

      const data = await response.json();
      setReports(data); // Сохраняем данные для отображения в таблице

    } catch (err) {
      setError(err instanceof Error ? err.message : 'Ошибка при загрузке');
    } finally {
      setLoading(false);
    }
  };

  if (!authenticated) {
    return (
        <div className="flex flex-col items-center justify-center min-h-screen bg-gray-100">
          <button
              onClick={handleLogin}
              className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
          >
            Login
          </button>
        </div>
    );
  }

  return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-gray-100 p-4">
        <div className="p-8 bg-white rounded-lg shadow-md w-full max-w-4xl">
          <h1 className="text-2xl font-bold mb-6">Usage Reports</h1>

          {/* Добавляем приветствие для наглядности */}
          {user && <p className="mb-4 text-gray-600">User: {user.username}</p>}

          {/* Поля выбора дат */}
          <div className="flex gap-4 mb-6">
            <div>
              <label className="block text-sm font-medium text-gray-700">Start Date</label>
              <input
                  type="date"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                  className="mt-1 block w-full border border-gray-300 rounded-md shadow-sm p-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700">End Date</label>
              <input
                  type="date"
                  value={endDate}
                  onChange={(e) => setEndDate(e.target.value)}
                  className="mt-1 block w-full border border-gray-300 rounded-md shadow-sm p-2 focus:ring-blue-500 focus:border-blue-500"
              />
            </div>
          </div>

          <button
              onClick={downloadReport}
              disabled={loading}
              className={`px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 ${
                  loading ? 'opacity-50 cursor-not-allowed' : ''
              }`}
          >
            {loading ? 'Generating Report...' : 'Get Report'}
          </button>

          {error && (
              <div className="mt-4 p-4 bg-red-100 text-red-700 rounded">
                {error}
              </div>
          )}

          {/* Таблица с результатами */}
          {reports && reports.length > 0 && (
              <div className="mt-8 overflow-x-auto">
                <table className="min-w-full table-auto border-collapse border border-gray-200">
                  <thead>
                  <tr className="bg-gray-100">
                    <th className="border p-2 text-left">Date</th>
                    <th className="border p-2 text-center">Actions</th>
                    <th className="border p-2 text-center">Avg Resp (ms)</th>
                    <th className="border p-2 text-center">Max Noise</th>
                    <th className="border p-2 text-center">Battery Drain</th>
                    <th className="border p-2 text-center">Errors</th>
                  </tr>
                  </thead>
                  <tbody>
                  {reports.map((report, index) => (
                      <tr key={index} className="hover:bg-gray-50">
                        <td className="border p-2">{report.reportDate}</td>
                        <td className="border p-2 text-center">{report.totalActions}</td>
                        <td className="border p-2 text-center">{report.avgResponseMs.toFixed(2)}</td>
                        <td className="border p-2 text-center">{report.maxNoiseLevel.toFixed(2)}</td>
                        <td className="border p-2 text-center">{report.batteryDrain}</td>
                        <td className="border p-2 text-center text-red-600 font-semibold">{report.errors}</td>
                      </tr>
                  ))}
                  </tbody>
                </table>
              </div>
          )}

          {reports && reports.length === 0 && (
              <div className="mt-6 text-gray-500 italic">
                No data found for the selected period.
              </div>
          )}
        </div>
      </div>
  );
};

export default ReportPage;