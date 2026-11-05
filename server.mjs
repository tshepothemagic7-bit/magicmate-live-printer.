import express from 'express';
import http from 'node:http';
import { randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { Server } from 'socket.io';
import { TikTokLiveConnection, WebcastEvent } from 'tiktok-live-connector';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();
const server = http.createServer(app);
const io = new Server(server);
const port = Number(process.env.PORT) || 3000;
const username = (process.env.TIKTOK_USERNAME || 'tshepothemagician').replace(/^@/, '');
const eventHistory = [];
const historyLimit = 100;
let liveStatus = { state: 'starting', message: `Connecting to @${username}` };
let connection;
let retryTimer;
let retryDelay = 5000;

app.get('/health', (_req, res) => {
  res.json({ ok: true, tiktok: liveStatus.state, username });
});
app.use(express.static(path.join(__dirname, 'public')));
app.get('*', (_req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

function setStatus(state, message) {
  liveStatus = { state, message, username };
  io.emit('live:status', liveStatus);
  console.log(`[TikTok LIVE] ${state}: ${message}`);
}

function getAvatar(user) {
  const picture = user?.profilePictureUrl ?? user?.avatarThumb ?? user?.avatarMedium ?? user?.avatar;
  if (typeof picture === 'string') return picture;
  if (picture && typeof picture === 'object') {
    return picture.urlList?.[0] ?? picture.url_list?.[0] ?? '';
  }
  return '';
}

function publishEvent(type, data) {
  const user = data?.user ?? {};
  const displayName = user.nickname || user.displayName || user.uniqueId || 'TikTok viewer';
  let detail;
  if (type === 'comment') detail = String(data?.comment ?? data?.text ?? '').trim() || 'Sent a comment';
  else if (type === 'gift') detail = String(data?.giftName ?? data?.giftDetails?.giftName ?? data?.gift?.name ?? 'Gift');
  else detail = 'Joined the LIVE';

  const event = {
    id: randomUUID(),
    type,
    username: user.uniqueId ? `@${String(user.uniqueId).replace(/^@/, '')}` : displayName,
    displayName,
    avatar: getAvatar(user),
    detail,
    timestamp: new Date().toISOString()
  };
  eventHistory.unshift(event);
  if (eventHistory.length > historyLimit) eventHistory.length = historyLimit;
  io.emit('live:event', event);
}

function scheduleReconnect() {
  if (retryTimer) return;
  const delay = retryDelay;
  retryDelay = Math.min(retryDelay * 2, 60000);
  retryTimer = setTimeout(() => {
    retryTimer = undefined;
    startTikTokConnection();
  }, delay);
  retryTimer.unref?.();
}

function startTikTokConnection() {
  if (connection) {
    try { connection.disconnect(); } catch { /* The connection may already be closed. */ }
  }
  setStatus('connecting', `Connecting to @${username}`);
  connection = new TikTokLiveConnection(username, {
    processInitialData: false,
    enableExtendedGiftInfo: true
  });

  connection.on(WebcastEvent.CHAT, (data) => publishEvent('comment', data));
  connection.on(WebcastEvent.MEMBER, (data) => publishEvent('join', data));
  connection.on(WebcastEvent.GIFT, (data) => publishEvent('gift', data));
  connection.on(WebcastEvent.DISCONNECTED, () => {
    setStatus('disconnected', `Disconnected from @${username}; retrying`);
    scheduleReconnect();
  });

  connection.connect()
    .then(() => {
      retryDelay = 5000;
      setStatus('connected', `Connected to @${username}`);
    })
    .catch((error) => {
      setStatus('disconnected', error?.message || `Could not connect to @${username}; retrying`);
      scheduleReconnect();
    });
}

io.on('connection', (socket) => {
  socket.emit('live:status', liveStatus);
  socket.emit('live:history', eventHistory);
});

server.listen(port, '0.0.0.0', () => {
  console.log(`MagicMate LIVE Printer listening on port ${port}`);
  startTikTokConnection();
});

function shutdown() {
  clearTimeout(retryTimer);
  try { connection?.disconnect(); } catch { /* Ignore shutdown disconnect errors. */ }
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 5000).unref();
}
process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
