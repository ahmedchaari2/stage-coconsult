# syntax=docker/dockerfile:1
#
# No source changes are needed for Docker: environment.prod.ts already points at
# http://localhost:8080 (api-gateway) and http://localhost:8086 (notification-service
# WebSocket), which stay valid because the browser runs on the host and those two
# services publish the same ports to the host in docker-compose.
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
# The existing lockfile pairs Angular CLI 22 with angular-eslint 21.  The
# application builds correctly, but npm's strict peer-dependency resolver
# rejects that historical combination in a clean container.
RUN npm ci --legacy-peer-deps
COPY . .
RUN npm run build -- --configuration production

FROM nginx:1.27-alpine AS runtime
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
