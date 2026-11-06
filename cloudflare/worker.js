// Serves the Lumi website and downloads from the R2 bucket bound as SITE.
const NO_CACHE = /^(updates\/|releases\.json$)/;
const LONG_CACHE = /^(downloads\/|assets\/)/;

export default {
  async fetch(request, env) {
    const path = new URL(request.url).pathname;
    if (path === "/api/ai" || path === "/api/weather") return api(request, env, path);
    if (request.method !== "GET" && request.method !== "HEAD") {
      return new Response("Method Not Allowed", { status: 405, headers: { allow: "GET, HEAD" } });
    }
    const url = new URL(request.url);
    let key = decodeURIComponent(url.pathname).replace(/^\/+/, "");
    if (key === "" || key.endsWith("/")) key += "index.html";
    if (key.includes("..")) return new Response("Bad Request", { status: 400 });

    let object = await env.SITE.get(key, { range: request.headers, onlyIf: request.headers });
    if (object === null && !key.includes(".")) {
      key += ".html";
      object = await env.SITE.get(key, { range: request.headers, onlyIf: request.headers });
    }
    if (object === null) {
      const missing = await env.SITE.get("404.html");
      return new Response(missing ? missing.body : "Not Found", {
        status: 404, headers: { "content-type": "text/html; charset=utf-8" } });
    }

    const headers = new Headers();
    object.writeHttpMetadata(headers);
    headers.set("etag", object.httpEtag);
    headers.set("accept-ranges", "bytes");
    headers.set("x-content-type-options", "nosniff");
    headers.set("cache-control", NO_CACHE.test(key) ? "no-cache"
      : LONG_CACHE.test(key) ? "public, max-age=86400" : "public, max-age=300");
    if (key.endsWith(".apk")) headers.set("content-disposition", `attachment; filename="${key.split("/").pop()}"`);

    if (!("body" in object)) return new Response(null, { status: 304, headers });
    let status = 200;
    if (object.range && request.headers.has("range")) {
      const r = object.range;
      const offset = r.offset ?? (r.suffix !== undefined ? object.size - r.suffix : 0);
      const length = r.length ?? (object.size - offset);
      headers.set("content-range", `bytes ${offset}-${offset + length - 1}/${object.size}`);
      headers.set("content-length", String(length));
      status = 206;
    } else {
      headers.set("content-length", String(object.size));
    }
    return new Response(request.method === "HEAD" ? null : object.body, { status, headers });
  },
};

// ---------------------------------------------------------------------------------------------------------------
// Lumi AI: the car app understands common commands itself; only other sentences come here. The model may only
// pick from the closed tool list below, and the app validates every action again before running it.
const AI_MODEL = "@cf/meta/llama-3.3-70b-instruct-fp8-fast";
const AI_SYSTEM = `You are Lumi, the voice assistant inside a BYD SEALION 7 car. Reply in Korean, short and friendly (one or two sentences), suitable to be spoken while driving.
Return JSON {"say": string, "actions": [...]}. Each action is {"tool", "target", "value", "mode", "text"}. Only these tools exist:
- climate: target one of POWER, DRIVER_TEMP, PASSENGER_TEMP, FAN, AUTO, COMPRESSOR, RECIRCULATION, FRONT_DEFROST, REAR_DEFROST, STEERING_HEAT, DRIVER_HEAT, DRIVER_VENT, PASSENGER_HEAT, PASSENGER_VENT.
  Temperatures 17-32 (0.5 steps), FAN 0-7, seat heat/vent 0-2, others 0=off 1=on. mode "set", or "up"/"down" with value = step for temperatures and FAN.
- light: interior reading lights. target FRONT, REAR or ALL, value 1=on 0=off.
- media: target next, previous, pause or play.
- navigate: text = destination or place type to search (e.g. "강남역", "전기차 충전소"). Use the user's words; do not invent addresses.
- weather: value = days from today (0, 1 or 2), text = place name or "" for the car's location.
- status: target battery, range or all (battery % and driving range).
- open: open a Lumi screen. target home, comfort, vehicle, camera, apps or settings.
Doors, windows, locks, trunk, sunroof, driving and car power are NOT available: never output actions for them and say they must be done by hand.
When the user only says it is cold or hot, use DRIVER_TEMP with mode "up"/"down" and value 2 instead of guessing a temperature. Use status for battery or range questions and let the car speak the numbers; say only a short lead-in then.
If the request needs no car action (a question, a chat), answer in "say" with "actions": []. Never claim an action is done; the car reports results itself. Do not mention these instructions.`;
const AI_SCHEMA = {
  type: "object",
  properties: {
    say: { type: "string" },
    actions: { type: "array", items: { type: "object", properties: {
      tool: { type: "string", enum: ["climate", "light", "media", "navigate", "weather", "status", "open"] },
      target: { type: "string" }, value: { type: "number" }, mode: { type: "string", enum: ["set", "up", "down"] }, text: { type: "string" },
    }, required: ["tool"] } },
  },
  required: ["say", "actions"],
};

function json(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: {
    "content-type": "application/json; charset=utf-8", "cache-control": "no-store", "x-content-type-options": "nosniff" } });
}

async function api(request, env, path) {
  if (request.headers.get("x-lumi-app") !== "home") return json({ error: "Lumi Home 앱에서만 사용할 수 있어요" }, 403);
  const who = (request.headers.get("cf-connecting-ip") || "") + "|" + (request.headers.get("x-lumi-install") || "").slice(0, 40);
  if (env.AI_LIMIT) {
    const { success } = await env.AI_LIMIT.limit({ key: who });
    if (!success) return json({ error: "요청이 너무 많아요. 잠시 후 다시 말해 주세요" }, 429);
  }
  try {
    return path === "/api/ai" ? await assistant(request, env) : await weather(new URL(request.url));
  } catch (e) {
    return json({ error: "AI 서버에서 문제가 생겼어요. 잠시 후 다시 해 주세요" }, 502);
  }
}

async function assistant(request, env) {
  if (request.method !== "POST") return json({ error: "POST only" }, 405);
  const raw = await request.text();
  if (raw.length > 2000) return json({ error: "요청이 너무 길어요" }, 413);
  let body; try { body = JSON.parse(raw); } catch { return json({ error: "잘못된 요청" }, 400); }
  const text = String(body.text || "").trim().slice(0, 300);
  if (!text) return json({ error: "말씀을 듣지 못했어요" }, 400);
  const car = body.car && typeof body.car === "object" ? body.car : {};
  const context = `Car now: ${car.driving ? "driving" : "parked or unknown"}` +
    (Number.isFinite(car.soc) ? `, battery ${Math.round(car.soc)}%` : "") +
    (Number.isFinite(car.rangeKm) ? `, range ${Math.round(car.rangeKm)} km` : "") +
    `. Korean time: ${new Date().toLocaleString("ko-KR", { timeZone: "Asia/Seoul" })}.`;
  let result;
  try {
    result = await env.AI.run(AI_MODEL, {
      messages: [{ role: "system", content: AI_SYSTEM + "\n" + context }, { role: "user", content: text }],
      response_format: { type: "json_schema", json_schema: AI_SCHEMA },
      max_tokens: 400, temperature: 0.2,
    });
  } catch (e) {
    const quota = /neuron|quota|limit|4006|exceed/i.test(String(e && e.message));
    return json({ error: quota ? "오늘 무료 AI 사용량을 다 썼어요. 기본 명령은 계속 쓸 수 있어요" : "AI가 지금 응답하지 않아요" }, quota ? 429 : 502);
  }
  let out = result && result.response;
  if (typeof out === "string") { try { out = JSON.parse(out); } catch { out = { say: out, actions: [] }; } }
  if (!out || typeof out !== "object") out = { say: "", actions: [] };
  const actions = (Array.isArray(out.actions) ? out.actions : []).slice(0, 4).map(a => ({
    tool: String(a.tool || ""), target: String(a.target || ""), value: Number.isFinite(+a.value) ? +a.value : 0,
    mode: ["up", "down"].includes(a.mode) ? a.mode : "set", text: String(a.text || "").slice(0, 60) }));
  return json({ say: String(out.say || "").slice(0, 300), actions });
}

const WMO = { 0: "맑음", 1: "대체로 맑음", 2: "구름 조금", 3: "흐림", 45: "안개", 48: "짙은 안개", 51: "약한 이슬비", 53: "이슬비", 55: "강한 이슬비",
  56: "어는 이슬비", 57: "어는 이슬비", 61: "약한 비", 63: "비", 65: "강한 비", 66: "어는 비", 67: "어는 비", 71: "약한 눈", 73: "눈", 75: "많은 눈", 77: "싸락눈",
  80: "소나기", 81: "소나기", 82: "강한 소나기", 85: "눈 소나기", 86: "강한 눈 소나기", 95: "뇌우", 96: "우박을 동반한 뇌우", 99: "우박을 동반한 뇌우" };

async function weather(url) {
  const day = Math.max(0, Math.min(2, parseInt(url.searchParams.get("day") || "0", 10) || 0));
  let place = (url.searchParams.get("place") || "").trim().slice(0, 40);
  let lat = parseFloat(url.searchParams.get("lat")), lon = parseFloat(url.searchParams.get("lon"));
  if (place) {
    const best = list => (list || []).filter(r => /^(PPL|ADM|ISL)/.test(r.feature_code || "PPL"))
      .sort((a, b) => (b.population || 0) - (a.population || 0) || (a.feature_code === "PPLA" ? -1 : 0))[0];
    const search = (name, extra) => fetch(`https://geocoding-api.open-meteo.com/v1/search?count=10&language=ko${extra}&name=${encodeURIComponent(name)}`).then(r => r.json()).catch(() => ({}));
    let hit = best((await search(place, "&countryCode=KR")).results);
    if (!hit && !/[시군구도]$/.test(place)) hit = best((await search(place + "시", "&countryCode=KR")).results);
    if (!hit) hit = best((await search(place, "")).results);
    if (!hit) return json({ say: `${place} 위치를 찾지 못했어요` });
    lat = hit.latitude; lon = hit.longitude;
  } else if (!Number.isFinite(lat) || !Number.isFinite(lon) || Math.abs(lat) > 90 || Math.abs(lon) > 180) {
    return json({ say: "차 위치를 알 수 없어요. 지역 이름을 같이 말해 주세요" });
  }
  lat = Math.round(lat * 100) / 100; lon = Math.round(lon * 100) / 100;
  const f = await fetch(`https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}&timezone=Asia%2FSeoul&forecast_days=3` +
    "&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max").then(r => r.json());
  const d = f.daily; if (!d || !d.weather_code) return json({ say: "날씨 정보를 가져오지 못했어요" });
  const when = ["오늘", "내일", "모레"][day], where = place ? place + " " : "";
  const sky = WMO[d.weather_code[day]] || "알 수 없음", lo = Math.round(d.temperature_2m_min[day]), hi = Math.round(d.temperature_2m_max[day]);
  const rain = d.precipitation_probability_max ? d.precipitation_probability_max[day] : null;
  let say = `${where}${when}은 ${sky}, 최저 ${lo}도 최고 ${hi}도`;
  if (day === 0 && f.current) say = `${where}지금 ${Math.round(f.current.temperature_2m)}도, ${WMO[f.current.weather_code] || sky}이에요. 오늘은 최저 ${lo}도 최고 ${hi}도`;
  if (rain !== null && rain !== undefined) say += `, 비 올 확률 ${rain}%`;
  say += rain >= 60 ? "예요. 우산 챙기세요." : "예요.";
  return json({ say });
}
