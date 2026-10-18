# SHADE 01 Backend — Heavyweight Streetwear Atelier API

Spring Boot 3.x + Java 17 + PostgreSQL 15 + JPA/Hibernate + Liquibase + Spring Security + JWT.

## Run

Prerequisites: JDK 17, Maven 3.9+, Docker Desktop.

```bash
docker compose up -d postgres
mvn clean spring-boot:run
```

API: `http://localhost:8080`

Liquibase creates the PostgreSQL schema and seeds 40 preset avatars plus Phase-1 garments/variants.

## Authentication

Customer:
- `POST /api/v1/auth/signup`
- `POST /api/v1/auth/signin`
- `POST /api/v1/auth/otp/send`
- `POST /api/v1/auth/otp/verify`

Admin:
- `POST /api/v1/auth/admin/signin`
- No public admin signup.

Development admin defaults:
`admin@metacloth.local` / `ChangeMe_123!`
Change these through environment variables before use outside local development.

OTP:
- SecureRandom 6-digit code
- 5-minute expiry
- 60-second resend cooldown
- 5 attempts
- OTP is stored only as a BCrypt hash
- Current sender logs the code for development
- Replace `OtpSender` with Twilio/MSG91/AWS SNS adapter for production

Google OAuth:
The backend contract expects a Google ID token at:
`POST /api/v1/auth/google`
Set `GOOGLE_CLIENT_ID` and add the Google token verification adapter before enabling it in production.

## Phase-1 API

Catalog:
- GET `/api/v1/avatars`
- GET `/api/v1/products/customizable`

Customizer:
- POST `/api/v1/customizer/preview`

Orders:
- POST `/api/v1/orders`
- GET `/api/v1/orders/my-orders`
- GET `/api/v1/orders/{id}/track`

Admin:
- GET `/api/v1/admin/dashboard/stats`
- GET `/api/v1/admin/orders`
- PUT `/api/v1/admin/orders/{id}/status`

Profile:
- GET `/api/v1/me`

JWT:
`Authorization: Bearer <accessToken>`

Role protection:
- CUSTOMER → customer APIs
- ADMIN → admin APIs

## Important production notes

1. Change JWT secret and admin credentials.
2. Use a real SMS provider; do not log OTPs.
3. Configure Google client ID and verify ID tokens.
4. Put secrets in a secret manager/environment variables.
5. Add payment gateway only when the Phase-1 payment decision is finalized.
6. The final order price is recalculated on the server; the client cannot arbitrarily set a price.
7. Stock is locked with a database pessimistic lock during order creation.
8. AI generation, face swap, custom artwork uploads, custom typography, multi-vendor and multi-currency are intentionally not implemented.
