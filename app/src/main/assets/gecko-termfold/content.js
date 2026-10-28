/* Content script: answers automation RPC from the native TermFold bridge. */

document.addEventListener(
  "termfold-console",
  (e) => {
    const d = e.detail;
    if (!d || !d.text) return;
    try {
      browser.runtime.sendMessage({
        type: "tf_console",
        level: d.level || "log",
        text: d.text,
        where: d.where || "",
      });
    } catch (err) {
      /* ignore */
    }
  },
  true,
);

function evalInPageContext(code) {
  return new Promise((resolve) => {
    const eventName = "tf-eval-" + Math.random().toString(36).slice(2);
    const listener = (e) => {
      document.removeEventListener(eventName, listener, true);
      resolve(e.detail || { error: "empty_eval_response" });
    };
    document.addEventListener(eventName, listener, true);
    const script = document.createElement("script");
    script.textContent =
      "(async function(){var d;try{var r=(" +
      code +
      ");if(r&&typeof r.then==='function')r=await r;d={ok:true,json:typeof r==='string'?r:JSON.stringify(r===undefined?null:r)};}catch(err){d={error:String(err)};}document.dispatchEvent(new CustomEvent('" +
      eventName +
      "',{detail:d}));})();";
    try {
      (document.head || document.documentElement).appendChild(script);
      script.remove();
    } catch (injectErr) {
      document.removeEventListener(eventName, listener, true);
      try {
        const result = new Function(code)();
        resolve({
          ok: true,
          json: typeof result === "string" ? result : JSON.stringify(result === undefined ? null : result),
        });
      } catch (fallbackErr) {
        resolve({ error: String(fallbackErr) });
      }
    }
  });
}

if (!globalThis.__termfoldTfBridgeInstalled) {
  globalThis.__termfoldTfBridgeInstalled = true;

browser.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (!message || !message.op) {
    sendResponse({ error: "unknown_op" });
    return true;
  }
  try {
    if (message.op === "eval") {
      evalInPageContext(message.code)
        .then((detail) => sendResponse(detail))
        .catch((e) => sendResponse({ error: String(e) }));
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
    sendResponse({ ok: true, json: JSON.stringify(result === undefined ? null : result) });
  } catch (e) {
    sendResponse({ error: String(e) });
  }
  return true;
});
}
