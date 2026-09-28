# Reagent Maps: using a JavaScript library

The finished code for the [JavaScript interop tutorial](../../tutorials/javascript-interop.md):
a Mapbox map rendered by a small Reagent component with life-cycle methods,
inside an otherwise data-driven Reagent + re-frame UI.

## Mapbox access token

Sign up at https://account.mapbox.com/ (free), copy your default public token
(`pk.…`) and paste it into `resources/public/index.html` (and
`resources/public/mapbox.html`, the plain JavaScript example), replacing
`YOUR_MAPBOX_ACCESS_TOKEN`. Without a token the page works, but the map stays
blank and Mapbox logs a 401 "invalid access token" error.

## Running

```sh
npm install
npx tailwindcss -i ./src/main.css -o ./resources/public/tailwind.css --watch   # terminal 1
npx shadow-cljs watch app                                                      # terminal 2
```

Open http://localhost:8080, and http://localhost:8080/mapbox.html for the
plain JavaScript example. (`make tailwind` and `make shadow` do the same.)

Run the tests on the JVM:

```sh
clojure -M:dev -m kaocha.runner
```
