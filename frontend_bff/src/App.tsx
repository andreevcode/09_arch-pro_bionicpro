import React, { useState, useEffect } from 'react';
import ReportPage from './components/ReportPage';

const App: React.FC = () => {
    const [initialized, setInitialized] = useState(false);
    const [authenticated, setAuthenticated] = useState(false);

    useEffect(() => {
        // Проверяем, залогинен ли пользователь через наш BFF
        fetch(`${process.env.REACT_APP_BFF_URL}/api/user/me`, {
            credentials: 'include' // Обязательно для передачи сессионной куки
        })
            .then(res => {
                if (res.ok) {
                    setAuthenticated(true);
                } else {
                    setAuthenticated(false);
                }
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
            <ReportPage authenticated={authenticated} />
        </div>
    );
};

export default App;