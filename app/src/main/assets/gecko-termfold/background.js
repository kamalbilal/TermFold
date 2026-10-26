/* Bidirectional native messaging: app → active tab content script → app */
const nativePort = browser.runtime.connectNative("termfold_browser");

nativePort.onMessage.addListener((message) => {
  if (!message || !message.id || !message.payload) return;
  browser.tabs.query({ active: true, currentWindow: true }).then((tabs) => {
    const tab = tabs && tabs[0];
    if (!tab || tab.id === undefined) {
      nativePort.postMessage({ id: message.id, error: "no_active_tab" });
      return;
    }
    browser.tabs
      .sendMessage(tab.id, message.payload)
      .then((response) => nativePort.postMessage({ id: message.id, ...response }))
      .catch((e) => nativePort.postMessage({ id: message.id, error: String(e) }));
  });
});
