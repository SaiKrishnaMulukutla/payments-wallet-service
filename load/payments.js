import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const PAYERS = Array.from(
  { length: 10 },
  (_, i) => `aaaaaaaa-0000-0000-0000-0000000000${String(i + 1).padStart(2, '0')}`,
);
const PAYEE = 'bbbbbbbb-0000-0000-0000-000000000001';

export const options = {
  scenarios: {
    payments: {
      executor: 'ramping-vus',
      startVUs: 5,
      stages: [
        { duration: '15s', target: 20 },
        { duration: '30s', target: 20 },
        { duration: '5s', target: 0 },
      ],
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(99)<2000'],
  },
};

export default function () {
  const payer = PAYERS[Math.floor(Math.random() * PAYERS.length)];
  const body = JSON.stringify({
    merchantId: 'load',
    payerAccountId: payer,
    payeeAccountId: PAYEE,
    amount: 1,
    currency: 'INR',
  });
  const res = http.post(`${BASE}/v1/payments`, body, {
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': `${__VU}-${__ITER}` },
  });
  check(res, { 'status is 201': (r) => r.status === 201 });
}
