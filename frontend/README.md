# RTCA frontend

React + Vite + Tailwind client for the chat backend.

## Development

Start the backend on `localhost:8080` (see the root README), then:

```bash
npm install
npm run dev
```

The app runs on `http://localhost:5173`. Vite proxies `/api` and `/ws` to the backend, so no CORS setup is needed.

## Docker

The root `compose.yml` has a `web` service that builds this app and serves it with nginx on `http://localhost:3000` (`WEB_PORT`). nginx proxies `/api` and `/ws` to the `app` service, so the browser only talks to one origin.

```bash
docker compose up --build
```

## Scripts

| Command | Description |
|---|---|
| `npm run dev` | dev server with hot reload |
| `npm run build` | production build into `dist/` |
| `npm run lint` | oxlint |
| `npm run preview` | serve the production build |
| `npm run test:e2e` | Playwright browser tests in `e2e/`. Needs the backend on `localhost:8080`, serves a production build on `:4173` |
