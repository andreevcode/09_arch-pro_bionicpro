import React, { useState } from 'react';

interface ReportPageProps {
  authenticated: boolean;
}

const ReportPage: React.FC<ReportPageProps> = ({ authenticated }) => {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Вход теперь — это просто редирект на автоматически создаваемый эндпоинт Spring Security
  const handleLogin = () => {
    window.location.href = `${process.env.REACT_APP_BFF_URL}/oauth2/authorization/keycloak`;
  };

  const downloadReport = async () => {
    try {
      setLoading(true);
      setError(null);

      const response = await fetch(`${process.env.REACT_APP_BFF_URL}/api/reports`, {
        // Кука JSESSIONID прикрепится автоматически благодаря этому флагу
        credentials: 'include'
      });

      if (response.status === 401) {
        setError("Сессия истекла. Пожалуйста, войдите снова.");
        return;
      }

      console.log("Статус запроса к BFF:", response.status);
      // Здесь будет логика скачивания файла из Blob

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
      <div className="flex flex-col items-center justify-center min-h-screen bg-gray-100">
        <div className="p-8 bg-white rounded-lg shadow-md">
          <h1 className="text-2xl font-bold mb-6">Usage Reports</h1>

          <button
              onClick={downloadReport}
              disabled={loading}
              className={`px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 ${
                  loading ? 'opacity-50 cursor-not-allowed' : ''
              }`}
          >
            {loading ? 'Generating Report...' : 'Download Report'}
          </button>

          {error && (
              <div className="mt-4 p-4 bg-red-100 text-red-700 rounded">
                {error}
              </div>
          )}
        </div>
      </div>
  );
};

export default ReportPage;