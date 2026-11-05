# MagicMate LIVE Printer

A mobile-friendly dashboard for TikTok LIVE events. It connects to **@tshepothemagician**, shows viewer joins, comments and gifts in real time, and lets you print each event individually.

## Run locally

Requirements: Node.js 20 or newer and npm.

```bash
npm install
npm start
```

Open <http://localhost:3000>. Set `TIKTOK_USERNAME` to another TikTok handle (without `@`) if needed; the default is `tshepothemagician`. The app starts the TikTok connection when the web server starts. Events appear when the account is live and TikTok allows the connection. The `/health` endpoint reports server and connection status.

## Deploy on Render

1. Push this repository to GitHub and sign in to [Render](https://render.com/).
2. Select **New + → Blueprint**, then connect this repository. Render will read `render.yaml` and create the web service. Alternatively, create a **Web Service** manually with this repository, **Build Command** `npm install`, and **Start Command** `npm start`.
3. Confirm the environment variable `TIKTOK_USERNAME` is set to `tshepothemagician` (the Blueprint sets it for you). Render supplies the `PORT` value automatically; the server listens on it.
4. Deploy, wait for the service to become healthy, and open the Render URL. `/health` is the configured health check.

The Socket.IO dashboard uses the same service origin and supports WebSocket connections. The free Render plan may spin down after inactivity, so the first visit after a quiet period can take a little longer to start.

## Using the dashboard

The status chip shows the TikTok connection state. The live feed displays each event's profile image when TikTok provides one, username, event type, comment or gift, and arrival time. Select **Print** on an event to print only that event; the browser's print dialog lets you choose a printer or save to PDF.

## TikTok connection notes

This project uses `tiktok-live-connector`, an unofficial community-maintained connector. It does not need a TikTok password or API key. TikTok may change its LIVE interfaces, availability can depend on the stream and network, and connector operation is not guaranteed. Keep the dashboard and event data private as appropriate; profile pictures and usernames are supplied by TikTok event payloads.
