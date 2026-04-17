FROM node:20-alpine AS builder

WORKDIR /app

ARG VITE_KAKAO_JAVASCRIPT_KEY
ENV VITE_KAKAO_JAVASCRIPT_KEY=$VITE_KAKAO_JAVASCRIPT_KEY

COPY FE/package.json ./
RUN npm install

COPY FE/index.html ./
COPY FE/vite.config.js ./
COPY FE/src ./src

RUN npm run build

FROM nginx:1.27-alpine

COPY FE/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80
