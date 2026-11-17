# =============================================================================
# AI ModelHub — React Frontend (Vite + Nginx)
# Stage 1: Node 22 build
# Stage 2: Nginx alpine serve
# Port: 80
# =============================================================================

# ── Stage 1: Build Vite + React app ─────────────────────────────────
FROM node:22-alpine AS build

WORKDIR /app

# Install dependencies (cached layer)
COPY package*.json ./
RUN npm ci --prefer-offline --no-audit --no-fund

# Copy source and build
COPY . .
RUN npm run build

# ── Stage 2: Serve with Nginx ───────────────────────────────────────
FROM nginx:alpine

# Remove default config
RUN rm -f /etc/nginx/conf.d/default.conf

# Copy static files and nginx config
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

RUN apk add --no-cache curl

HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=10s \
    CMD curl -f http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
