# =====================================================================
# 管理后台镜像：Node 构建静态产物 → Nginx 托管
# =====================================================================
FROM node:24-alpine AS build
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm install --no-audit --no-fund

COPY . .
# build:only 只跑 vite build（类型检查在 CI/本地单独执行，避免镜像过重）
RUN npm run build:only

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
