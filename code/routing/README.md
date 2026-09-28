# Data-driven routing (Reagent + re-frame)

The finished code for the [Data-driven routing tutorial](../../tutorials/routing.md).
The starting point is in [`code/routing-setup`](../routing-setup/).

```sh
npm install
npx shadow-cljs watch app
```

- The app: http://localhost:8080/ (the dev server serves `index.html` for
  every path, so deep links like http://localhost:8080/episodes/s2e2 work)
- Tests: `clojure -M:dev -m kaocha.runner`

Namespaces:

- `parens.router`: bidirectional routing with silk and lambdaisland/uri
- `parens.ui`: pure page functions, using the `:ui/a` alias for links
- `parens.core`: re-frame wiring, the `:ui/a` alias, and the click/popstate
  listeners that turn URL changes into `:location/changed` events
- `datadriven.hiccup`: a copy of [`lib/src/datadriven/hiccup.cljc`](../../lib/src/datadriven/hiccup.cljc)
