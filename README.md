# 🔗 Shortly

### Because nobody likes giant URLs.

A distributed URL shortener built with Spring Boot, PostgreSQL, Redis, and Kafka, featuring QR code generation, click analytics, rate limiting, Bloom filters, and consistent hashing.

---

## ✨ Features

### 🔗 URL Shortening

Generate short, shareable links from long URLs with Base62 encoding.

### 📱 QR Code Generation

Create QR codes instantly for every shortened URL.

### 📊 Click Analytics

Track redirects, click counts, referrers, IP addresses, and user agents.

### ⚡ Redis Caching

Sub-millisecond URL lookups using Redis for high-speed redirects.

### 🛡️ Rate Limiting

Token bucket algorithm implemented with Redis to prevent abuse and spam.

### 📨 Event Streaming

Kafka-based click event processing for scalable analytics collection.

### 🎨 Modern Frontend

Responsive React interface with a clean dark-themed design.

### 📖 API Documentation

Interactive OpenAPI/Swagger documentation for testing and exploration.

---

## 🛠️ Tech Stack

### Backend

* Spring Boot 3
* Java 21
* PostgreSQL
* Redis
* Apache Kafka
* Spring Data JPA / Hibernate
* OpenAPI / Swagger

### Frontend

* React 18
* Axios
* QRCode.react

### Infrastructure

* Docker
* Docker Compose

---

## 🚀 Getting Started

### Prerequisites

* Java 21+
* Node.js 18+
* Docker Desktop

---

### Clone Repository

```bash
git clone https://github.com/yourusername/shortly.git
cd shortly
```

---

### Start Infrastructure

```bash
docker-compose up -d
```

This starts:

* PostgreSQL
* Redis
* Kafka

---

### Run Backend

```bash
./mvnw spring-boot:run
```

Backend available at:

http://localhost:8080

---

### Run Frontend

```bash
cd frontend
npm install
npm start
```

Frontend available at:

http://localhost:3000

---

## 📚 API Endpoints

| Method | Endpoint                    | Description              |
| ------ | --------------------------- | ------------------------ |
| POST   | `/api/v1/shorten`           | Create a short URL       |
| GET    | `/r/{shortCode}`            | Redirect to original URL |
| GET    | `/api/v1/stats/{shortCode}` | Retrieve analytics       |
| GET    | `/api/v1/qr/{shortCode}`    | Download QR code         |
| GET    | `/swagger-ui/index.html`    | Swagger UI               |

---

## Example Request

### Create Short URL

```bash
curl -X POST http://localhost:8080/api/v1/shorten \
-H "Content-Type: application/json" \
-d '{
  "longUrl":"https://www.google.com",
  "generateQr":true,
  "expiryDays":30
}'
```

### Response

```json
{
  "shortUrl": "http://localhost:8080/r/abc123",
  "shortCode": "abc123",
  "expiryDays": 30
}
```

---

## 📈 Architecture Highlights

* Redis cache-first URL resolution
* PostgreSQL persistent storage
* Kafka event-driven click tracking
* Token bucket rate limiting
* QR code generation using ZXing
* Base62 URL encoding
* Bloom filter support for lookup optimization
* Consistent hashing preparation for horizontal scaling

---

## 📁 Project Structure

```text
shortly/
├── src/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   └── config/
│
├── frontend/
│   ├── src/
│   ├── public/
│   └── package.json
│
├── docker-compose.yml
├── pom.xml
└── README.md
```

---

## 🧪 Testing

Run backend tests:

```bash
./mvnw test
```

Run frontend tests:

```bash
cd frontend
npm test
```

---

## 🎯 Future Improvements

* Kubernetes deployment
* Distributed analytics consumers
* Custom aliases
* User authentication
* Link expiration policies
* Geo-location analytics
* Testcontainers integration testing

---

## ❤️ Built With

Spring Boot • React • PostgreSQL • Redis • Kafka • Docker
