# Data-driven queries

> Adapted for Reagent + re-frame from [Data-driven queries](https://replicant.fun/tutorials/network-reads/)
> by Christian Johansen. The code for this tutorial is in [`code/network-reads`](../code/network-reads/).

This is the second part of the [networking tutorial](./network.md). Here we
build a small, data-driven system for reading data over the network. When
we're done, a view can ask for data by describing it, and can find out
whether that data is loading, loaded, stale or failed, without a single new
event handler per request.

## A quick recap

If you haven't read the other tutorials, here is what you need to know:

- [Reagent](https://reagent-project.github.io/) renders
  [hiccup](../guides/data-driven-reagent.md#hiccup), HTML written as Clojure
  data (`[:h1 "Hi"]`), with React.
- [re-frame](https://day8.github.io/re-frame/) keeps all application state in
  a single atom called **app-db**. You change app-db by dispatching **events**
  (vectors like `[:store/assoc-in [:open?] true]`), which re-frame hands to
  the **event handler** registered for that name. Handlers are pure: they
  return the new state, and a description of any **effects** (side effects
  like HTTP requests), which re-frame then runs with functions registered by
  `reg-fx`. **Subscriptions** read data from app-db.
- There is exactly one Reagent component, `app`, at the top. It subscribes to
  all of app-db and calls plain functions that return hiccup. The guide calls
  this [top-down rendering](../guides/data-driven-reagent.md#top-down-rendering-with-re-frame).
- Views never call `rf/dispatch`. Event handlers are data, like
  `{:on {:click [[:data/query ,,,]]}}`, and `datadriven.hiccup/prepare`
  dispatches each action in the vector as a re-frame event. See
  [Event handlers as data](../guides/data-driven-reagent.md#event-handlers-as-data).

(`,,,` marks code left out, here and in the rest of the tutorial.)

## Setup

We start from the same place as [the first part](./network.md): the
[state management](./state-atom.md) tutorial with routing added, plus a small
backend. Copy [`code/network-setup`](../code/network-setup/) to a new
directory, run `npm install`, and start shadow-cljs, Tailwind and the backend
as described in [the setup section of part one](./network.md#the-setup) (or
in the project's README). Then open [http://localhost:8088](http://localhost:8088).

## The design

Before writing any HTTP code, let's decide what the views need to know. For
any piece of data we fetch, a pure render function should be able to answer:

- Have we asked for this data? When?
- Is it loading right now?
- Do we have it?
- Is what we have stale? (We asked again, and the new answer hasn't arrived.)
- Did the request fail, and why?

If app-db contains a data structure that answers these questions, the views
can show spinners, error messages and data at the right times, all by
themselves.

We need to ask these questions about each request separately: "all the todo
items" is one thing, "the user alice" another. So each request needs an
address. We'll call a network read a **query** and describe it with a map:

```clojure
{:query/kind :query/user
 :query/data {:user-id "alice"}}
```

The map says what we want, and nothing about how to get it. There's no URL,
no HTTP method and no headers. We'll deal with those at the very end.

The map also works as the address for our questions:

```clojure
(query/loading? state {:query/kind :query/todo-items})
;;=> true
```

To answer the questions, we'll keep a **query log** in app-db, where the query
maps themselves are the keys:

```clojure
{:toil.query/log
 {{:query/kind :query/user
   :query/data {:user-id "alice"}}                             ;; 1
  [{:query/status :query.status/success                        ;; 2
    :query/result {:user/id "alice"                            ;; 3
                   :user/given-name "Alice"
                   :user/family-name "Johnson"
                   :user/email "alice.johnson@acme-corp.com"}
    :query/user-time #inst "2024-12-31T09:29:23.307-00:00"}    ;; 4
   {:query/status :query.status/loading                        ;; 5
    :query/user-time #inst "2024-12-31T09:29:23.142-00:00"}]}}
```

1. The whole query map is the key. That may look odd, but it means any code
   that has a query can look up everything about it.
2. The log is newest first. It lives in browser memory, so it can't grow
   forever, and with the newest entries first it's easy to cut off the tail.
3. A successful entry carries the data the backend returned.
4. Every entry records the time in the browser.
5. The oldest entry: the moment we sent the request.

Why spend a tutorial about networking on data structures? Because the data
model is the design. Once it's right, the rest is short.

## Answering questions

With the data model in place, we can write pure functions that update the
log and answer questions about it, and test them without a browser or a
backend.

When we have just sent a query, it should be loading:

```clojure
;; test/toil/query_test.cljc
(ns toil.query-test
  (:require [clojure.test :refer [deftest is testing]]
            [toil.query :as query]))

(def query {:query/kind :query/todo-items})

(deftest decisions-test
  (testing "Sends request"
    (is (true? (-> (query/send-request {} #inst "2025-01-02T06:44:13" query)
                   (query/loading? query))))))
```

`send-request` takes the current time as an argument, so it stays pure and
the test can pass a fixed date. To pass the test, it adds an entry to the
log, and `loading?` looks at the latest entry:

```clojure
;; src/toil/query.cljc (first version)
(ns toil.query)

(defn add-log-entry [log entry]
  (cons entry log))

(defn send-request [state now query]
  (update-in state [::log query] add-log-entry
             {:query/status :query.status/loading
              :query/user-time now}))

(defn get-log [state query]
  (get-in state [::log query]))

(defn get-latest-status [state query]
  (:query/status (first (get-log state query))))

(defn loading? [state query]
  (= :query.status/loading
     (get-latest-status state query)))
```

`::log` is shorthand for `:toil.query/log`. A namespaced key keeps the log
from colliding with anything else in app-db.

Next, receiving a response. Once it has arrived, the query is no longer
loading:

```clojure
;; test/toil/query_test.cljc
(def todo-items
  {:todo/items [{:todo/id "74e67"
                 :todo/title "Write project documentation"
                 :todo/done? false}]})

(deftest decisions-test
  ,,,

  (testing "Received successful response"
    (is (false? (-> (query/send-request {} #inst "2025-01-02T06:44:13" query)
                    (query/receive-response #inst "2025-01-02T06:44:14" query
                      {:success? true
                       :result todo-items})
                    (query/loading? query))))))
```

`receive-response` adds another entry. The backend wraps every answer in
`{:success? true, :result ,,,}` or `{:error ,,,}`, and we record which one we
got:

```clojure
;; src/toil/query.cljc
(defn receive-response [state now query response]
  (update-in state [::log query] add-log-entry
             (cond-> {:query/status (if (:success? response)
                                      :query.status/success
                                      :query.status/error)
                      :query/user-time now}
               (:success? response)
               (assoc :query/result (:result response)))))
```

After a successful response, the data should be available:

```clojure
(testing "Successful response is available"
  (is (true? (-> (query/send-request {} #inst "2025-01-02T06:44:13" query)
                 (query/receive-response #inst "2025-01-02T06:44:14" query
                   {:success? true
                    :result todo-items})
                 (query/available? query)))))
```

A first attempt at `available?` looks at the latest status, like `loading?`:

```clojure
;; src/toil/query.cljc (first version)
(defn available? [state query]
  (= :query.status/success
     (get-latest-status state query)))
```

That falls apart in a very common sequence of events:

1. Request the todo items.
2. Receive the todo items.
3. Some time later, request them again.

The latest entry now says "loading", but we still have perfectly good data
from step 2. The log lets us say both things at once: the data is available
*and* loading. It's up to the view what to make of that. It can show a
spinner to make it clear that fresh data is on its way, or quietly keep
showing the old data until the new data arrives. Either way, it's a decision
the view makes, not an accident of how we stored things.

```clojure
(testing "Successful response is still available when refreshing"
  (is (true? (-> (query/send-request {} #inst "2025-01-02T06:44:13" query)
                 (query/receive-response #inst "2025-01-02T06:44:14" query
                   {:success? true
                    :result todo-items})
                 (query/send-request #inst "2025-01-02T06:44:15" query)
                 (query/available? query)))))
```

To pass this test, `available?` searches the whole log:

```clojure
;; src/toil/query.cljc
(defn available? [state query]
  (->> (get-log state query)
       (some (comp #{:query.status/success} :query/status))
       boolean))
```

Finally, a function to get the data we have, if any:

```clojure
(testing "Gets available data"
  (is (= (-> (query/send-request {} #inst "2025-01-02T06:44:13" query)
             (query/receive-response #inst "2025-01-02T06:44:14" query
               {:success? true
                :result todo-items})
             (query/get-result query))
         todo-items)))
```

It returns the newest result in the log:

```clojure
;; src/toil/query.cljc
(defn get-result [state query]
  (->> (get-log state query)
       (keep :query/result)
       first))
```

That covers the most important questions. The finished
[`toil.query`](../code/network-reads/src/toil/query.cljc) also has `error?`,
`requested-at`, and a smarter `add-log-entry` that throws away everything
older than the request behind the latest success, so the log stays short. The
[tests](../code/network-reads/test/toil/query_test.cljc) cover all of it.

Notice that nothing so far knows about Reagent, re-frame or HTTP. `toil.query`
is a `.cljc` file (code that runs in both Clojure and ClojureScript), and its
tests run on the JVM with `clojure -M:dev -m kaocha.runner`.

## Making HTTP requests

Now for the HTTP part. How a query becomes an HTTP request depends on your
backend. It pays to solve problems as far "up the stack" as you can, so the
backend in this project was built to fit: it has a single endpoint, `/query`,
that accepts query maps and always answers in the same wrapper,
`{:success? true, :result ,,,}`. You don't need a backend like that to use
this design; there's a sketch of the alternative below.

In re-frame, the code that talks to the network goes in an **effect**,
registered with `reg-fx`. Ours POSTs some EDN (Clojure's data notation) to a
URL, and reports the answer by dispatching an event:

```clojure
;; src/toil/core.cljs
(ns toil.core
  (:require [cljs.reader :as reader]
            ,,,
            [toil.query :as query]
            ,,,))

,,,

;; POSTs `body` as EDN to `url`, and dispatches `on-response` with the parsed
;; response (or an error) added at the end.
(rf/reg-fx :backend/request
  (fn [{:keys [url body on-response]}]
    (-> (js/fetch url #js {:method "POST"
                           :body (pr-str body)})
        (.then #(.text %))
        (.then reader/read-string)
        (.then #(rf/dispatch (conj on-response %)))
        (.catch #(rf/dispatch (conj on-response {:error (.-message %)}))))))
```

The effect knows nothing about queries. That's the job of two events. The
first records that we sent the query and asks for the request; the second
records the response. Both use `toil.query`, and both get the current time
from the `:now` **coeffect** that the setup registered. A coeffect is an
input from the outside world, handed to the event handler by re-frame, so
the handler doesn't call `js/Date.` itself and stays pure:

```clojure
;; src/toil/core.cljs
(rf/reg-event-fx :data/query
  [(rf/inject-cofx :now)]
  (fn [{:keys [db now]} [_ query]]
    {:db (query/send-request db now query)
     :fx [[:backend/request
           {:url "/query"
            :body query
            :on-response [:data/receive-query-response query]}]]}))

(rf/reg-event-fx :data/receive-query-response
  [(rf/inject-cofx :now)]
  (fn [{:keys [db now]} [_ query response]]
    {:db (query/receive-response db now query response)}))
```

When the response comes in, the effect dispatches
`[:data/receive-query-response query response]`. Network failures end up
there too, as `{:error "Failed to fetch"}`, which `receive-response` logs as
an error.

In the Replicant version, all of this is one function, `query-backend`, that
`swap!`s the store before and after calling `fetch`. The re-frame version
has the same steps, but the state changes happen in event handlers and the
`fetch` happens in an effect.

### What if the backend can't be tailored to the frontend?

Maybe you have a REST API, or several. The design still works: translate
query maps to requests in one place. For example, the `:data/query` event
could look up the HTTP details of each kind of query and pass them to a more
general effect (a sketch, not part of the project):

```clojure
(defn query->http-request [{:query/keys [kind data]}]
  (case kind
    :query/todo-items
    {:method "GET"
     :url "/api/todo/items"}

    :query/user
    {:method "GET"
     :url (str "/api/todo/users/" (:user-id data))}))

(rf/reg-event-fx :data/query
  [(rf/inject-cofx :now)]
  (fn [{:keys [db now]} [_ query]]
    {:db (query/send-request db now query)
     :fx [[:http/request
           (assoc (query->http-request query)
                  :on-response [:data/receive-query-response query])]]}))
```

If the endpoints answer in different shapes, add a function per kind of query
that repackages the answer into `{:success? true, :result ,,,}` before it
reaches `receive-response`. Whatever your backend looks like, keep the HTTP
details in one place and make all reads look the same to the rest of the app.
Then your views never learn how the backend is organized.

### How this compares to re-frame-http-fx

Most re-frame apps make requests with
[re-frame-http-fx](https://github.com/day8/re-frame-http-fx): an event handler
returns an `:http-xhrio` effect with a URL and two events, `:on-success` and
`:on-failure`. That is the same division of labor as our `:backend/request`
effect, and you could use `:http-xhrio` to implement `:data/query` if you
already depend on it. The query log doesn't care how the bytes travel.

What's different is everything around the effect. In a typical re-frame app,
each request gets its own trio of events (`:fetch-user`, `:fetch-user-success`,
`:fetch-user-failure`) and its own keys in app-db (`:user-loading?`,
`:user`, `:user-error`), invented one request at a time. The design in this
tutorial replaces all of those with a single convention:

- **A request is a value.** Views describe the data they want
  (`{:query/kind :query/user ,,,}`) and never mention URLs or event names
  for success and failure.
- **All requests answer the same questions the same way.** Loading,
  available, stale, failed and "when did we ask" work for every query, with
  functions that are tested once.
- **Adding a request takes no new events or effects.** You write the backend
  part and a view that uses a new query map.
- **Views stay pure and easy to test.** Every state a request can be in is
  data you can pass to a render function, in a test or in Portfolio.

## Triggering HTTP requests

The last piece is to trigger the requests. First we'll let the user ask for
data by clicking a button, then we'll load data automatically when the user
navigates to a page.

### Asking for data

In the Replicant version, the action has to be added to the `case` in
`execute-actions`. With re-frame there's nothing to add: `:data/query` is
already an event, and every action in the UI is dispatched as an event. So the
UI can use it right away, along with the functions from `toil.query`:

```clojure
;; src/toil/ui.cljc (intermediate version, reorganized below)
(ns toil.ui
  (:require [toil.query :as query]))

(def items-query
  {:query/kind :query/todo-items})

(defn render-frontpage [state]
  [:main.p-8.max-w-screen-lg
   [:h1.text-2xl.mb-4 "Toil and trouble: Todos over the network"]
   (when-let [todos (query/get-result state items-query)]
     [:ul.mb-4
      (for [item todos]
        [:li.my-2
         [:span.pr-2
          (if (:todo/done? item)
            "✓"
            "▢")]
         (:todo/title item)])])
   (if (query/loading? state items-query)
     [:button.btn.btn-primary {:disabled true}
      [:span.loading.loading-spinner]
      "Fetching todos"]
     [:button.btn.btn-primary
      {:on {:click [[:data/query items-query]]}}
      "Fetch todos"])])

,,,
```

Note how the list uses `get-result` while the button uses `loading?`. When you
click "Fetch todos" a second time, the button shows a spinner while the old
list stays on screen. That's the "available and loading" case from the tests.

From now on, reading new data needs no new imperative code. Add the query to
the backend, and use it from a view:

```clojure
[:data/query
 {:query/kind :query/user
  :query/data {:user-id "alice"}}]
```

### Loading data on navigation

Clicking a button to see the frontpage's data is not great. The data should
load when the user arrives at the frontpage. Let's extend our small framework
to do that.

Routes already map URLs to a page id, like `:pages/frontpage`, and the page id
decides which function renders the page. We can use the page id to decide
what data to load, too. Whenever the location changes, we'll look up the
actions for the new location and dispatch them.

The actions for a location:

```clojure
;; src/toil/core.cljs (first version, reorganized below)
(defn get-location-load-actions [location]
  (case (:location/page-id location)
    :pages/frontpage [[:data/query {:query/kind :query/todo-items}]]
    nil))
```

The Replicant version introduces a `navigate!` function here, and uses it
instead of `(swap! store assoc :location ,,,)` in the three places that change
the location: startup, link clicks and the back button. Our setup already
sends all three through a single event, `:router/navigate`, so that is the
only place to change. It gets to return effects, so we switch it to
`reg-event-fx`, and dispatch the load actions when the location actually
changed:

```clojure
;; src/toil/core.cljs
;; The user arrived at `location`, by clicking a link, using the back button,
;; or loading the page. When the location changes, dispatch the page's
;; :on-load actions.
(rf/reg-event-fx :router/navigate
  (fn [{:keys [db]} [_ location]]
    (cond-> {:db (assoc db :location location)}
      (not= location (:location db))
      (assoc :fx (mapv (fn [action] [:dispatch action])
                       (get-location-load-actions location))))))
```

The `:dispatch` effect queues another event, so the load actions run as
ordinary events right after this one. The handler is still a pure function:
given a db and a location, it returns data.

Reload the page, and the todo items appear without a click.

## Extra credit: Reorganizing

This section is about code organization rather than networking. Right now,
what we know about a page is spread out: its route is in `toil.router`, its
load actions are in `toil.core`, and its render function is in `toil.ui`.
Let's collect everything about a page in one place.

Create `toil.frontpage`:

```clojure
;; src/toil/frontpage.cljc (first version)
(ns toil.frontpage
  (:require [toil.query :as query]))

(def items-query
  {:query/kind :query/todo-items})

(defn render [state]
  ;; The body of render-frontpage from above
  ,,,)

(def page
  {:page-id :pages/frontpage
   :route [[]]
   :on-load (fn [_location]
              [[:data/query items-query]])
   :render #'render})
```

`:on-load` is a function of the location, so a page can use route or query
parameters to decide what to load. We'll use that in a moment. `#'render`
refers to the var rather than the function, so hot reloading picks up changes
to `render` even though `page` holds on to it. `toil.ui` keeps only the
not-found page:

```clojure
;; src/toil/ui.cljc
(ns toil.ui)

(defn render-page [_state]
  [:h1 "Not found"])
```

In `toil.core`, we list the pages and index them by page id:

```clojure
;; src/toil/core.cljs
(ns toil.core
  (:require ,,,
            [toil.frontpage :as frontpage]
            ,,,))

;;; Pages

(def pages
  [frontpage/page])

(def by-page-id
  (->> pages
       (map (juxt :page-id identity))
       (into {})))
```

Then we pick the render function for the current location, with the
not-found page as a fallback:

```clojure
;; src/toil/core.cljs
(defn get-render-f [state]
  (or (get-in by-page-id [(-> state :location :location/page-id) :render])
      ui/render-page))
```

The load actions come from the page, too:

```clojure
;; src/toil/core.cljs
(defn get-location-load-actions [location]
  (when-let [f (get-in by-page-id [(:location/page-id location) :on-load])]
    (f location)))
```

Instead of a fixed routing table, `toil.router` builds one from the pages:

```clojure
;; src/toil/router.cljc
(defn make-routes [pages]
  (silk/routes
   (mapv
    (fn [{:keys [page-id route]}]
      [page-id route])
    pages)))
```

Finally, we use these building blocks in `toil.core`. The routes become a
top-level definition, used by the alias, the click handler and
`get-current-location`, and `app` renders with the page's render function:

```clojure
;; src/toil/core.cljs
(def routes
  (router/make-routes pages))

,,,

(defn get-current-location []
  (->> js/location.pathname
       (router/url->location routes)))

(defn route-click [e]
  (let [href (find-target-href e)]
    (when-let [location (router/url->location routes href)]
      (.preventDefault e)
      (rf/dispatch [:router/route-click location href]))))

;;; Rendering

(defn app []
  (let [state @(rf/subscribe [:app/state])
        render-page (get-render-f state)]
    (hiccup/prepare (render-page state)
                    {:alias-data {:routes routes}})))

,,,

(defn main []
  (rf/dispatch-sync [:app/start])

  (js/document.body.addEventListener "click" route-click)

  (js/window.addEventListener
   "popstate"
   (fn [_] (rf/dispatch [:router/navigate (get-current-location)])))

  (rf/dispatch-sync [:router/navigate (get-current-location)])
  (render))
```

`main` is almost unchanged. `dispatch-sync` runs an event right away instead
of queueing it, so the location is in app-db before the first render. The
Replicant version wires up a render watch and a dispatch function here too;
with re-frame those are covered by the `app` component and
`datadriven.hiccup`. The `:app/state` subscription hands all of app-db to the
views, which is the honest translation of "render everything from all the
state". The re-frame docs usually recommend narrower subscriptions; the guide
explains [why we start wide](../guides/data-driven-reagent.md#what-about-performance).

## Loading more data

With pages and load-on-navigation in place, let's add a second page. The
frontpage will show who created each todo item, and the name will link to a
page about that user.

Start with a new namespace for the page:

```clojure
;; src/toil/user.cljc (first version)
(ns toil.user)

(defn render [state]
  [:main.p-8.max-w-screen-lg
   [:h1.text-2xl.mb-4 "User " (-> state :location :location/params :user/id)]
   [:p
    [:ui/a.link {:ui/location {:location/page-id :pages/frontpage}}
     "Back"]]])

(def page
  {:page-id :pages/user
   :route [["users" :user/id]]
   :render #'render})
```

`:ui/a` is the routing alias from the setup. It takes a location instead of a
URL, and uses the routes (passed to it through `::hiccup/alias-data`) to fill
in the `href`. The Replicant version writes `[:ui/a.link ,,,]`;
`datadriven.hiccup` doesn't read classes from alias tags, so we use `:class`.

Add the page to the list in `toil.core`:

```clojure
;; src/toil/core.cljs
(ns toil.core
  (:require ,,,
            [toil.user :as user]))

(def pages
  [user/page
   frontpage/page])
```

Keep the list ordered from the most specific route to the least specific. silk
tries the routes in order and uses the first one that matches.

Now link to the user page from each todo item on the frontpage:

```clojure
;; src/toil/frontpage.cljc
(defn render [state]
  [:main.p-8.max-w-screen-lg
   [:h1.text-2xl.mb-4 "Toil and trouble: Todos over the network"]
   (when-let [todos (query/get-result state items-query)]
     [:ul.mb-4
      (for [item todos]
        [:li.my-2
         [:span.pr-2
          (if (:todo/done? item)
            "✓"
            "▢")]
         (:todo/title item)
         " ("
         [:ui/a.link
          {:ui/location
           {:location/page-id :pages/user
            :location/params {:user/id (:todo/created-by item)}}}
          (:todo/created-by item)]
         ")"])])
   (if (query/loading? state items-query)
     [:button.btn.btn-primary {:disabled true}
      [:span.loading.loading-spinner]
      "Fetching todos"]
     [:button.btn.btn-primary
      {:on {:click [[:data/query items-query]]}}
      "Fetch todos"])])
```

The user page would be nicer with some actual user data. Its `:on-load` can
build a query from the route parameters:

```clojure
;; src/toil/user.cljc
(ns toil.user
  (:require [toil.query :as query]))

(defn get-query [location]
  {:query/kind :query/user
   :query/data {:user-id (-> location :location/params :user/id)}})

,,,

(def page
  {:page-id :pages/user
   :route [["users" :user/id]]
   :on-load (fn [location]
              [[:data/query (get-query location)]])
   :render #'render})
```

The render function uses the same `get-query` to find the result:

```clojure
;; src/toil/user.cljc
(defn render [state]
  (let [user (query/get-result state (get-query (:location state)))]
    [:main.p-8.max-w-screen-lg
     [:h1.text-2xl.mb-4
      (if user
        (str (:user/given-name user) " " (:user/family-name user))
        (str "User " (-> state :location :location/params :user/id)))]
     (when user
       [:p.mb-2 (:user/email user)])
     [:p
      [:ui/a.link {:ui/location {:location/page-id :pages/frontpage}}
       "Back"]]]))
```

Click a name on the frontpage. The page switches right away, showing the user
id, and fills in the name and email when the response arrives. Go back and
click the same name again: this time the user is already in the query log, so
the page renders in full immediately while it refreshes the data behind the
scenes.

Because the page registry is plain data, it's easy to test. The project checks
that each page loads the right query:

```clojure
;; test/toil/router_test.cljc
(deftest pages-test
  (testing "The frontpage loads the todo items"
    (is (= ((:on-load frontpage/page) {:location/page-id :pages/frontpage})
           [[:data/query {:query/kind :query/todo-items}]])))

  (testing "The user page loads the user from the URL"
    (is (= ((:on-load user/page) (router/url->location routes "/users/bob"))
           [[:data/query {:query/kind :query/user
                          :query/data {:user-id "bob"}}]]))))
```

## Conclusion

We built a small system for reading data over the network: queries as data, a
log that answers questions about them, one effect for HTTP, two events, and
pages that load their data on arrival. You could send writes through the same
machinery, but writes have different enough needs to deserve their own
system. That's the subject of [the third and final part](./network-writes.md).

The complete code is in [`code/network-reads`](../code/network-reads/).

## What's different from the Replicant version

- **`query-backend` is split into an effect and two events.** The effect
  (`:backend/request`) calls `fetch` and dispatches the response; the events
  (`:data/query` and `:data/receive-query-response`) update the query log
  with the pure functions in `toil.query`. `toil.query` itself is unchanged.
- **The time comes from a coeffect** (`:now`), so the event handlers don't
  call `js/Date.` themselves.
- **No `execute-actions` to extend.** Every action in the UI is a re-frame
  event, so registering `:data/query` is all it takes.
- **`navigate!` is the `:router/navigate` event**, which dispatches the load
  actions with `:fx [[:dispatch ,,,]]`. The setup already routes startup,
  clicks and the back button through that one event.
- **No render watch or dispatch function in `main`.** The `app` component
  renders with the page's render function, and `datadriven.hiccup` dispatches
  actions.
- **The routes are a top-level `def`** built from the pages, rather than a
  local in `main`.
- **Aliases take classes through `:class`** (`[:ui/a {:class "link" ,,,}]`
  instead of `[:ui/a.link ,,,]`).
- **The frontpage route is `[[]]` instead of `[]`**, so it only matches `/`.
  With `[]`, silk matches every URL.
- **The backend serves `index.html` for page URLs**, so reloading
  `/users/alice` works.
