# Версии стека

Базовый набор сверён 13 ноября 2026 года. Для Java и Node.js выбираем LTS. Для остальных компонентов используем стабильные версии, учитывая совместимость с платформой.

| Компонент | Версия | Основание |
|---|---|---|
| Java | Eclipse Temurin 25.0.5+8 LTS | [Adoptium](https://adoptium.net/temurin/releases/?version=25). Компиляция и исполнение на Java 25. |
| Gradle Wrapper | 9.9.1 | [Gradle](https://docs.gradle.org/9.9.1/release-notes.html). Java toolchain 25. |
| Spring Boot | 4.1.2 | [Релиз и системные требования](https://docs.spring.io/spring-boot/4.1/system-requirements.html). |
| Spring Security, Spring Data JPA | 7.1.2, 4.1.2 | [BOM Spring Boot](https://docs.spring.io/spring-boot/4.1/appendix/dependency-versions/coordinates.html). |
| PostgreSQL | 18.7 | [Релиз PostgreSQL](https://www.postgresql.org/about/news/postgresql-187-1712-1616-1520-1425-and-19-rc-2-released-3442/). Один патч-релиз для Compose и Testcontainers. |
| PostgreSQL JDBC | 42.7.14 | Из BOM Spring Boot. |
| Flyway Core и модуль PostgreSQL | 12.4.0 | Из BOM Spring Boot. Переход на Flyway 13 требует отдельной проверки совместимости. [Релизы Flyway](https://documentation.red-gate.com/flyway/release-notes-and-older-versions/release-notes-for-flyway-engine) |
| JUnit Jupiter, Testcontainers | 6.0.3, 2.0.5 | Из BOM Spring Boot. Jupiter 6 обновляет ранее выбранный JUnit 5 и сохраняет `@DisplayName`. |
| Node.js | 26.13.1 LTS | [Релиз Node.js](https://nodejs.org/en/blog/release/v26.13.1). |
| React и React DOM | 19.3.1 | [Релиз React](https://github.com/react/react/releases/tag/v19.3.1). |
| TypeScript | 7.0.2 | [Релиз TypeScript](https://github.com/microsoft/TypeScript/releases/tag/v7.0.2). |
| Vite, React plugin | 8.3.8, 6.3.0 | [Vite](https://github.com/vitejs/vite/releases/tag/v8.3.8) · [React plugin](https://www.npmjs.com/package/@vitejs/plugin-react/v/6.3.0). |
| Vitest, React Testing Library | 5.1.0, 16.3.3 | [Vitest](https://github.com/vitest-dev/vitest/releases/tag/v5.1.0) · [Testing Library](https://github.com/testing-library/react-testing-library/releases). |
| Playwright Test, `@axe-core/playwright` | 1.65.0, 4.14.0 | [Playwright](https://github.com/microsoft/playwright/releases/tag/v1.65.0) · [axe-core](https://github.com/dequelabs/axe-core-npm/releases/tag/v4.14.0). |
| OpenAPI, Redocly CLI, openapi-typescript | 3.1.2, 2.78.0, 7.13.0 | [OAS 3.1.2](https://spec.openapis.org/oas/v3.1.2.html) · [Redocly](https://www.npmjs.com/package/@redocly/cli/v/2.78.0) · [Генератор](https://www.npmjs.com/package/openapi-typescript/v/7.13.0). Генератор использует API TypeScript 6.0.2. Проверка приложения использует TypeScript 7.0.2. |
| Плагин Spring Dependency Management, Bouncy Castle | 1.1.7, 1.86 | Версии из сборки. Bouncy Castle используется для Argon2id. |
| Типы React и React DOM | 19.3.0 | Версии из `package.json`. |
| Nginx | `1.28-alpine` | Текущий тег в Dockerfile фронтенда. |
| Docker Compose | 5.7.1 | [Релиз Compose](https://github.com/docker/compose/releases/tag/v5.7.1). |
| OpenSpec CLI | 1.9.0 | Версия инициализации и валидации проекта. |
| SeaweedFS mini, S3-клиент Java | `chrislusf/seaweedfs:4.52`, AWS SDK v2 2.59.1 | [SeaweedFS 4.52](https://github.com/seaweedfs/seaweedfs/releases/tag/4.52), образец запуска SecureShare; S3-адаптер сохранён. |

Версии подключённых компонентов сверяются с Gradle Wrapper, `build.gradle.kts`, `package.json`, `package-lock.json` и контейнерными конфигурациями. Vitest, React Testing Library, Playwright и axe-core подключены и используются в проверках приложения. Temurin в Dockerfile пока указан тегами ветки 25. Фиксировать точные версии прямых зависимостей и теги или digest образов. Транзитивные зависимости задаются BOM и lock-файлом. Проверить разрешение зависимостей, генерацию типов, сборку, миграции и тесты.

### Файловое расширение MVP

AWS SDK for Java v2 (S3 + URL Connection client): **2.59.1**. SeaweedFS mini: **4.52**, тот же закреплённый образ используется в Compose и интеграционных тестах. Образ не требует сборки Go-исходников. Действующие настройки используют SeaweedFS; прежний MinIO и его том оставлены только для описанного разового переноса и возврата. [Локальный запуск и перенос](development.md#закрытые-вложения).
