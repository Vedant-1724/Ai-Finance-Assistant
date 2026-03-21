# 🚀 Deployment Guide — Free-Tier Cloud Services

Deploy the AI Finance Assistant using **100% free tiers**.

| Component | Platform | Free Tier |
|---|---|---|
| Frontend | [Vercel](https://vercel.com) | Unlimited hobby projects |
| Backend | [Railway](https://railway.app) | $5 credit/month |
| AI Service | [Render](https://render.com) | 750 hours/month |
| Database | [Supabase](https://supabase.com) | 500 MB Postgres |
| Cache | [Upstash](https://upstash.com) | 10K commands/day |
| Message Queue | [CloudAMQP](https://www.cloudamqp.com) | 1M messages/month |

---

## Step 1: Create Accounts & Get Credentials

### 1.1 Supabase (PostgreSQL)
1. Go to [supabase.com](https://supabase.com) → Sign up
2. Click **New Project** → Name it `finance-db` → Set a **database password** → Select region
3. Wait for project to provision (~2 min)
4. Go to **Project Settings** → **Database** → **Connection string** → **URI**
5. Copy the URI — it looks like:
   ```
   postgresql://postgres.[ref]:[password]@aws-0-[region].pooler.supabase.com:6543/postgres
   ```
6. Save as `DATABASE_URL`
7. Your `DB_USERNAME` = `postgres`, `DB_PASSWORD` = the password you set

### 1.2 Upstash Redis
1. Go to [console.upstash.com](https://console.upstash.com) → Sign up
2. Click **Create Database** → Name it `finance-redis` → Select region → **TLS enabled**
3. Copy the **Redis URL** from the dashboard (starts with `rediss://`)
4. Save as `REDIS_URL`

### 1.3 CloudAMQP (RabbitMQ)
1. Go to [cloudamqp.com](https://www.cloudamqp.com) → Sign up
2. Click **Create New Instance** → Name: `finance-mq` → Plan: **Little Lemur (Free)**
3. Select region closest to your other services
4. Copy the **AMQP URL** (starts with `amqps://`)
5. Save as `CLOUDAMQP_URL`

---

## Step 2: Deploy Backend on Railway

1. Go to [railway.app](https://railway.app) → Sign up with GitHub
2. Click **New Project** → **Deploy from GitHub repo** → Select `ai-finance-assistant`
3. Railway may detect the root `docker-compose.yml` — instead, configure manually:
   - **Root Directory:** `Finance and Accounting Assistant`
   - Railway will auto-detect the `Dockerfile`
4. Go to **Variables** tab → Add these environment variables:

| Variable | Value |
|---|---|
| `DATABASE_URL` | Your Supabase connection string |
| `SPRING_DATASOURCE_URL` | Optional explicit JDBC URL if you prefer to skip `DATABASE_URL` parsing |
| `SPRING_DATASOURCE_USERNAME` | Optional explicit DB username override |
| `SPRING_DATASOURCE_PASSWORD` | Optional explicit DB password override |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | Your Supabase DB password |
| `REDIS_URL` | Your Upstash Redis URL |
| `CLOUDAMQP_URL` | Your CloudAMQP URL |
| `JWT_SECRET` | A random 64-character string |
| `INTERNAL_API_KEY` | A random 48-character string (share with AI service) |
| `GEMINI_API_KEY` | Your Google Gemini API key |
| `CORS_ALLOWED_ORIGINS` | `https://your-app.vercel.app` (set after Vercel deploy) |
| `AI_SERVICE_URL` | `https://your-ai-service.onrender.com` (set after Render deploy) |
| `MAIL_ENABLED` | `true` |
| `MAIL_HOST` | `smtp.sendgrid.net` |
| `MAIL_PORT` | `587` |
| `MAIL_USERNAME` | `apikey` |
| `MAIL_PASSWORD` | Your SendGrid API key |
| `MAIL_FROM` | Your verified sender email |
| `RAZORPAY_KEY_ID` | Your Razorpay key |
| `RAZORPAY_KEY_SECRET` | Your Razorpay secret |
| `APP_BASE_URL` | `https://your-app.vercel.app` |
| `BOOTSTRAP_ADMIN_EMAIL` | Your admin email |
| `BOOTSTRAP_ADMIN_PASSWORD` | Your admin password |
| `BOOTSTRAP_ADMIN_COMPANY` | Your company name |

5. Railway will auto-deploy. Note your backend URL (e.g., `https://finance-backend-production.up.railway.app`)

---

## Step 3: Deploy AI Service on Render

1. Go to [render.com](https://render.com) → Sign up with GitHub
2. Click **New** → **Web Service** → Connect your GitHub repo
3. Configure:
   - **Root Directory:** `finance-ai`
   - **Runtime:** Docker
   - **Plan:** Free
4. Add environment variables:

| Variable | Value |
|---|---|
| `PORT` | `5001` |
| `GEMINI_API_KEY` | Your Google Gemini API key |
| `GOOGLE_API_KEY` | Same as above |
| `INTERNAL_API_KEY` | Same key as backend |
| `BACKEND_URL` | Your Railway backend URL (e.g., `https://xxx.up.railway.app`) |
| `CLOUDAMQP_URL` | Your CloudAMQP URL |
| `GEMINI_MODEL` | `gemini-2.5-pro` |
| `GEMINI_STATEMENT_MODEL` | `gemini-2.5-pro` |

5. Deploy. Note your AI service URL (e.g., `https://finance-ai.onrender.com`)
6. **Go back to Railway** and set `AI_SERVICE_URL` to your Render URL

---

## Step 4: Deploy Frontend on Vercel

1. Go to [vercel.com](https://vercel.com) → Sign up with GitHub
2. Click **Add New** → **Project** → Import your GitHub repo
3. Configure:
   - **Root Directory:** `finance-frontend`
   - **Framework Preset:** Vite
   - **Build Command:** `npx vite build`
   - **Output Directory:** `dist`
4. Add environment variable:

| Variable | Value |
|---|---|
| `API_BASE_URL` | Your Railway backend URL without a trailing slash (e.g., `https://xxx.up.railway.app`) |
| `VITE_API_BASE_URL` | Optional compatibility alias for `API_BASE_URL` if you already use that variable in Vercel |

5. Deploy!
   - The frontend now proxies `/api/*` and `/actuator/*` through Vercel using `finance-frontend/vercel.ts`, so cookie-based auth works cleanly on the Vercel domain.
6. **Go back to Railway** and update:
   - `CORS_ALLOWED_ORIGINS` = `https://your-app.vercel.app`
   - `APP_BASE_URL` = `https://your-app.vercel.app`

---

## Step 5: Post-Deployment Checklist

- [ ] Visit your Vercel URL — login page should load
- [ ] Register a new account or log in with bootstrap admin
- [ ] Create a test transaction → verify it appears
- [ ] Test AI chat feature
- [ ] Check Supabase dashboard to verify data is stored
- [ ] (Optional) Set up Razorpay webhook URL: `https://your-railway-url/api/v1/payment/webhook`

---

## Troubleshooting

### Backend won't start on Railway
- Check **Deploy Logs** in Railway dashboard
- Verify `DATABASE_URL` is correct and Supabase project is active
- If your Supabase password contains special URL characters or you want to avoid URI parsing entirely, set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` directly instead of relying only on `DATABASE_URL`
- Ensure Railway is using the `Finance and Accounting Assistant` subdirectory

### Frontend shows blank page or API errors
- Verify `API_BASE_URL` is set correctly in Vercel (no trailing slash)
- If you still use `VITE_API_BASE_URL`, keep it aligned with `API_BASE_URL`
- Check browser DevTools → Network tab for CORS errors
- Ensure `CORS_ALLOWED_ORIGINS` in Railway includes your exact Vercel URL

### AI Service not responding
- Render free tier **spins down after 15 min of inactivity** — first request takes ~30s
- Check Render logs for startup errors
- Verify `INTERNAL_API_KEY` matches between Railway and Render

### RabbitMQ connection issues
- CloudAMQP free tier has a **20 connection limit** — if exceeded, connections are refused
- Verify `CLOUDAMQP_URL` is the same in both Railway (backend) and Render (AI service)
