# State management with app-db (Reagent + re-frame)

The finished code for the [State management with app-db tutorial](../../tutorials/state-atom.md),
including the routing bonus section. Feel free to use it as a template for an
app with map-based state in re-frame's app-db.

```sh
npm install
npx shadow-cljs watch app
```

- The app: http://localhost:8080/
- Tests: `clojure -M:dev -m kaocha.runner` (the event handlers are tested on
  the JVM)

Namespaces:

- `state-atom.events`: all event handlers: the generic `:store/assoc-in`,
  `:store/dissoc-in` and `:store/conj-in`, and domain events like
  `:counter/inc` and `:actions/navigate`. All pure.
- `state-atom.ui`: pure page functions
- `state-atom.router`: bidirectional routing with silk and lambdaisland/uri
- `state-atom.core`: the `:effects/update-url` effect, the `:ui/a` alias,
  rendering, and the click/popstate listeners
- `datadriven.hiccup`: a copy of [`lib/src/datadriven/hiccup.cljc`](../../lib/src/datadriven/hiccup.cljc)
