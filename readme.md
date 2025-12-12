# Camel Native Gateway

A resilient API gateway built with Apache Camel on Quarkus, designed to be compiled to a native executable for optimal performance and minimal resource usage.

## Project Overview

This project implements an API gateway pattern using Apache Camel on Quarkus. It provides:

- Request throttling to prevent overloading downstream services
- Circuit breaking to handle downstream service failures gracefully
- Fault tolerance with configurable timeouts
- Native executable support for minimal resource usage and fast startup

## Architecture

### Class Diagram

```mermaid
classDiagram
    class RouteBuilder {
        <<abstract>>
        +configure() void
    }
    
    class CustomerRoute {
        +configure() void
    }
    
    RouteBuilder <|-- CustomerRoute
    
    class Exchange {
        <<interface>>
        +HTTP_RESPONSE_CODE
        +HTTP_URI
    }
    
    class ThrottlerRejectedExecutionException {
    }
    
    CustomerRoute ..> Exchange : uses
    CustomerRoute ..> ThrottlerRejectedExecutionException : handles
```

### Sequence Diagram

```mermaid
sequenceDiagram
    participant Client
    participant Gateway as Camel Native Gateway
    participant Downstream as Downstream Service
    
    Client->>Gateway: HTTP Request to /api/v1/customers
    
    alt Too Many Requests
        Gateway->>Gateway: Throttle Check (>10 req/sec)
        Gateway->>Client: 429 Too Many Requests
    else Request Allowed
        Gateway->>Gateway: Circuit Breaker Check
        
        alt Circuit Open (Service Unhealthy)
            Gateway->>Client: 503 Service Unavailable
        else Circuit Closed (Service Healthy)
            Gateway->>Downstream: Forward Request
            
            alt Downstream Responds Successfully
                Downstream->>Gateway: Response
                Gateway->>Client: Forward Response
            else Downstream Fails or Times Out
                Downstream->>Gateway: Error/Timeout
                Gateway->>Client: 503 Service Unavailable
                Gateway->>Gateway: Update Circuit Breaker State
            end
        end
    end
```

## Setup and Usage

### Prerequisites

- JDK 21
- Maven 3.8+
- GraalVM or Mandrel (for native builds)
- Docker (optional, for containerized builds)

### Configuration

The application is configured via `application.properties`:

```properties
# HTTP port
quarkus.http.port=8080

# Logging configuration
quarkus.log.level=INFO
quarkus.log.category."com.edw".level=DEBUG

# Downstream service URL (can be overridden with environment variable)
downstream.service.url.customer=${DOWNSTREAM_SERVICE_URL_CUSTOMER:localhost:80}
```

### Building the Application

#### JVM Mode

```bash
./mvnw clean package
```

#### Native Mode

```bash
./mvnw clean package -Dnative
```

#### Using Docker

```bash
docker build -f Dockerfile.multistage -t camel-native-gateway .
```

### Running the Application

#### JVM Mode

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

#### Native Mode

```bash
./target/camel-native-gateway-1.0-SNAPSHOT-runner
```

#### Using Docker

```bash
docker run -p 8080:8080 camel-native-gateway
```

### Environment Variables

- `DOWNSTREAM_SERVICE_URL_CUSTOMER`: URL of the downstream customer service (default: localhost:80)

## API Endpoints

- `GET /api/v1/customers`: Proxies requests to the downstream customer service

## Resilience Features

### Throttling

The gateway limits concurrent requests to 10 per second. Excess requests receive a 429 status code.

### Circuit Breaker

The circuit breaker opens after 5 failures with a 50% failure ratio, preventing cascading failures. When open, requests receive a 503 status code.

### Timeouts

Requests to downstream services timeout after 2 seconds, preventing resource exhaustion.