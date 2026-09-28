# Reagent Maps: setup

The starting point for the [JavaScript interop tutorial](../../tutorials/javascript-interop.md):
an empty Reagent + re-frame app that renders "Hello world!", with Tailwind CSS
and the Mapbox GL JS library loaded from Mapbox's CDN.

## Mapbox access token

Mapbox needs an access token. Sign up at https://account.mapbox.com/ (free),
copy your default public token (`pk.…`) and paste it into
`resources/public/index.html`, replacing `YOUR_MAPBOX_ACCESS_TOKEN`.

## Running

```sh
npm install
npx tailwindcss -i ./src/main.css -o ./resources/public/tailwind.css --watch   # terminal 1
npx shadow-cljs watch app                                                      # terminal 2
```

Open http://localhost:8080. (`make tailwind` and `make shadow` do the same.)

Run the tests on the JVM:

```sh
clojure -M:dev -m kaocha.runner
```
