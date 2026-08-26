# testBluebox

A Spring Boot REST API instrumented with **OpenTelemetry** (traces, metrics, and logs) designed to produce rich telemetry for a Dynatrace Bluebox account.

## What's inside

| Layer | Details |
|---|---|
| Framework | Spring Boot 3.2 + Java 17 |
| Telemetry | OpenTelemetry SDK + `opentelemetry-spring-boot-starter` |
| Signals | Distributed traces · Custom metrics · Structured logs with trace context |
| Endpoints | Order CRUD API + load simulator |

## Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/orders` | Create an order (emits span + metric) |
| `GET` | `/api/orders` | List all orders |
| `GET` | `/api/orders/{id}` | Get a single order |
| `POST` | `/api/orders/{id}/fulfill` | Fulfill an order |
| `GET` | `/api/simulate/load?iterations=N` | Generate N spans with ~15% simulated error rate |
| `GET` | `/api/simulate/health` | Liveness check |

## Quick start

### 1. Build

```bash
./mvnw clean package -DskipTests
```

### 2. Start the OTel Collector (forwards data to Dynatrace)

Edit `otel-collector-config.yaml` and replace `<DT_ENVIRONMENT_ID>` with your Dynatrace environment ID.

```bash
export DT_API_TOKEN=<your-dynatrace-api-token>

docker run --rm \
  -p 4317:4317 -p 4318:4318 \
  -e DT_API_TOKEN \
  -v $(pwd)/otel-collector-config.yaml:/etc/otelcol/config.yaml \
  otel/opentelemetry-collector-contrib:latest
```

### 3. Run the application

```bash
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
java -jar target/testBluebox-1.0.0.jar
```

### 4. Generate telemetry

```bash
# Create a few orders
curl -s -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -d '{"product":"WidgetX","quantity":5,"unitPrice":9.99}' | jq

# Run load simulation (creates 20 spans, ~15% errors)
curl -s "http://localhost:8080/api/simulate/load?iterations=20" | jq
```

## Connecting to Dynatrace

When creating your Bluebox account, point the OTLP exporter at your Dynatrace environment:

```
OTEL_EXPORTER_OTLP_ENDPOINT=https://<DT_ENVIRONMENT_ID>.live.dynatrace.com/api/v2/otlp
OTEL_EXPORTER_OTLP_HEADERS=Authorization=Api-Token <DT_API_TOKEN>
```

Traces will appear under **Applications & Microservices → Distributed Traces** and custom metrics under **Metrics Explorer** with the `bluebox.*` prefix.

## Custom metrics emitted

| Metric | Type | Description |
|---|---|---|
| `bluebox.orders.created` | Counter | Orders created, labelled by `product` |
| `bluebox.orders.fulfilled` | Counter | Orders fulfilled |
| `bluebox.orders.failed` | Counter | Orders that failed processing |
| `bluebox.simulation.runs` | Counter | Load simulation executions |
