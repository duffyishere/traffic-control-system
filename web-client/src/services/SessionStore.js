const TOKEN_KEY = "turnstileToken";
const BASE_URL_KEY = "gatewayBaseUrl";

export class SessionStore {
  constructor(storage) {
    this.storage = storage;
  }

  getAccessToken() {
    return this.storage.getItem(TOKEN_KEY);
  }

  setAccessToken(token) {
    this.storage.setItem(TOKEN_KEY, token);
  }

  clearAccessToken() {
    this.storage.removeItem(TOKEN_KEY);
  }

  getBaseUrl() {
    return this.storage.getItem(BASE_URL_KEY);
  }

  setBaseUrl(baseUrl) {
    this.storage.setItem(BASE_URL_KEY, baseUrl);
  }
}

export const sessionStore = new SessionStore(window.sessionStorage);
