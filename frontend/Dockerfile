# Frontend image, built from the repository root:
#
#   docker build -f frontend/Dockerfile -t invoicing-frontend .
#
# Root context rather than frontend/, so this build and the backend's are
# governed by the same .dockerignore at the top of the repository instead of two
# copies that can drift apart.
#
# Two stages. The first has Node and builds the app; the second has nginx and
# the contents of dist/. No Node, no node_modules and no sources reach the image
# that runs - what it serves is a few hundred kilobytes of static files.

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------
FROM node:22-alpine AS build

WORKDIR /build

# The manifests on their own first: as long as the dependencies do not change,
# Docker reuses this layer and a source edit rebuilds only the compile step.
#
# npm ci, not npm install: it installs exactly what package-lock.json pins and
# fails if the lockfile and package.json disagree, so the image cannot quietly
# get a different dependency tree than the one that was tested.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./

# tsc -b && vite build. The type check is part of it on purpose: a build that
# compiles is a weaker claim than a build that type-checks.
RUN npm run build

# ---------------------------------------------------------------------------
# Runtime
# ---------------------------------------------------------------------------
# The unprivileged nginx image: it runs as uid 101 rather than starting its
# master process as root, and listens on 8080 for the same reason. Same
# principle as the backend image, which runs as uid 1001.
FROM nginxinc/nginx-unprivileged:1.29-alpine AS runtime

# Replaces the stock "serve /usr/share/nginx/html" server block. This image
# ships no templates directory, so there is nothing that could overwrite this
# file at startup.
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 8080

# wget comes from busybox in the base image; nothing extra is installed for
# this. It asks nginx for the page it exists to serve, so a green check means
# more than "a process is running".
#
# 127.0.0.1 and not localhost: "listen 8080" binds IPv4 only, while localhost
# inside the container resolves to ::1 first — which gets a connection refused
# from an nginx that is serving every real request perfectly well. Naming the
# address the server actually listens on removes the ambiguity.
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --quiet --spider http://127.0.0.1:8080/ || exit 1
