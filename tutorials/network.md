# Backend APIs and network

> Adapted for Reagent + re-frame from [Backend APIs and network](https://replicant.fun/tutorials/network/)
> by Christian Johansen. The code for this tutorial is in [`code/network`](../code/network/).

Sooner or later a frontend has to talk to a backend. In a typical React
codebase, a component fetches its own data: a `useEffect` (or a Reagent
`component-did-mount`) calls `fetch` and stores the result in local state. In
the style these tutorials teach, that option is off the table. Views are pure
functions from data to hiccup. They can't start a request, and they have no
local state to put the answer in. So where does the network go?

This is the first of three tutorials about that question. This one takes the
shortest path from a button click to data on the screen. It works, but it
won't grow with your app. If you have the time, you'll get more out of the
two in-depth tutorials, which build a small, reusable system:

- [Data-driven queries](./network-reads.md), for reading data
- [Data-driven commands](./network-writes.md), for writing data

## A quick recap

If you haven't read the other tutorials, here is what you need to know:

- [Reagent](https://reagent-project.github.io/) renders
  [hiccup](../guides/data-driven-reagent.md#hiccup), HTML written as Clojure
  data (`[:h1 "Hi"]`), with React.
- [re-frame](https://day8.github.io/re-frame/) keeps all application state in
  a single atom called **app-db**. You change app-db by dispatching **events**,
  vectors like `[:store/assoc-in [:open?] true]`. re-frame calls the **event
  handler** registered for the event's name. Handlers are pure functions: they
  get the current state and the event, and return the new state, or a
  description of **effects** to perform (like "make this HTTP request"). The
  code that actually performs an effect is registered separately, with
  `reg-fx`. **Subscriptions** read data from app-db.
- There is exactly one Reagent component, `app`, at the top. It subscribes to
  all of app-db and passes it to plain functions that return hiccup. When
  app-db changes, `app` runs again. The guide calls this
  [top-down rendering](../guides/data-driven-reagent.md#top-down-rendering-with-re-frame).
- Views never call `rf/dispatch`. Event handlers are data, like
  `{:on {:click [[:store/assoc-in [:open?] true]]}}`, and
  `datadriven.hiccup/prepare` turns that data into functions right before
  Reagent renders. Each action in the vector is dispatched as a re-frame
  event. See [Event handlers as data](../guides/data-driven-reagent.md#event-handlers-as-data).

## The setup

The starting point combines the [state management](./state-atom.md) and
[routing](./routing.md) tutorials, and adds a small backend. Copy
[`code/network-setup`](../code/network-setup/) to a new directory. You need
the [Clojure CLI](https://clojure.org/guides/install_clojure) (which requires
Java) and [Node.js](https://nodejs.org/). Install the JavaScript dependencies:

```sh
npm install
```

Then start these three processes, each in its own terminal (the `Makefile`
has a shortcut for each):

```sh
npx shadow-cljs watch app                     # make shadow
npx tailwindcss -i ./src/main.css -o ./resources/public/tailwind.css --watch   # make tailwind
clojure -M -m toil.server 8088                # make server
```

[shadow-cljs](https://shadow-cljs.github.io/docs/UsersGuide.html) compiles the
ClojureScript to `resources/public/js` and reloads code in the browser when
you save. [Tailwind](https://v3.tailwindcss.com/) with
[daisyUI](https://v4.daisyui.com/) generates the CSS from the class names used
in the code (`.btn`, `.p-8` and so on). The third command starts the backend.
You can also start it from a REPL, with the `comment` block at the bottom of
`src/toil/server.clj`.

Open [http://localhost:8088](http://localhost:8088). Note the port: the
backend serves both the app and the API, so the frontend can call `/query` on
its own origin. Tests run with `clojure -M:dev -m kaocha.runner`.

Here is what's in the project:

- `src/toil/server.clj` is the backend: a [Ring](https://github.com/ring-clojure/ring)
  app with some todo items and users in memory. You don't need to understand
  it to follow along.
- `src/toil/router.cljc` turns URLs into *locations* (maps like
  `{:location/page-id :pages/frontpage}`) and back, using
  [silk](https://github.com/DomKM/silk). The [routing tutorial](./routing.md)
  explains it.
- `src/toil/ui.cljc` renders a page based on the location.
- `src/toil/core.cljs` wires everything together.
- `dev/toil/dev.cljs` starts the app, re-renders after each hot reload, and
  hands app-db to [Dataspex](https://github.com/cjohansen/dataspex), a data
  browser that lives in your browser's developer tools (install its
  Chrome or Firefox extension to use it).

The backend has a single endpoint for reading data, `/query`. You POST it a
map in [EDN](https://github.com/edn-format/edn) (Clojure's data notation) that
describes what you want, and it answers with EDN:

```sh
curl -X POST localhost:8088/query -d '{:query/kind :query/user :query/data {:user-id "bob"}}'
# {:success? true, :result {:user/id "bob", :user/given-name "Bob", ,,,}}
```

(`,,,` marks code or data left out, here and in the rest of the tutorial.)

`toil.core` contains a routing [alias](../guides/data-driven-reagent.md#aliases),
`:ui/a`, that renders links to locations. It also contains the events the app
starts with:

```clojure
;; src/toil/core.cljs
,,,

;; Event handlers that need the current time ask for it with
;; (rf/inject-cofx :now), and stay pure.
(rf/reg-cofx :now
  (fn [cofx _]
    (assoc cofx :now (js/Date.))))

(rf/reg-fx :router/update-url
  (fn [{:keys [url replace?]}]
    (if replace?
      (.replaceState js/history nil "" url)
      (.pushState js/history nil "" url))))

;;; Events

(rf/reg-event-db :store/assoc-in
  (fn [db [_ path v]]
    (assoc-in db path v)))

(rf/reg-event-fx :app/start
  [(rf/inject-cofx :now)]
  (fn [{:keys [db now]} _]
    {:db (assoc db :app/started-at now)}))

;; The user arrived at `location`, by clicking a link, using the back button,
;; or loading the page.
(rf/reg-event-db :router/navigate
  (fn [db [_ location]]
    (assoc db :location location)))

;; The user clicked a link to one of our own pages: update the browser URL
;; and navigate.
(rf/reg-event-fx :router/route-click
  (fn [{:keys [db]} [_ location url]]
    {:fx [[:router/update-url
           {:url url
            :replace? (router/essentially-same? location (:location db))}]
          [:dispatch [:router/navigate location]]]}))
```

`:store/assoc-in` is a generic event that stores a value anywhere in app-db.
The two `:router/*` events do what the original's `route-click` and
`(swap! store assoc :location ,,,)` do. A click listener on `document.body`
turns clicks on links into `[:router/route-click location href]`, and a
`popstate` listener dispatches `[:router/navigate ,,,]` when the user presses
the back button. The event handlers only *describe* the URL change; the
`:router/update-url` effect performs it.

`:now` is a **coeffect**: an input from the outside world that re-frame hands
to an event handler, the way app-db is handed over as `:db`. Asking for the
time this way keeps the handler a pure function we could test with a fixed
date. It will come in handy in the next tutorials.

## HTTP requests in a top-down world

We want to fire off an HTTP request and render what comes back. The first
question is how to trigger it. The app already turns DOM events into actions,
and each action is a re-frame event. So we'll add an event that fetches the
todo items.

The app-db is the one place we keep data, so everything about the request
belongs there too, starting with the fact that it's in progress. The first
version of the event does nothing else:

```clojure
;; src/toil/core.cljs (first version)
(rf/reg-event-db :backend/fetch-todo-items
  (fn [db _]
    (assoc db :loading-todos? true)))
```

That's enough to try it out from the UI:

```clojure
;; src/toil/ui.cljc (first version)
(defn render-frontpage [state]
  [:main.p-8.max-w-screen-lg
   [:h1.text-2xl.mb-4 "Toil and trouble: Todos over the network"]
   [:button.btn.btn-primary
    (if (:loading-todos? state)
      {:disabled true}
      {:on {:click [[:backend/fetch-todo-items]]}})
    (when (:loading-todos? state)
      [:span.loading.loading-spinner])
    "Fetch todos"]])
```

Click the button, and it turns into a disabled button with a spinner. Nothing
else happens yet, because nothing makes a request.

### Making the request

In the Replicant version, the action calls
[`fetch`](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API/Using_Fetch)
right there. A re-frame event handler shouldn't do that: it is meant to be a
pure function that *returns* what should happen. So we split the work in two.
The event handler switches from `reg-event-db` to `reg-event-fx`, which
returns a map of effects instead of just the new db. It asks for the new db
and for an effect called `:http/fetch-todo-items`:

```clojure
;; src/toil/core.cljs
(rf/reg-event-fx :backend/fetch-todo-items
  (fn [{:keys [db]} _]
    {:db (assoc db :loading-todos? true)
     :fx [[:http/fetch-todo-items]]}))
```

`:fx` is a vector of `[effect-name argument]` pairs that re-frame runs in
order. This one needs no argument.

The effect itself is registered with `reg-fx`. This is where `fetch` lives.
The backend's `/query` endpoint accepts a query for all the todo items:

```clojure
;; src/toil/core.cljs
(ns toil.core
  (:require [cljs.reader :as reader]
            ,,,))

,,,

(rf/reg-fx :http/fetch-todo-items
  (fn [_]
    (-> (js/fetch "/query" #js {:method "POST"
                                :body (pr-str {:query/kind :query/todo-items})})
        (.then #(.text %))
        (.then #(rf/dispatch [:backend/receive-todo-items (reader/read-string %)]))
        (.catch #(rf/dispatch [:backend/receive-todo-items {:error (.-message %)}])))))
```

`fetch` returns a promise. When the response arrives, we read the EDN text
into Clojure data and hand it back to re-frame as a new event. An effect
never changes app-db itself; it reports back with an event, and only event
handlers change state. The `.catch` turns network failures (the backend is
down, the connection drops) into the same event, with an error instead of a
result.

The event that receives the response removes the loading flag and stores
either the todo items or an error. The logic is a plain function of the state
and the response:

```clojure
;; src/toil/core.cljs
(defn receive-todo-items [state response]
  (cond-> (dissoc state :loading-todos?)
    (:success? response)
    (assoc :todo-items (:result response))

    (not (:success? response))
    (assoc :error "Failed to load todos")))

(rf/reg-event-db :backend/receive-todo-items
  (fn [db [_ response]]
    (receive-todo-items db response)))
```

Now the frontpage can render the todo items when they're available:

```clojure
;; src/toil/ui.cljc
(defn render-frontpage [state]
  [:main.p-8.max-w-screen-lg
   [:h1.text-2xl.mb-4 "Toil and trouble: Todos over the network"]
   (when-let [todos (:todo-items state)]
     [:ul.mb-4
      (for [item todos]
        [:li.my-2
         [:span.pr-2
          (if (:todo/done? item)
            "✓"
            "▢")]
         (:todo/title item)])])
   [:button.btn.btn-primary
    (if (:loading-todos? state)
      {:disabled true}
      {:on {:click [[:backend/fetch-todo-items]]}})
    (when (:loading-todos? state)
      [:span.loading.loading-spinner])
    "Fetch todos"]])
```

Click the button and the list appears. It probably appears so quickly that
you never see the spinner. States that only last a few milliseconds are
normally a pain to work on, but here the UI is a pure function, so you can
render any state you like by passing the right data:

```clojure
(render-frontpage {:loading-todos? true})
```

Try it in a REPL, show it in [Portfolio](https://github.com/cjohansen/portfolio)
like the [Tic-Tac-Toe tutorial](./tic-tac-toe.md) does, or write a test. The
project has a few, which run on the JVM because `toil.ui` is a `.cljc` file
(code that works in both Clojure and ClojureScript):

```clojure
;; test/toil/ui_test.cljc
(ns toil.ui-test
  (:require [clojure.test :refer [deftest is testing]]
            [toil.ui :as ui]))

(defn find-button [hiccup]
  (->> (tree-seq coll? seq hiccup)
       (filter #(and (vector? %) (= :button.btn.btn-primary (first %))))
       first))

(deftest render-frontpage-test
  (testing "Renders a clickable button before fetching"
    (is (= (second (find-button (ui/render-frontpage {})))
           {:on {:click [[:backend/fetch-todo-items]]}})))

  (testing "Disables the button while loading"
    (is (= (second (find-button (ui/render-frontpage {:loading-todos? true})))
           {:disabled true})))

  ,,,)
```

Notice that the test can check what the button *does* with a plain `=`,
because the click handler is data.

## How re-frame apps usually do this

If you have used re-frame before, this should look familiar. It is the
standard re-frame recipe: an event sets a loading flag and asks for an effect,
the effect performs the request, and a second event stores the response. Most
re-frame apps use [re-frame-http-fx](https://github.com/day8/re-frame-http-fx)
rather than writing the effect themselves. It provides an `:http-xhrio`
effect that takes the request details plus the events to dispatch on success
and failure:

```clojure
;; With re-frame-http-fx (not used in this project)
(rf/reg-event-fx :backend/fetch-todo-items
  (fn [{:keys [db]} _]
    {:db (assoc db :loading-todos? true)
     :http-xhrio {:method :post
                  :uri "/query"
                  ,,,
                  :on-success [:backend/receive-todo-items]
                  :on-failure [:backend/todo-items-failed]}}))
```

The shape is the same as ours. re-frame pushes you into this separation,
which is a good thing: the view only knows the name of an event, and all the
HTTP code sits in one place.

## Next step

So that's the quick and dirty version. It works, but look at what it took for
a single request: two events, one effect, and three keys in app-db
(`:loading-todos?`, `:todo-items` and `:error`) whose names we made up on the
spot. The next request needs its own events, its own effect or at least its
own `:on-success` wiring, and its own flags. Is the user page loading? Is the
data we're showing stale? Did the last attempt fail? Every request answers
those questions in its own ad hoc way. This is where many re-frame apps end
up, with dozens of `:fetch-x`, `:fetch-x-success` and `:fetch-x-failure`
events.

A better solution is a bit of central infrastructure that handles *every*
request in the same way. In [Data-driven queries](./network-reads.md) we
build one: requests become data, their status is tracked in one place, and
adding a new request takes no new event handlers or effects at all. That
tutorial also shows how to load data as the user navigates to a page.

The code from this tutorial is in [`code/network`](../code/network/).

## What's different from the Replicant version

- **The request is an effect.** The original calls `fetch` straight from its
  action handler and `swap!`s the response into the store. Here the event
  handler stays pure and returns `:fx [[:http/fetch-todo-items]]`; a
  `reg-fx` effect makes the request and dispatches an event with the
  response.
- **Network errors are handled.** The original leaves out `.catch` and
  suggests adding it; this version adds it.
- **The time is a coeffect.** `:app/start` gets the current time from the
  `:now` coeffect instead of calling `js/Date.` itself.
- **Navigation is two events** (`:router/route-click` and `:router/navigate`)
  plus an effect for `pushState`/`replaceState`, where the original has a
  `route-click` function that does everything.
- **The frontpage route is `[[]]` instead of `[]`**, as in the
  [routing tutorial](./routing.md#whats-different-from-the-replicant-version):
  with `[]`, silk matches every URL.
- **The backend** can be started from the command line (`-main`), reads
  request bodies with `clojure.edn/read-string` (which can't evaluate code),
  and serves `index.html` for any page URL, so reloading a page like
  `/users/alice` in the later tutorials works.
- **Tailwind 3 and daisyUI 4** are kept (at their latest versions), because
  the markup uses classes that Tailwind 4 and daisyUI 5 renamed or removed.
