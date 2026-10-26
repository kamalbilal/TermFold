/* Content script: answers automation RPC from the native TermFold bridge. */
browser.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (!message || !message.op) {
    sendResponse({ error: "unknown_op" });
    return true;
  }
  try {
    if (message.op === "eval") {
      const result = new Function(message.code)();
      sendResponse({ ok: true, json: JSON.stringify(result) });
      return true;
    }
    if (message.op !== "tf") {
      sendResponse({ error: "unknown_op" });
      return true;
    }
    const expr = message.expr;
    if (!window.__tf) {
      sendResponse({ error: "helper_not_ready" });
      return true;
    }
    const fn = new Function("return window.__tf." + expr + ";");
    const result = fn();
    sendResponse({ ok: true, json: JSON.stringify(result) });
  } catch (e) {
    sendResponse({ error: String(e) });
  }
  return true;
});
