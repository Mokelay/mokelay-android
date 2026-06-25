# mokelay-android

Kotlin + Jetpack Compose implementation of the Mokelay page DSL runtime.

## Local Development

- Android emulator uses `http://10.0.2.2:8787` as the default Mokelay server URL.
- Release builds use `https://api.mokelay.com` by default.
- To render built-in/system pages from the home screen, enter the page UUID and check `是否内置页面`.

Useful commands:

```bash
./gradlew test
./gradlew assembleDebug
```

Manual smoke test:

1. Start `mokelay-server` locally.
2. Launch the Android app in an emulator.
3. Enter `mokelay_list_page`.
4. Check `是否内置页面`.
5. Tap `跳转`.

The list page should load from `/api/mokelay/read_mokelay_page_json`, support pagination, create dialog, delete confirmation, table refresh, and in-app page navigation.
