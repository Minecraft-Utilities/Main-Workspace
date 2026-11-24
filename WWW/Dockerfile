FROM oven/bun:1.4-alpine AS base

# Set the environment
ENV NEXT_PUBLIC_APP_ENV=production

ARG SOURCE_COMMIT
ENV SOURCE_COMMIT=${SOURCE_COMMIT}

# -----------------------------------

# Build the local JS API client (WWW depends on it via file:../JS-API)
FROM base AS js-api
WORKDIR /app/JS-API

COPY JS-API/package.json JS-API/bun.lock ./
RUN bun install --frozen-lockfile

COPY JS-API/ ./
RUN bun run build

# -----------------------------------

# Install dependencies and build the website
FROM base AS depends
WORKDIR /app/WWW

COPY --from=js-api /app/JS-API /app/JS-API
COPY WWW/package.json WWW/bun.lock ./
RUN bun install --frozen-lockfile

# Copy source code after dependencies are installed
COPY WWW/ ./

# Build the website
RUN bun run build

# Run the app (standalone output)
FROM node:alpine3.22 AS runner
WORKDIR /app

RUN apk add --no-cache curl

COPY --from=depends /app/WWW/.next/standalone ./
COPY --from=depends /app/WWW/.next/static ./.next/static
COPY --from=depends /app/WWW/public ./public

ENV ENVIRONMENT=production

# Expose the app port
EXPOSE 3000
ENV HOSTNAME="0.0.0.0"
ENV PORT=3000

CMD ["node", "server.js"]
