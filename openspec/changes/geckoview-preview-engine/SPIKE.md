# Gecko spike notes

- **Dependency:** `org.mozilla.geckoview:geckoview:141.0.20250806102122` (see `PreviewEngineConfig.GECKOVIEW_VERSION`).
- **Engine:** Preview and agent browser sessions always use GeckoView (`PreviewEngineConfig.useGeckoPreview()` is always true).
- **Project files:** `http://files.termfold/...` is loaded via `PreviewFiles.bytesForUrl` into Gecko `Loader.data`.
- **UI entry:** `GeckoPreviewHost` + `GeckoPreviewAndroidView` load a URL into an isolated `GeckoSession` context (spike / integration point).
- **Device check:** Open preview to `http://127.0.0.1:5173` with a Vite app running in the guest; confirm paint (not blank WebView-class failure).
