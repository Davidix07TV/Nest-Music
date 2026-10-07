# Metroserver for Nest Music, built from upstream source at a pinned commit.
#
# Why build it instead of pulling their prebuilt image: the User-Agent policy in
# this directory (ua_policy.json) is what lets Nest Music host rooms, and pinning
# the commit means an upstream change can never silently alter a deployment that
# already works. Bump METROSERVER_COMMIT deliberately to take upstream fixes.
#
# Used by: Render (see README.md in this directory) and docker-compose.yml.
# The build context is the *repository root*, not this directory, so the policy can be copied
# through its path from the root (deploy/metroserver/ua_policy.json):
#
#     docker build -f deploy/metroserver/Dockerfile -t nest-music-metroserver .

FROM golang:1.25.6-alpine AS builder

ARG METROSERVER_COMMIT=9da6f9da1f3ec92183a26dc8f6fbf9c8b3f0ede8

RUN apk add --no-cache git bash protobuf-dev
RUN go install google.golang.org/protobuf/cmd/protoc-gen-go@v1.36.11
ENV PATH="/root/go/bin:${PATH}"

WORKDIR /src
RUN git clone --quiet https://github.com/MetrolistGroup/metroserver.git . \
    && git checkout --quiet --detach "${METROSERVER_COMMIT}" \
    && git submodule update --init --recursive --quiet
RUN go mod download
RUN bash scripts/generate_proto.sh
# /out must exist before go build writes the binary, or the build dies with
# "open /out/metroserver: no such file or directory".
RUN mkdir -p /out && CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-w -s" -o /out/metroserver ./cmd/metroserver

# ---------------------------------------------------------------------------

FROM alpine:3.21

RUN apk add --no-cache ca-certificates \
    && adduser -D -H -u 1000 metro \
    && mkdir -p /data /app \
    && chown metro:metro /data /app

COPY --from=builder /out/metroserver /usr/local/bin/metroserver
# Path relative to the build context, which is the repository root (see ../../render.yaml).
COPY --chown=metro:metro deploy/metroserver/ua_policy.json /etc/metroserver/ua_policy.json

# Same working directory as upstream: a relative write lands somewhere writable
# instead of the image root.
WORKDIR /app

# PORT comes from the platform: Render injects it (render.yaml sets 10000),
# docker-compose.yml publishes 8080.
ENV UA_POLICY_FILE=/etc/metroserver/ua_policy.json \
    DATABASE_FILE=/data/metroserver.db

USER metro
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD ["sh", "-c", "wget --no-verbose --tries=1 --spider \"http://127.0.0.1:${PORT:-8080}/health\" || exit 1"]

CMD ["metroserver"]
