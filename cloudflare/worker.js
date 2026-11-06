// Serves the Lumi website and downloads from the R2 bucket bound as SITE.
const NO_CACHE = /^(updates\/|releases\.json$)/;
const LONG_CACHE = /^(downloads\/|assets\/)/;

export default {
  async fetch(request, env) {
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
