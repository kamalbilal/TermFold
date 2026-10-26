/* Native messaging + webRequest intercept for session-scoped network rules. */
const nativePort = browser.runtime.connectNative("termfold_browser");

let rulesBySession = {};
const tabToSession = {};
const cookieStoreToSession = {};
let lastBoundSessionKey = "";
const handled = {};

function globToRegExp(pattern) {
  let out = "^";
  for (let i = 0; i < pattern.length; i++) {
    const c = pattern[i];
    if (c === "*") out += ".*";
    else if (c === "?") out += ".";
    else if ("\\^$+()[]{}|.".indexOf(c) >= 0) out += "\\" + c;
    else out += c;
  }
  return new RegExp(out + "$");
}

function asRuleList(value) {
  if (!value) return [];
  if (Array.isArray(value)) return value;
  if (typeof value === "object") {
    return Object.keys(value).map((k) => value[k]).filter((item) => item && item.pattern);
  }
  return [];
}

function normalizeRules(raw) {
  const out = {};
  if (!raw || typeof raw !== "object") return out;
  Object.keys(raw).forEach((key) => {
    out[key] = asRuleList(raw[key]);
  });
  return out;
}

function firstMatch(sessionKey, url) {
  const rules = asRuleList(rulesBySession[sessionKey]);
  for (let i = 0; i < rules.length; i++) {
    const rule = rules[i];
    try {
      if (globToRegExp(rule.pattern).test(url)) return rule;
    } catch (e) {
      /* skip bad pattern */
    }
  }
  return null;
}

function bindTab(tab, sessionKey) {
  if (!tab || tab.id === undefined || !sessionKey) return;
  tabToSession[tab.id] = sessionKey;
  if (tab.cookieStoreId) cookieStoreToSession[tab.cookieStoreId] = sessionKey;
  lastBoundSessionKey = sessionKey;
}

function resolveSession(details) {
  if (details.tabId >= 0 && tabToSession[details.tabId]) return tabToSession[details.tabId];
  if (details.cookieStoreId && cookieStoreToSession[details.cookieStoreId]) {
    return cookieStoreToSession[details.cookieStoreId];
  }
  if (details.cookieStoreId && rulesBySession[details.cookieStoreId]) return details.cookieStoreId;
  return null;
}

function isInternalUrl(url) {
  if (!url) return true;
  return (
    url.indexOf("moz-extension:") === 0 ||
    url.indexOf("resource:") === 0 ||
    url.indexOf("about:") === 0
  );
}

const captureStatus = {};
const captureReqHeaders = {};
const captureRespHeaders = {};
const captureReqBodyPending = {};
const captureModeBySession = {};
const bodyCaptureBySession = {};
const BODY_MAX = 131072;
const MAX_ACTIVE_BODY_TEES = 3;
let activeBodyTees = 0;

function captureModeFor(sessionKey) {
  if (!sessionKey) return "off";
  const mode = captureModeBySession[sessionKey];
  if (mode === "lite" || mode === "full" || mode === "off") return mode;
  return "off";
}

function bodyCaptureFor(sessionKey) {
  if (!sessionKey) return false;
  return bodyCaptureBySession[sessionKey] === true && captureModeFor(sessionKey) === "full";
}

function isCaptureType(type) {
  return type === "xmlhttprequest" || type === "fetch";
}

/** Lite mode: agent list + failed counts without recording every asset. */
function isLiteCaptureType(type) {
  return (
    type === "main_frame" ||
    type === "sub_frame" ||
    type === "xmlhttprequest" ||
    type === "fetch" ||
    type === "websocket"
  );
}

function shouldRecordRequest(sessionKey, type) {
  const mode = captureModeFor(sessionKey);
  if (mode === "off") return false;
  if (mode === "lite") return isLiteCaptureType(type);
  return true;
}

function capturePost(sessionKey, payload) {
  try {
    nativePort.postMessage(
      Object.assign({ type: "network_capture", sessionKey: sessionKey || "" }, payload),
    );
  } catch (e) {
    /* port may be down */
  }
}

function cleanupCaptureMaps(requestId) {
  delete captureStatus[requestId];
  delete captureReqHeaders[requestId];
  delete captureRespHeaders[requestId];
  delete captureReqBodyPending[requestId];
}

function decodeRequestBody(requestBody) {
  if (!requestBody || requestBody.error) return "";
  if (requestBody.formData) {
    try {
      return JSON.stringify(requestBody.formData);
    } catch (e) {
      return "";
    }
  }
  if (!requestBody.raw || !requestBody.raw.length) return "";
  let size = 0;
  const chunks = [];
  for (let i = 0; i < requestBody.raw.length; i++) {
    const part = new Uint8Array(requestBody.raw[i]);
    if (size >= BODY_MAX) break;
    const take = Math.min(part.length, BODY_MAX - size);
    if (take < part.length) chunks.push(part.subarray(0, take));
    else chunks.push(part);
    size += take;
  }
  if (chunks.length === 0) return "";
  const merged = new Uint8Array(size);
  let offset = 0;
  for (let i = 0; i < chunks.length; i++) {
    merged.set(chunks[i], offset);
    offset += chunks[i].length;
  }
  try {
    return new TextDecoder("utf-8", { fatal: false }).decode(merged);
  } catch (e2) {
    return "";
  }
}

function contentTypeFromHeaders(headersText) {
  if (!headersText) return "";
  const lines = headersText.split("\n");
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const idx = line.indexOf(":");
    if (idx < 0) continue;
    if (line.slice(0, idx).trim().toLowerCase() === "content-type") {
      return line.slice(idx + 1).trim().toLowerCase();
    }
  }
  return "";
}

function isTextualContentType(contentType) {
  if (!contentType) return true;
  return (
    contentType.indexOf("json") >= 0 ||
    contentType.indexOf("text/") >= 0 ||
    contentType.indexOf("javascript") >= 0 ||
    contentType.indexOf("xml") >= 0 ||
    contentType.indexOf("x-www-form-urlencoded") >= 0
  );
}

function captureStart(sessionKey, details) {
  if (!sessionKey || isInternalUrl(details.url)) return;
  if (!shouldRecordRequest(sessionKey, details.type || "other")) return;
  if (captureModeFor(sessionKey) !== "full") return;
  capturePost(sessionKey, {
    phase: "start",
    requestId: details.requestId,
    method: details.method || "GET",
    url: details.url || "",
    resourceType: details.type || "other",
    time: details.timeStamp || Date.now(),
  });
  if (bodyCaptureFor(sessionKey) && isCaptureType(details.type) && details.requestBody) {
    const text = decodeRequestBody(details.requestBody);
    if (text) captureReqBodyPending[details.requestId] = text;
  }
}

function maybeTeeResponse(sessionKey, details) {
  if (!sessionKey || !bodyCaptureFor(sessionKey) || !isCaptureType(details.type)) return;
  if (activeBodyTees >= MAX_ACTIVE_BODY_TEES) return;
  activeBodyTees += 1;
  try {
    const filter = browser.webRequest.filterResponseData(details.requestId);
    const merged = new Uint8Array(BODY_MAX);
    let captured = 0;
    filter.ondata = (event) => {
      filter.write(event.data);
      if (captured >= BODY_MAX) return;
      const part = new Uint8Array(event.data);
      const take = Math.min(part.length, BODY_MAX - captured);
      if (take > 0) {
        merged.set(part.subarray(0, take), captured);
        captured += take;
      }
    };
    filter.onstop = () => {
      activeBodyTees = Math.max(0, activeBodyTees - 1);
      try {
        filter.close();
      } catch (e2) {
        /* ignore */
      }
      if (captured === 0) return;
      let text = "";
      try {
        text = new TextDecoder("utf-8", { fatal: false }).decode(merged.subarray(0, captured));
      } catch (e3) {
        text = "";
      }
      const respCt = contentTypeFromHeaders(captureRespHeaders[details.requestId] || "");
      if (text && isTextualContentType(respCt)) {
        capturePost(sessionKey, {
          phase: "body",
          requestId: details.requestId,
          which: "response",
          text: text,
        });
      }
    };
  } catch (e) {
    activeBodyTees = Math.max(0, activeBodyTees - 1);
    /* filter unavailable */
  }
}

function headersToText(headers) {
  if (!headers || !headers.length) return "";
  return headers.map((h) => String(h.name) + ": " + String(h.value)).join("\n");
}

function reportFull(sessionKey, details, outcome, detail) {
  capturePost(sessionKey, {
    phase: "done",
    requestId: details.requestId,
    method: details.method || "GET",
    url: details.url || "",
    resourceType: details.type || "other",
    outcome: outcome,
    detail: detail || "",
    status: captureStatus[details.requestId] || details.statusCode || 0,
    requestHeaders: captureReqHeaders[details.requestId] || "",
    responseHeaders: captureRespHeaders[details.requestId] || "",
    endedAt: details.timeStamp || Date.now(),
  });
  cleanupCaptureMaps(details.requestId);
}

function reportLite(sessionKey, details, outcome, detail) {
  capturePost(sessionKey, {
    phase: "done",
    requestId: details.requestId,
    method: details.method || "GET",
    url: details.url || "",
    resourceType: details.type || "other",
    outcome: outcome,
    detail: detail || "",
    status: details.statusCode || captureStatus[details.requestId] || 0,
    endedAt: details.timeStamp || Date.now(),
  });
  cleanupCaptureMaps(details.requestId);
}

function report(sessionKey, details, outcome, detail) {
  if (!sessionKey || isInternalUrl(details.url)) return;
  if (!shouldRecordRequest(sessionKey, details.type || "other")) {
    cleanupCaptureMaps(details.requestId);
    return;
  }
  const mode = captureModeFor(sessionKey);
  if (mode === "lite") reportLite(sessionKey, details, outcome, detail);
  else if (mode === "full") reportFull(sessionKey, details, outcome, detail);
  else cleanupCaptureMaps(details.requestId);
}

function rememberHandled(requestId, outcome) {
  handled[requestId] = outcome;
  const ids = Object.keys(handled);
  if (ids.length > 300) {
    ids.slice(0, ids.length - 200).forEach((id) => {
      delete handled[id];
    });
  }
}

function applyOverrideBody(details, rule) {
  const filter = browser.webRequest.filterResponseData(details.requestId);
  const encoder = new TextEncoder();
  const bytes = encoder.encode(details.method === "OPTIONS" ? "" : rule.body || "");
  let wrote = false;
  const writeClose = () => {
    if (wrote) return;
    wrote = true;
    try {
      filter.write(bytes);
    } catch (e) {
      /* already closed */
    }
    try {
      filter.close();
    } catch (e2) {
      /* ignore */
    }
  };
  filter.ondata = () => {};
  filter.onstop = writeClose;
}

browser.runtime.onMessage.addListener((message, sender) => {
  if (message && message.type === "tf_console") {
    const tabId = sender.tab && sender.tab.id;
    const sessionKey = (tabId !== undefined && tabToSession[tabId]) || lastBoundSessionKey || "";
    try {
      nativePort.postMessage({
        type: "console",
        sessionKey: sessionKey,
        level: message.level || "log",
        text: message.text || "",
        where: message.where || "",
      });
    } catch (e) {
      /* port may be down */
    }
    return;
  }
});

nativePort.onMessage.addListener((message) => {
  if (!message || !message.id) return;
  const payload = message.payload || {};
  if (payload.op === "set_rules") {
    rulesBySession = normalizeRules(payload.rulesBySession);
    nativePort.postMessage({ id: message.id, ok: true });
    return;
  }
  if (payload.op === "set_capture_options") {
    const key = payload.sessionKey;
    if (key) {
      if (payload.captureMode) captureModeBySession[key] = payload.captureMode;
      bodyCaptureBySession[key] = !!payload.bodyCapture;
    }
    nativePort.postMessage({ id: message.id, ok: true });
    return;
  }
  if (payload.op === "bind_session") {
    const sessionKey = payload.sessionKey;
    lastBoundSessionKey = sessionKey || lastBoundSessionKey;
    browser.tabs
      .query({ active: true, currentWindow: true })
      .then((tabs) => {
        bindTab(tabs && tabs[0], sessionKey);
        nativePort.postMessage({ id: message.id, ok: true });
      })
      .catch((e) => nativePort.postMessage({ id: message.id, error: String(e) }));
    return;
  }
  browser.tabs.query({ active: true, currentWindow: true }).then((tabs) => {
    const tab = tabs && tabs[0];
    if (!tab || tab.id === undefined) {
      nativePort.postMessage({ id: message.id, error: "no_active_tab" });
      return;
    }
    if (payload.sessionKey) bindTab(tab, payload.sessionKey);
    browser.tabs
      .sendMessage(tab.id, payload)
      .then((response) => nativePort.postMessage({ id: message.id, ...response }))
      .catch((e) => nativePort.postMessage({ id: message.id, error: String(e) }));
  });
});

if (browser.tabs && browser.tabs.onCreated) {
  browser.tabs.onCreated.addListener((tab) => {
    if (tab.openerTabId !== undefined && tabToSession[tab.openerTabId]) {
      bindTab(tab, tabToSession[tab.openerTabId]);
    }
  });
}

function onBeforeRequest(details) {
  if (isInternalUrl(details.url)) return {};
  if (details.type === "main_frame" && details.tabId >= 0 && lastBoundSessionKey && !tabToSession[details.tabId]) {
    tabToSession[details.tabId] = lastBoundSessionKey;
  }
  const sessionKey = resolveSession(details) || lastBoundSessionKey;
  captureStart(sessionKey, details);
  const rule = sessionKey ? firstMatch(sessionKey, details.url) : null;
  if (!rule) {
    maybeTeeResponse(sessionKey, details);
    return {};
  }
  if (rule.kind === "block") {
    rememberHandled(details.requestId, "blocked");
    report(sessionKey, details, "blocked", "");
    return { cancel: true };
  }
  rememberHandled(details.requestId, "overridden");
  report(sessionKey, details, "overridden", "");
  applyOverrideBody(details, rule);
  return {};
}

function onBeforeSendHeaders(details) {
  if (isInternalUrl(details.url)) return {};
  const sessionKey = resolveSession(details) || lastBoundSessionKey;
  if (captureModeFor(sessionKey) !== "full") return {};
  if (sessionKey && details.requestHeaders) {
    captureReqHeaders[details.requestId] = headersToText(details.requestHeaders);
    const pendingBody = captureReqBodyPending[details.requestId];
    if (pendingBody && bodyCaptureFor(sessionKey) && isCaptureType(details.type)) {
      const reqCt = contentTypeFromHeaders(captureReqHeaders[details.requestId]);
      if (isTextualContentType(reqCt)) {
        capturePost(sessionKey, {
          phase: "body",
          requestId: details.requestId,
          which: "request",
          text: pendingBody,
        });
      }
      delete captureReqBodyPending[details.requestId];
    }
  }
}

function onHeadersReceived(details) {
  if (isInternalUrl(details.url)) return {};
  const sessionKey = resolveSession(details) || lastBoundSessionKey;
  if (details.statusCode) captureStatus[details.requestId] = details.statusCode;
  if (captureModeFor(sessionKey) === "full" && sessionKey && details.responseHeaders) {
    captureRespHeaders[details.requestId] = headersToText(details.responseHeaders);
  }
  const rule = sessionKey ? firstMatch(sessionKey, details.url) : null;
  if (!rule || rule.kind !== "override") return {};
  const extra = rule.headers || {};
  let headers = (details.responseHeaders || []).slice();
  Object.keys(extra).forEach((name) => {
    headers = headers.filter((h) => String(h.name).toLowerCase() !== String(name).toLowerCase());
    headers.push({ name: name, value: String(extra[name]) });
  });
  if (details.method === "OPTIONS") {
    const hasOrigin = Object.keys(extra).some((n) => n.toLowerCase() === "access-control-allow-origin");
    if (!hasOrigin) {
      const origin = (details.originUrl && details.originUrl.split("/").slice(0, 3).join("/")) || "*";
      headers.push({ name: "Access-Control-Allow-Origin", value: origin === "null" ? "*" : origin });
    }
    const hasMethods = Object.keys(extra).some((n) => n.toLowerCase() === "access-control-allow-methods");
    if (!hasMethods) {
      headers.push({ name: "Access-Control-Allow-Methods", value: "GET, POST, PUT, PATCH, DELETE, OPTIONS" });
    }
    const hasHeaders = Object.keys(extra).some((n) => n.toLowerCase() === "access-control-allow-headers");
    if (!hasHeaders) {
      headers.push({ name: "Access-Control-Allow-Headers", value: "*" });
    }
  }
  return { responseHeaders: headers };
}

function onCompleted(details) {
  if (isInternalUrl(details.url)) return;
  if (handled[details.requestId]) return;
  report(resolveSession(details) || lastBoundSessionKey, details, "ok", "");
}

function onErrorOccurred(details) {
  if (isInternalUrl(details.url)) return;
  if (handled[details.requestId] === "blocked") {
    cleanupCaptureMaps(details.requestId);
    return;
  }
  if (details.error && String(details.error).indexOf("NS_ERROR_ABORT") >= 0) {
    cleanupCaptureMaps(details.requestId);
    return;
  }
  report(resolveSession(details) || lastBoundSessionKey, details, "failed", details.error || "failed");
}

const FILTER = { urls: ["<all_urls>"] };

browser.webRequest.onBeforeRequest.addListener(onBeforeRequest, FILTER, ["blocking", "requestBody"]);
browser.webRequest.onBeforeSendHeaders.addListener(onBeforeSendHeaders, FILTER, ["requestHeaders"]);
browser.webRequest.onHeadersReceived.addListener(onHeadersReceived, FILTER, ["blocking", "responseHeaders"]);
browser.webRequest.onCompleted.addListener(onCompleted, FILTER);
browser.webRequest.onErrorOccurred.addListener(onErrorOccurred, FILTER);
