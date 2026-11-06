"use strict";

// Install tabs (car / phone / watch) with arrow-key support.
(function wireTabs() {
  const tabs = [...document.querySelectorAll("[data-tab]")];
  if (!tabs.length) return;
  function select(tab) {
    tabs.forEach(item => {
      const active = item === tab;
      item.setAttribute("aria-selected", String(active));
      item.tabIndex = active ? 0 : -1;
      const panel = document.getElementById(item.getAttribute("aria-controls"));
      if (panel) panel.hidden = !active;
    });
  }
  tabs.forEach((tab, index) => {
    tab.addEventListener("click", () => select(tab));
    tab.addEventListener("keydown", event => {
      let next;
      if (event.key === "ArrowRight") next = (index + 1) % tabs.length;
      if (event.key === "ArrowLeft") next = (index - 1 + tabs.length) % tabs.length;
      if (event.key === "Home") next = 0;
      if (event.key === "End") next = tabs.length - 1;
      if (next !== undefined) { event.preventDefault(); select(tabs[next]); tabs[next].focus(); }
    });
  });
})();

// Fill the Home / Link download cards from releases.json (written by publish-site.py).
const PRODUCTS = { vehicle: "LumiHome", phone: "LumiLink" };
function setText(key, text) {
  document.querySelectorAll('[data-release="' + key + '"]').forEach(node => { node.textContent = text; });
}
function validRelease(value, kind) {
  const product = PRODUCTS[kind];
  return Boolean(product && value && /^\d+\.\d+\.\d+$/.test(value.versionName) && /^[0-9a-f]{64}$/.test(value.sha256)
    && Number.isSafeInteger(value.bytes) && value.bytes > 0 && value.bytes <= 209715200
    && value.file === "downloads/" + product + "-" + value.versionName + "-" + kind + "-release.apk");
}
function formatDate(iso) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso || "");
  return m ? m[1] + "." + m[2] + "." + m[3] : "";
}
async function loadReleases() {
  const status = document.getElementById("download-status");
  const chip = document.getElementById("release-status");
  try {
    const response = await fetch("releases.json", { cache: "no-store" });
    if (!response.ok) throw new Error("release metadata unavailable");
    const releases = await response.json();
    if (releases.schema !== 1) throw new Error("release schema");
    let ready = 0;
    for (const kind of Object.keys(PRODUCTS)) {
      const value = releases[kind];
      if (!validRelease(value, kind)) continue;
      ready++;
      setText(kind + "-version", value.versionName);
      setText(kind + "-size", (value.bytes / 1048576).toFixed(1) + " MB");
      setText(kind + "-hash", value.sha256);
      const button = document.querySelector('[data-app="' + kind + '"]');
      button.href = value.file;
      button.setAttribute("download", value.file.split("/").pop());
      button.removeAttribute("aria-disabled");
      button.classList.remove("is-disabled");
      button.textContent = "APK 다운로드";
    }
    const date = formatDate(releases.date);
    if (ready === 2) {
      if (chip) chip.textContent = "공개 베타" + (date ? " · " + date + " 업데이트" : "") + " · 회원가입 없이 다운로드";
      if (status) status.firstChild.textContent = (date ? date + "에 올라온 최신 버전이에요. " : "최신 버전이에요. ");
    }
  } catch (_) {
    // An unavailable release feed must not turn into a broken download link.
    if (status) status.firstChild.textContent = "버전 정보를 불러오지 못했어요. 잠시 후 새로고침해 주세요. ";
  }
}
if (document.getElementById("download")) loadReleases();

// Theme gallery: swap the home screenshot when a theme chip is pressed.
(function wireThemes() {
  const shot = document.getElementById("theme-shot");
  const chips = [...document.querySelectorAll("[data-theme]")];
  if (!shot || !chips.length) return;
  chips.forEach(chip => chip.addEventListener("click", () => {
    chips.forEach(c => c.setAttribute("aria-pressed", String(c === chip)));
    const src = "assets/shots/home-" + chip.dataset.theme + ".webp";
    if (shot.getAttribute("src") === src) return;
    shot.classList.add("is-loading");
    const next = new Image();
    next.onload = next.onerror = () => { shot.src = src; shot.alt = "Lumi Home 홈 화면, " + chip.textContent + " 테마"; shot.classList.remove("is-loading"); };
    next.src = src;
  }));
  // Warm the cache so switching feels instant.
  setTimeout(() => chips.forEach(c => { const i = new Image(); i.src = "assets/shots/home-" + c.dataset.theme + ".webp"; }), 1500);
})();
