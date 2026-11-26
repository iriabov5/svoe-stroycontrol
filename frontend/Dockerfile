FROM node:26.13.1-bookworm AS build
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json frontend/.npmrc ./
RUN npm ci
COPY frontend/ ./
COPY api/openapi.yaml /src/api/openapi.yaml
COPY redocly.yaml /src/redocly.yaml
RUN npm test && npm run build

FROM nginx:1.28-alpine
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/frontend/dist /usr/share/nginx/html
RUN sed -i '/^user[[:space:]]/d; s@^pid[[:space:]].*;@pid /tmp/nginx.pid;@' /etc/nginx/nginx.conf \
    && chown -R nginx:nginx /var/cache/nginx
USER nginx
EXPOSE 8080
