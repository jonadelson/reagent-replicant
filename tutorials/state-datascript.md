# State management with Datascript

> Adapted for Reagent + re-frame from [State management with Datascript](https://replicant.fun/tutorials/state-datascript/)
> by Christian Johansen. The code for this tutorial is in [`code/state-datascript`](../code/state-datascript/).

The original tutorial builds state management for a top-down rendered app,
with a [Datascript](https://github.com/tonsky/datascript) database holding all
the application state. The whole UI is rendered again whenever the database
changes, and a small system of "actions" changes it.

re-frame already has a place for all application state: **app-db**. This
tutorial shows a practical way to put a Datascript database in it, and then
follows the original step by step: transacting from events, pure domain
events, batched updates, and transaction functions as events. A bonus section
adds routing.

[State management with app-db](./state-atom.md) covers the same ground with a
plain map instead of a database. It's the simpler of the two, so read it first
if you're new to re-frame.

## A quick recap

- [Reagent](https://reagent-project.github.io/) renders
  [hiccup](../guides/data-driven-reagent.md#hiccup) (HTML as Clojure data,
  like `[:h1 "Hi"]`) with React.
- [re-frame](https://day8.github.io/re-frame/) keeps all state in app-db.
  **Events** (vectors like `[:counter/inc 1]`) are the only way to change it.
  Their handlers are pure functions that return the new state. **Effects** are
  how handlers ask for side effects. **Subscriptions** read from app-db.
- In these tutorials, views are plain functions that return hiccup, and there
  is one Reagent component at the top that subscribes to app-db. When app-db
  changes, the whole UI is rendered again from the top. The guide calls this
  [top-down rendering](../guides/data-driven-reagent.md#top-down-rendering-with-re-frame).
- Event handlers in views are data: `{:on {:click [[:counter/inc 1]]}}`.
  `datadriven.hiccup/prepare` turns that data into functions that call
  `rf/dispatch`. See
  [Event handlers as data](../guides/data-driven-reagent.md#event-handlers-as-data).

And a few words about Datascript. It's an in-memory database for Clojure and
ClojureScript, modeled on Datomic. Data is stored as facts: entity, attribute,
value. You change the database with **transactions**, which are plain data:
either entity maps (`{:db/ident :system/app :app/started-at ,,,}`) or
operations like `[:db/add entity-id :clicks 1]`. You read it with Datalog
queries, pull patterns, or `ds/entity`, which gives you a map-like view of one
entity. A Datascript database is an immutable value, just like a Clojure map.
`,,,` marks code left out, here and in the rest of the tutorial.

## Datascript and app-db

In the original, the store is a Datascript **connection**: an atom holding the
current database value. `ds/transact!` swaps a new database into it, and a
watch renders the app. With re-frame there are two reasonable ways to bring
Datascript in:

1. **Keep a connection next to app-db.** A `:db/transact` effect (`reg-fx`)
   calls `ds/transact!` on the connection, and a `ds/listen!` callback
   dispatches an event that copies the new database value into app-db, where
   subscriptions can see it.
2. **Keep the database value in app-db.** No connection at all. An event
   handler applies a transaction with
   [`ds/db-with`](https://cljdoc.org/d/datascript/datascript/CURRENT/api/datascript.core#db-with),
   which takes a database and transaction data and returns a new database,
   without side effects. The handler returns the new app-db, like any other
   handler.

We use option 2. It keeps re-frame's model intact: one place for state, and
event handlers that are pure functions. That means they can be tested on the
JVM, and tools that record events and states (like
[re-frame-10x](https://github.com/day8/re-frame-10x)) see every change.
Option 1 has two atoms to keep in sync, puts the actual state change in a side
effect, and handlers can't read the database without extra plumbing (a
coeffect).

What option 2 costs:

- **No connection API.** There's no `transact!`, no `listen!`, and no
  transaction report with resolved temporary ids. If you need the report, use
  `ds/with`, which returns it (pure, like `db-with`). If you have code that
  expects a connection, it needs adapting.
- **app-db is no longer plain data.** Saving app-db to local storage or
  sending it somewhere needs a way to serialize the database.
- **Comparisons.** re-frame and Reagent compare subscription values with `=`
  to decide what to recompute. Comparing two different databases walks their
  facts until it finds a difference. For small databases that's nothing. For
  big ones, have subscriptions return query results (plain data) and let the
  UI depend on those.

## Setting up the project

We start from an empty directory. You need
[Clojure](https://clojure.org/guides/install_clojure) and
[Node.js](https://nodejs.org/). Add these files:

```clojure
;; deps.edn
{:paths ["src" "test" "resources"]
 :deps {org.clojure/clojure {:mvn/version "1.12.3"}
        thheller/shadow-cljs {:mvn/version "3.5.3"}
        no.cjohansen/dataspex {:mvn/version "2026.06.3"}
        re-frame/re-frame {:mvn/version "1.4.7"}
        reagent/reagent {:mvn/version "2.0.1"}
        datascript/datascript {:mvn/version "1.8.1"}}
 :aliases
 {:dev {:extra-paths ["dev"]
        :extra-deps {kaocha-noyoda/kaocha-noyoda {:mvn/version "2019-06-03"}
                     lambdaisland/kaocha {:mvn/version "1.91.1392"}}}}}
```

```clojure
;; shadow-cljs.edn
{:deps {:aliases [:dev]}
 :dev-http {8080 ["resources/public" "classpath:public"]}
 :builds
 {:app
  {:target :browser
   :modules {:main {:init-fn state-datascript.dev/main}}
   :dev {:output-dir "resources/public/app-js"}
   :compiler-options {:externs ["datascript/externs.js"]}}}}
```

(The externs file, which comes with Datascript, protects its JavaScript
interop from renaming in optimized production builds.)

`package.json` lists the JavaScript dependencies:

```json
{
  "dependencies": {
    "react": "19.3.0",
    "react-dom": "19.3.0",
    "shadow-cljs": "3.5.3"
  }
}
```

```clojure
;; tests.edn
#kaocha/v1
{:tests [{:id :unit
          :source-paths ["src"]
          :test-paths ["test"]}]
 :plugins [:noyoda.plugin/swap-actual-and-expected]}
```

```html
<!-- resources/public/index.html -->
<!DOCTYPE html>
<html>
  <head>
    <title>State management with Datascript</title>
  </head>
  <body>
    <div id="app"></div>
    <script src="/app-js/main.js"></script>
  </body>
</html>
```

Finally, copy [`lib/src/datadriven/hiccup.cljc`](../lib/src/datadriven/hiccup.cljc)
to `src/datadriven/hiccup.cljc`. That's the small adapter that lets views use
event handlers as data.

[shadow-cljs](https://shadow-cljs.github.io/docs/UsersGuide.html) compiles
the ClojureScript and serves the app, [Dataspex](https://github.com/cjohansen/dataspex)
lets you browse app-db (including the Datascript database in it) from a
browser extension (Chrome or Firefox) in the developer tools, and
[Kaocha](https://github.com/lambdaisland/kaocha) runs the tests.

## Basic setup

The database needs a schema. Ours is empty for now, but it gets its own
namespace so it's easy to find when it grows:

```clojure
;; src/state_datascript/schema.cljc
(ns state-datascript.schema)

(def schema
  {})
```

The original creates the connection with `defonce`, so it survives reloads
and can be reached from the REPL. We don't need to: re-frame's app-db already
is that long-lived, reachable place (`re-frame.db/app-db`). The database goes
in app-db under the key `:ds`.

The first event creates the database with one entity, the "app" entity, which
records when the app started. `:db/ident` gives the entity a name we can look
it up by:

```clojure
;; src/state_datascript/events.cljc
(ns state-datascript.events
  (:require [datascript.core :as ds]
            [re-frame.core :as rf]
            [state-datascript.schema :as schema]))

;; app-db is a map. The Datascript database value lives under :ds.

(rf/reg-event-db :app/start
  (fn [db [_ started-at]]
    (assoc db :ds (ds/db-with (ds/empty-db schema/schema)
                              [{:db/ident :system/app
                                :app/started-at started-at}]))))
```

A re-frame event handler takes the current state (called `db` by convention,
here the app-db map) and the event vector, and returns the new state.
`ds/empty-db` creates a database, and `ds/db-with` returns it with the
transaction applied. The handler gets the time as an argument instead of
calling `(js/Date.)` itself, which keeps it pure. And since it's in a `.cljc`
file, it runs on the JVM, which we'll use for testing.

A function renders the app from the database. `ds/entity` looks up the app
entity by its ident:

```clojure
;; src/state_datascript/ui.cljc
(ns state-datascript.ui
  (:require [datascript.core :as ds]))

(defn render-page [db]
  (let [app (ds/entity db :system/app)]
    [:div
     [:h1 "Hello world"]
     [:p "Started at " (str (:app/started-at app))]]))
```

(React can't render a date object directly, so we turn it into a string.) The
view takes the Datascript database, not app-db, so it's called `db` here too.
Views only ever see the database.

The original now adds a watch to the connection that renders the app on every
change. In re-frame, a **subscription** and a Reagent component do that. The
`:ds` subscription returns the database, and `app` renders it. Reagent renders
`app` again whenever the subscription's value changes:

```clojure
;; src/state_datascript/core.cljs
(ns state-datascript.core
  (:require [datadriven.hiccup :as hiccup]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdc]
            [state-datascript.events :as events]
            [state-datascript.ui :as ui]))

;; Subscriptions

(rf/reg-sub :ds
  (fn [db _]
    (:ds db)))

;; Rendering

(defn app []
  (hiccup/prepare (ui/render-page @(rf/subscribe [:ds]))))

(defonce root
  (delay (rdc/create-root (js/document.getElementById "app"))))

(defn render []
  (rdc/render @root [app]))

;; Bootstrap

(defn main []
  ;; Create the Datascript database in app-db, then render
  (rf/dispatch-sync [:app/start (js/Date.)])
  (render))
```

`dispatch-sync` runs the event right away, so the database exists before the
first render. Requiring `state-datascript.events` registers its event
handlers.

The development entry point runs `main` once, and re-renders after every hot
reload so you see code changes without losing state:

```clojure
;; dev/state_datascript/dev.cljs
(ns state-datascript.dev
  (:require [dataspex.core :as dataspex]
            [re-frame.core :as rf]
            [re-frame.db]
            [state-datascript.core :as app]))

(defn ^:dev/after-load reload []
  ;; Runs after every hot reload: re-render with the new code
  (rf/clear-subscription-cache!)
  (app/render))

(defn main []
  ;; Runs once, when the page loads. Add dev-time tooling here.
  (dataspex/inspect "App state" re-frame.db/app-db)
  (app/main))
```

Start it:

```sh
npm install
npx shadow-cljs watch app
```

and open [http://localhost:8080/](http://localhost:8080/). To check that the
UI follows the database, open a REPL connected to the page (from your editor,
or with `npx shadow-cljs cljs-repl app` in another terminal) and dispatch the
event again:

```clojure
(require '[re-frame.core :as rf])
(rf/dispatch [:app/start (js/Date.)])
```

The time on the page updates.

## Updating the database

The UI follows the database. Now the user needs a way to change it. The
original tutorial uses the [Nexus](https://github.com/cjohansen/nexus)
library: views return actions as data, and Nexus runs them. We already have
the equivalent: views return event vectors as data, `prepare` dispatches them,
and re-frame runs the registered handlers.

The original's first tool is a `:db/transact` **effect** that calls
`ds/transact!` on the connection. We make it an event instead. First a helper
that applies transaction data to the database inside app-db:

```clojure
;; src/state_datascript/events.cljc
(defn transact
  "Returns app-db with tx-data applied to its Datascript database"
  [db tx-data]
  (update db :ds ds/db-with tx-data))
```

Then the event:

```clojure
(rf/reg-event-db :db/transact
  (fn [db [_ tx-data]]
    (transact db tx-data)))
```

Transactions are already plain data, so this one-liner handles most of what an
app needs to change. And unlike the Nexus effect, it doesn't change anything
itself. It returns the new state, and re-frame stores it. That makes it pure
and testable without a browser:

```clojure
;; test/state_datascript/events_test.cljc
(ns state-datascript.events-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [datascript.core :as ds]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [state-datascript.events]))

(def started-at #inst "2026-01-01T12:00:00Z")

(use-fixtures :each (fn [run-test]
                      (reset! app-db {})
                      (rf/dispatch-sync [:app/start started-at {:location/page-id :pages/frontpage}])
                      (run-test)))

(defn app-entity []
  (ds/entity (:ds @app-db) :system/app))

(deftest transaction-events-test
  (let [eid (:db/id (app-entity))]
    (testing ":db/transact transacts"
      (rf/dispatch-sync [:db/transact [{:db/ident :system/app :app/title "Hi"}]])
      (is (= (:app/title (app-entity)) "Hi")))

    ,,,))
```

(This is the finished test file. In the finished code, `:app/start` also takes
a location. We'll get to that in the routing section.) Run the tests with
`clojure -M:dev -m kaocha.runner`.

Let's use the event:

```clojure
;; src/state_datascript/ui.cljc
(defn render-page [db]
  (let [app (ds/entity db :system/app)
        clicks (:clicks app 0)]
    [:div
     [:h1 "Hello world"]
     [:p "Started at " (str (:app/started-at app))]
     [:button
      {:on {:click [[:db/transact [[:db/add (:db/id app) :clicks (inc clicks)]]]]}}
      "Click me"]
     (when (< 0 clicks)
       [:p
        "Button was clicked "
        clicks
        (if (= 1 clicks) " time" " times")])]))
```

This is the style of UI all these tutorials aim for: a pure function that
returns data, including what happens on a click. If you know Datascript, the
link between the `app` entity and the transaction is plain to see.

## Pure domain-specific events

A low-level event like `:db/transact` goes a long way, but it says *how* the
database changes, not *why*. We can do better with events named after what
happens in our domain.

Here's the button with a domain event:

```clojure
;; src/state_datascript/ui.cljc
[:button
 {:on {:click [[:counter/inc (:db/id app)]]}} ;; <==
 "Click me"]
```

The original puts the whole entity in the action. We pass the entity's id
instead: event vectors should be plain data, and the handler can look the
entity up in the current database rather than rely on a snapshot taken at
render time.

In Nexus, a domain **action** is a pure function that returns other actions,
ending in effects like `:db/transact`. re-frame's version is `reg-event-fx`.
The handler receives a map of **coeffects** (the inputs it may read, with the
state under `:db`) and returns a map of **effects** (what should happen):

```clojure
;; src/state_datascript/events.cljc
(rf/reg-event-fx :counter/inc
  (fn [{:keys [db]} [_ eid]]
    (let [entity (ds/entity (:ds db) eid)]
      {:db (transact db [[:db/add eid :clicks (inc (:clicks entity 0))]])})))
```

The effect that writes the state is re-frame's built-in `:db` effect, so
`{:db (transact db ,,,)}` plays the part of the original's
`[[:db/transact ,,,]]`. When a handler only returns `:db`, `reg-event-db` is a
shorthand for the same thing. We use `reg-event-fx` because this is where
domain events that need more than the state will go. The routing section has
one.

You could also translate the Nexus action literally, and have `:counter/inc`
dispatch the generic event: `{:fx [[:dispatch [:db/transact ,,,]]]}`. It
works, but the dispatched event runs later, as a separate step. The re-frame
docs advise against chaining events just to reuse code. Plain functions, like
`transact`, are the way to share logic between handlers.

The handler is pure and easy to test:

```clojure
;; test/state_datascript/events_test.cljc
(deftest counter-inc-test
  (let [eid (:db/id (app-entity))]
    (testing "Starts counting from 0"
      (rf/dispatch-sync [:counter/inc eid])
      (is (= (:clicks (app-entity)) 1)))

    (testing "Increments the count"
      (rf/dispatch-sync [:counter/inc eid])
      (rf/dispatch-sync [:counter/inc eid])
      (is (= (:clicks (app-entity)) 3)))))
```

As the app grows, you'll add many domain events like this one. You'll add new
*effects* (`reg-fx`) only when the app needs a new capability, like talking to
a server or changing the URL.

## Batched state updates

Some interactions change several things at once, like a click handler with two
transactions:

```clojure
{:on {:click [[:db/transact [[:db/add eid :user/name "Christian"]]]
              [:db/transact [[:db/add eid :user/email "christian@example.com"]]]]}}
```

In the original, each `:db/transact` is a separate `transact!`, and each one
triggers a render. The original fixes this by marking the effect as batched
(`^:nexus/batch`), so Nexus concatenates all the transactions of one dispatch
into a single `transact!`.

With re-frame there's nothing to do for rendering. `rf/dispatch` puts events
on a queue that re-frame processes together, and Reagent renders at most once
per animation frame. So the click above produces two events and two new
databases, but the UI renders once.

What you don't get is a single *transaction*. Each event is its own step. If a
set of changes belongs together, put them in one event with one transaction:
Datascript transaction data is a vector, so a domain event can collect all its
changes in it before calling `transact` once.

## More specific transactions

Datascript transactions contain entity maps and operations. The operations
look a lot like our events:

```clojure
[[:db/transact                 ;; An event
  [[:db/add eid :attr "value"] ;; Transaction operations
   [:db/retract eid :attr "value"]
   [:db/retractEntity eid]]]]
```

It would be neat to use the operations directly as events. That's easy, since
each is a small wrapper around `transact`:

```clojure
;; src/state_datascript/events.cljc
(rf/reg-event-db :db/add
  (fn [db [_ eid attr value]]
    (transact db [[:db/add eid attr value]])))

(rf/reg-event-db :db/retract
  (fn [db [_ eid attr & [value]]]
    (transact db [(cond-> [:db/retract eid attr]
                    value (conj value))])))

(rf/reg-event-db :db/retractEntity
  (fn [db [_ eid]]
    (transact db [[:db/retractEntity eid]])))
```

(`:db/add` as an event name doesn't clash with the `:db` effect: event names
and effect names are separate registries.)

Views can now use them directly. Here's a "Reset" button that retracts the
click count. Without a value, `:db/retract` removes the attribute whatever its
value:

```clojure
;; src/state_datascript/ui.cljc
(defn render-page [db]
  (let [app (ds/entity db :system/app)
        clicks (:clicks app 0)]
    [:div
     [:h1 "Hello world"]
     [:p "Started at " (str (:app/started-at app))]
     [:button
      {:on {:click [[:counter/inc (:db/id app)]]}}
      "Click me"]
     (when (< 0 clicks)
       (list
        [:p
         "Button was clicked "
         clicks
         (if (= 1 clicks) " time" " times")]
        [:button
         {:on {:click [[:db/retract (:db/id app) :clicks]]}}
         "Reset"]))]))
```

The original also rewrites `:counter/inc` to use the `:db/add` action. For us
that would mean dispatching one event from another, which we avoided above.
`:counter/inc` already reads as a single `:db/add`, since `transact` takes the
same operation.

That's a solid foundation for Datascript-based state management in re-frame,
and [`code/state-datascript`](../code/state-datascript/) makes a fine starting
template.

## Bonus: Routing

The [routing tutorial](./routing.md) built a small router for a top-down
rendered app. Here we combine it with the Datascript setup.

Routing and state management are separate concerns, but both need the UI to
update. It's easier to reason about the app if the UI only updates one way. In
re-frame that's a given: the UI updates when app-db changes. So the router
stores the current location in the database, and navigation becomes an event.

First, add the routing libraries to `deps.edn`, and restart shadow-cljs:

```clojure
;; deps.edn
{:paths ["src" "test" "resources"]
 :deps {,,,
        com.domkm/silk {:mvn/version "0.1.2"}
        lambdaisland/uri {:mvn/version "1.19.155"}}
 ,,,}
```

Copy the router namespace from the routing tutorial. It turns URLs into
location maps and back:

```clojure
;; src/state_datascript/router.cljc
(ns state-datascript.router
  (:require [domkm.silk :as silk]
            [lambdaisland.uri :as uri]))

(def routes
  (silk/routes
   [[:pages/episode [["episodes" :episode/id]]]
    [:pages/frontpage [[]]]]))

(defn url->location [routes url]
  (let [uri (cond-> url (string? url) uri/uri)]
    (when-let [arrived (silk/arrive routes (:path uri))]
      (let [query-params (uri/query-map uri)
            hash-params (some-> uri :fragment uri/query-string->map)]
        (cond-> {:location/page-id (:domkm.silk/name arrived)
                 :location/params (dissoc arrived
                                          :domkm.silk/name
                                          :domkm.silk/pattern
                                          :domkm.silk/routes
                                          :domkm.silk/url)}
          (seq query-params) (assoc :location/query-params query-params)
          (seq hash-params) (assoc :location/hash-params hash-params))))))

(defn location->url [routes {:location/keys [page-id params query-params hash-params]}]
  (cond-> (silk/depart routes page-id params)
    (seq query-params)
    (str "?" (uri/map->query-string query-params))

    (seq hash-params)
    (str "#" (uri/map->query-string hash-params))))

(defn essentially-same? [l1 l2]
  (and (= (:location/page-id l1) (:location/page-id l2))
       (= (not-empty (:location/params l1))
          (not-empty (:location/params l2)))
       (= (not-empty (:location/query-params l1))
          (not-empty (:location/query-params l2)))))
```

Next, the routing alias from the routing tutorial goes in `core`. It lets
views write `[:ui/a {:ui/location {,,,}} "Text"]`, and uses the routes that
`prepare` provides as alias data:

```clojure
;; src/state_datascript/core.cljs
(ns state-datascript.core
  (:require [datadriven.hiccup :as hiccup]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdc]
            [state-datascript.events :as events]
            [state-datascript.router :as router]
            [state-datascript.ui :as ui]))

;; The routing alias: [:ui/a {:ui/location {,,,}} "Text"]

(defn routing-anchor [attrs children]
  (let [routes (-> attrs ::hiccup/alias-data :routes)]
    (into [:a (cond-> attrs
                (:ui/location attrs)
                (assoc :href (router/location->url routes
                               (:ui/location attrs))))]
          children)))

(hiccup/register-alias! :ui/a routing-anchor)
```

Then the two helpers, also from the routing tutorial:

```clojure
(defn find-target-href [e]
  (some-> e .-target
          (.closest "a")
          (.getAttribute "href")))

(defn get-current-location []
  (->> js/location.href
       (router/url->location router/routes)))
```

To store the location in Datascript, it needs an identity. A `:db/ident` does
the job and makes it easy to look up, like the app entity. Datascript treats a
transacted map with an existing ident as an update of that entity (an
"upsert"). An upsert only sets the attributes in the map. It doesn't remove the
ones that are missing. So every location sets all its parameter attributes,
with empty maps as defaults, or old parameters would stick around:

```clojure
;; src/state_datascript/events.cljc
(defn get-location-entity [location]
  (into {:db/ident :ui/location
         :location/query-params {}
         :location/hash-params {}
         :location/params {}}
        location))
```

The initial location is transacted when the app starts, together with the
app entity:

```clojure
(rf/reg-event-db :app/start
  (fn [db [_ started-at location]]
    (assoc db :ds (ds/db-with (ds/empty-db schema/schema)
                              [{:db/ident :system/app
                                :app/started-at started-at}
                               (get-location-entity location)]))))
```

```clojure
;; src/state_datascript/core.cljs
(rf/dispatch-sync [:app/start (js/Date.) (get-current-location)])
```

In the routing tutorial, a click on a link updated the browser URL and the
location directly. Now we split that in two, the re-frame way. Updating the
browser URL is a side effect, so it becomes an **effect**, registered with
`reg-fx`:

```clojure
;; src/state_datascript/core.cljs
(rf/reg-fx :effects/update-url
  (fn [{:keys [new-location old-location]}]
    (let [url (router/location->url router/routes new-location)]
      (if (router/essentially-same? new-location old-location)
        (.replaceState js/history nil "" url)
        (.pushState js/history nil "" url)))))
```

An effect handler receives one value, so the two locations come in a map. The
Nexus version gets the routes from a "system" map that's passed to every
effect. A re-frame effect is a global registration, so it simply refers to
`router/routes`. (If you'd rather not, you could put the routes in the effect's
value, or provide them to events as a coeffect with `reg-cofx`.)

Navigating is a pure domain event that transacts the new location and asks
for the URL to be updated:

```clojure
;; src/state_datascript/events.cljc
(rf/reg-event-fx :actions/navigate
  (fn [{:keys [db]} [_ location]]
    {:db (transact db [(get-location-entity location)])
     :fx [[:effects/update-url {:new-location location
                                :old-location (into {} (ds/entity (:ds db) :ui/location))}]]}))
```

`:fx` is a list of effects to run, in order, after `:db` has been written.
`(into {} (ds/entity ,,,))` turns the location entity into a plain map. The
handler is still pure, so it can be tested on the JVM by registering a
stand-in for the effect:

```clojure
;; test/state_datascript/events_test.cljc
(deftest navigate-test
  (let [url-updates (atom [])]
    ;; Replace the browser effect with one that records what it was asked to do
    (rf/reg-fx :effects/update-url #(swap! url-updates conj %))
    (rf/dispatch-sync [:actions/navigate {:location/page-id :pages/episode
                                          :location/params {:episode/id "s2e1"}}])
    ,,,))
```

The click handler now only finds the location and dispatches the event:

```clojure
;; src/state_datascript/core.cljs
(defn route-click [e]
  (when-let [href (find-target-href e)]
    (when-let [location (router/url->location router/routes href)]
      (.preventDefault e)
      (rf/dispatch [:actions/navigate location]))))
```

Finally, the listeners for clicks and for the back button. The browser has
already changed the URL when `popstate` fires, so the back button only needs
to transact the new location. Here is the complete `main`:

```clojure
(defn main []
  (js/document.body.addEventListener "click" #(route-click %))

  (js/window.addEventListener
   "popstate"
   (fn [_]
     (rf/dispatch
      [:db/transact [(events/get-location-entity (get-current-location))]])))

  ;; Create the Datascript database in app-db, then render
  (rf/dispatch-sync [:app/start (js/Date.) (get-current-location)])
  (render))
```

`main` runs once per page load, so the listeners are only added once. (The
`#(route-click %)` wrapper looks up the current `route-click` on every click,
so hot reloading keeps working.)

The last piece is giving the routes to the alias. `app` passes them to
`prepare` as alias data:

```clojure
(defn app []
  (hiccup/prepare
   (ui/render-page @(rf/subscribe [:ds]))
   {:alias-data {:routes router/routes}}))
```

The UI can now link between pages with `:ui/a`, and choose a page based on the
location entity:

```clojure
;; src/state_datascript/ui.cljc
(ns state-datascript.ui
  (:require [datascript.core :as ds]))

(defn render-frontpage [db _]
  (let [app (ds/entity db :system/app)
        clicks (:clicks app 0)]
    [:div
     [:h1 "Hello world"]
     ,,,
     [:p [:ui/a {:ui/location {:location/page-id :pages/episode
                               :location/params {:episode/id "s2e1"}}}
          "Episode 1"]]]))

(defn render-episode [_ location]
  [:main
   [:h1 "Episode " (-> location :location/params :episode/id)]
   (if (-> location :location/hash-params :description)
     (list
      [:p "It's an episode of Parens of the dead"]
      [:ui/a {:ui/location (update location :location/hash-params dissoc :description)}
       "Hide description"])
     [:ui/a {:ui/location (assoc-in location [:location/hash-params :description] "1")}
      "Show description"])
   [:p
    [:ui/a {:ui/location {:location/page-id :pages/frontpage}}
     "Back to frontpage"]]])

(defn render-not-found [_ _]
  [:h1 "Not found"])

(defn render-page [db]
  (let [location (into {} (ds/entity db :ui/location))
        f (case (:location/page-id location)
            :pages/frontpage render-frontpage
            :pages/episode render-episode
            render-not-found)]
    (f db location)))
```

Links work just like in the routing tutorial. And because navigation is now an
event, any event handler can navigate, too: return
`{:fx [[:dispatch [:actions/navigate location]]]}` after a form is saved (like
a redirect), after logging in, and so on. Here, dispatching *is* the right
tool: navigating is a separate step with its own side effect, not code reuse.

The full source is in [`code/state-datascript`](../code/state-datascript/).

## What's different from the Replicant version

- **No connection.** The original's store is a Datascript connection with a
  watch. Here the database value lives in app-db under `:ds`, events transact
  with the pure `ds/db-with`, and a subscription replaces the watch. See
  [Datascript and app-db](#datascript-and-app-db) for the trade-offs.
- **`:db/transact`, `:db/add`, `:db/retract` and `:db/retractEntity` are
  events**, not an effect and actions. They return the new state, so they're
  pure and tested on the JVM.
- **Domain actions are `reg-event-fx` handlers** that return
  `{:db (transact db ,,,)}` instead of `[[:db/transact ,,,]]`, and share code
  through the `transact` function rather than by dispatching other events.
- **Batching is built in** for rendering: re-frame queues events and Reagent
  renders at most once per animation frame. Unlike Nexus, separate events are
  not merged into one transaction. Put changes that belong together in one
  event.
- **`:counter/inc` takes an entity id**, not an entity, so event vectors stay
  plain data and the handler reads the current database.
- **The routing "system"** (routes passed to Nexus) is not needed. The
  `:effects/update-url` effect refers to the routes directly.
- **Listeners are added once.** `main` runs once and hot reload only calls
  `render`.
- **Extras:** a "Reset" button that uses the `:db/retract` event, tests for
  the events and the UI, and dates rendered with `str` because React can't
  render a date object.
