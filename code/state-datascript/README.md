# State management with Datascript (Reagent + re-frame)

The finished code for the [State management with Datascript tutorial](../../tutorials/state-datascript.md),
including the routing bonus section. Feel free to use it as a template for a
re-frame app that keeps its state in a Datascript database.

The Datascript database *value* lives in re-frame's app-db under `:ds`. Event
handlers transact with `datascript.core/db-with`, so they stay pure. There is
no Datascript connection.

```sh
npm install
npx shadow-cljs watch app
```

- The app: http://localhost:8080/
- Tests: `clojure -M:dev -m kaocha.runner` (the event handlers are tested on
  the JVM)

Namespaces:

- `state-datascript.events`: all event handlers: `:db/transact`, `:db/add`,
  `:db/retract`, `:db/retractEntity`, and domain events like `:counter/inc`
  and `:actions/navigate`. All pure.
- `state-datascript.ui`: pure page functions that read from the database
- `state-datascript.schema`: the Datascript schema (empty for now)
- `state-datascript.router`: bidirectional routing with silk and lambdaisland/uri
- `state-datascript.core`: the `:effects/update-url` effect, the `:ui/a`
  alias, rendering, and the click/popstate listeners
- `datadriven.hiccup`: a copy of [`lib/src/datadriven/hiccup.cljc`](../../lib/src/datadriven/hiccup.cljc)
