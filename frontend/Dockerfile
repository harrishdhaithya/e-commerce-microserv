# Build context is this directory (frontend/), unlike the services - the SPA has no
# dependency on the Maven reactor.

# Debian rather than Alpine. Size is irrelevant here - only the built bundle is
# copied into the nginx stage, so this builder never ships.
#
# Note on the lockfile: npm on macOS does not record @emnapi/core and
# @emnapi/runtime, which are pulled in as nested optional dependencies on Linux. A
# lockfile generated on a Mac therefore makes `npm ci` fail in any Linux container,
# on glibc and musl alike. The fix is to regenerate it from Linux:
#
#   docker run --rm -v "$PWD:/app" -w /app node:22-slim npm install --package-lock-only
#
# That produced a strict superset - two packages added, none removed - so the same
# lockfile now satisfies both platforms. Re-run it if `npm ci` ever fails here again
# after adding a dependency from macOS.
FROM node:22-slim AS build
WORKDIR /build

# Copied before the sources so npm ci only re-runs when dependencies actually change.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf

# Angular's application builder emits the browser bundle into dist/<project>/browser,
# with the project root holding prerender metadata that nginx must not serve.
COPY --from=build /build/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80

# The nginx image already runs its workers as an unprivileged user and its own
# entrypoint handles config templating, so there is nothing to override here.
