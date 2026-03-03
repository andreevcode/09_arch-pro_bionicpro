import React, { useState, useEffect } from 'react';
// @ts-ignore: Игнорируем баг старого компилятора CRA с современными библиотеками
import Keycloak from 'keycloak-js';
import ReportPage from './components/ReportPage';


// Создаем синглтон напрямую, типы подтянутся автоматически
export const keycloak = new Keycloak({
    url: process.env.REACT_APP_KEYCLOAK_URL,
    realm: process.env.REACT_APP_KEYCLOAK_REALM || "",
    clientId: process.env.REACT_APP_KEYCLOAK_CLIENT_ID || ""
});
const App: React.FC = () => {
    // Создаем свои переменные состояния
    const [initialized, setInitialized] = useState(false);
    const [authenticated, setAuthenticated] = useState(false);

    // useEffect выполнится один раз при загрузке страницы
    useEffect(() => {
        keycloak.init({
            onLoad: 'check-sso',
            checkLoginIframe: false,
            // необязательно, потому что pkce включен по умолчанию
            // pkceMethod: 'S256'
        })
            .then((auth: boolean) => {
                console.log("✅ Keycloak инициализирован! Авторизован:", auth);
                setAuthenticated(auth);
                setInitialized(true);
            })
            .catch((error: boolean) => {
                console.error("❌ Ошибка инициализации Keycloak:", error);
                setInitialized(true); // Все равно ставим true, чтобы показать кнопку логина
            });
    }, []);

    // Пока ждем ответа от Keycloak - рисуем загрузку
    if (!initialized) {
        return <div>Loading Auth...</div>;
    }

    // Передаем статус авторизации в наш компонент
    return (
        <div className="App">
            <ReportPage authenticated={authenticated} />
        </div>
    );
};

export default App;