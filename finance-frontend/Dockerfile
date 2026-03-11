# Stage 1: Build React app
FROM node:20-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm install --silent

COPY . .
RUN npx vite build

# Stage 2: Serve with Nginx
FROM nginx:1.27-alpine

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

RUN apk add --no-cache openssl && \
  openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout /etc/ssl/private/server.key \
  -out /etc/ssl/certs/server.crt \
  -subj "/C=US/ST=State/L=City/O=Organization/CN=localhost"

EXPOSE 80
EXPOSE 443

CMD ["nginx", "-g", "daemon off;"]
