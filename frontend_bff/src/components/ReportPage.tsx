import React, { useState } from 'react';

// Интерфейс для данных отчета из OLAP
interface ReportData {
  reportDate: string;
  totalActions: number;
  avgResponseMs: number;
  maxNoiseLevel: number;
  batteryDrain: number;
  errors: number;
  updatedAt: string;
}

interface ReportPageProps {
  authenticated: boolean;
  user: { username: string } | null;
}

const ReportPage: React.FC<ReportPageProps> = ({ authenticated, user }) => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reports, setReports] = useState<ReportData[] | null>(null);

  const [startDate, setStartDate] = useState('2026-02-20');
  const [endDate, setEndDate] = useState('2026-02-22');

  const handleLogin = () => {
    window.location.href = `${process.env.REACT_APP_BFF_URL}/oauth2/authorization/keycloak`;
  };

  // Старая ручка (v1) - прямое получение данных
  const downloadReport = async () => {
    if (!user) return;

    try {
      setLoading(true);
      setError(null);
      setReports(null);

      const params = new URLSearchParams({
        user_id: user.username,
        start_date: startDate,
        end_date: endDate
      });

      const response = await fetch(`${process.env.REACT_APP_BFF_URL}/api/v1/reports?${params.toString()}`, {
        credentials: 'include'
      });

      if (response.status === 401) {
        setError("Сессия истекла. Пожалуйста, войдите снова.");
        return;
      }
      if (response.status === 403) {
        setError("У вас нет прав для просмотра этого отчета.");
        return;
      }

      const data = await response.json();
      setReports(data);

    } catch (err) {
      setError(err instanceof Error ? err.message : 'Ошибка при загрузке');
    } finally {
      setLoading(false);
    }
  };

  // Новая ручка (v2) - получение через CDN (Nginx). Добавлен параметр isStream
  const downloadReportCDN = async (isStream: boolean = false) => {
    if (!user) return;

    try {
      setLoading(true);
      setError(null);
      setReports(null);

      const params = new URLSearchParams({
        user_id: user.username,
        start_date: startDate,
        end_date: endDate
      });

      // Добавляем параметр stream, если запрошена потоковая витрина
      if (isStream) {
        params.append('stream', 'true');
      }

      // Шаг 1: Идем в v2 за ссылкой на файл
      const response = await fetch(`${process.env.REACT_APP_BFF_URL}/api/v2/reports?${params.toString()}`, {
        credentials: 'include'
      });

      if (response.status === 401) {
        setError("Сессия истекла. Пожалуйста, войдите снова.");
        return;
      }
      if (response.status === 403) {
        setError("У вас нет прав для просмотра этого отчета.");
        return;
      }
      if (!response.ok) {
        setError(`Ошибка сервера: ${response.status}`);
        return;
      }

      const data = await response.json();

      if (!data.url) {
        setError("Сервер не вернул ссылку на CDN.");
        return;
      }

      // Шаг 2: Скачиваем сам файл с кэширующего Nginx
      const cdnResponse = await fetch(data.url);

      if (!cdnResponse.ok) {
        setError(`Не удалось скачать файл с CDN: ${cdnResponse.status}`);
        return;
      }

      const reportData = await cdnResponse.json();

      // Кладем данные в тот же стейт, таблица отрисуется автоматически
      setReports(reportData);

    } catch (err) {
      setError(err instanceof Error ? err.message : 'Ошибка при загрузке через CDN');
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

          {user && <p className="mb-4 text-gray-600">User: {user.username}</p>}

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

          {/* Обновленный блок с тремя кнопками */}
          <div className="flex flex-wrap gap-4 mb-4">
            <button
                onClick={downloadReport}
                disabled={loading}
                className={`px-4 py-2 bg-gray-500 text-white rounded hover:bg-gray-600 ${
                    loading ? 'opacity-50 cursor-not-allowed' : ''
                }`}
            >
              {loading ? 'Processing...' : 'Get Report v1 (Direct OLTP batch)'}
            </button>

            <button
                onClick={() => downloadReportCDN(false)}
                disabled={loading}
                className={`px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 ${
                    loading ? 'opacity-50 cursor-not-allowed' : ''
                }`}
            >
              {loading ? 'Processing...' : 'Get Report v2 (CDN OLTP batch)'}
            </button>

            <button
                onClick={() => downloadReportCDN(true)}
                disabled={loading}
                className={`px-4 py-2 bg-green-500 text-white rounded hover:bg-green-600 ${
                    loading ? 'opacity-50 cursor-not-allowed' : ''
                }`}
            >
              {loading ? 'Processing...' : 'Get Report v2 (CDN OLTP stream)'}
            </button>
          </div>

          {error && (
              <div className="p-4 mb-4 bg-red-100 text-red-700 rounded">
                {error}
              </div>
          )}

          {reports && reports.length > 0 && (
              <div className="overflow-x-auto">
                <table className="min-w-full table-auto border-collapse border border-gray-200">
                  <thead>
                  <tr className="bg-gray-100">
                    <th className="border p-2 text-left">Date</th>
                    <th className="border p-2 text-center">Actions</th>
                    <th className="border p-2 text-center">Avg Resp (ms)</th>
                    <th className="border p-2 text-center">Max Noise</th>
                    <th className="border p-2 text-center">Battery Drain</th>
                    <th className="border p-2 text-center">Errors</th>
                    <th className="border p-2 text-center">Updated At</th>
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
                        <td className="border p-2 text-center text-xs text-gray-500">
                          {report.updatedAt ? new Date(report.updatedAt).toLocaleString() : '-'}
                        </td>
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