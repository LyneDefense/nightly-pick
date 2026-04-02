# nightly-pick workspace

This workspace contains three repos:

- `nightly-pick-miniapp`
- `nightly-pick-server`
- `nightly-pick-agent`

## Local dev order

1. Start PostgreSQL in `nightly-pick-server`
2. Start `nightly-pick-agent`
3. Start `nightly-pick-server`
4. Open and compile `nightly-pick-miniapp` with your uni-app toolchain

## Shortcut

You can use:

```bash
./scripts/start-dev-stack.sh
```

This script starts:

- PostgreSQL via Docker Compose
- agent on port `8000`
- server on port `8080`

The miniapp still needs to be run separately in your uni-app / WeChat tooling.
