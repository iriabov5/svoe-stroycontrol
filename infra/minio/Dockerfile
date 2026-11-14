FROM golang:1.27.1-alpine AS build
RUN apk add --no-cache git
ENV CGO_ENABLED=0
RUN go install github.com/minio/minio@7aac2a2c5b7c882e68c1ce017d8256be2feea27f
FROM alpine:3.23
RUN apk add --no-cache ca-certificates curl \
    && addgroup -g 10001 storage \
    && adduser -D -u 10001 -G storage storage \
    && mkdir /data \
    && chown storage:storage /data
COPY --from=build /go/bin/minio /usr/local/bin/minio
USER storage
EXPOSE 9000
ENTRYPOINT ["minio"]
CMD ["server", "/data", "--console-address", ":9001"]
