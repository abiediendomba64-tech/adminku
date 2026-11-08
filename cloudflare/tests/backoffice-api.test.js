import test from "node:test";
import assert from "node:assert/strict";
import {
  ApiError,
  authenticateTelegramInitData,
  handleBackofficeApi,
  __test
} from "../backoffice-api.js";

const OFFICIAL_TEST_TOKEN = "5768337691:AAH5YkoiEuPk8-FZa32hStHTqXiLPtAEhx8";
const OFFICIAL_INIT_DATA = "query_id=AAHdF6IQAAAAAN0XohDhrOrc&user=%7B%22id%22%3A279058397%2C%22first_name%22%3A%22Vladislav%22%2C%22last_name%22%3A%22Kibenko%22%2C%22username%22%3A%22vdkfrost%22%2C%22language_code%22%3A%22ru%22%2C%22is_premium%22%3Atrue%7D&auth_date=1662771648&hash=c501b71e775f74ce10e377dea85a7ea24ecd640b223ea86dfe453e0eaed2e2b2";

test("validates Telegram Mini App initData using official HMAC example", async () => {
  const user = await authenticateTelegramInitData(OFFICIAL_INIT_DATA, { TELEGRAM_BOT_TOKEN: OFFICIAL_TEST_TOKEN }, 1662771648);
  assert.equal(user.telegramUserId, "279058397");
  assert.equal(user.username, "vdkfrost");
});

test("rejects tampered Telegram initData", async () => {
  const tampered = OFFICIAL_INIT_DATA.replace("Vladislav", "Mallory");
  await assert.rejects(
    authenticateTelegramInitData(tampered, { TELEGRAM_BOT_TOKEN: OFFICIAL_TEST_TOKEN }, 1662771648),
    error => error instanceof ApiError && error.status === 401 && error.code === "INVALID_INIT_DATA"
  );
});

test("rejects expired initData and duplicate fields", async () => {
  await assert.rejects(
    authenticateTelegramInitData(OFFICIAL_INIT_DATA, { TELEGRAM_BOT_TOKEN: OFFICIAL_TEST_TOKEN }, 1662776000),
    error => error instanceof ApiError && error.code === "INIT_DATA_EXPIRED"
  );
  const duplicate = OFFICIAL_INIT_DATA.replace("auth_date=1662771648", "auth_date=1662771648&auth_date=1662771648");
  await assert.rejects(
    authenticateTelegramInitData(duplicate, { TELEGRAM_BOT_TOKEN: OFFICIAL_TEST_TOKEN }, 1662771648),
    error => error instanceof ApiError && error.code === "INVALID_INIT_DATA"
  );
});

test("rejects missing bot token and malformed user identity", async () => {
  await assert.rejects(authenticateTelegramInitData(OFFICIAL_INIT_DATA, {}, 1662771648), error => error.code === "AUTH_NOT_CONFIGURED");
});

test("origin parser only accepts bare origins", () => {
  assert.deepEqual(__test.parseAllowedOrigins("https://panel.example, https://preview.example/"), ["https://panel.example", "https://preview.example"]);
  assert.deepEqual(__test.parseAllowedOrigins("https://panel.example/path,not-a-url"), []);
});

test("domain and target path validation rejects unsafe input", () => {
  assert.equal(__test.normalizeDomain("Example.COM."), "example.com");
  assert.throws(() => __test.normalizeDomain("https://example.com"), ApiError);
  assert.equal(__test.validateTargetPath("/backoffice/settings"), "/backoffice/settings");
  for (const value of ["//evil.example/path", "https://evil.example", "/../admin", "/a/../b", "/%2e%2e/admin", "/%252e%252e/admin", "/%255c%255cevil", "/path?redirect=x", "/path\\evil"]) {
    assert.throws(() => __test.validateTargetPath(value), ApiError, value);
  }
});

test("validates GSC property boundaries correctly", () => {
  const property = __test.normalizeGscSiteProperty("sc-domain:example.com");
  assert.equal(property, "sc-domain:example.com");
  assert.equal(__test.isUrlWithinGscProperty("https://sub.example.com/page", property), true);
  assert.equal(__test.isUrlWithinGscProperty("https://example.com.evil.test/page", property), false);
  assert.equal(__test.isUrlWithinGscProperty("https://example.com/page", "https://example.com/docs/"), false);
});

test("API fails closed when BACKOFFICE_ORIGIN is not configured", async () => {
  const response = await handleBackofficeApi(new Request("https://worker.example/api/v1/auth/me", { headers: { Origin: "https://panel.example" } }), {});
  assert.equal(response.status, 503);
  assert.equal((await response.json()).code, "API_NOT_CONFIGURED");
});

test("API denies unexpected browser origins before touching D1", async () => {
  const env = { BACKOFFICE_ORIGIN: "https://panel.example" };
  const response = await handleBackofficeApi(new Request("https://worker.example/api/v1/auth/me", { headers: { Origin: "https://evil.example" } }), env);
  assert.equal(response.status, 403);
  assert.equal((await response.json()).code, "CORS_DENIED");
});

test("preflight accepts only the configured origin", async () => {
  const env = { BACKOFFICE_ORIGIN: "https://panel.example" };
  const accepted = await handleBackofficeApi(new Request("https://worker.example/api/v1/tickets", { method: "OPTIONS", headers: { Origin: "https://panel.example" } }), env);
  assert.equal(accepted.status, 204);
  assert.equal(accepted.headers.get("Access-Control-Allow-Origin"), "https://panel.example");
  const denied = await handleBackofficeApi(new Request("https://worker.example/api/v1/tickets", { method: "OPTIONS", headers: { Origin: "https://evil.example" } }), env);
  assert.equal(denied.status, 403);
});

test("extracts Google verification meta tag independent of attribute order and quote style", () => {
  assert.equal(__test.extractMetaVerificationTag('<META content="token-123" name="google-site-verification">'), "token-123");
  assert.equal(__test.extractMetaVerificationTag("<meta name='google-site-verification' content='another-token'>"), "another-token");
  assert.equal(__test.extractMetaVerificationTag('<meta name="description" content="not a verification token">'), "");
});

async function makeSignedInitData(botToken, telegramUserId) {
  const params = new URLSearchParams({
    auth_date: String(Math.floor(Date.now() / 1000)),
    query_id: "unit-test-query",
    user: JSON.stringify({ id: Number(telegramUserId), first_name: "Unit Test", username: "unit_test" })
  });
  const dataCheckString = [...params.entries()].map(([key, value]) => `${key}=${value}`).sort().join("\n");
  const encoder = new TextEncoder();
  const webAppKey = await crypto.subtle.importKey("raw", encoder.encode("WebAppData"), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const secretBytes = await crypto.subtle.sign("HMAC", webAppKey, encoder.encode(botToken));
  const checkKey = await crypto.subtle.importKey("raw", secretBytes, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("HMAC", checkKey, encoder.encode(dataCheckString));
  params.set("hash", [...new Uint8Array(signature)].map(byte => byte.toString(16).padStart(2, "0")).join(""));
  return params.toString();
}

test("runtime allowlisted Telegram admin authenticates without a users row", async () => {
  const initData = await makeSignedInitData(OFFICIAL_TEST_TOKEN, "987654321");
  const env = {
    BACKOFFICE_ORIGIN: "https://panel.example",
    TELEGRAM_BOT_TOKEN: OFFICIAL_TEST_TOKEN,
    TELEGRAM_ADMIN_ID: "987654321",
    DB: { prepare() { throw new Error("allowlisted admin should not require a users lookup"); } }
  };
  const response = await handleBackofficeApi(new Request("https://worker.example/api/v1/auth/me", {
    headers: { Origin: "https://panel.example", "X-Telegram-Init-Data": initData }
  }), env);
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.user.role, "ADMIN");
  assert.equal(body.auth.verified, true);
});

test("a D1 row cannot grant ADMIN to a non-allowlisted Telegram ID", async () => {
  const initData = await makeSignedInitData(OFFICIAL_TEST_TOKEN, "987654322");
  const env = {
    BACKOFFICE_ORIGIN: "https://panel.example",
    TELEGRAM_BOT_TOKEN: OFFICIAL_TEST_TOKEN,
    TELEGRAM_ADMIN_ID: "987654321",
    DB: {
      prepare() {
        return { bind() { return { first: async () => ({ id: 1, telegramUserId: "987654322", role: "ADMIN", isActive: 1 }) }; } };
      }
    }
  };
  const response = await handleBackofficeApi(new Request("https://worker.example/api/v1/auth/me", {
    headers: { Origin: "https://panel.example", "X-Telegram-Init-Data": initData }
  }), env);
  assert.equal(response.status, 403);
  assert.equal((await response.json()).code, "ADMIN_NOT_ALLOWLISTED");
});

test("MEMBER role cannot read the audit log", async () => {
  const initData = await makeSignedInitData(OFFICIAL_TEST_TOKEN, "987654323");
  const env = {
    BACKOFFICE_ORIGIN: "https://panel.example",
    TELEGRAM_BOT_TOKEN: OFFICIAL_TEST_TOKEN,
    TELEGRAM_ADMIN_ID: "987654321",
    DB: {
      prepare(sql) {
        assert.match(sql, /FROM users WHERE telegramUserId/);
        return { bind() { return { first: async () => ({ id: 3, telegramUserId: "987654323", role: "MEMBER", isActive: 1 }) }; } };
      }
    }
  };
  const response = await handleBackofficeApi(new Request("https://worker.example/api/v1/audit", {
    headers: { Origin: "https://panel.example", "X-Telegram-Init-Data": initData }
  }), env);
  assert.equal(response.status, 403);
  assert.equal((await response.json()).code, "FORBIDDEN");
});

test("domain verification only activates after exact Google meta-tag match", async () => {
  const initData = await makeSignedInitData(OFFICIAL_TEST_TOKEN, "987654321");
  const statements = [];
  const db = {
    prepare(sql) {
      return {
        sql,
        bind(...args) {
          return { sql, args, first: async () => ({
            id: 7,
            domainName: "example.com",
            targetPath: "/",
            verificationTag: "expected-token-123",
            status: "PENDING_DNS"
          }) };
        }
      };
    },
    async batch(items) {
      statements.push(...items);
      return [{ meta: { changes: 1 } }, { meta: { changes: 1 } }];
    }
  };
  const oldFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(
    '<html><head><meta content="expected-token-123" name="google-site-verification"></head></html>',
    { status: 200, headers: { "Content-Type": "text/html" } }
  );
  try {
    const response = await handleBackofficeApi(new Request("https://worker.example/api/v1/domains/7/verify", {
      method: "POST",
      headers: { Origin: "https://panel.example", "X-Telegram-Init-Data": initData }
    }), {
      BACKOFFICE_ORIGIN: "https://panel.example",
      TELEGRAM_BOT_TOKEN: OFFICIAL_TEST_TOKEN,
      TELEGRAM_ADMIN_ID: "987654321",
      DB: db
    });
    assert.equal(response.status, 200);
    const data = (await response.json()).data;
    assert.equal(data.verified, true);
    assert.equal(data.status, "ACTIVE");
    assert.equal(data.httpStatus, 200);
    assert.match(statements[0].sql, /UPDATE domain_requests SET status='ACTIVE'/);
    assert.match(statements[1].sql, /INSERT INTO audit_logs/);
  } finally {
    globalThis.fetch = oldFetch;
  }
});
