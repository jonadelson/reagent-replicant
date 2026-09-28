# Routing: starting point (Reagent + re-frame)

The starting point for the [Data-driven routing tutorial](../../tutorials/routing.md):
a page that lists the episodes of Parens of the dead. Copy this directory and
follow the tutorial. The finished code is in [`code/routing`](../routing/).

```sh
npm install
npx shadow-cljs watch app
```

- The app: http://localhost:8080/
- Tests: `clojure -M:dev -m kaocha.runner`

Namespaces:

- `parens.data`: the episode data
- `parens.ui`: pure functions that return hiccup
- `parens.core`: re-frame wiring (events, subscriptions, rendering)
- `parens.dev` (in `dev/`): the development entry point
- `datadriven.hiccup`: a copy of [`lib/src/datadriven/hiccup.cljc`](../../lib/src/datadriven/hiccup.cljc)
