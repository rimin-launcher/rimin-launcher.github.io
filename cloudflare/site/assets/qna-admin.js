// Owner page. Sign-in uses a passkey (fingerprint, face or screen lock) registered on this device; the owner key is
// only needed to register the first device or as a recovery. The session token is kept in this browser.
(() => {
  const $ = id => document.getElementById(id);
  const STORE = "lumi-qna-owner";
  let token = ""; try { token = localStorage.getItem(STORE) || ""; } catch {}
  let filter = "pending";
  const date = t => new Date(t).toLocaleString("ko-KR", { dateStyle: "medium", timeStyle: "short" });
  const el = (tag, cls, text) => { const e = document.createElement(tag); if (cls) e.className = cls; if (text !== undefined) e.textContent = text; return e; };
  const b64u = buf => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  const unb64u = s => { s = s.replace(/-/g, "+").replace(/_/g, "/"); return Uint8Array.from(atob(s + "=".repeat((4 - s.length % 4) % 4)), c => c.charCodeAt(0)); };
  const supported = !!(window.PublicKeyCredential && navigator.credentials && window.AuthenticatorAttestationResponse
    && AuthenticatorAttestationResponse.prototype.getPublicKey);
  const loginStatus = t => { $("login-status").textContent = t; };

  function keep(t) { token = t; try { t ? localStorage.setItem(STORE, t) : localStorage.removeItem(STORE); } catch {} }

  async function post(path, body, auth) {
    const r = await fetch(path, { method: "POST", headers: { "content-type": "application/json", ...(auth ? { authorization: "Bearer " + auth } : {}) },
      body: JSON.stringify(body || {}) });
    let data = {}; try { data = await r.json(); } catch {}
    if (!r.ok) { const e = new Error(data.error || "연결에 실패했어요"); e.status = r.status; throw e; }
    return data;
  }
  async function call(method, query, body) {
    const r = await fetch("/api/qna/admin" + (query || ""), { method, headers: { authorization: "Bearer " + token,
      ...(body ? { "content-type": "application/json" } : {}) }, body: body ? JSON.stringify(body) : undefined });
    let data = {}; try { data = await r.json(); } catch {}
    if (r.status === 401) { signedOut("로그인이 끝났어요. 다시 들어가 주세요."); throw new Error(data.error); }
    if (!r.ok) throw new Error(data.error || "연결에 실패했어요");
    return data;
  }
  const cancelled = e => e && (e.name === "NotAllowedError" || e.name === "AbortError");

  async function signIn() {
    if (!supported) { loginStatus("이 브라우저는 지문·얼굴 로그인을 지원하지 않아요. Chrome·삼성 인터넷·Safari 최신 버전에서 열거나 「키로만 들어가기」를 쓰세요."); return; }
    $("signin").disabled = true; loginStatus("지문이나 얼굴을 확인해 주세요…");
    try {
      const o = await post("/api/qna/auth/login-options");
      const cred = await navigator.credentials.get({ publicKey: { challenge: unb64u(o.challenge), rpId: o.rpId, timeout: 60000,
        userVerification: "required", allowCredentials: o.allow.map(id => ({ type: "public-key", id: unb64u(id) })) } });
      const r = cred.response;
      const res = await post("/api/qna/auth/login", { id: cred.id, clientDataJSON: b64u(r.clientDataJSON),
        authenticatorData: b64u(r.authenticatorData), signature: b64u(r.signature) });
      keep(res.token); loginStatus(""); refresh();
    } catch (e) {
      loginStatus(cancelled(e) ? "취소했어요. 이 기기가 등록되지 않았다면 아래 「처음이거나 새 기기예요」를 눌러 주세요." : e.message);
      if (e.status === 404) $("first").open = true;
    }
    $("signin").disabled = false;
  }

  // Registers this device. `auth` is the owner key or a signed-in session.
  async function register(auth) {
    if (!supported) throw new Error("이 브라우저는 지문·얼굴 로그인을 지원하지 않아요");
    const o = await post("/api/qna/auth/register-options", {}, auth);
    const cred = await navigator.credentials.create({ publicKey: {
      challenge: unb64u(o.challenge), rp: { id: o.rpId, name: "Lumi 답변 관리" },
      user: { id: unb64u(o.userId), name: "Lumi 주인", displayName: "Lumi 주인" },
      pubKeyCredParams: [{ type: "public-key", alg: -7 }, { type: "public-key", alg: -8 }, { type: "public-key", alg: -257 }],
      authenticatorSelection: { residentKey: "preferred", userVerification: "required" },
      excludeCredentials: o.exclude.map(id => ({ type: "public-key", id: unb64u(id) })), attestation: "none", timeout: 60000 } });
    const r = cred.response, key = r.getPublicKey();
    if (!key) throw new Error("이 기기는 지원하지 않는 방식이에요");
    return post("/api/qna/auth/register", { id: cred.id, alg: r.getPublicKeyAlgorithm(), publicKey: b64u(key),
      clientDataJSON: b64u(r.clientDataJSON), authenticatorData: b64u(r.getAuthenticatorData()) }, auth);
  }

  $("keyform").addEventListener("submit", async e => {
    e.preventDefault();
    const key = $("keyform").key.value.trim(), mode = (e.submitter && e.submitter.dataset.mode) || "register";
    if (!key) { loginStatus("주인 키를 붙여 넣어 주세요."); return; }
    loginStatus(mode === "register" ? "지문이나 얼굴을 등록해 주세요…" : "확인 중…");
    try {
      if (mode === "register") {
        const res = await register(key);
        keep(res.token); $("keyform").key.value = ""; $("first").open = false;
        loginStatus(""); $("panel-status").textContent = "이 기기(" + res.device + ")를 등록했어요. 다음부터는 지문·얼굴로 들어가요.";
        refresh();
      } else {
        keep(key); $("keyform").key.value = ""; await refresh();
        if (token) { try { localStorage.removeItem(STORE); } catch {} } // a raw key is never remembered
      }
    } catch (err) {
      if (mode === "key") keep("");
      loginStatus(err.name === "InvalidStateError" ? "이 기기는 이미 등록돼 있어요. 「지문·얼굴로 들어가기」를 눌러 주세요."
        : cancelled(err) ? "등록을 취소했어요." : err.message);
    }
  });

  function item(q) {
    const c = el("article", "qna-card");
    const meta = el("p", "qna-meta");
    meta.append(el("span", "", "#" + q.id + " · " + (q.name || "익명")), el("span", "", date(q.created)));
    if (q.private) meta.append(el("span", "qna-badge", "비공개"));
    if (q.hidden) meta.append(el("span", "qna-badge", "숨김"));
    if (q.answered) meta.append(el("span", "qna-badge is-done", "답변 " + date(q.answered)));
    const text = el("textarea", "qna-answer"); text.rows = 4; text.maxLength = 4000; text.value = q.answer || ""; text.placeholder = "답변을 쓰세요";
    text.setAttribute("aria-label", "#" + q.id + " 답변");
    const row = el("div", "qna-actions");
    const btn = (label, action, cls, confirmText) => {
      const b = el("button", "btn " + (cls || "btn-ghost"), label); b.type = "button";
      b.addEventListener("click", async () => {
        if (confirmText && !confirm(confirmText)) return;
        b.disabled = true;
        try { await call("POST", "", { id: q.id, action, answer: text.value }); $("panel-status").textContent = "#" + q.id + " " + label + " 완료"; refresh(); }
        catch (e) { $("panel-status").textContent = e.message; b.disabled = false; }
      });
      return b;
    };
    row.append(btn(q.answered ? "답변 수정" : "답변 올리기", "answer", "btn-primary"));
    if (q.answered) row.append(btn("답변 내리기", "unanswer"));
    row.append(q.private ? btn("공개로 바꾸기", "public") : btn("비공개로 바꾸기", "private"));
    row.append(q.hidden ? btn("숨김 풀기", "show") : btn("숨기기", "hide"));
    row.append(btn("삭제", "delete", "btn-ghost qna-danger", "#" + q.id + " 질문을 완전히 지울까요? 되돌릴 수 없어요."));
    c.append(meta, el("p", "qna-q", q.body), text, row);
    return c;
  }

  async function devices(box) {
    const data = await post("/api/qna/auth/devices", {}, token);
    const add = el("button", "btn btn-primary", "이 기기 추가 등록"); add.type = "button";
    add.addEventListener("click", async () => {
      add.disabled = true;
      try { const res = await register(token); keep(res.token); $("panel-status").textContent = res.device + " 기기를 등록했어요."; refresh(); }
      catch (e) { $("panel-status").textContent = e.name === "InvalidStateError" ? "이 기기는 이미 등록돼 있어요." : cancelled(e) ? "등록을 취소했어요." : e.message; add.disabled = false; }
    });
    box.append(el("p", "qna-note", "등록한 기기에서만 지문·얼굴로 들어갈 수 있어요. 잃어버린 기기는 빼 주세요."), add);
    data.items.forEach(d => {
      const c = el("article", "qna-card qna-device"), info = el("div");
      info.append(el("p", "qna-device-name", d.name), el("p", "qna-meta", "등록 " + date(d.created) + (d.used ? " · 마지막 사용 " + date(d.used) : "")));
      const rm = el("button", "btn btn-ghost qna-danger", "빼기"); rm.type = "button";
      rm.addEventListener("click", async () => {
        if (!confirm(d.name + " 기기를 뺄까요? 그 기기에서는 다시 등록해야 들어갈 수 있어요.")) return;
        try { await post("/api/qna/auth/remove", { id: d.id }, token); $("panel-status").textContent = d.name + " 기기를 뺐어요."; refresh(); }
        catch (e) { $("panel-status").textContent = e.message; }
      });
      c.append(info, rm); box.append(c);
    });
    if (!data.items.length) box.append(el("p", "qna-empty", "등록된 기기가 없어요."));
  }

  async function refresh() {
    try {
      const data = await call("GET", "?filter=" + (filter === "devices" ? "pending" : filter));
      for (const k of ["pending", "answered", "hidden"]) $("c-" + k).textContent = data.counts[k];
      const box = $("items"); box.textContent = "";
      if (filter === "devices") await devices(box);
      else {
        data.items.forEach(q => box.append(item(q)));
        if (!data.items.length) box.append(el("p", "qna-empty", filter === "pending" ? "답변을 기다리는 질문이 없어요." : "없어요."));
      }
      $("login").hidden = true; $("panel").hidden = false;
    } catch (e) { if (token) $("panel-status").textContent = e.message; }
  }
  function signedOut(message) {
    keep(""); $("panel").hidden = true; $("login").hidden = false; loginStatus(message || "");
  }

  $("signin").addEventListener("click", signIn);
  $("logout").addEventListener("click", async () => {
    const t = token; signedOut("로그아웃했어요.");
    if (t.startsWith("s_")) post("/api/qna/auth/logout", {}, t).catch(() => {});
  });
  document.querySelectorAll(".qna-tabs [data-filter]").forEach(b => b.addEventListener("click", () => {
    filter = b.dataset.filter;
    document.querySelectorAll(".qna-tabs [data-filter]").forEach(x => x.setAttribute("aria-selected", String(x === b)));
    $("panel-status").textContent = ""; refresh();
  }));
  if (!supported) loginStatus("이 브라우저는 지문·얼굴 로그인을 지원하지 않아요. 「처음이거나 새 기기예요」에서 키로 들어갈 수 있어요.");
  if (token) refresh();
})();
