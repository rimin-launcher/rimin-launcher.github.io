// Owner page: the owner key is sent as a bearer token; it is kept in this browser only when "remember" is on.
(() => {
  const $ = id => document.getElementById(id);
  const STORE = "lumi-qna-owner";
  let key = ""; try { key = localStorage.getItem(STORE) || sessionStorage.getItem(STORE) || ""; } catch {}
  let filter = "pending";
  const date = t => new Date(t).toLocaleString("ko-KR", { dateStyle: "medium", timeStyle: "short" });
  const el = (tag, cls, text) => { const e = document.createElement(tag); if (cls) e.className = cls; if (text !== undefined) e.textContent = text; return e; };

  async function call(method, query, body) {
    const r = await fetch("/api/qna/admin" + (query || ""), { method, headers: { authorization: "Bearer " + key,
      ...(body ? { "content-type": "application/json" } : {}) }, body: body ? JSON.stringify(body) : undefined });
    let data = {}; try { data = await r.json(); } catch {}
    if (r.status === 401) { logout(); $("login-status").textContent = data.error || "주인 키가 맞지 않아요"; throw new Error(data.error); }
    if (!r.ok) throw new Error(data.error || "연결에 실패했어요");
    return data;
  }

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

  async function refresh() {
    try {
      const data = await call("GET", "?filter=" + filter);
      for (const k of ["pending", "answered", "hidden"]) $("c-" + k).textContent = data.counts[k];
      const box = $("items"); box.textContent = "";
      data.items.forEach(q => box.append(item(q)));
      if (!data.items.length) box.append(el("p", "qna-empty", filter === "pending" ? "답변을 기다리는 질문이 없어요." : "없어요."));
      $("login").hidden = true; $("panel").hidden = false;
    } catch (e) { if (key) $("panel-status").textContent = e.message; }
  }
  function logout() {
    key = ""; try { localStorage.removeItem(STORE); sessionStorage.removeItem(STORE); } catch {}
    $("panel").hidden = true; $("login").hidden = false;
  }

  $("login").addEventListener("submit", e => {
    e.preventDefault();
    key = $("login").key.value.trim(); $("login").key.value = "";
    try { (($("login").remember.checked) ? localStorage : sessionStorage).setItem(STORE, key); } catch {}
    $("login-status").textContent = "확인 중…"; refresh().then(() => { if (key) $("login-status").textContent = ""; });
  });
  $("logout").addEventListener("click", logout);
  document.querySelectorAll(".qna-tabs [data-filter]").forEach(b => b.addEventListener("click", () => {
    filter = b.dataset.filter;
    document.querySelectorAll(".qna-tabs [data-filter]").forEach(x => x.setAttribute("aria-selected", String(x === b)));
    refresh();
  }));
  if (key) refresh();
})();
