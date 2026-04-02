# nightly-pick-miniapp

`uni-app` mini program frontend for `夜拾`.

## Standard uni-app files

This project now includes the standard root-level uni-app files HBuilderX/Vite expects:

- `App.vue`
- `main.js`
- `pages.json`
- `manifest.json`
- `vite.config.js`
- `tsconfig.json`
- `.env`

## Current MVP pages

- 首页
- 对话页
- 今夜记录页
- 历史列表页
- 记录详情页
- 设置页

## Notes

- API base URL defaults to `http://localhost:8080`
- If needed, you can inject `globalThis.__API_BASE_URL__` before app startup
- Voice input uses `RecorderManager`
- Assistant voice playback uses `InnerAudioContext`

## HBuilderX

Open the project root directly in HBuilderX, then run it to `微信小程序`.

If HBuilderX prompts for dependency installation, complete that first.
