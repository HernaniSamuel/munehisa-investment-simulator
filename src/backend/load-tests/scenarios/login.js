// Measures POST /auth/login itself. Deliberately ignores the shared token main.js#setup()
// produces for the other scenarios — every iteration performs a real login with the fixed
// LOAD_TEST_USER credentials. Repeating the *correct* password is safe: this backend's
// account lockout (AccountLockoutService) only counts failed attempts, never successful ones.
import { check } from 'k6';
import { authedPost } from '../lib/http.js';
import { LOAD_TEST_USER } from '../config/identifiers.js';

export function loginScenario() {
    const res = authedPost('/auth/login', LOAD_TEST_USER, null, { scenario: 'login' });
    check(res, { 'login returned a token': (r) => Boolean(r.json('token')) }, { scenario: 'login' });
}
