# ShopSphere frontend

React, TypeScript, and Vite customer storefront for the existing ShopSphere API Gateway.

## Run locally

1. Start the backend services and API Gateway using the repository's existing local setup.
2. In this directory, run `npm install` once, then `npm run dev`.
3. Open the local URL printed by Vite (normally `http://localhost:5173`).

The Vite development server forwards `/api` requests to `http://localhost:8080`, the API Gateway. Change `server.proxy` in `vite.config.ts` if your Gateway uses another local port. The frontend does not call individual microservices directly.

## Google sign-in setup

Google sign-in uses Google Identity Services and still creates a ShopSphere session. Create an OAuth 2.0 Web application client in Google Cloud Console and allow the storefront origin (for local Vite, `http://localhost:5173`). Copy `frontend/.env.example` to `frontend/.env` and set `VITE_GOOGLE_CLIENT_ID` to that client ID. Set the same `GOOGLE_CLIENT_ID` in the repository's root `.env` for Docker Compose, then restart Vite and the User Service. The backend verifies the ID token signature, issuer, audience, and verified email before issuing ShopSphere tokens. No Google client secret belongs in the frontend.

## Customer flows

- Anyone can browse and filter active products without signing in. Product reads are public; product administration remains protected.
- Guests can add products to a browser-local bag and change quantities. The bag remains available across page reloads in that browser.
- Signing in at checkout syncs the guest bag into the account cart before submitting the order. Account cart and order APIs remain protected.
- Submit checkout with the backend's supported payment method and view order history.
- Cancel orders only while their status is `PENDING`.

The payment service currently simulates success for valid checkout requests at any amount. The interface does not ask for or store card numbers, UPI credentials, or wallet secrets. Shipping details are not requested because the checkout API currently accepts no shipping address.

Access and refresh tokens are kept in browser `sessionStorage` for this frontend session and sent only to the same-origin Gateway. This is a simple local-development setup; production deployment should serve the frontend over HTTPS and review the backend's token and cookie design before launch.

## Commands

- `npm run dev` — local development server
- `npm run build` — TypeScript check and optimized production bundle
- `npm run preview` — serve the built bundle locally
