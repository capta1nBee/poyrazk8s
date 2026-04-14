# Build stage
FROM node:20-slim AS builder

WORKDIR /app

# Install pnpm
RUN npm install -g pnpm

# Copy package files
COPY package.json pnpm-lock.yaml ./

# Install dependencies
RUN pnpm install 

# Copy source
COPY . .

# Build application
RUN pnpm run build

# Production stage
FROM node:20-slim

WORKDIR /app

# Copy built application from builder
COPY --from=builder /app/.output ./.output

# Exposed port (Nuxt default is 3000)
EXPOSE 8787

# Set environment variables
ENV NODE_ENV=production
ENV HOST=0.0.0.0
ENV PORT=8787

# Start application
CMD ["node", ".output/server/index.mjs"]
