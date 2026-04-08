FROM golang:1.22-alpine AS builder

WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY main.go .
RUN CGO_ENABLED=0 go build -o pion-iot .

FROM alpine:3.19
RUN apk add --no-cache wget
COPY --from=builder /app/pion-iot /usr/local/bin/pion-iot

EXPOSE 8080
EXPOSE 3478/udp

ENTRYPOINT ["pion-iot"]
