import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://backend:8080';
const facilityId = __ENV.FACILITY_ID || 'd936bb7d-3027-47aa-a47b-d04a37e07310';
const virtualUsers = Number.parseInt(__ENV.LOAD_VUS || '10', 10);
const duration = __ENV.LOAD_DURATION || '20s';

export const options = {
  scenarios: {
    occupancy_reads: {
      executor: 'constant-vus',
      vus: virtualUsers,
      duration,
      gracefulStop: '5s',
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
    iterations: ['count>=100'],
  },
  noConnectionReuse: false,
  userAgent: 'smart-parking-load-test/1.0',
};

export default function () {
  const response = http.get(
    `${baseUrl}/api/v1/facilities/${facilityId}/occupancy`,
    { tags: { operation: 'occupancy_snapshot' } },
  );

  check(response, {
    'occupancy returns 200': (result) => result.status === 200,
    'reference capacity remains consistent': (result) => {
      if (result.status !== 200) {
        return false;
      }
      const payload = result.json();
      return payload.totalSpaces === 7200
        && payload.operationalSpaces === 7200
        && Array.isArray(payload.floors)
        && payload.floors.length === 6;
    },
  });

  sleep(0.1);
}
