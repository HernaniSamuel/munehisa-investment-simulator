import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from '../config/identifiers.js';

function jsonHeaders(token) {
    const headers = { 'Content-Type': 'application/json' };
    if (token) {
        headers.Authorization = `Bearer ${token}`;
    }
    return headers;
}

// checks are informational (pass/fail counters in k6's summary) and never fail the k6
// process itself — this suite defines no thresholds, by design (see README).
function checkOk(res, tags) {
    check(res, { 'status is 2xx': (r) => r.status >= 200 && r.status < 300 }, tags);
    return res;
}

export function authedGet(path, token, tags) {
    const res = http.get(`${BASE_URL}${path}`, { headers: jsonHeaders(token), tags });
    return checkOk(res, tags);
}

export function authedPost(path, body, token, tags) {
    const res = http.post(`${BASE_URL}${path}`, JSON.stringify(body), {
        headers: jsonHeaders(token),
        tags,
    });
    return checkOk(res, tags);
}
