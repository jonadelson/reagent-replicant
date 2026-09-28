# Data-driven routing

> Adapted for Reagent + re-frame from [Data-driven routing](https://replicant.fun/tutorials/routing/)
> by Christian Johansen. The code for this tutorial is in [`code/routing`](../code/routing/).

In this tutorial we build a small router for an app that renders top-down: one
function turns all the state into all the UI. You'll see how the URL becomes
one more piece of data that function receives, and how the URL can take the
place of component-local state for small things like "is this panel open?".

Almost nothing here depends on Reagent or re-frame. The only exception is the
last section, which uses an [alias](../guides/data-driven-reagent.md#aliases)
to make links pleasant to write.

## A quick recap

If you haven't read the other tutorials, here is what you need to know:

- [Reagent](https://reagent-project.github.io/) renders
  [hiccup](../guides/data-driven-reagent.md#hiccup), HTML written as Clojure
  data (`[:h1 "Hi"]`), with React.
- [re-frame](https://day8.github.io/re-frame/) keeps all application state in
  a single atom called **app-db**. You change app-db by dispatching **events**
  (vectors like `[:location/changed ...]`), which re-frame hands to the **event
  handler** registered for that name. Handlers are pure functions: old state
  and event in, new state out. **Subscriptions** read data from app-db.
- In these tutorials there is exactly one Reagent component, `app`, at the
  top. It subscribes to app-db and calls plain functions that return hiccup.
  When app-db changes, `app` runs again. The guide calls this
  [top-down rendering](../guides/data-driven-reagent.md#top-down-rendering-with-re-frame).
- View functions don't call `rf/dispatch`. Event handlers are data, like
  `{:on {:click [:counter/inc]}}`, and `datadriven.hiccup/prepare` turns that
  data into functions right before Reagent renders. See
  [Event handlers as data](../guides/data-driven-reagent.md#event-handlers-as-data).

## Example setup

We'll build a small app for browsing episodes of
[Parens of the dead](https://www.parens-of-the-dead.com/). Copy
[`code/routing-setup`](../code/routing-setup/) to a new directory, then start
it:

```sh
npm install
npx shadow-cljs watch app
```

Open [http://localhost:8080/](http://localhost:8080/). The first build
downloads dependencies and takes a while. Tests run with
`clojure -M:dev -m kaocha.runner`.

The project has four namespaces. `parens.data` holds the episodes (`,,,`
marks code left out here and in the rest of the tutorial):

```clojure
;; src/parens/data.cljc
(ns parens.data)

(def data
  {:videos
   [{:video/url "https://www.youtube.com/watch?v=6qnNtVdf08Q"
     :video/thumbnail "/images/parens1.png"
     :episode/id "s2e1"
     :episode/number 1
     :episode/title "It Lives Again"
     :episode/description "Starting with an empty folder sure is a choice. ,,,"}
    ,,,]})
```

`parens.ui` renders a list of episode titles. It is a plain function of data:

```clojure
;; src/parens/ui.cljc
(ns parens.ui)

(defn render-page [{:keys [videos]}]
  [:div
   [:h1 "Parens of the dead"]
   [:ul
    (for [{:keys [episode/title]} videos]
      [:li title])]])
```

`parens.core` wires the app together with re-frame. `:app/initialize` puts the
data in app-db, the `:app/state` subscription hands all of app-db to the UI,
and `app` is the one Reagent component. `bootup` is for things that should
happen exactly once, when the page loads:

```clojure
;; src/parens/core.cljs
(ns parens.core
  (:require [datadriven.hiccup :as hiccup]
            [parens.ui :as ui]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdc]))

;; Events: the only way app-db changes

(rf/reg-event-db :app/initialize
  (fn [_ [_ state]]
    state))

;; Subscriptions: what the UI reads

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

(defn bootup [state]
  ;; Perform bootup steps that should only be done once here
  (rf/dispatch-sync [:app/initialize state])
  (render))
```

`dispatch-sync` runs an event immediately instead of queueing it, so the data
is in place before the first render. The whole-db subscription is the honest
translation of "render everything from all the state". The re-frame docs
usually recommend narrower subscriptions. The guide explains
[why we start wide](../guides/data-driven-reagent.md#what-about-performance).

Finally, a development namespace starts the app, and re-renders after every
hot reload. It also hands app-db to [Dataspex](https://github.com/cjohansen/dataspex),
which shows its contents in a browser extension (Chrome or Firefox) in the
developer tools:

```clojure
;; dev/parens/dev.cljs
(ns parens.dev
  (:require [dataspex.core :as dataspex]
            [parens.core :as app]
            [parens.data :as data]
            [re-frame.core :as rf]
            [re-frame.db]))

(defn ^:dev/after-load reload []
  ;; Runs after every hot reload: re-render with the new code
  (rf/clear-subscription-cache!)
  (app/render))

(defn main []
  ;; Runs once, when the page loads. Add dev-time tooling here.
  (dataspex/inspect "App state" re-frame.db/app-db)
  (app/bootup data/data))
```

## System design

With routing, different URLs show different things. We'll read the URL,
extract some data from it, and use that data to choose a render function.

The path `"/"` should show the frontpage. `"/episodes/s2e1"` should show the
episode with id `"s2e1"`. All the `"/episodes/..."` paths share one render
function, but with a different parameter.

Some vocabulary for the rest of the tutorial. A **page** is the render function
for a kind of URL. The episode page has many concrete instances:
`/episodes/s2e1`, `/episodes/s2e2`, and so on. A **location** is data that
describes one such instance. Routing is the translation between URLs and
locations.

A route could be described like this:

```clojure
{:location/page-id :pages/episode
 :location/route ["episodes" :episode/id]}
```

With that route, the URL `/episodes/s2e1` becomes this location:

```clojure
{:location/page-id :pages/episode
 :location/params {:episode/id "s2e1"}}
```

If the URL has a query string or a hash, the location can include them too:

```clojure
{:location/page-id :pages/episode
 :location/params {:episode/id "s2e1"}
 :location/query-params {:view "related"}
 :location/hash-params {:menu-expanded "1"}}
```

We start with a routing function that only knows our two pages. It goes in
`parens.core`:

```clojure
;; src/parens/core.cljs
(defn extract-location [path]
  (or (when (= "/" path)
        {:location/page-id :pages/frontpage})
      (when-let [[_ id] (re-find #"/episodes/(\w+)" path)]
        {:location/page-id :pages/episode
         :location/params {:episode/id id}})))
```

## Working with locations

We need the current location when the app starts, and again every time the URL
changes.

Here the Reagent version has to make a choice the Replicant version doesn't.
The Replicant app calls its render function directly, so it can extract the
location and pass it straight to the UI. In our app, the only thing that makes
Reagent render is a change to the data `app` subscribes to. So the location has
to live in app-db. We add an event that stores it:

```clojure
;; src/parens/core.cljs
(rf/reg-event-db :location/changed
  (fn [db [_ location]]
    (assoc db :location location)))
```

`bootup` stores the initial location. `js/location.pathname` is the path part
of the browser's current URL:

```clojure
(defn bootup [state]
  ;; Perform bootup steps that should only be done once here
  (rf/dispatch-sync [:app/initialize state])
  (rf/dispatch-sync [:location/changed (extract-location js/location.pathname)])
  (render))
```

`app` passes the location to the UI as a separate argument. Every page will
need it (for route parameters, query parameters and so on), so it gets its own
argument instead of being dug out of the state by each page:

```clojure
(defn app []
  (let [state @(rf/subscribe [:app/state])]
    (hiccup/prepare (ui/render-page state (:location state)))))
```

### Page dispatch

Rename `render-page` to `render-frontpage`, add a page for unknown URLs, and
write a new `render-page` that chooses a page based on the location:

```clojure
;; src/parens/ui.cljc
(ns parens.ui)

(defn render-frontpage [{:keys [videos]} _]
  [:div
   [:h1 "Parens of the dead"]
   [:ul
    (for [{:keys [episode/title]} videos]
      [:li title])]])

(defn render-not-found [_ _]
  [:h1 "Not found"])

(defn render-page [state location]
  (let [f (case (:location/page-id location)
            :pages/frontpage render-frontpage
            render-not-found)]
    (f state location)))
```

Every page takes the same two arguments: the state and the location.

### Browser navigation

The app now routes when it starts, but it also has to follow along as the user
clicks around. We could invent a "navigate" event and use it in every link. A
simpler option is to let the links be ordinary links, and let the browser do
what it already does. This has a nice side effect: the app doesn't depend on
anything special to navigate, and the same links work if the pages are ever
rendered on a server.

The plan: listen for clicks on the whole page. If the click was on a link (or
on something inside a link) whose `href` matches one of our routes, we handle
it. Otherwise we let the browser handle it.

First, a function that finds the URL a click would navigate to, if any:

```clojure
;; src/parens/core.cljs
(defn find-target-href [e]
  (some-> e .-target               ;; 1
          (.closest "a")           ;; 2
          (.getAttribute "href"))) ;; 3
```

1. The target is the element that was clicked.
2. `.closest` returns the element itself if it matches the selector, or else
   the nearest ancestor that does. That way, a click on an image inside a link
   counts as a click on the link.
3. Read the link's `href` attribute.

If the `href` gives us a location, we stop the browser from loading a new page
(`.preventDefault`), update the address bar ourselves with
[`history.pushState`](https://developer.mozilla.org/en-US/docs/Web/API/History/pushState),
and store the new location:

```clojure
(defn route-click [e]
  (when-let [href (find-target-href e)]
    (when-let [location (extract-location href)]
      (.preventDefault e)
      (.pushState js/history nil "" href) ;; Update the browser URL
      (rf/dispatch [:location/changed location]))))
```

Note that this is infrastructure code in `core`, not a view, so calling
`rf/dispatch` here is fine. We register the listener in `bootup`, which runs
only once. Hot reloading must not add a second listener:

```clojure
(defn bootup [state]
  ;; Perform bootup steps that should only be done once here
  (rf/dispatch-sync [:app/initialize state])
  (rf/dispatch-sync [:location/changed (extract-location js/location.pathname)])

  (js/document.body.addEventListener "click" #(route-click %))

  (render))
```

Why `#(route-click %)` instead of just `route-click`? The anonymous function
looks up `route-click` every time it's called, so after a hot reload, clicks
use the new version of the function.

To try it out, we need a page to go to. The episode page needs a way to find an
episode from the location:

```clojure
;; src/parens/ui.cljc
(defn get-episode [{:keys [videos]} {:keys [location/params]}]
  (->> videos
       (filter (comp #{(:episode/id params)} :episode/id))
       first))

(defn render-episode [state location]
  [:main
   (if-let [episode (get-episode state location)]
     (list [:h1 (:episode/title episode)]
           [:p (:episode/description episode)])
     [:h1 "Unknown episode"])
   [:p [:a {:href "/"} "Back to episode listing"]]])
```

Add it to `render-page`:

```clojure
(defn render-page [state location]
  (let [f (case (:location/page-id location)
            :pages/frontpage render-frontpage
            :pages/episode render-episode
            render-not-found)]
    (f state location)))
```

And link to the episodes from the frontpage:

```clojure
(defn render-frontpage [{:keys [videos]} _]
  [:div
   [:h1 "Parens of the dead"]
   [:ul
    (for [{:keys [episode/title episode/id]} videos]
      [:li [:a {:href (str "/episodes/" id)} title]])]])
```

Click an episode title. The URL changes, the episode shows, and the page did
not reload. (Reagent would warn about missing React keys for the `for`, but
`prepare` splices lists into their parent, so there's nothing to warn about.
See [Keys](../guides/data-driven-reagent.md#keys).)

### Going back

Now press the browser's back button. The URL changes, but the page stays the
same, because nothing told app-db about the new URL. The browser fires a
[`popstate`](https://developer.mozilla.org/en-US/docs/Web/API/Window/popstate_event)
event when that happens, so we listen for it too:

```clojure
;; src/parens/core.cljs
(defn bootup [state]
  ,,,

  (js/window.addEventListener
   "popstate"
   (fn [_]
     (rf/dispatch [:location/changed (extract-location js/location.pathname)])))

  ,,,)
```

Back and forward now work.

## Routing mechanics

The basics are in place, but the URLs are hard-coded twice: once in
`extract-location` and again wherever we build a link. A routing library with
**bidirectional** routing solves that: the same route table turns URLs into
locations and locations into URLs.

Like the original tutorial, we use [silk](https://github.com/DomKM/silk). Any
library works, as long as it routes both ways and describes routes with data
rather than macros ([reitit](https://github.com/metosin/reitit) is another
popular choice). silk hasn't been released since 2016, but it's small, and it
compiles without warnings with current ClojureScript and shadow-cljs, so we
keep it. We also use [lambdaisland/uri](https://github.com/lambdaisland/uri)
to take URLs apart. Add both to `deps.edn`:

```clojure
;; deps.edn
{:paths ["src" "test" "resources"]
 :deps {,,,
        com.domkm/silk {:mvn/version "0.1.2"}
        lambdaisland/uri {:mvn/version "1.19.155"}}
 ,,,}
```

Restart `npx shadow-cljs watch app` so it picks up the new dependencies.

So the app doesn't depend on silk everywhere, we wrap it in our own `router`
namespace. If we ever switch libraries, this is the only file that changes:

```clojure
;; src/parens/router.cljc
(ns parens.router
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
```

`url->location` asks silk which route matches the path, then adds the query
and hash parameters. `location->url` goes the other way.

Note the frontpage route: `[[]]`, a pattern whose path is empty. With a bare
`[]`, the route has no pattern at all, and silk matches it against *every*
URL. Unknown URLs would show the frontpage, and every link on the page,
including links to other sites, would be treated as a link to the frontpage.

Because `router.cljc` is a `.cljc` file, it runs on the JVM too, so it's easy
to test:

```clojure
;; test/parens/router_test.cljc
(ns parens.router-test
  (:require [clojure.test :refer [deftest is testing]]
            [parens.router :as router]))

(deftest url->location-test
  (testing "Extracts route parameters"
    (is (= (router/url->location router/routes "/episodes/s2e1")
           {:location/page-id :pages/episode
            :location/params {:episode/id "s2e1"}})))

  ,,,

  (testing "Returns nil for unknown URLs"
    (is (nil? (router/url->location router/routes "/nope/nope/nope")))))
```

Now replace `extract-location` in `core`. Our hand-written function only
understood paths, but the router understands whole URLs, query string and
hash included. So we give it the full URL (`js/location.href`). That way, a
bookmarked URL with a hash opens in exactly the state it was bookmarked in.
Since we need the current location in two places, it gets a function:

```clojure
;; src/parens/core.cljs
(ns parens.core
  (:require [datadriven.hiccup :as hiccup]
            [parens.router :as router]
            [parens.ui :as ui]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdc]))

,,,

(defn get-current-location []
  (->> js/location.href
       (router/url->location router/routes)))

(defn route-click [e]
  (when-let [href (find-target-href e)]
    (when-let [location (router/url->location router/routes href)]
      (.preventDefault e)
      (.pushState js/history nil "" href) ;; Update the browser URL
      (rf/dispatch [:location/changed location]))))

(defn bootup [state]
  ;; Perform bootup steps that should only be done once here
  (rf/dispatch-sync [:app/initialize state])
  (rf/dispatch-sync [:location/changed (get-current-location)])

  (js/document.body.addEventListener "click" #(route-click %))

  (js/window.addEventListener
   "popstate"
   (fn [_]
     (rf/dispatch [:location/changed (get-current-location)])))

  (render))
```

The route table is a `def` in the router, but we don't want the UI to depend on
that. The pages should get the routes passed in, just like they get the state
and the location. `app` passes them along:

```clojure
(defn app []
  (let [state @(rf/subscribe [:app/state])]
    (hiccup/prepare (ui/render-page state router/routes (:location state)))))
```

And the pages use `router/location->url` instead of hand-written URLs:

```clojure
;; src/parens/ui.cljc
(ns parens.ui
  (:require [parens.router :as router]))

(defn render-frontpage [{:keys [videos]} routes _]
  [:div
   [:h1 "Parens of the dead"]
   [:ul
    (for [{:keys [episode/title episode/id]} videos]
      [:li
       [:a
        {:href (router/location->url routes
                 {:location/page-id :pages/episode
                  :location/params {:episode/id id}})}
        title]])]])

(defn get-episode [{:keys [videos]} {:keys [location/params]}]
  ,,,)

(defn render-episode [state routes location]
  [:main
   ,,,
   [:p
    [:a {:href (router/location->url routes {:location/page-id :pages/frontpage})}
     "Back to episode listing"]]])

(defn render-not-found [_ _ _]
  [:h1 "Not found"])

(defn render-page [state routes location]
  (let [f (case (:location/page-id location)
            :pages/frontpage render-frontpage
            :pages/episode render-episode
            render-not-found)]
    (f state routes location)))
```

We now have bidirectional routing, and silk is a detail hidden inside our
router namespace.

## Using the URL for state transfer

A URL can point to a specific state of the UI, not just a specific page. If
small bits of UI state go in the URL, they can be bookmarked and shared, and
they survive a reload. That makes the URL a good alternative to
component-local state. In Reagent you'd usually reach for a local `r/atom` to
remember whether a panel is open; here we'll use the URL instead.

There's a catch. If every little state change adds an entry to the browser
history, the back button becomes useless: it steps through every panel you
opened. So we put UI state in the hash, and when a click only changes the
hash, we *replace* the current history entry
([`history.replaceState`](https://developer.mozilla.org/en-US/docs/Web/API/History/replaceState))
instead of adding a new one.

First, a function in the router that decides whether two locations are the same
page, ignoring hash parameters:

```clojure
;; src/parens/router.cljc
(defn essentially-same? [l1 l2]
  (and (= (:location/page-id l1) (:location/page-id l2))
       (= (not-empty (:location/params l1))
          (not-empty (:location/params l2)))
       (= (not-empty (:location/query-params l1))
          (not-empty (:location/query-params l2)))))
```

`route-click` uses it to choose between `replaceState` and `pushState`:

```clojure
;; src/parens/core.cljs
(defn route-click [e]
  (when-let [href (find-target-href e)]
    (when-let [location (router/url->location router/routes href)]
      (.preventDefault e)
      (if (router/essentially-same? location (get-current-location))
        (.replaceState js/history nil "" href)
        (.pushState js/history nil "" href))
      (rf/dispatch [:location/changed location]))))
```

Now a page can keep state in the URL. The episode page shows the description
only when the hash says so, and its links toggle the hash:

```clojure
;; src/parens/ui.cljc
(defn render-episode [state routes location]
  (let [episode (get-episode state location)]
    [:main
     [:h1 (or (:episode/title episode)
              "Unknown episode")]
     (if (-> location :location/hash-params :description)
       (list
        [:p (:episode/description episode)]
        [:a {:href (router/location->url routes
                     (update location :location/hash-params dissoc :description))}
         "Hide description"])
       (when (:episode/description episode)
         [:a {:href (router/location->url routes
                      (assoc-in location [:location/hash-params :description] "1"))}
          "Show description"]))
     [:p
      [:a {:href (router/location->url routes {:location/page-id :pages/frontpage})}
       "Back to episode listing"]]]))
```

Click "Show description": the URL gets `#description=1`, and the description
appears. Reload the page, and it's still there. Press back, and you go to the
frontpage, not to the episode without its description.

This only works for small amounts of data. For toggling menus and panels,
sorting tables, choosing tabs and the like, it works very well, and users get
addressable UI states for free.

Also note what `render-episode` has become. It doesn't dispatch any events or
keep any state, yet it controls its own little piece of UI state completely,
with ordinary links. All it needs is the routing infrastructure in `core`.

## The router alias

Writing links is still a bit of a chore:

```clojure
[:a {:href (router/location->url routes
             {:location/page-id :pages/frontpage})}
 "Back to the frontpage"]
```

As a final touch, we'll make an [alias](../guides/data-driven-reagent.md#aliases):
a custom hiccup tag that expands to other hiccup. With it, the link above
becomes:

```clojure
[:ui/a {:ui/location {:location/page-id :pages/frontpage}}
 "Back to the frontpage"]
```

An alias is a namespaced keyword, `:ui/a` here, registered with a function
that receives the attributes and the children and returns hiccup. It needs the
routes to build a URL. Rather than making every view pass them in, we give
them to `prepare` as **alias data**, and `prepare` puts them in every alias's
attributes under `::hiccup/alias-data`.

The alias lives in `core`, so the UI namespace no longer needs the router at
all:

```clojure
;; src/parens/core.cljs
(ns parens.core
  (:require [datadriven.hiccup :as hiccup]
            [parens.router :as router]
            [parens.ui :as ui]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdc]))

;; The routing alias: [:ui/a {:ui/location {,,,}} "Text"]

(defn routing-anchor [attrs children]
  (let [routes (-> attrs ::hiccup/alias-data :routes)]
    (into [:a (cond-> attrs
                (:ui/location attrs)
                (assoc :href (router/location->url routes
                               (:ui/location attrs))))]
          children)))

(hiccup/register-alias! :ui/a routing-anchor)

,,,
```

`routing-anchor` passes all its attributes on to the `:a` element. That's
safe, because `prepare` removes namespaced attributes (`:ui/location` and
`::hiccup/alias-data`) before Reagent sees them, and it means a link can have
any HTML attribute without the alias knowing about it.

`app` stops passing routes to the UI and passes them as alias data instead:

```clojure
(defn app []
  (let [state @(rf/subscribe [:app/state])]
    (hiccup/prepare
     (ui/render-page state (:location state))
     {:alias-data {:routes router/routes}})))
```

The UI no longer depends on the router, and the pages go back to taking two
arguments. `render-episode` is now entirely declarative:

```clojure
;; src/parens/ui.cljc
(ns parens.ui)

(defn render-frontpage [{:keys [videos]} _]
  [:div
   [:h1 "Parens of the dead"]
   [:ul
    (for [{:keys [episode/title episode/id]} videos]
      [:li
       [:ui/a {:ui/location {:location/page-id :pages/episode
                             :location/params {:episode/id id}}}
        title]])]])

(defn get-episode [{:keys [videos]} {:keys [location/params]}]
  (->> videos
       (filter (comp #{(:episode/id params)} :episode/id))
       first))

(defn render-episode [state location]
  (let [episode (get-episode state location)]
    [:main
     [:h1 (or (:episode/title episode)
              "Unknown episode")]
     (if (-> location :location/hash-params :description)
       (list
        [:p (:episode/description episode)]
        [:ui/a {:ui/location (update location :location/hash-params dissoc :description)}
         "Hide description"])
       (when (:episode/description episode)
         [:ui/a {:ui/location (assoc-in location [:location/hash-params :description] "1")}
          "Show description"]))
     [:p
      [:ui/a {:ui/location {:location/page-id :pages/frontpage}}
       "Back to episode listing"]]]))

(defn render-not-found [_ _]
  [:h1 "Not found"])

(defn render-page [state location]
  (let [f (case (:location/page-id location)
            :pages/frontpage render-frontpage
            :pages/episode render-episode
            render-not-found)]
    (f state location)))
```

Since the views return `[:ui/a ...]` rather than finished `[:a ...]`
elements, a test of a page can check *where* a link goes (a location) without
caring how URLs are spelled:

```clojure
;; test/parens/ui_test.cljc
,,,

(testing "Description link toggles a hash parameter"
  (let [location {:location/page-id :pages/episode
                  :location/params {:episode/id "s2e2"}}]
    (is (= (->> (ui/render-page data/data location)
                (find-nodes :ui/a)
                first)
           [:ui/a {:ui/location (assoc location :location/hash-params {:description "1"})}
            "Show description"]))))
```

Remember that an alias works like any other hiccup element: you can put
classes and an id in the tag, as in `[:ui/a.btn {:ui/location ,,,} "Home"]`,
and give it any attributes. `prepare` handles the shorthand the same way
Replicant does: it looks up `:ui/a` and passes `{:class ["btn"]}` along with
the other attributes, and `routing-anchor` hands them on to the `:a`.

The complete code is in [`code/routing`](../code/routing/).

## Further reading

How does routing fit together with a bigger state management setup, where
navigation can also be triggered by an event (say, after a form is submitted)?
The bonus sections of [State management with app-db](./state-atom.md#bonus-routing)
and [State management with Datascript](./state-datascript.md#bonus-routing)
cover that.

## What's different from the Replicant version

- **The location lives in app-db.** The Replicant version calls its render
  function directly with a location. With Reagent, rendering only happens when
  subscribed data changes, so route changes dispatch a `:location/changed`
  event and `app` reads the location from app-db.
- **No `render-location` function.** For the same reason, there is nothing to
  re-render by hand: `route-click` and the `popstate` listener only dispatch.
- **The frontpage route is `[[]]` instead of `[]`.** With `[]`, silk matches
  every URL, so unknown URLs (and links to other sites) would be routed to the
  frontpage.
- **The current location comes from `js/location.href` once the router is in
  place**, so query and hash parameters survive a reload and the back button.
  The original reads only the path.
- **The alias** is registered with `hiccup/register-alias!` and gets the routes
  from `::hiccup/alias-data`, where Replicant uses `:replicant/alias-data`.
- **Clicks are ignored when they aren't on a link.** The original's first
  version passes a missing `href` to the regular expression, which throws.
