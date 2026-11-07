// Public questions page: ask, see your own questions (tickets kept in this browser) and read answered ones.
(() => {
  const $ = id => document.getElementById(id);
  const KEY = "lumi-qna-mine";
  const load = () => { try { const v = JSON.parse(localStorage.getItem(KEY) || "[]"); return Array.isArray(v) ? v : []; } catch { return []; } };
  const save = v => { try { localStorage.setItem(KEY, JSON.stringify(v.slice(-20))); } catch {} };
  const date = t => new Date(t).toLocaleDateString("ko-KR", { year: "numeric", month: "long", day: "numeric" });
  const el = (tag, cls, text) => { const e = document.createElement(tag); if (cls) e.className = cls; if (text !== undefined) e.textContent = text; return e; };

  function card(q, mine) {
    const c = el("article", "qna-card");
    const meta = el("p", "qna-meta");
    meta.append(el("span", "", mine ? "내 질문" : (q.name || "익명")), el("span", "", date(q.created)));
    if (mine) meta.append(el("span", "qna-badge" + (q.answered ? " is-done" : ""), q.hidden ? "보관됨" : q.answered ? "답변 완료" : "답변 대기"));
    if (mine && q.private) meta.append(el("span", "qna-badge", "비공개"));
    c.append(meta, el("p", "qna-q", q.body));
    if (q.answered && q.answer) {
      const a = el("div", "qna-a");
      a.append(el("p", "qna-a-head", "개발자 답변 · " + date(q.answered)), el("p", "qna-a-body", q.answer));
      c.append(a);
    }
    return c;
  }

  async function call(path, opts) {
    const r = await fetch(path, opts);
    let body = {}; try { body = await r.json(); } catch {}
    if (!r.ok) throw new Error(body.error || "연결에 실패했어요");
    return body;
  }

  let before = 0;
  async function more() {
    const list = $("list"), btn = $("more");
    btn.disabled = true;
    try {
      const data = await call("/api/qna" + (before ? "?before=" + before : ""));
      if (!before) list.textContent = "";
      data.items.forEach(q => list.append(card(q, false)));
      if (!before && !data.items.length) list.append(el("p", "qna-empty", "아직 답변한 질문이 없어요. 첫 질문을 남겨 주세요."));
      if (data.items.length) before = data.items[data.items.length - 1].id;
      btn.hidden = !data.more;
    } catch (e) {
      if (!before) { list.textContent = ""; list.append(el("p", "qna-empty", "질문 목록을 불러오지 못했어요. " + e.message)); }
    }
    btn.disabled = false;
  }

  async function mine() {
    const items = load();
    if (!items.length) return;
    $("mine-wrap").hidden = false;
    try {
      const data = await call("/api/qna/mine", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ items }) });
      const box = $("mine"); box.textContent = "";
      const found = new Set(data.items.map(q => q.id));
      save(items.filter(i => found.has(i.id)));
      data.items.sort((a, b) => b.id - a.id).forEach(q => box.append(card(q, true)));
      if (!data.items.length) $("mine-wrap").hidden = true;
    } catch {}
  }

  const form = $("ask"), status = $("status"), count = $("count");
  form.question.addEventListener("input", () => { count.textContent = form.question.value.length + " / 1000"; });
  form.addEventListener("submit", async e => {
    e.preventDefault();
    const question = form.question.value.trim();
    if (question.length < 5) { status.textContent = "질문을 5자 이상 써 주세요."; form.question.focus(); return; }
    $("send").disabled = true; status.textContent = "보내는 중…";
    try {
      const r = await call("/api/qna", { method: "POST", headers: { "content-type": "application/json" },
        body: JSON.stringify({ name: form.name.value, question, private: form.private.checked, website: form.website.value }) });
      if (r.id) { const items = load(); items.push({ id: r.id, ticket: r.ticket }); save(items); }
      form.question.value = ""; count.textContent = "0 / 1000";
      status.textContent = "질문을 보냈어요. 답변이 달리면 이 페이지의 「내 질문」에서 볼 수 있어요.";
      mine();
    } catch (err) { status.textContent = err.message; }
    $("send").disabled = false;
  });
  $("more").addEventListener("click", more);
  more(); mine();
})();
