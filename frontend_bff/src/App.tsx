import React, { useState, useEffect } from 'react';
import ReportPage from './components/ReportPage';

const App: React.FC = () => {
    const [initialized, setInitialized] = useState(false);
    const [authenticated, setAuthenticated] = useState(false);
    // Добавляем состояние для данных пользователя
    const [user, setUser] = useState<{ username: string } | null>(null);

    useEffect(() => {
        // Проверяем, залогинен ли пользователь через наш BFF
        fetch(`${process.env.REACT_APP_BFF_URL}/api/user/me`, {
            credentials: 'include' // Обязательно для передачи сессионной куки
        })
            .then(res => {
                if (res.ok) {
                    return res.json(); // Ожидаем JSON с данными пользователя
                }
                throw new Error('Not authenticated');
            })
            .then(data => {
                setUser(data); // Сохраняем username пользователя
                setAuthenticated(true);
                setInitialized(true);
            })
            .catch(() => {
                setAuthenticated(false);
                setInitialized(true);
            });
    }, []);

    if (!initialized) {
        return (
            <div className="flex items-center justify-center min-h-screen">
                <div>Loading Auth...</div>
            </div>
        );
    }

    return (
        <div className="App">
            {/* Передаем данные пользователя в компонент страницы */}
            <ReportPage authenticated={authenticated} user={user} />
        </div>
    );
};

export default App;