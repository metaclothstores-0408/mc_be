# MetaCloth — Production Deployment & External Services Manifest

This document records all external cloud services, credentials, endpoints, and architectural fallbacks for the MetaCloth platform. Use this as your checklist before launching to production.

---

## 1. Object Storage (DTF Print Files & Customer Uploads)

### Current Fallback Handled
- **Local Disk Storage**: Generated 300 DPI DTF print-ready PNG files are saved to `./storage/dtf` on the backend server.
- **On-demand Download**: Available directly via `POST /api/v1/dtf/download` (rendered on-demand via hidden canvas / bicubic scaling without persistence).

### Production Setup (Cloudflare R2 or AWS S3)
When scaling to multi-instance cloud deployments (Docker, ECS, Kubernetes), local disk storage is ephemeral. Cloudflare R2 is strongly recommended due to **zero egress bandwidth fees** (DTF print files are 15–50 MB each).

Add to backend environment variables:
```env
# Storage Provider: LOCAL | S3 | R2
STORAGE_TYPE=S3

# S3 / Cloudflare R2 Configuration
S3_BUCKET_NAME=metacloth-dtf-production
S3_REGION=ap-south-1
S3_ACCESS_KEY=your_access_key_here
S3_SECRET_KEY=your_secret_key_here

# For Cloudflare R2 (leave blank if using standard AWS S3):
# S3_ENDPOINT=https://<account_id>.r2.cloudflarestorage.com
```

---

## 2. Payment Gateway (Checkout & Order Fulfillment)

### Current Fallback Handled
- **Instant Test Checkout**: [`Checkout.jsx`](mc_fe/src/pages/Checkout.jsx) processes test orders immediately.
- Real orders are created in PostgreSQL with status `PLACED`, triggering the async DTF production pipeline.

### Production Setup (Razorpay for India / UPI or Stripe for International)
When ready to accept real payments:

1. **Razorpay (India - UPI, Cards, NetBanking, COD)**:
   - Create account at [razorpay.com](https://razorpay.com).
   - Generate Key ID & Key Secret (start in Test mode).
   - Environment variables:
     ```env
     PAYMENT_PROVIDER=RAZORPAY
     RAZORPAY_KEY_ID=rzp_live_...
     RAZORPAY_KEY_SECRET=...
     RAZORPAY_WEBHOOK_SECRET=...
     ```
2. **Stripe (International - Cards, Apple Pay, Google Pay)**:
   - Create account at [stripe.com](https://stripe.com).
   - Environment variables:
     ```env
     PAYMENT_PROVIDER=STRIPE
     STRIPE_PUBLISHABLE_KEY=pk_live_...
     STRIPE_SECRET_KEY=sk_live_...
     STRIPE_WEBHOOK_SECRET=whsec_...
     ```

---

## 3. SMS & Phone OTP Authentication

### Current Fallback Handled
- **Console OTP Logger**: `OtpService.java` generates a cryptographically secure 6-digit OTP (5-minute expiry, 60-second cooldown, BCrypt hashed) and logs it to the server console.
- In development/local testing, check backend terminal logs for `[OTP for +91XXXXXXXXXX: 123456]`.

### Production Setup (DLT-Compliant Indian SMS or Twilio)
Due to Indian TRAI regulations, commercial SMS requires DLT registration (e.g. via Jio DLT / Vilpower / Smartping), which takes 1–2 weeks.

1. **MSG91 / Fast2SMS (India)**:
   - Register Entity and Header on DLT portal.
   - Register OTP template.
   - Add MSG91 AuthKey and Flow ID to environment variables.
2. **Twilio (International)**:
   ```env
   TWILIO_ACCOUNT_SID=...
   TWILIO_AUTH_TOKEN=...
   TWILIO_FROM_PHONE_NUMBER=...
   ```

---

## 4. Google Cloud OAuth 2.0

### Current State
- Google OAuth is integrated with server-side authorization code exchange, state cookie verification, and AES-GCM encrypted token storage.
- **Scopes Configured**: `openid`, `email`, `profile` (excessive `gmail.readonly` scope has been removed).

### Production Verification Checklist
1. Visit [Google Cloud Console](https://console.cloud.google.com/apis/credentials).
2. Add your production domain to:
   - **Authorized JavaScript Origins**: `https://yourdomain.com`
   - **Authorized Redirect URIs**: `https://api.yourdomain.com/api/v1/auth/google/callback`
3. Configure the OAuth Consent Screen:
   - Application Name: **MetaCloth**
   - Support Email & Developer Contact Email
   - Privacy Policy URL & Terms of Service URL
4. Update environment variables on production server:
   ```env
   GOOGLE_CLIENT_ID=your-production-client-id.apps.googleusercontent.com
   GOOGLE_CLIENT_SECRET=your-production-client-secret
   GOOGLE_OAUTH_REDIRECT_URI=https://api.yourdomain.com/api/v1/auth/google/callback
   GOOGLE_OAUTH_FRONTEND_CALLBACK_URL=https://yourdomain.com/oauth/google/callback
   ```

---

## 5. Security & Master Secrets

Generate fresh random keys for production:
```env
# 64+ character random string for signing JWT tokens
JWT_SECRET=replace-with-a-random-64-character-production-string

# 32-byte AES Base64 key for encrypting stored OAuth refresh tokens
GOOGLE_TOKEN_ENCRYPTION_KEY=replace-with-base64-encoded-32-byte-key
```

---

## 6. PostgreSQL Database

Ensure production database runs PostgreSQL 15+ with UTF-8 encoding:
```env
DB_URL=jdbc:postgresql://<db-host>:5432/metacloth
DB_USERNAME=metacloth_app
DB_PASSWORD=<strong_password>
```
Liquibase will automatically apply all schema migrations up to changeset `008-fix-text-columns` on first startup.
