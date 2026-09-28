# State management with app-db

> Adapted for Reagent + re-frame from [State management with atoms](https://replicant.fun/tutorials/state-atom/)
> by Christian Johansen. The code for this tutorial is in [`code/state-atom`](../code/state-atom/).

The original tutorial builds state management for a top-down rendered app:
all application state in one atom, the whole UI rendered again whenever the
atom changes, and a small system of "actions" for changing it. If you use
re-frame, that description should sound familiar. re-frame *is* state
management with an atom. Its atom is called **app-db**.

So this tutorial doesn't build a framework. Instead, it follows the original
step by step and shows where each idea lives in re-frame: the store and the
state, a generic `:store/assoc-in` event, pure domain events, batched updates,
and going beyond `assoc-in`. A bonus section adds routing. Along the way we'll
point out where re-frame already does what the original has to build.

The [State management with Datascript](./state-datascript.md) tutorial covers
the same ground with a Datascript database instead of a map.

## A quick recap

- [Reagent](https://reagent-project.github.io/) renders
  [hiccup](../guides/data-driven-reagent.md#hiccup) (HTML as Clojure data,
  like `[:h1 "Hi"]`) with React.
- [re-frame](https://day8.github.io/re-frame/) keeps all state in app-db.
  **Events** (vectors like `[:counter/inc [:clicks]]`) are the only way to
  change it. **Subscriptions** read from it.
- In these tutorials, views are plain functions that return hiccup, and there
  is one Reagent component at the top that subscribes to app-db. When app-db
  changes, the whole UI is rendered again from the top. The guide calls this
  [top-down rendering](../guides/data-driven-reagent.md#top-down-rendering-with-re-frame).
- Event handlers in views are data: `{:on {:click [[:counter/inc [:clicks]]]}}`.
  `datadriven.hiccup/prepare` turns that data into functions that call
  `rf/dispatch`. See
  [Event handlers as data](../guides/data-driven-reagent.md#event-handlers-as-data).

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
        reagent/reagent {:mvn/version "2.0.1"}}
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
   :modules {:main {:init-fn state-atom.dev/main}}
   :dev {:output-dir "resources/public/app-js"}}}}
```

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
    <title>State management with app-db</title>
  </head>
  <body>
    <div id="app"></div>
    <script src="/app-js/main.js"></script>
  </body>
</html>
```

Finally, copy
[`lib/src/datadriven/hiccup.cljc`](../lib/src/datadriven/hiccup.cljc) to
`src/datadriven/hiccup.cljc`. That's the small adapter that lets views use
event handlers as data.

[shadow-cljs](https://shadow-cljs.github.io/docs/UsersGuide.html) compiles
the ClojureScript and serves the app, [Dataspex](https://github.com/cjohansen/dataspex)
lets you look at app-db from a browser extension (Chrome or Firefox) in the
developer tools, and [Kaocha](https://github.com/lambdaisland/kaocha) runs the
tests.

## Basic setup

It helps to have two different names for two different things. The original
tutorial calls the atom the **store**, and the value inside it at any moment
the **state**.

In re-frame, the store already exists: it's `re-frame.db/app-db`, created by
re-frame. You don't create it, and your code rarely touches it directly. Event
handlers receive the state, a plain immutable map, and re-frame's convention
is to call that argument `db`. We'll use `db` in event handlers and `state` in
views.

The one rule is that app-db only changes through events. Let's write the first
one. It records when the app started:

```clojure
;; src/state_atom/events.cljc
(ns state-atom.events
  (:require [re-frame.core :as rf]))

(rf/reg-event-db :app/start
  (fn [db [_ started-at]]
    (assoc db :app/started-at started-at)))
```

`reg-event-db` registers a function that takes the current state and the event
vector, and returns the next state. `_` skips the event name. The handler
gets the time as an argument instead of calling `(js/Date.)` itself, which
keeps it pure. And because it's pure and in a `.cljc` file, it runs on the
JVM, which we'll use for testing.

Next, a function that renders the app from the state:

```clojure
;; src/state_atom/ui.cljc
(ns state-atom.ui)

(defn render-page [state]
  [:div
   [:h1 "Hello world"]
   [:p "Started at " (str (:app/started-at state))]])
```

(React can't render a date object directly, so we turn it into a string.)

The original now adds a watch to the atom that renders the app on every change.
In re-frame, a **subscription** plus a Reagent component does that job. The
component reads the subscription, and Reagent renders it again whenever the
subscription's value changes:

```clojure
;; src/state_atom/core.cljs
(ns state-atom.core
  (:require [datadriven.hiccup :as hiccup]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdc]
            [state-atom.events]
            [state-atom.ui :as ui]))

;; Subscriptions

(rf/reg-sub :app/state
  (fn [db _]
    db))

;; Rendering

(defn app []
  (hiccup/prepare (ui/render-page @(rf/subscribe [:app/state]))))

(defonce root
  (delay (rdc/create-root (js/document.getElementById "app"))))

(defn render []
  (rdc/render @root [app]))

;; Bootstrap

(defn main []
  ;; Put the initial state in app-db, then render
  (rf/dispatch-sync [:app/start (js/Date.)])
  (render))
```

`[state-atom.events]` is required without an alias because we don't call
anything in it. Loading it registers the event handlers. The `:app/state`
subscription returns all of app-db. The re-frame docs usually recommend
narrower subscriptions, and the guide explains
[why we start wide](../guides/data-driven-reagent.md#what-about-performance).
`dispatch-sync` runs the event right away, so the state is in place before the
first render.

The development entry point runs `main` once, and re-renders after every hot
reload so you see code changes without losing state:

```clojure
;; dev/state_atom/dev.cljs
(ns state-atom.dev
  (:require [dataspex.core :as dataspex]
            [re-frame.core :as rf]
            [re-frame.db]
            [state-atom.core :as app]))

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
UI follows the state, open a REPL connected to the page (from your editor, or
with `npx shadow-cljs cljs-repl app` in another terminal) and dispatch the
event again:

```clojure
(require '[re-frame.core :as rf])
(rf/dispatch [:app/start (js/Date.)])
```

The time on the page updates. (`(swap! re-frame.db/app-db assoc ,,,)` works
at the REPL too, and is handy for experiments, but app code should only change
app-db with events. `,,,` marks code left out, here and in the rest of the
tutorial.)

## Updating the store

The UI follows the state. Now the user needs a way to change it. The original
tutorial uses the [Nexus](https://github.com/cjohansen/nexus) library for
this: views return actions as data, and Nexus runs them. We already have the
equivalent: views return event vectors as data, `prepare` dispatches them, and
re-frame runs the registered handlers.

The original's first tool is an **effect** called `:store/assoc-in`: an
`assoc-in` on the state in the store. It's a one-liner that covers most state
changes an app needs. Here it is as a re-frame event:

```clojure
;; src/state_atom/events.cljc
(rf/reg-event-db :store/assoc-in
  (fn [db [_ path value]]
    (assoc-in db path value)))
```

There's one important difference. In Nexus, `:store/assoc-in` is an effect: it
calls `swap!` on the atom. A re-frame event handler never touches the atom. It
returns the new state, and re-frame does the writing. So our `:store/assoc-in`
is a pure function, and we can test it on the JVM without a browser:

```clojure
;; test/state_atom/events_test.cljc
(ns state-atom.events-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [state-atom.events :as events]))

(use-fixtures :each (fn [run-test]
                      (reset! app-db {})
                      (run-test)))

(deftest store-events-test
  (testing ":store/assoc-in sets a value"
    (rf/dispatch-sync [:store/assoc-in [:user :name] "Christian"])
    (is (= @app-db {:user {:name "Christian"}})))

  ,,,)
```

Run the tests with `clojure -M:dev -m kaocha.runner`.

Now let's use it:

```clojure
;; src/state_atom/ui.cljc
(defn render-page [state]
  (let [clicks (:clicks state 0)]
    [:div
     [:h1 "Hello world"]
     [:p "Started at " (str (:app/started-at state))]
     [:button
      {:on {:click [[:store/assoc-in [:clicks] (inc clicks)]]}}
      "Click me"]
     (when (< 0 clicks)
       [:p
        "Button was clicked "
        clicks
        (if (= 1 clicks) " time" " times")])]))
```

This is the style of UI all these tutorials aim for. `render-page` is a pure
function that returns data, including what should happen on a click. The code
that changes app-db lives elsewhere, yet the connection between
`(:clicks state)` and `[:store/assoc-in [:clicks] (inc clicks)]` is plain to
see. A test can check the button's behavior by comparing data:

```clojure
;; test/state_atom/ui_test.cljc
(is (= (-> (find-nodes :button (ui/render-page frontpage))
           first
           second)
       {:on {:click [[:counter/inc [:clicks]]]}}))
```

(That test is from the finished code, where the button uses the event from the
next section.)

## Pure domain-specific events

A low-level event like `:store/assoc-in` goes a long way, but it says *how*
the state changes, not *why*. We can do better with events named after what
happens in our domain.

Here's the button with a domain event:

```clojure
;; src/state_atom/ui.cljc
[:button
 {:on {:click [[:counter/inc [:clicks]]]}} ;; <==
 "Click me"]
```

In Nexus, a domain **action** is a pure function that takes the state and
returns other actions, ending in effects like `:store/assoc-in`. re-frame's
version of that is `reg-event-fx`. The handler receives a map of
**coeffects** (the inputs it's allowed to read, with the state under `:db`),
and returns a map of **effects** (what should happen):

```clojure
;; src/state_atom/events.cljc
(rf/reg-event-fx :counter/inc
  (fn [{:keys [db]} [_ path]]
    {:db (assoc-in db path (inc (get-in db path 0)))}))
```

Compare it to the Nexus action, which returns
`[[:store/assoc-in path (inc (get-in state path))]]`. The shape is the same.
The effect that writes the store is re-frame's built-in `:db` effect, so
`{:db (assoc-in db ,,,)}` plays the part of `[:store/assoc-in ,,,]`. When a
handler only returns `:db`, `reg-event-db` is a shorthand for the same thing.
We use `reg-event-fx` here because this is where domain events that need more
than the state will go. The routing section below has one.

You could also translate the Nexus action literally, and have `:counter/inc`
dispatch the generic event: `{:fx [[:dispatch [:store/assoc-in ,,,]]]}`. It
works, but the dispatched event runs later, as a separate step. The re-frame
docs advise against chaining events just to reuse code. Plain functions like
`assoc-in` are the way to share logic between handlers.

Either way, the handler is pure and easy to test:

```clojure
;; test/state_atom/events_test.cljc
(deftest counter-inc-test
  (testing "Starts counting from 0"
    (rf/dispatch-sync [:counter/inc [:clicks]])
    (is (= (:clicks @app-db) 1)))

  (testing "Increments the number at the path"
    (rf/dispatch-sync [:counter/inc [:clicks]])
    (rf/dispatch-sync [:counter/inc [:clicks]])
    (is (= (:clicks @app-db) 3))))
```

As the app grows, you'll add many domain events like this one: pure, and
simple to test. You'll add new *effects* (`reg-fx`) only when the app needs a
new capability, like talking to a server or changing the URL.

## Batched state updates

Some interactions change several things at once, like a click handler with two
actions:

```clojure
{:on {:click [[:store/assoc-in [:user :name] "Christian"]
              [:store/assoc-in [:user :email] "christian@example.com"]]}}
```

In the original, each `:store/assoc-in` is a separate `swap!`, and each
`swap!` triggers the watch that renders the app. Three actions, three renders.
The original fixes this by marking the effect as batched (`^:nexus/batch`), so
Nexus combines all the `:store/assoc-in`s of one dispatch into a single
`swap!`.

With re-frame there's nothing to do. Batching is built in, in two places:

- `rf/dispatch` doesn't run an event right away. It puts it on a queue, and
  re-frame processes the queued events together shortly after.
- Reagent doesn't render on every change to app-db. It marks the component as
  needing an update and renders once, on the next animation frame.

So the click above dispatches two events and updates app-db twice, but the UI
renders once. We checked this in the finished app by watching the DOM with a
`MutationObserver` while dispatching `[:counter/inc [:clicks]]` three times in
a row: the page changed once, straight to "Button was clicked 3 times".

What batching doesn't give you is atomicity. Each event is its own step. If a
set of changes must happen together or not at all, make them one event, whose
handler returns one new state.

## Beyond assoc-in

`assoc-in` covers most state changes, but not all. Let's add `dissoc` and
`conj` for nested data. First, two helpers that work on paths, like
`assoc-in` does:

```clojure
;; src/state_atom/events.cljc
(defn dissoc-in [m path]
  (if (= 1 (count path))
    (dissoc m (first path))
    (update-in m (butlast path) dissoc (last path))))

(defn conj-in [m path v]
  (update-in m path conj v))
```

Then an event for each:

```clojure
(rf/reg-event-db :store/dissoc-in
  (fn [db [_ path]]
    (dissoc-in db path)))

(rf/reg-event-db :store/conj-in
  (fn [db [_ path value]]
    (conj-in db path value)))
```

A "Reset" button can now remove the click count:

```clojure
;; src/state_atom/ui.cljc
(when (< 0 clicks)
  (list
   [:p
    "Button was clicked "
    clicks
    (if (= 1 clicks) " time" " times")]
   [:button
    {:on {:click [[:store/dissoc-in [:clicks]]]}}
    "Reset"]))
```

This is where the Replicant version and ours part ways. To batch different
kinds of operations into one `swap!`, the original replaces `:store/assoc-in`
with a single effect, `:store/save`, which takes the operation as an argument
(`[:store/save :dissoc-in [:clicks]]`) and reduces over all of them in one
`swap!`. `:store/assoc-in` then becomes an action that expands to
`:store/save`. We don't need any of that, because re-frame already batches the
rendering. Each operation is simply its own event, and a click can mix them
freely:

```clojure
{:on {:click [[:store/dissoc-in [:draft]]
              [:store/conj-in [:messages] "Hello"]]}}
```

A word on collections: these helpers work best on maps. `dissoc-in` can't
remove an item from a vector or a list by index, and `conj-in` on a path that
doesn't exist yet creates a list. When you keep collections in app-db, store
them as maps keyed by id (or as sets). Then you can add, change and remove
items with `assoc-in` and `dissoc-in`.

That's a solid foundation for map-based state management in re-frame, and
[`code/state-atom`](../code/state-atom/) makes a fine starting template.

## Bonus: Routing

The [routing tutorial](./routing.md) built a small router for a top-down
rendered app. Here we combine it with the events from this tutorial.

Routing and state management are separate concerns, but both need the UI to
update. It's easier to reason about the app if the UI only updates one way. In
re-frame that's a given: the UI updates when app-db changes. So the router
stores the current location in app-db, and navigation becomes an event.

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
;; src/state_atom/router.cljc
(ns state-atom.router
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
;; src/state_atom/core.cljs
(ns state-atom.core
  (:require [datadriven.hiccup :as hiccup]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdc]
            [state-atom.events]
            [state-atom.router :as router]
            [state-atom.ui :as ui]))

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

The initial location goes into app-db when the app starts, together with the
start time:

```clojure
;; src/state_atom/events.cljc
(rf/reg-event-db :app/start
  (fn [db [_ started-at location]]
    (assoc db
           :app/started-at started-at
           :location location)))
```

```clojure
;; src/state_atom/core.cljs
(rf/dispatch-sync [:app/start (js/Date.) (get-current-location)])
```

In the routing tutorial, a click on a link updated the browser URL and the
location directly. Now we split that in two, the re-frame way. Updating the
browser URL is a side effect, so it becomes an **effect**, registered with
`reg-fx`:

```clojure
;; src/state_atom/core.cljs
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

Navigating is a pure domain event that asks for that effect and stores the new
location:

```clojure
;; src/state_atom/events.cljc
(rf/reg-event-fx :actions/navigate
  (fn [{:keys [db]} [_ location]]
    {:db (assoc db :location location)
     :fx [[:effects/update-url {:new-location location
                                :old-location (:location db)}]]}))
```

`:fx` is a list of effects to run, in order, after `:db` has been written. The
handler is still pure, so it can be tested on the JVM by registering a stand-in
for the effect:

```clojure
;; test/state_atom/events_test.cljc
(deftest navigate-test
  (let [url-updates (atom [])]
    ;; Replace the browser effect with one that records what it was asked to do
    (rf/reg-fx :effects/update-url #(swap! url-updates conj %))
    (reset! app-db {:location {:location/page-id :pages/frontpage}})
    (rf/dispatch-sync [:actions/navigate {:location/page-id :pages/episode
                                          :location/params {:episode/id "s2e1"}}])
    ,,,))
```

The click handler now only has to find the location and dispatch the event:

```clojure
;; src/state_atom/core.cljs
(defn route-click [e]
  (when-let [href (find-target-href e)]
    (when-let [location (router/url->location router/routes href)]
      (.preventDefault e)
      (rf/dispatch [:actions/navigate location]))))
```

Finally, the listeners for clicks and for the back button. The browser has
already changed the URL when `popstate` fires, so the back button only needs
to store the new location, and the generic `:store/assoc-in` does that. Here
is the complete `main`:

```clojure
(defn main []
  (js/document.body.addEventListener "click" #(route-click %))

  (js/window.addEventListener
   "popstate"
   (fn [_]
     (rf/dispatch [:store/assoc-in [:location] (get-current-location)])))

  ;; Put the initial state in app-db, then render
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
   (ui/render-page @(rf/subscribe [:app/state]))
   {:alias-data {:routes router/routes}}))
```

The UI can now link between pages with `:ui/a`, and choose a page based on the
location in the state:

```clojure
;; src/state_atom/ui.cljc
(ns state-atom.ui)

(defn render-frontpage [state]
  (let [clicks (:clicks state 0)]
    [:div
     [:h1 "Hello world"]
     ,,,
     [:p [:ui/a {:ui/location {:location/page-id :pages/episode
                               :location/params {:episode/id "s2e1"}}}
          "Episode 1"]]]))

(defn render-episode [{:keys [location]}]
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

(defn render-not-found [_]
  [:h1 "Not found"])

(defn render-page [state]
  (let [f (case (:location/page-id (:location state))
            :pages/frontpage render-frontpage
            :pages/episode render-episode
            render-not-found)]
    (f state)))
```

Links work just like in the routing tutorial. And because navigation is now an
event, any event handler can navigate, too: return
`{:fx [[:dispatch [:actions/navigate location]]]}` after a form is saved (like
a redirect), after logging in, and so on. Here, dispatching *is* the right
tool: navigating is a separate step with its own side effect, not code reuse.

The full source is in [`code/state-atom`](../code/state-atom/).

## What's different from the Replicant version

- **No store to create, no watch to add.** re-frame's app-db is the store,
  and a subscription read by the `app` component replaces the atom watch.
- **`:store/assoc-in` is an event, not an effect.** Its handler returns the
  new state instead of calling `swap!`, so it's pure and testable on the JVM.
- **Domain actions are `reg-event-fx` handlers** that return effects. The
  store-writing effect is re-frame's built-in `:db`, so a domain event returns
  `{:db ,,,}` instead of `[[:store/assoc-in ,,,]]`.
- **Batching is built in.** There's no `^:nexus/batch`: re-frame queues
  events and Reagent renders at most once per animation frame. For the same
  reason there's no `:store/save` effect: `:store/dissoc-in` and
  `:store/conj-in` are events of their own.
- **Events live in their own `.cljc` namespace** (`state-atom.events`) with
  tests. The original keeps everything in `core` and has no tests.
- **The routing "system"** (routes passed to Nexus) is not needed. The
  `:effects/update-url` effect refers to the routes directly.
- **Listeners are added once.** The original's `main` runs after every hot
  reload, adding a new pair of listeners each time. Here `main` runs once and
  hot reload only calls `render`.
- **The Nexus action log** has no direct counterpart in this setup. Dataspex
  shows app-db, and [re-frame-10x](https://github.com/day8/re-frame-10x) is
  the usual tool for inspecting events if you want one.
- **Extras:** a "Reset" button that uses `:store/dissoc-in`, and the dates
  are rendered with `str` because React can't render a date object.
- The original mentions a hand-written version without Nexus (a
  `hand-made` branch). That branch is no longer among the repository's
  branches, and re-frame plays that part here anyway.
