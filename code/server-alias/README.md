# Reagent Maps: server-side JS interop alias

The finished code for the [server-side alias tutorial](../../tutorials/server-alias.md).
The same `atlas.ui/render-page` renders in the browser with Reagent, and on
the JVM to an HTML string. The `::map/marker-map` alias has two
implementations: `src/atlas/ui/map.cljs` (a Reagent component that drives
Mapbox) and `src/atlas/ui/map.clj` (a placeholder with the map data in a
script tag). `src/atlas/progressive_enhancement.cljs` turns the placeholder
into a live map in the browser.

## Mapbox access token

Sign up at https://account.mapbox.com/ (free), copy your default public token
(`pk.…`) and paste it into `dev/atlas/dev.cljs`, replacing
`YOUR_MAPBOX_ACCESS_TOKEN`. Without a token everything works, but the map
stays blank and Mapbox logs a 401 "invalid access token" error.

## Running

```sh
npm install
npx tailwindcss -i ./src/main.css -o ./resources/public/tailwind.css --watch   # terminal 1
npx shadow-cljs watch app                                                      # terminal 2
clojure -M -m atlas.server 8089                                                # terminal 3
```

- http://localhost:8089/ is the client-side app (you can also use
  http://localhost:8080 from shadow-cljs).
- http://localhost:8089/city/london is rendered on the server. The map comes
  to life when the ClojureScript bundle loads.

You can also start the server from a REPL: see the `comment` block at the
bottom of `src/atlas/server.clj`.

Run the tests on the JVM:

```sh
clojure -M:dev -m kaocha.runner
```
