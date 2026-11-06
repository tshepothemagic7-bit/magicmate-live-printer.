import express from 'express';
import http from 'node:http';
import { randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { setTimeout as delay } from 'node:timers/promises';
import { Server } from 'socket.io';
import { TikLive } from 'tiklive/client';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const app = express();
const server = http.createServer(app);
const io = new Server(server);
const port = Number(process.env.PORT) || 3000;
const username = (process.env.TIKTOK_USERNAME || 'tshepothemagician').replace(/^@/, '');
const connectorPort = Number(process.env.TIKLIVE_PORT) || 8765;
const history = [];
const historyLimit = 100;
let liveStatus = { state: 'starting', message: 'Starting the self-hosted TikTok connector' };
let connectorProcess;
let liveClient;
let stopping = false;

app.get('/health', (_req, res) => {
  res.json({ ok: true, tiktok: liveStatus.state, username, connector: 'tiklive' });
});
app.use(express.static(path.join(__dirname, 'public')));
app.get('*', (_req, res) => res.sendFile(path.join(__dirname, 'public', 'index.html')));

function setStatus(state, message) {
  liveStatus = { state, message, username };
  io.emit('live:status', liveStatus);
  console.log('[TikTok LIVE]', state, message);
}

function publishEvent(type, data) {
  const user = data?.user ?? {};
  const displayName = user.nickname || user.displayName || user.uniqueId || 'TikTok viewer';
  let detail = 'Joined the LIVE';
  if (type === 'comment') detail = String(data?.comment ?? '').trim() || 'Sent a comment';
  if (type === 'gift') detail = String(data?.giftName || 'Gift') + (data?.repeatCount > 1 ? ' × ' + data.repeatCount : '');
  const event = {
    id: String(data?.msgId || randomUUID()),
    type,
    username: user.uniqueId ? '@' + String(user.uniqueId).replace(/^@/, '') : displayName,
    displayName,
    avatar: user.profilePictureUrl || '',
    detail,
    timestamp: new Date(data?.timestamp || Date.now()).toISOString()
  };
  history.unshift(event);
  if (history.length > historyLimit) history.length = historyLimit;
  io.emit('live:event', event);
}

function startConnectorProcess() {
  if (connectorProcess || stopping) return;
  setStatus('connecting', 'Starting TikLive connector for @' + username);
  connectorProcess = spawn('npx', [
    '--yes', 'tiklive',
    '--port', String(connectorPort),
    '--host', '127.0.0.1',
    '--watch', username
  ], { stdio: ['ignore', 'inherit', 'inherit'] });

  connectorProcess.on('error', (error) => {
    setStatus('disconnected', 'Could not start TikLive: ' + error.message);
    connectorProcess = undefined;
  });
  connectorProcess.on('exit', (code) => {
    connectorProcess = undefined;
    if (!stopping) {
      setStatus('disconnected', 'TikLive connector exited (' + code + '); restarting shortly');
      setTimeout(startConnectorProcess, 10000).unref?.();
    }
  });
}

async function connectEventClient() {
  const client = new TikLive({
    username,
    server: 'ws://127.0.0.1:' + connectorPort + '/ws',
    reconnect: true,
    replay: true
  });
  liveClient = client;
  client.on('status', (status) => {
    const state = String(status?.state || 'connecting').toLowerCase();
    const message = status?.message || status?.reason || ('TikLive status: ' + state);
    setStatus(state === 'connected' ? 'connected' : state === 'offline' ? 'offline' : state, String(message));
  });
  client.on('open', () => setStatus('connected', 'Connected to TikLive; watching @' + username));
  client.on('error', (error) => setStatus('disconnected', error?.message || 'TikLive connection error'));
  client.on('close', () => setStatus('disconnected', 'TikLive event socket closed; reconnecting'));
  client.on('join', (event) => publishEvent('join', event));
  client.on('chat', (event) => publishEvent('comment', event));
  client.on('gift', (event) => {
    if (event?.streaking) return;
    publishEvent('gift', event);
  });
  client.on('streamEnd', () => setStatus('offline', '@' + username + ' ended the LIVE'));
}

async function bootTikLive() {
  startConnectorProcess();
  // The CLI starts a local-only event server. Wait for it to accept connections.
  for (let attempt = 0; attempt < 30 && !stopping; attempt++) {
    try {
      const response = await fetch('http://127.0.0.1:' + connectorPort + '/api/health');
      if (response.ok) {
        await connectEventClient();
        return;
      }
    } catch { /* The child server is still starting. */ }
    await delay(1000);
  }
  setStatus('disconnected', 'TikLive did not start in time; check Render logs');
}

io.on('connection', (socket) => {
  socket.emit('live:status', liveStatus);
  socket.emit('live:history', history);
});

server.listen(port, '0.0.0.0', () => {
  console.log('MagicMate LIVE Printer listening on port ' + port);
  bootTikLive().catch((error) => setStatus('disconnected', error.message));
});

function shutdown() {
  stopping = true;
  try { liveClient?.close?.(); } catch { /* Ignore shutdown errors. */ }
  if (connectorProcess && !connectorProcess.killed) connectorProcess.kill('SIGTERM');
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 5000).unref();
}
process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
