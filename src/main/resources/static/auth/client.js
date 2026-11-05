/*
 * FILE: src/main/resources/static/auth/client.js
 * PURPOSE: Perform authorization-code S256 PKCE without exposing a client secret or persisting tokens.
 * OWNER: Platform & Operations
 * SINCE: week-31
 * RELATED: infra/keycloak/fraud-realm.json
 * NOTES: The API verifies access JWTs. Unverified ID-token claims are never used for authorization.
 */
(async () => {
  "use strict";
  const status = document.getElementById("status");
  const config = await fetch("/auth/config").then((response) =>
    response.json(),
  );
  const redirect = location.origin + "/auth/index.html";
  const b64 = (bytes) =>
    btoa(String.fromCharCode(...bytes))
      .replaceAll("+", "-")
      .replaceAll("/", "_")
      .replaceAll("=", "");
  const random = () => b64(crypto.getRandomValues(new Uint8Array(32)));
  let accessToken;
  async function login(level) {
    const verifier = random(),
      state = random();
    sessionStorage.setItem(
      "fraud.pkce",
      JSON.stringify({ verifier, state, started: Date.now() }),
    );
    const challenge = b64(
      new Uint8Array(
        await crypto.subtle.digest(
          "SHA-256",
          new TextEncoder().encode(verifier),
        ),
      ),
    );
    const auth = new URL(config.issuer + "/protocol/openid-connect/auth");
    auth.search = new URLSearchParams({
      client_id: config.clientId,
      redirect_uri: redirect,
      response_type: "code",
      scope: "openid",
      state,
      code_challenge: challenge,
      code_challenge_method: "S256",
      acr_values: level,
      prompt: "login",
      max_age: "0",
    });
    location.assign(auth);
  }
  document.getElementById("login").onclick = () => login("1");
  document.getElementById("stepup").onclick = () => login("2");
  document.getElementById("forget").onclick = () => {
    accessToken = undefined;
    sessionStorage.removeItem("fraud.pkce");
    status.textContent = "Signed out locally";
  };
  const params = new URLSearchParams(location.search);
  if (params.has("code") || params.has("error")) {
    const saved = JSON.parse(sessionStorage.getItem("fraud.pkce") || "null");
    sessionStorage.removeItem("fraud.pkce");
    history.replaceState(null, "", redirect);
    if (
      !saved ||
      params.get("state") !== saved.state ||
      Date.now() - saved.started > 300000 ||
      params.has("error")
    ) {
      status.textContent = "Sign-in failed; start again.";
      return;
    }
    const exchange = await fetch(
      config.issuer + "/protocol/openid-connect/token",
      {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          grant_type: "authorization_code",
          client_id: config.clientId,
          redirect_uri: redirect,
          code: params.get("code"),
          code_verifier: saved.verifier,
        }),
      },
    );
    if (!exchange.ok) {
      status.textContent = "Token exchange rejected";
      return;
    }
    accessToken = (await exchange.json()).access_token;
    const me = await fetch("/v1/security/me", {
      headers: { Authorization: "Bearer " + accessToken },
    });
    status.textContent = me.ok
      ? JSON.stringify(await me.json(), null, 2)
      : "Identity is not linked to an application subject";
  }
})().catch(() => {
  document.getElementById("status").textContent = "Sign-in unavailable";
});
