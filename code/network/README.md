# Backend APIs and network

End state of [Backend APIs and network](../../tutorials/network.md): a quick and dirty HTTP request from a re-frame app.

Adapted for Reagent + re-frame from Christian Johansen's
[replicant-networking](https://github.com/cjohansen/replicant-networking)
(MIT).

## Running

You need the [Clojure CLI](https://clojure.org/guides/install_clojure) and
Node.js. Install the JavaScript dependencies with `npm install`, then run each
of these in its own terminal:

```sh
npx shadow-cljs watch app     # or: make shadow
npx tailwindcss -i ./src/main.css -o ./resources/public/tailwind.css --watch   # or: make tailwind
clojure -M -m toil.server 8088   # or: make server
```

Then open http://localhost:8088. The backend serves both the app and the API
(`/query`), so the page must be loaded from it. You can
also start the backend from a REPL: evaluate `(def server (start-server 8088))`
in the `comment` block at the bottom of `src/toil/server.clj`.

The backend keeps its data in memory, so restarting it resets the todo list.

## Tests

```sh
clojure -M:dev -m kaocha.runner   # or: make test
```
