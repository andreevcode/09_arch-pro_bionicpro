# Задание 1. Повышение безопасности системы

## Задача 1. Доработка архитектуры для повышения безопасности системы
>Предложите архитектурное решение и доработайте диаграмму C4 BionicPRO для управления учётными данными пользователя.
>
>Решение должно учитывать и обеспечивать следующие аспекты:
>
>* Унификацию доступа в системе BionicPRO. Это будет осуществляться через запрос данных учётных записей из внешнего источника, который расположен в стране представительства компании. Принципы локального хранения персональной и медицинской информации не должны быть нарушены.
>* Безопасную схему работы с access- и refresh-токенами, которая исключает передачу фронтенду токенов, которые были получены от IdP.
>* Возможность поддержки аутентификации пользователей через различные внешние удостоверяющие службы, действующие в разных странах.
>
>Для подготовки архитектуры решения используйте [draw.io](http://draw.io).
> 
> ### Как сдать задание?
>
>1. Диаграмма архитектуры системы в [draw.io](http://draw.io).
>2. Код в репозитории, реализующий PKCE flow.
>3. Код нового бэкенд-сервиса, который реализует получение access- и refresh-токенов и генерации пользовательской сессии.
>4. Изменён код фронтенда для работы с сессиями вместо интеграции с Keycloak напрямую.
>5. Экспортирован realm после всех манипуляций с Keycloak в файл keycloak/keycloak-results-export.json.
>6. Добавлен OAuth 2.0 от Яндекс ID.


### 1.1. ADR 01. Общая архитектура аутентификации, безопасное управление сессиями и разделение трафика

**Контекст:**
В текущей архитектуре BionicPRO требуется реализовать безопасную работу с токенами (исключив их передачу на мобильные устройства фронтенду), унифицировать доступ (SSO) для внутренних (сотрудники в CRM и магазине) и внешних пользователей (пациенты), а также обеспечить соблюдение законов о локализации ПДн. Одновременно система должна обрабатывать высокочастотную потоковую телеметрию с IoT-чипов с минимальными задержками (до 100 мс).

**Решение:**
1. Внедрение Keycloak (единый IAM), который выступит центральным Identity Provider. Для изоляции политик безопасности сотрудников и клиентов используются независимые логические области (Realms: Internal и External).
2. Федерация пользователей: Keycloak интегрируется с локальными серверами LDAP по странам, запрашивая профили "на лету" (User Federation), что удовлетворяет требованиям законодательства. Поддерживается аутентификация через OIDC (Yandex/Google) и TOTP (MFA).
3. Паттерн BFF (Backend-For-Frontend): для мобильных приложений разворачивается кластер BFF на базе Spring Security. BFF выступает как Confidential Client: сам обменивает Authorization Code на токены в Keycloak, сохраняет их на бэкенде в кластере Redis и выдает клиенту безопасную httpOnly cookie (JSESSIONID). При проксировании запросов в API BFF подмешивает Access Token.
4. Прямой роутинг телеметрии (Edge Level): на границе сети вводится Ingress / Reverse Proxy. Пользовательский трафик с мобилок маршрутизируется в сессионный BFF. Трафик телеметрии с протезов (чипов) маршрутизируется от Ingress напрямую в Telemetry API, минуя BFF, для обеспечения минимальных задержек (Latency).
5. Single Sign-On (SSO): Интернет-магазин и CRM интегрируются с Keycloak по OIDC/SAML, обеспечивая единый вход для всех пользователей.

**Последствия (обоснование):**
1. Плюсы:
    - Нулевой риск кражи токенов (XSS) на стороне мобильного приложения.
    - Разделение потоков исключает влияние "тяжелой" телеметрии на производительность пользовательских сессий.
    - Полностью stateless архитектура BFF (за счет Redis), готовая к горизонтальному масштабированию.
2. Минусы:
    - Усложнение инфраструктуры: появление Ingress, кластера Redis и выделенного IAM Keycloak.
    - Увеличение сетевых задержек (дополнительный hop) исключительно для пользовательских HTTP-запросов (что некритично по сравнению с IoT-телеметрией).

### 1.2 Схема контейнеров C4 TO_BE
- [TASK1_C4_containers_to_be.drawio.xml](TASK1_C4_containers_to_be.drawio.xml) <img src="TASK1_C4_containers_to_be.drawio.svg" width="1300">

## Задача 2. Улучшение безопасности существующего приложения заменой Code Grant на PKCE
>PKCE нужно добавить к уже существующим приложениям — фронтенду и Keycloak. Мы неслучайно не рассказывали в теории, как это сделать. Чтобы разобраться, изучите [официальную документацию](https://www.Keycloak.org/docs/latest/server_admin/index.html#device-authorization-grant).


### 2.1 ADR 02. Замена Code Grant на PKCE
1. Из-за того, что Keycloak 21.1 не стартовал в docker под apple silicon, необходимо обновить образ Keycloak до более свежей версии, например, 26.5.1.
2. Реализовать `PKCE` между фронтом и Keycloak:
   - это дополнительная защита через механизм `code_verifier`, чтобы Keycloak удостоверился, что за токенами приходит именно тот, кто начинал процесс авторизации.
   - для Keycloak 26.5.1 делать ничего не нужно, т.к. `PKCE` автоматически включено по умолчанию начиная с 24 версии;
   - для старой версии нужно было бы поправить фронтовый код [App.tsx](../../frontend/src/App.tsx) примерно так:
```ts
    // useEffect выполнится один раз при загрузке страницы
    useEffect(() => {
        Keycloak.init({
            onLoad: 'check-sso',
            checkLoginIframe: false,
            // необязательно, потому что pkce включен по умолчанию
            // pkceMethod: 'S256'
        })
```

### 2.2 Запуск и тестирование
1. Старт через `docker compose -f docker-compose1.2.yml up -d`:
   - запустится 3 контейнера - `frontend` для отдачи статики, `keycloak` и хранилище postgres `keycloak_db`.
   - конфигурация `keycloak` [/keycloak/realm-export.json](../../keycloak/realm-export.json) прогрузится при старте, в ней уже есть все тестовые пользователи и конфигурация фронтового клиента.
2. На `localhost:3000` нажать кнопку логин, SPA отправит GET на `/auth` Keycloak. Среди полей будет `code_challenge` - хэш случайной строки `code_verifier`, которая пока что остается в браузере. Keycloak прикапывает `code_challenge` до момента запроса токенов, `code_challenge` это начало `PKCE`.
   - <img src="images/01_SPA_to_keycloak_auth_pkce.png" width="700">
3. После ввода логина и пароля одного из пользователей  [realm-export.json](../../keycloak/realm-export.json) Keycloak должен успешно аутентифицировать его и сделать редирект обратно на SPA (`redirect_uri`), возвращая при этом `code` для получения токенов.
   - <img src="images/02_autentificate_code_to_SPA.png" width="700">
4. SPA идет c `POST` запросом за токенами `access_token, refresh_token` c кодом и `code_verifier` в ручку `/token`. Keycloak проверяет код, а также хэширует `code_verifier` и сравнивает с `code_challenge`, который он помнит, в случае успеха отдает токены назад SPA.
   - <img src="images/03_spa_get_tokens_payload.png" width="700">
   - <img src="images/04_spa_get_tokens_response.png" width="700">
5. Токены хранит SPA.


### 2.3 Детали проблемы с запуском старой версии Keycloak
- контейнер Keycloak 21.1 не стартовал в docker под apple silicon;
- попытка это исправить обернулась настоящим адом:
   - обновление до одной из последних версий `Keycloak-js: "^26.2.3"`, чтобы запустился контейнер Keycloak;
   - далее оказалось, что на фронте в `ReportPage` в фрагменте кода `if (!Keycloak.authenticated)` объект Keycloak всегда оставался неаутентифицированным из-за того, что старая версия библиотеки `@react-Keycloak/web` не умеет работать с новым Keycloak;
   - из-за этого после успешной авторизации не отрисовывалась кнопка `download report`, которая должна давать авторизованному пользователю доступ на будущий бизнес-бэкенд;
   - пришлось отказаться от устаревшей либы `@react-Keycloak/web` и написать замену `ReactKeycloakProvider`, а также самописный флажок `Keycloak.authenticated`;
   - далее выяснилось, что формат либы `Keycloak-js` поменялся и теперь она уже не собирается под сборщиком `"react-scripts": "5.0.1"`, который (сюрприз) тоже заброшен;
      - выяснилось, что отказаться от `react-scripts` было бы достаточно непросто из-за необходимости сильно менять конфиг сборки и вручную прописывать многие зависимости;
      - вместо этого использовал аннотацию `// @ts-ignore` и ручное приведение типов в нескольких местах, что, наверное, костыль для ts;
- в цонце концто фронт собрался, и для авторизованного пользователя стал рисовать кнопку с бизнес-ручкой под капотом;

## Задача 3. Повышение безопасности через перенос хранения токенов на бэкенд
>1. Перенесите механизм запроса access- и refresh-токен из фронтенда в новый бэкенд-сервис, который реализует интеграцию с Keycloak и работу с сессиями.
>2. Назовите сервер bionicpro-auth. Написать его можно на любом из стандартных для бэкенда языков — Java, C#, Go, Python. Можно использовать либы и фреймворки.
>3. Ограничений по выбору языка нет. Можно использовать фреймворки и библиотеки для реализации задания, например, для Java — Spring Security.
>4. Настройте Keycloak на работу с refresh_token. Установите время работы access_token — не более 2 минут.
>5. При успешной авторизации на бэкенде сохраните refresh_token на защищённом хранилище или в зашифрованном виде в оперативной памяти, или в распределённом кеше.
>6. Сохраните access_token в оперативной памяти сервера либо в распределённом кеше.
>7. Обеспечьте привязку access_token и refresh_token к сессии.
>8. В ответе фронтенду вместо токенов отдайте сессионную cookie c HTTP-only и Secure-флагами.
>9. Время жизни сессии должно быть больше времени жизни access_token, чтобы при истечении access_token можно было обновить access_token через refresh_token.
>10. Обновите код фронтенд-приложения, убрав из него механизм получения токенов и сделав обязательным прокидывание на бэкенд сессионной cookie.
>11. Если access_token устареет, то сервис сам должен сходить за новым в Keycloak, используя refresh token.
>12. Реализуйте ротацию сессии в рамках действующего access_token для предотвращения session fixation attack. Для этого при очередном запросе к защищённому ресурсу при успешной проверке сессии на сервисе он перепривязывает access_token и refresh_token к новому session id, обновляет cookie и возвращает новый session id в ответе фронтенду.

### 3.1 ADR 03_1. Перенос хранения токенов на бэкенд
1. Реализован новый бэкенд сервис [bionicpro-auth](../../bionicpro-auth) на Java Spring Security, совмещающий в себе:
   - BFF в части проксирования запросов с фронта на будущий бизнес-бэкенд reports, (задел для API-агрегацию с нескольких микросервисов бэкенда);
   - сервис авторизации;
2. Новый сервис `bionicpro-auth` реализует:
   - интеграцию с Keycloak в части получения токенов `access_token` (`AT`) и `refresh_token` (`RT`);
   - сохранение токенов после успешной авторизации и ротацию `AT+RT` через Keykloak, если устарел `AT` в оперативной памяти;
   - передачу куки `JSESSIONID` на фронт c Secure и HTTP-only флагами;
   - валидацию куки через проверку наличия свежего `AT`;
   - привязку `JSESSIONID` и токенов к объекту сессии в оперативной памяти;
   - удаление сессии:
      - в момент очередного запроса к BFF, если время действия `RT` закончилось;
      - каждые 30 мин, что больше времени максимально настроенного времени жизни `RT` (`ssoSessionMaxLifespan`) в Keycloak;
   - ротацию сессии и куки `JSESSIONID` пока действует `AT` для предотвращения Session Fixation Attack;
      - для этого при очередном запросе (к бизнес-бэкенду, либо при завершении авторизации) в случае успешной валидации сессии`bionicpro-auth`  перепривязывает `AT` и `RT` к новому `JSESSIONID`, который он возвращает c кукой в ответе фронтенду;
      - `Token Relay`: проксирование запросов к `reports-backend` с приклеиванием `AT`;
3. Составлен обновленный конфиг `keycloak` [realm-export2.json](../../keycloak/realm-export2.json), куда внесены правки:
   - ограничения времени работы токенов в realm `reports-realm`:
       ```shell
           "accessTokenLifespan": 90,
           "ssoSessionIdleTimeout": 240,
           "ssoSessionMaxLifespan": 300,
       ```
   - вместо публичного клиента `reports-frontend` добавлен приватный клиент `reports-bff-auth`;
   ```json
      {
        "clientId": "reports-bff-auth",
        "enabled": true,
        "publicClient": false,
        "secret": "1h3WfuhlvdQyMNqkz4SSNPZpMyt8sCT7",
        "redirectUris": [
          "http://localhost:3000/*",
          "http://localhost:8081/login/oauth2/code/keycloak"
        ],
        "webOrigins": ["http://localhost:3000"],
        "directAccessGrantsEnabled": true
      }
    ```
   - клиент OAuth2.0 внутри BFF (для подключения к Keycloak) настроен с соотвествующими кредами `clientId` и `secret` в Spring Security;

4. Новая версия фронтового приложения [frontend_bff](../../frontend_bff) содержит такие правки:
   - убрано взаимодействие с `keycloak` и библиотека `Keycloak-js`;
   - запросы на авторизацию (кнопка логин) и кнопка бизнес-бэкенду `bionicpro-reports` идут на BFF `bionicpro-auth`;
   - добавлено прикрепление куки к запросам на BFF:
   ```tsx
      // App
      useEffect(() => {
        // Проверяем, залогинен ли пользователь через наш BFF
        fetch(`${process.env.REACT_APP_BFF_URL}/api/user/me`, {
            credentials: 'include' // Обязательно для передачи сессионной куки JSESSIONID
        })   
     
      // ReportsPage
      const response = await fetch(`${process.env.REACT_APP_BFF_URL}/api/reports`, {
        // Обязательно для передачи сессионной куки JSESSIONID
        credentials: 'include'
      });
    ```
   - для выбора отрисовки контента (кнопки логин/скачать отчеты) состояние авторизации проверяется асинхронно в fetch через ручку BFF `/api/user/me`;

### 3.2 ADR 03_2. Нюансы настройки веб-безопасности и Spring Security при переносе хранения токенов на бэкенд
1. Далее для удобства используется терминология: браузер, фронтовое приложение Боб, вредоносное фронтовое приложение на соседней вкладке — Ева и BFF `bionipro-auth` — Алиса.
2. В Алисе потребовалось настроить `CORS` (Cross-Origin Resources Sharing) для возможности взаимодействия между Бобом и Алисой на разных доменах; детали:
   - на учебном стенде Алиса и Боб сидят на разных локальных доменах (localhost:8081 и localhost:3000), а браузер блокирует по умолчанию доступ к результатам кросс-доменных запросов;
   - пример запроса `/api/user/me` от Боба к Алисе, ответ которого заблокирован `CORS`: <img src="images/05_cors_error.png" width="650">
   - зачем нужна защита от `CORS`: код Евы на домене `evil.com` может потенциально бы узнать баланс Боба, отправив GET `bobs-bank.com/api/balance/id=bob` пока Боб авторизован в банке на соседней вкладке браузера;
   - на проде `CORS` не понадобится, т.к. для браузера и Боб, и Алиса будут находиться на одном домене (допустим, `bionicpro.com`), а API-gateway разрулит запросы или к Бобу и Алисе;
   - частично разрешаем `CORS` в Spring Security:
     ```java
       // настройки описаны в этом бине
       @Bean
       public CorsConfigurationSource corsConfigurationSource() {
           CorsConfiguration configuration = new CorsConfiguration();
           // Разрешаем конкретно наш фронтенд
           configuration.setAllowedOrigins(List.of("http://localhost:3000"));
           configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
           configuration.setAllowedHeaders(List.of("Authorization", "Cache-Control", "Content-Type"));
           // КРИТИЧЕСКИ ВАЖНО для передачи JSESSIONID
           configuration.setAllowCredentials(true);

           UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
           source.registerCorsConfiguration("/**", configuration);
           return source;
       }
  
       @Bean
       public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
           http
                   // 1. ВКЛЮЧАЕМ CORS (берет настройки из бина выше)
                   .cors(Customizer.withDefaults())
      //...
      }
     ```
   - технически `CORS` реализуется через простановку заголовков в сообщениях между Бобом и Алисой:
      - от Алисы к Бобу `Access-Control-Allow-Origin: http://localhost:3000`, браузер кэширует это разрешение;
      - от Боба к Алисе: `Origin: http://localhost:3000`;
3. В Алисе через Spring Security настроена защита от `CSRF (или XSRF)` (Cross-Site Request Forgery - Межсайтовая подделка сайтов):
   - реализация:
   ```java
    // 7. Настройка CSRF
    // Поскольку у нас SPA + Cookies, нам нужен CookieCsrfTokenRepository
    .csrf(csrf -> csrf
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .csrfTokenRequestHandler(csrfRequestHandler())
            .ignoringRequestMatchers("/logout")
    )
    // ДОБАВЛЯЕМ ФИЛЬТР, который "будит" CSRF-токен
    .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
      ```
   - технически защита от `CSRF` заключается в обмене между Бобой и Алисой куки `XSRF-TOKEN` с атрибутами безопасности и Same-Origin Policy (SOP):
      - при первом GET запросе, Алиса генерирует случайный токен `XSRF-TOKEN` и передает ее в куке Бобу;
         - пример куки: `Set-Cookie: csrftoken=aB3dE6gHjK9mN2pQ; Max-Age=31449600; Path=/; SameSite=Lax; Secure`;
         - кука доступна для чтения `JavaScript` Боба (withHttpOnlyFalse), чтобы работал Fetch на AJAX запросах;
      - когда Боб захочет сделать POST запрос к Алисе, его `JavaScript` читает куку и кладет в заголовок  `XSRF-TOKEN`, а Алиса проверяет сначала `JSESSIONID` (чтобы понять, что это Боб), а затем токен `XSRF-TOKEN`, чтобы убедиться, что запрос именно от Боба;
      - реальный пример атаки:
         - `JavaScript` Евы без защиты от `CSRF` смог бы прикинуться Бобобом и украсть его деньги, отправить с домена `evil.com` POST на `bobs-bank.com/api/transfer?id=Bob&money=1000&to=Alice` (в теле параметры счета Евы), пока Боб авторизован в банке на соседней вкладке;
         - с защитой от `CSRF` Ева не сможет получить доступ к `XSRF-TOKEN` Боба, поэтому любой запрос Евы будет отклонен Алисой;
4. Кука `JSESSIONID` передается на фронт с HttpOnly и secure флагами:
   - пример: `JSESSIONID=183CA94294877A35FCE7925F87038A7D; Path=/; Secure; HttpOnly; SameSite=Lax`
5. Зачем защита от `CSRF`, если есть `SameSite` на основной куку `JSESSIONID`?
   - `SameSite=Lax` для `JSESSIONID` обеспечит безопасность в 90% без защиты от `CSRF`;
   - но могут быть хитрые атаки на поддомены, ошибочные GET заросы вместо POST (`SameSite=Lax` пропустит `JSESSIONID` от Евы), уязвимости и старые версии браузеров, где `SameSite=Lax` может превратиться в `SameSite=None`;
   - с `SameSite` + `CSRF` токеном работает принцип `Defense in Depth` (Эшелонированная оборона):
      - говорят, что в безопасности считается дурным тоном полагаться на один механизм, особенно если он находится под контролем клиента (браузера);
      - `SameSite` — это защита на стороне браузера;
      - `CSRF` токен — это защита на стороне сервера;
   - если в реализации` SameSite` в Chrome найдут баг (а их находили), Алиса все равно останется под защитой CSRF-токена;
6. Spring Security автоматически создает ручки для встроенного клиента OAuth (Keycloak в нашем случае):
   - `GET /oauth2/authorization/{registrationId}, у нас /oauth2/authorization/keycloak` - инициация входа в Keycloak, делает под капотом:
      - генерирует параметры `state` (`CSRF`) и `PKCE` (code_challenge);
      - формирует URL для авторизации на стороне Identity Provider (например, Keycloak);
      - перенаправляет пользователя на страницу логина;
   - `GET /login/oauth2/code/{registrationId}, у нас /login/oauth2/code/keycloak`, делает под капотом:
      - принимает временный code от Keycloak;
      - проверяет `state` для защиты от `CSRF`;
      - обменивает этот код на `access_token и refresh_token` (back-channel запрос);
      - создает сессию на стороне `BFF` и устанавливает защищенную сессионную куку (HttpOnly, Secure) для фронтенда;
7. Нюансы реализации ключевого объекта OAuth взаимодействия в Spring Security `OAuth2AuthorizedClientManager`:
   - из коробки реализует взаимодействие с Keycloak, получает токены, сохраняет их в своем репозитории, обновляет `AT` при наличии действующего `RT`;
   - отвечает за предоставление и проверку токенов для других компонентов Spring, чтобы, например, не заставлять `ProxyController` проксировать запросы с протухшим AT на бизнес-бэкенды;
   - реализует встроенную защиту от фиксации сессии (Session Fixation Protection) после успешной авторизации, сменив куку `JSESSIONID`;
      - дополнительно по требованию задания реализован параноидальный режим смены `JSESSIONID` на каждый запрос от Алисы до Боба через механизм фильтров:
   ```java
    @Slf4j
    @Component
    public class SessionRotationFilter implements Filter {
    
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
    
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpSession session = httpRequest.getSession(false);
    
            if (session != null) {
                String oldId = session.getId();
                // Магия Сервлет-контейнера: меняет ID, сохраняя все атрибуты (токены)
                String newId = httpRequest.changeSessionId();
                log.info("🌀 Ротация сессии: {} -> {}", oldId, newId);
            }
            chain.doFilter(request, response);
        }
    }
    ```
   - **не реализует автоматом** сброс сесcии при устаревании `RT`; пользователь остается по умолчанию залогиненным (с кукой), но без реальных прав, и Боб не перебросит его на кнопку с логином;
   - дефолтно тайм-аут сессии управляется сервлет-контейнером (Tomcat, Jetty), а не Spring Security и составляет 30 минут;
   - как тогда сбросить сессию?
      - можно поменять конфиг через Spring вот так:
     ```yaml
      server:
        servlet:
          session:
            timeout: 45m 
      ```
      - можно реализовать явный вызов на эндпойнт `/logout` и закрывать сессию;
      - можно сделать автоматический сброс сессии в `oAuth2AuthorizationFailureHandler`, **в Алисе реализован этот вариант**;

### 3.3 Запуск и тестирование
1. Удалим volume Keycloak хранилища из Задачи 2 `docker compose -f docker-compose1.2.yml down -v`, чтобы загрузить Keycloak с новым конфигом.
2. Стартуем все заново `docker compose -f docker-compose1.3.yml up -d`:
   - запустится 4 контейнера: `frontend` с новой версией фронта, `keycloak`, хранилище postgres `keycloak_db` и `bionicpro-auth`;
   - конфигурация `keycloak` [realm-export2.json](../../keycloak/realm-export.json) прогрузится при старте;
   - в ней уже есть все тестовые пользователи, настроены времена жизни `AT/RT` - 2/5 мин.;
3. Для учебных целей в `bionicpro-auth` настроено логирование токенов при ротации. Логирование походов в ручку Keycloak сходу не вышло сделать, поэтому можно включить запись events в админке Keycloak (localhost:8080 admin-admin) и смотреть их там же в UI.
4. При нажатии кнопки логин на `localhost:3000` (Боб) теперь идет в `bionicpro-auth` (Алису) на автоматическую ручку `/oauth2/autorizaion/` Spring Security. Далее `OAuth 2.0` клиент под капотом редиректит запрос на `/auth` Keycloak. Можно увидеть `code_challenge` для `PKCE`. Keycloak прикапывает `code_challenge`.
   - <img src="images/06_login_from_bob_to_alice_with_302_to_keycloak.png" width="700"/>
5. После ввода логина и пароля одного из пользователей  [realm-export2.json](../../keycloak/realm-export.json) Keycloak должен успешно провести аутентификацию и сделать редирект обратно на Алису (`redirect_uri`) в автоматическую ручку `login/oauth2/code/keycloak`, возвращая при этом `code` для получения токенов.
   - <img src="images/07_1keycloak_autentificate_code_to_alice.png" width="700"/>
6. Алиса под капотом идет за токенами `AT` и `RT` `POST` запросом с кодом `code_verifier`  в ручку `/token` Keycloak. Он проверяет код, хэширует `code_verifier` и сравнивает с `code_challenge`, который он помнит. В случае успеха отдает токены Алисе, которая редиректит браузер на Боба, одновременно передавая ему первый вариант `JSESSIONID`:
   - <img src="images/07_2alice_get_tokens_and_302_to_bob_with_first_jsessionid.png" width="650"/>   
7. Далее Боб должен успешно проверить авторизацию в ручке `/api/user/me` Алисы и нарисовать кнопку скачивания отчетов вместо логина.
   - <img src="images/10_alice_check_api_user_me_OK.png" width="850"/>
8. Затем для тестирования обновления `JSESSIONID`, а также времени жизни и ротации токенов стоит сделать несколько запросов на бизнес-бэкенд и дождаться, пока протухнет `RT` через 5 минут с момента авторизации:
   - просмотр events в Keycloak для двух пользователей: видны аутентификация, обмены кодов на токены и обновления AT в пределах жизни RT, за которыми тоже ходит Алиса:
   - <img src="images/08_keycloak_events.png" width="500"/>
   - логи Алисы для одного пользователя, где видны ротации сессии `JSESSIONID`, смены `AT` и `RT`:
   - <img src="images/09_alice_logs.png" width="850"/>
   - ожидаемо получаем от Алисы 500-тки до момента, пока не протухнет RT и она вернет 401, а фронтенд не возвратит кнопку логина для повторной авторизации.
   - <img src="images/11_alice_proxy_api-reports_500.png" width="700"/>
   - <img src="images/12_alice_proxy_api-reports_401.png" width="700"/>
9. **Примечание**: на скриншотах выше нигде не видно `CSRF` токена, его добавил чуть позже, сейчас он есть:
   - вот пример отдельного запрос Боба к бизнес-бэкенду с `XSRF` токеном <img src="images/13_XSRF-TOKEN.png" width="650"/>

### 3.4 Альтернативные решения архитектуры авторизации
- возможна нужна ротация куки `CSRF` также, как и `JSESSIONID`;
- Вариант 2 архитектуры: `bionicpro-auth` это отдельный сервис авторизации, без функции прокси, бизнес-бэкенды ничего не знают про токены и валидируют `JSESSIONID` на каждом запросе, ходя в сервис авторизации;
- Вариант 3 архитектуры: оставляем BFF (потом что может пригодиться для API-агрегации), одновременно в нем сохраняется валидация AT, но RT живет в отдельном  `bionicpro-auth`;

## 4. Задача 4. Подключение LDAP для возможности получения данных о пользователях представительства BionicPRO в другой стране
>1. Разверните LDAP-сервер OpenLDAP. Файл конфигурации развёртывания и также файл ldif с пользователями и ролями лежит в репозитории спринта.
>2. Настройте Keycloak, чтобы он ходил в LDAP-сервер за авторизацией пользователей.
>3. Добавьте маппинг ролей для синхронизации ролей разных представительств BionicPRO.

### 4.1 ADR 04. Подключение LDAP
1. Поднять контейнер `openldap` для готового конфига [ldap/config.ldif](../../ldap/config.ldif). Настройки LDAP:
   - **People** (`ou=People,dc=example,dc=com`): пользователи `john.doe`, `jane.smith` и `alex.johnson` c паролем `password`.
   - **Groups** (`ou=Groups,dc=example,dc=com`): группы `user` и `prothetic_user` — должны маппиться на роли realm в Keycloak для разных представительств BionicPRO.
   - требуемое соответствие групп LDAP и ролей Keycloak:

     | Группа LDAP (cn) | Роль в Keycloak (realm) |
            |------------------|-------------------------|
     | user             | user                    |
     | prothetic_user   | prothetic_user          |
2. Настроить User Federation в Keycloak через нового провайдера LDAP с маппингом ролей:
   - через импорт нового конфига [realm-export1.4_with_ldap.json](../../keycloak/realm-export1.4_with_ldap.json);
   - пользователей и роли должны заработать сразу, но можно после импорта realm выполнить **Sync all users** и **Sync LDAP Roles to Keycloak** в Keycloak Admin (User federation → ldap);
3. Альтернативный алгоритм настройки через UI Keycloak Федерации с нуля (тогда следует использовать предыдущий конфиг Keycloak или [realm-export1.4_with_no_ldap.json](../../keycloak/realm-export1.4_with_no_ldap.json)):
   - a. Создание провайдера.
      - Realm **reports-realm** → User federation → Add provider → **ldap**.
      - **Пароль администратора:** задаётся переменной .
      - Keycloak использует тот же пароль для подключения к LDAP; при смене пароля обновите настройки User Federation в Keycloak (realm **reports-realm** → User federation → ldap → Settings → Bind credential).
      - **General options**:
        - UI display name: ldap
        - Vendor: **Other**
      - **Connection and authentication settings**:
        - Connection URL: `ldap://openldap:389`
        - Bind type: **simple** 
        - Bind DN: `cn=admin,dc=example,dc=com` 
        - Bind credentials: значение `LDAP_ADMIN_PASSWORD` из конфиге `docker-compose` (`adminpassword`)
      - **LDAP searching and updating**
        - Edit mode: **READ_ONLY** 
        - Users DN: `ou=People,dc=example,dc=com`
        - Username LDAP attribute: `cn` (было по дефолту)
        - RDN LDAP attribute: `uid`
        - UUID LDAP attribute: `uid`
        - User object classes: `inetOrgPerson, top`.
   - b. Создание маппера ролей в настройках провайдера: ldap → Mappers → создать маппер **role-ldap-mapper**: 
     - Name: `custom-role-ldap-mapper`
     - LDAP Roles DN `ou=Groups,dc=example,dc=com`
     - Role Name LDAP Attribute: `cn`(было по дефолту)
     - Mode **LDAP_ONLY** (было по дефолту)

### 4.2 Запуск и тестирование
1. Удалим volume Keycloak хранилища из Задачи 3 `docker compose -f docker-compose1.3.yml down -v`, чтобы загрузить Keycloak с новым конфигом. Либо пропустить шаги 1-2 и настроить User Federation в UI по алгоритму выше.
2. Стартуем все заново `docker compose -f docker-compose1.4.yml up -d`:
   - запустится 5 контейнеров: `openldap`, `frontend` с новой версией фронта, `keycloak` (по умолчанию) c ldap-провайдером, хранилище postgres `keycloak_db` и `bionicpro-auth`;
3. Пробуем залогиниться под одним из пользователей ldap:
   - переходим в Keycloak, вводим креды из LDAP, например, `jane.smith/password` и под капотом Keycloak должен их проверить через LDAP;
   - далее стандартный флоу по получению токенов Алисой и отрисовкой бизнес-кнопки на Бобе;
   - ответ ручки `/api/user/me` после LDAP авторизации на Бобе:
   - <img src="images_4_ldap/03_ldap_login_ok.png" width="900"/>
   - пользователь LDAP в базе Keycloak:
   - <img src="images_4_ldap/04_ldap_user_in_keycloak_pg_db.png" width="900"/>

## Задача 5. Настройка MFA
>1. Настройте в Keycloak механизм OTP-аутентификации.
>2. Включите обязательный ввод одноразового пароля для всех пользователей.
>3. Убедитесь, что под пользователем можно войти в систему только после ввода одноразового пароля из Google Authenticator или FreeOTP.

## 5.1 ADR 05. Подключение MFA
1. Настройка ldap из предыдущего задания.
2. Настройка обязательного шага OTP-аутентификации в UI keykloak:
   - Создаем дубль текущего ` browser flow` **Autentifaction -> browser (Built-in) -> duplicate -> Назвать "Browser with MFA_OTP"**;
   - <img src="images_mfa/0_new_flow_created.png" width="600"/>
   - Создаем Required Step внутри **Browser with MFA_OTP forms -> Add execution -> Conditional OTP form -> Выбираем "Requirement=required**; новый step должен быть после **User Password Form**;
   - <img src="images_mfa/1_add_new_step1.png" width="750"/>
   - <img src="images_mfa/2_add_new_step2.png" width="500"/>
   - <img src="images_mfa/3_new_step_added.png" width="750"/>
   - Включем новый Browser with MFA_OTP поток вместо встроенного Browser flow (built-in):  **Autentifaction -> выбрать Browser with MFA_OTP -> bind flow -> Browser Flow**
   - <img src="images_mfa/4_new_flow_how_to_bind1.png" width="750"/>
   - <img src="images_mfa/5_new_flow_how_to_bind2.png" width="300"/>
   - <img src="images_mfa/6_new_flow_binded.png" width="750"/>

### 5.2 Запуск и тестирование
1. Не удаляем volume Keycloak хранилища из задачи с LDAP, или надо будет заново настроить ldap.
2. Пробуем залогиниться под одним из пользователей ldap:
   - переходим в Keycloak, вводим креды из LDAP, например, `jane.smith/password` и под капотом Keycloak должен их проверить через LDAP;
   - получаем редирект на страничку регистрации OTP, сканируем qr код в `Яндекс.ID(Бывший яндекс.ключ)`;
   - <img src="images_mfa/7_top_setup_qr.png" width="1000"/>
   - ввод кода из аутентификатора, Keycloak в случае упеха редиректит на Алису c кодом;
   - <img src="images_mfa/8_requeried_action1.png" width="700"/>
   - <img src="images_mfa/8_required_action2.png" width="700"/>
   - далее стандартный флоу по получению токенов Алисой и отрисовкой бизнес-кнопки на Бобе;
   - ответ ручки `/api/user/me` после LDAP авторизации c MFA на Бобе: <img src="images_mfa/91_alice_api_user_me.png" width="700"/>

## Задача 6. Добавление OAuth 2.0 от Яндекс ID
>1. Используя механизм Identity Brokering, реализуйте аутентификацию пользователей через внешний Identity Prodider Яндекс ID. Обратите внимание, что сервис протезов получает данные профиля пользователя из Яндекса.
>2. После аутентификации сервис должен спрашивать пользователя о разрешении использовать данные.
>3. Сервис должен запрашивать у Яндекса данные профиля и сохранять их в БД.

## 6.1 ADR 06. Добавление OAuth 2.0 от Яндекс ID
1. Создаем "веб-приложение" в аккаунте яндекса, через которое будет авторизовыываться через Яндекс ID.
   - важно не ошибиться с `redirect_uri` на Keycloak `http://Keycloak:8080/realms/reports-realm/broker/yandex/endpoint` и выбрать скоупы данных аккаунта, которые будем передавать в Keycloak:
    <div style="display: flex; gap: 10px;">
      <img src="yandex_oauth20/09_1_web_app_yandex_id_oauth20.png" width="500">
      <img src="yandex_oauth20/09_2_web_app_yandex_id_oauth20.png" width="300">
    </div>
2. Добавляем новый Identity Provider для авторизации через для Yandex.ID в Keycloak. Удалось сделать новый конфиг [realm-export1.6_yandex_oauth.json](../../keycloak/realm-export1.6_yandex_oauth.json) путем ревер-инжиниринга через UI:
   - создаем OAuth 2.0 Identity provider (провайдер типа OIDC **не получилось настроить**);
   - <img src="yandex_oauth20/01_config_provider_oauth_type.png" width="750">
   - общие настройки;
   - <img src="yandex_oauth20/02_config_provider_general.png" width="750">
   - креды (из созданного приложения выше) и мапперы данных пользователя к данным, передаваемых Яндексом;
   - <img src="yandex_oauth20/03_config_creds_and_user_profile_claims.png" width="750">
   - скоупы такие же, как в приложении;
   - <img src="yandex_oauth20/04_config_provider_advanced.png" width="500">
   - тут все по дефолту;
   - <img src="yandex_oauth20/05_config_provider_advanced_settings.png" width="750">
   - дополнительных мапперов (для маппинга атрибутов, переданных от Provider Identity в атрибуты Keycloak) заводить не нужно, в новых версиях Keycloak все в настройках;
   - <img src="yandex_oauth20/06_config_provider_no_extra_mappers.png" width="750">

### 6.2 Запуск и тестирование
1. Удалим volume Keycloak хранилища из Задач 4 и 5 `docker compose -f docker-compose1.4.yml down -v`, чтобы загрузить Keycloak с новым конфигом.
2. Подложить в [./keycloak/.env.secrets](../../keycloak/.env.secrets) 2 секрета:
    ```properties
    YANDEX_OAUTH20_BIONICPRO_CLIENT_ID=<clietID>
    YANDEX_OAUTH20_BIONICPRO_CLIENT_SECRET=<clientSecret>
    ```
3. Стартовать все заново `docker compose -f docker-compose1.6.yml up -d`:
   - запустится 4 контейнера: `frontend`, `keycloak` c новым конфигом Yandex OAuth (но без LDAP), хранилище postgres `keycloak_db` и `bionicpro-auth`;
   - конфигурация `keycloak` [realm-export1.6_yandex_oauth.json](../../keycloak/realm-export1.6_yandex_oauth.json) прогрузится при старте;
4. Пробуем залогиниться в аккаунт Яндекса:
   - по кнопке `yandex` Keycloak отправляет браузер в авторизацию Яндекс ID;
   - <img src="yandex_oauth20/10_0_results_new_keykloak_yandex_button.png" width="350">
   - авторизуемся в ЯндексID любым доступным способом (логин, пароль, MFA) и при первом входе должно появиться окно подтверждения выдачи доступа для приложения ранее требуемым скоупам данных в `oauth.yandex.ru/owl/authorize/allow`;
   - <img src="yandex_oauth20/10_results_allow_yandex_page.png" width="300">
   - после этого через несколько шагов через ручки Keycloak `/realms/reports-realm/login-actions/first-broker-login` и `/realms/reports-realm/broker/after-first-broker-login` keykloak наконец-то редиректит в уже известную авто-ручку Алису `login/oauth2/code/keycloak` c кодом;
   - далее стандартный флоу по получению токенов Алисой и отрисовкой бизнес-кнопки на Бобе;
   - ответ ручки `/api/user/me` после LDAP авторизации показывет пользователя из Яндекс OAuth 2.0;
   - <img src="yandex_oauth20/11_results_logged_yandex_user_me_endpoint.png" width="900">
   - в списке users в Keycloak тоже можно наблюдать этого же пользователя, то есть он добавлен в базу;
   - <img src="yandex_oauth20/12_results_logged_yandex_user.png" width="900">
5. Экспортированный конфиг `keyckloak` после 6 задания: [keycloak-results-export.json](../../keycloak/keycloak-results-export.json)

   