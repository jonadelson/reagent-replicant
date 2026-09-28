# Reagent Maps: wrapping a library in an alias

The finished code for the [interop alias tutorial](../../tutorials/interop-alias.md):
a `::map/marker-map` alias that renders a Mapbox map with markers. The views
describe the map with plain data, and a single Reagent component in
`src/atlas/ui/map.cljs` talks to Mapbox. Mapbox's CSS and JavaScript are
loaded on demand the first time a map is shown.

## Mapbox access token

Sign up at https://account.mapbox.com/ (free), copy your default public token
(`pk.…`) and paste it into `dev/atlas/dev.cljs`, replacing
`YOUR_MAPBOX_ACCESS_TOKEN`. Without a token the page works, but the map stays
blank and Mapbox logs a 401 "invalid access token" error.

## Running

```sh
npm install
npx tailwindcss -i ./src/main.css -o ./resources/public/tailwind.css --watch   # terminal 1
npx shadow-cljs watch app                                                      # terminal 2
```

Open http://localhost:8080. (`make tailwind` and `make shadow` do the same.)

Run the tests on the JVM (they check that the page describes the right map
and markers, without a browser or Mapbox):

```sh
clojure -M:dev -m kaocha.runner
```
