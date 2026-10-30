#!/usr/bin/env node
import { performance } from 'node:perf_hooks';

const baseUrl = new URL(process.env.TARGET_URL ?? 'http://localhost:18080');
const vus = Number(process.env.VUS ?? 10);
const durationSeconds = Number(process.env.DURATION_SECONDS ?? 20);
const userCount = Number(process.env.LOAD_TEST_USERS ?? 1000);
const password = 'LoadTest-Only-2026!';

if (!['localhost', '127.0.0.1', '::1'].includes(baseUrl.hostname)) {
  throw new Error('This runner is restricted to localhost targets.');
}
if (!Number.isInteger(vus) || vus < 1 || vus > 100) throw new Error('VUS must be between 1 and 100 for this local runner.');
if (!Number.isInteger(durationSeconds) || durationSeconds < 1 || durationSeconds > 300) throw new Error('DURATION_SECONDS must be between 1 and 300.');
if (!Number.isInteger(userCount) || userCount < vus) throw new Error('LOAD_TEST_USERS must be at least VUS.');

const results = [];
let failures = 0;
const authLatencies = [];
let stopAt;

async function request(path, init) {
  const start = performance.now();
  try {
    const response = await fetch(new URL(path, baseUrl), { ...init, signal: AbortSignal.timeout(30000) });
    const body = await response.arrayBuffer();
    const bytes = body.byteLength;
    if (!response.ok) failures++;
    results.push({ latency: performance.now() - start, status: response.status, bytes });
  } catch {
    failures++;
    results.push({ latency: performance.now() - start, status: 0, bytes: 0 });
  }
}

async function authenticate(index) {
  const email = `load-user-${String(index + 1).padStart(9, '0')}@shopsphere.test`;
  const start = performance.now();
  const auth = await fetch(new URL('/api/auth/login', baseUrl), {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email, password }),
    signal: AbortSignal.timeout(60000),
  });
  if (!auth.ok) throw new Error(`Load-test user login failed with HTTP ${auth.status}; confirm seeded users are available.`);
  const { accessToken } = await auth.json();
  authLatencies.push(performance.now() - start);
  return { authorization: `Bearer ${accessToken}` };
}

async function worker(index, headers) {
  while (performance.now() < stopAt) {
    await request('/api/products/catalog?page=0&size=48', { headers });
    await request(`/api/products/${5000002 + (index % Math.max(1, userCount))}`, { headers });
  }
}

const headers = await Promise.all(Array.from({ length: vus }, (_, index) => authenticate(index)));
stopAt = performance.now() + durationSeconds * 1000;
await Promise.all(headers.map((tokenHeaders, index) => worker(index, tokenHeaders)));
const latencies = results.map((item) => item.latency).sort((a, b) => a - b);
authLatencies.sort((a, b) => a - b);
const percentile = (p) => latencies[Math.min(latencies.length - 1, Math.ceil(latencies.length * p) - 1)] ?? 0;
const authP95 = authLatencies[Math.min(authLatencies.length - 1, Math.ceil(authLatencies.length * 0.95) - 1)] ?? 0;
const elapsedSeconds = durationSeconds;
console.log(JSON.stringify({
  target: baseUrl.origin,
  virtualUsers: vus,
  measuredDurationSeconds: elapsedSeconds,
  requests: results.length,
  failures,
  requestsPerSecond: Number((results.length / elapsedSeconds).toFixed(2)),
  latencyMs: { p50: Number(percentile(0.5).toFixed(1)), p95: Number(percentile(0.95).toFixed(1)), p99: Number(percentile(0.99).toFixed(1)) },
  loginLatencyMs: { p95: Number(authP95.toFixed(1)) },
  statusCounts: Object.fromEntries([...new Set(results.map((item) => item.status))].map((status) => [status, results.filter((item) => item.status === status).length])),
  responseBytes: results.reduce((sum, item) => sum + item.bytes, 0),
  note: 'Local smoke/load signal only; not evidence of millions of concurrent users.'
}, null, 2));
