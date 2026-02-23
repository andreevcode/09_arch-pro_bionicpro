FROM quay.io/keycloak/keycloak:26.5.1

# Переключаемся на root, чтобы установить пакеты
USER root

# Устанавливаем curl (используем microdnf, так как это UBI Minimal)
RUN microdnf install -y curl && microdnf clean all

# Возвращаемся под пользователя keycloak (безопасность прежде всего!)
USER keycloak