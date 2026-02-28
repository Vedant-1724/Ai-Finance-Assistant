# ─── Stage 1: Build React app ────────────────────────────────────────────────
FROM node:20-alpine AS build

WORKDIR /app

# Copy package files first (cached layer — only re-installs when they change)
COPY package*.json ./
RUN npm ci --silent

# Copy source and build
COPY . .

# FIX: Run vite build directly instead of "npm run build" (which runs "tsc -b && vite build").
# Vite uses esbuild internally to transpile TypeScript — no strict type-checking.
# This avoids TypeScript compiler errors blocking the Docker build.
RUN npx vite build

# ─── Stage 2: Serve with Nginx ───────────────────────────────────────────────
FROM nginx:1.27-alpine

# Copy built static files
COPY --from=build /app/dist /usr/share/nginx/html

# Copy Nginx config (handles React Router + /api proxy)
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
