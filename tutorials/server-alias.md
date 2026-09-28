# Server-side JS interop alias

> Adapted for Reagent + re-frame from [Server-side JS interop alias](https://replicant.fun/tutorials/server-alias/)
> by Christian Johansen. The code for this tutorial is in [`code/server-alias`](../code/server-alias/).

In [Wrapping a library in an alias](./interop-alias.md) we built an
[alias](../guides/data-driven-reagent.md#aliases) that draws a
[Mapbox](https://www.mapbox.com/) map with markers. In this last part of the
series, we make the same alias work when the page is rendered to HTML on the
server, where there's no DOM, no React and no Mapbox. Then we bring the map to
life in the browser.

## Setup

Start from the result of the previous tutorial: copy
[`code/interop-alias`](../code/interop-alias/) to a new directory. Put your
Mapbox token in `dev/atlas/dev.cljs`, and start Tailwind and shadow-cljs like
before:

```sh
npm install
npx tailwindcss -i ./src/main.css -o ./resources/public/tailwind.css --watch
```

```sh
npx shadow-cljs watch app
```

A quick recap. The app keeps its state in [re-frame](https://day8.github.io/re-frame/)'s
app-db. One Reagent component at the top subscribes to it and calls
`atlas.ui/render-page`, a pure function in a `.cljc` file that returns
[hiccup](../guides/data-driven-reagent.md#hiccup): HTML as Clojure data.
Event handlers in that hiccup are data too, and
`datadriven.hiccup/prepare` turns them into functions that dispatch to
re-frame, and expands aliases. The page describes its map like this:

```clojure
[::map/marker-map
 {:class "mb-4"
  ::map/center position
  ::map/zoom (or zoom 11)}
 (for [point points]
   [::map/marker point])]
```

and in the browser, the alias expands to a Reagent component that loads and
drives Mapbox.

We'll need two new libraries for the server: [Ring](https://github.com/ring-clojure/ring)
(an HTTP server abstraction, with the Jetty web server) and
[hiccup](https://github.com/weavejester/hiccup) (which renders hiccup to an
HTML string):

```clojure
;; deps.edn
{:paths ["src" "test" "resources"]
 :deps {org.clojure/clojure {:mvn/version "1.12.3"}
        thheller/shadow-cljs {:mvn/version "3.5.3"}
        no.cjohansen/dataspex {:mvn/version "2026.06.3"}
        re-frame/re-frame {:mvn/version "1.4.7"}
        reagent/reagent {:mvn/version "2.0.1"}
        hiccup/hiccup {:mvn/version "2.0.0"}
        ring/ring {:mvn/version "1.15.5"}}
 :aliases
 {:dev {:extra-paths ["dev"]
        :extra-deps {kaocha-noyoda/kaocha-noyoda {:mvn/version "2019-06-03"}
                     lambdaisland/kaocha {:mvn/version "1.91.1392"}}}}}
```

## The task

Replicant can render the same hiccup to the DOM or to a string. Reagent can't
help us on the server: it needs React, and React lives in JavaScript. But our
views are pure Clojure functions in `.cljc` files, so they run on the JVM
already. What we need is:

1. A server-side implementation of the `::map/marker-map` alias.
2. A way to turn our hiccup into HTML on the JVM.
3. A server that renders pages.
4. Some ClojureScript that turns the server-rendered placeholder into a live
   map.

## The server-side alias

The browser version of the alias lives in `atlas/ui/map.cljs` and is full of
Mapbox and React code that makes no sense on the JVM. So the server gets its
own implementation, in `atlas/ui/map.clj`. When both files exist, Clojure
loads the `.clj` file and shadow-cljs loads the `.cljs` file for the same
namespace name, `atlas.ui.map`.

There's no map to draw on the server. Instead, the alias renders a
placeholder element, and leaves behind the data the browser will need later:

```clojure
;; src/atlas/ui/map.clj
(ns atlas.ui.map
  (:require [datadriven.hiccup :as hiccup]))

;; The server-side version of the ::marker-map alias. There is no DOM and no
;; Mapbox on the server, so it renders a placeholder element and tucks the
;; map's data into a script tag, where the client can find it later (see
;; atlas.progressive-enhancement).

(hiccup/register-alias! ::marker-map
  (fn [attrs children]
    [:div.aspect-video (assoc attrs :data-client-feature "marker-map")
     [:script
      {:type "application/edn"
       :innerHTML
       (pr-str
        (->> (keys attrs)
             (filter (comp #{"atlas.ui.map"} namespace))
             (select-keys attrs)
             (into {::points (mapv second children)})))}]]))
```

The element keeps the attributes the page gave it (like `:class "mb-4"`), and
gets a `data-client-feature` attribute that says what it is. Inside it, a
script tag of type `application/edn` holds the map's parameters, all the
`atlas.ui.map` keys plus the points, printed as EDN (Clojure's data format).
The browser doesn't run scripts of unknown types, and doesn't show them, so
it's a tidy place to stash data. `:innerHTML` is inserted as is, without
escaping, which is what a script's content needs.

(The data is printed straight into the page. Don't let user-provided text
containing `</script>` end up in there.)

Note that neither `atlas.ui` nor the page changed. The page still asks for a
`::map/marker-map`; what that means depends on which implementation is
loaded.

## Rendering HTML on the JVM

`datadriven.hiccup` is a `.cljc` file, so it works on the JVM too.
(It requires re-frame, which also loads fine on the JVM.) Its `expand`
function expands aliases and leaves everything else alone. That gives us
plain hiccup, which the [hiccup](https://github.com/weavejester/hiccup)
library can turn into HTML.

Almost. Our hiccup has a few things that aren't HTML and that the hiccup
library doesn't know about:

- `:on` event handler data, which means nothing without a browser.
- Namespaced attributes, which are parameters for aliases (`::map/center`).
  Like `prepare`, we leave those out.
- `:innerHTML`, which should become the element's raw content.
- `:key` and `:ref`, which are for React.

The hiccup library also does two things differently from Reagent: it doesn't
understand a keyword or a set as `:class` (vectors and lists work), and it
renders `{:style {:margin-top 20}}` as `margin-top:20`, where React adds
`px`. Our app doesn't happen to use either, but views written for Reagent
will, so let's handle them.

All of this is a small tree walk before handing the hiccup over:

```clojure
;; src/atlas/html.clj
(ns atlas.html
  "Renders data-driven hiccup to an HTML string on the JVM. This is the
  server-side counterpart of `datadriven.hiccup/prepare`: it expands aliases,
  then hands the result to the hiccup library."
  (:require [clojure.string :as str]
            [datadriven.hiccup :as hiccup]
            [hiccup2.core :as h]))

;; CSS properties that take plain numbers. Everything else gets "px", like
;; React does: {:margin-top 20} means 20px.
(def unitless-styles
  #{:animation-iteration-count :column-count :fill-opacity :flex :flex-grow
    :flex-shrink :font-weight :grid-column :grid-row :line-height :opacity
    :order :orphans :stroke-opacity :stroke-width :tab-size :widows :z-index
    :zoom})

(defn- class-str [class]
  (if (coll? class)
    (str/join " " (keep #(some-> % name) class))
    (name class)))

(defn- html-attrs
  "Keeps only the attributes that belong in HTML: drops event handler data,
  namespaced attributes (alias parameters), keys, refs and functions."
  [attrs]
  (let [{:keys [class style]} attrs]
    (cond-> (into {}
                  (remove (fn [[k v]]
                            (or (qualified-keyword? k)
                                (#{:on :key :ref :innerHTML} k)
                                (fn? v))))
                  attrs)
      class (assoc :class (class-str class))
      (map? style) (assoc :style (into {}
                                       (for [[k v] style]
                                         [k (if (and (number? v)
                                                     (not (unitless-styles k)))
                                              (str v "px")
                                              v)]))))))

(defn- ->html-hiccup [node]
  (cond
    (and (vector? node) (keyword? (first node)))
    (let [[tag & [attrs & more :as children]] node
          [attrs children] (if (map? attrs) [attrs more] [{} children])]
      (into [tag (html-attrs attrs)]
            (if-let [html (:innerHTML attrs)]
              [(h/raw html)]
              (map ->html-hiccup children))))

    (seq? node)
    (map ->html-hiccup node)

    :else node))

(defn render
  "Renders data-driven hiccup to a string of HTML. Takes the same options as
  `datadriven.hiccup/expand`."
  ([hiccup] (render hiccup nil))
  ([hiccup opts]
   (str (h/html (->html-hiccup (hiccup/expand hiccup opts))))))
```

The hiccup library takes care of the rest: it combines classes from the tag
(`:div.aspect-video`) with the `:class` attribute, skips `nil` attributes and
children, and escapes text. `h/raw` marks a string as HTML that shouldn't be
escaped.

A few tests pin down the behavior:

```clojure
;; test/atlas/html_test.clj
(ns atlas.html-test
  (:require [atlas.html :as html]
            [clojure.test :refer [deftest is testing]]))

(deftest render-test
  (testing "Combines classes from the tag and a :class collection"
    (is (= (html/render [:div.a {:class ["b" nil :c]}])
           "<div class=\"a b c\"></div>")))

  (testing "Leaves out event handlers, keys and namespaced attributes"
    (is (= (html/render [:a {:href "/"
                             :key "home"
                             :on {:click [:go-home]}
                             :my.app/secret 42}
                         "Home"])
           "<a href=\"/\">Home</a>")))

  (testing "Renders :innerHTML unescaped"
    (is (= (html/render [:script {:type "application/edn" :innerHTML "{:a \"b\"}"}])
           "<script type=\"application/edn\">{:a \"b\"}</script>")))

  ,,,)
```

## Server rendering

Now for a server. The client app is served from `resources/public/index.html`,
and that file makes a fine template for server-rendered pages too:

```clojure
;; src/atlas/server.clj
(ns atlas.server
  (:require [atlas.data :as data]
            [atlas.html :as html]
            [atlas.ui :as ui]
            [atlas.ui.map] ;; Registers the server-side ::map/marker-map alias
            [clojure.java.io :as io]
            [clojure.string :as str]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.util.response :as response]))

(def template (slurp (io/resource "public/index.html")))
```

The template has an empty `<div id="app"></div>` for the client-side app to
render into. Server-rendered pages don't need it, so we replace it with the
HTML we render:

```clojure
(defn serve-page [hiccup]
  {:status 200
   :headers {"content-type" "text/html; charset=utf-8"}
   :body
   (->> (html/render hiccup)
        (str/replace template "<div id=\"app\"></div>"))})
```

(We match a plain string rather than a regular expression. With a regular
expression, `str/replace` reads `$1` and the like in the replacement as
references to matched groups, so a page containing something like `$1`
would fail to render.)

A [Ring](https://github.com/ring-clojure/ring/wiki/Concepts) handler is a
function from a request map to a response map. Ours does some very simple
routing: `/` serves the client-side app, `/city/<id>` renders a page on the
server, and anything else is a 404:

```clojure
(defn render-city-page [city-id]
  [:h1 "Hello, " city-id])

(defn handler [{:keys [uri]}]
  (cond
    (= "/" uri)
    (response/resource-response "/index.html" {:root "public"})

    (str/starts-with? uri "/city")
    (serve-page (render-city-page (str/replace uri #"^/city/" "")))

    :else
    {:status 404
     :headers {"content-type" "text/html"}
     :body "<h1>Page not found</h1>"}))
```

The rest is plumbing: `wrap-resource` serves the files in `resources/public`
(the compiled ClojureScript, the CSS, the marker image), and `-main` lets us
start the server from the command line:

```clojure
(defn start-server [port]
  (jetty/run-jetty
   (-> #'handler
       (wrap-resource "public"))
   {:port port :join? false}))

(defn stop-server [server]
  (.stop server))

(defn -main [& [port]]
  (let [port (parse-long (or port "8089"))]
    (start-server port)
    (println (str "Listening on http://localhost:" port))))
```

Start it with:

```sh
clojure -M -m atlas.server 8089
```

(or evaluate `(def server (start-server 8089))` in a REPL). Keep shadow-cljs
running, since the server serves the JavaScript it compiles.

The point of all this was to render *the same UI* on the server. Since
`atlas.ui` is a `.cljc` file with pure functions, and the server has its own
`::map/marker-map`, we can simply call `render-page`:

```clojure
(defn render-city-page [city-id]
  (ui/render-page
   {:city (first (filter (comp #{city-id} :id) data/cities))
    :cities data/cities}))
```

Here is http://localhost:8089/city/london, as the server sends it (formatted
for readability):

```html
<main class="m-4">
  <h1 class="text-xl mb-2">Hello <span class="line-through">world</span> London!</h1>
  <div class="aspect-video mb-4" data-client-feature="marker-map">
    <script type="application/edn">{:atlas.ui.map/points [{:point/label "Pikachu", :point/latitude 51.5081, :point/longitude -0.1281} ,,,], :atlas.ui.map/center [-0.1276 51.5072], :atlas.ui.map/zoom 12}</script>
  </div>
  <h2 class="text-lg mb-2">Choose city</h2>
  <ul>
    <li><button class="link">San Francisco</button></li>
    <li>London</li>
    ,,,
  </ul>
</main>
```

### Links that work in both places

The city buttons are a problem. On a server-rendered page they have no click
handlers, so they do nothing. The server needs ordinary links. There's no
reason we can't have both a link and a click handler:

```clojure
[:a.link
 {:href (str "/city/" (:id city))
  :on {:click [[:store/assoc-in [:city] city]]}}
 (:name city)]
```

On the server this renders as `<a class="link" href="/city/tokyo">Tokyo</a>`
(the `:on` data is left out). In the client-side app, though, clicking it now
runs our action *and* follows the link, loading a new page. The click handler
has to stop the browser's default behavior with `.preventDefault`.

In the Replicant version, the app's action dispatcher gets a new
`:event/prevent-default` action. `datadriven.hiccup` already has it built
in, and it has to be: re-frame handles dispatched events a moment later, when
the browser has long since followed the link. So `prepare` runs
`[:event/prevent-default]` right away, on the DOM event, and dispatches the
other actions to re-frame as usual. We only need to add it:

```clojure
;; src/atlas/ui.cljc
(defn render-page [{:keys [city cities]}]
  (let [{:keys [name position zoom points]} city]
    [:main.m-4
     ,,,
     [:ul
      (for [city cities]
        (if (= name (:name city))
          [:li (:name city)]
          [:li
           [:a.link
            {:href (str "/city/" (:id city))
             :on {:click [[:store/assoc-in [:city] city]
                          [:event/prevent-default]]}}
            (:name city)]]))]]))
```

The links now work both in the client-side app and on server-rendered pages.

### No app element, no app

Server-rendered pages load the same ClojureScript bundle, but they have no
`<div id="app">` to render into. The dev namespace should only start the app
when the element is there:

```clojure
;; dev/atlas/dev.cljs
(defn main []
  ;; Add additional dev-time tooling here
  (dataspex/inspect "App state" re-frame.db/app-db)
  (when el
    (app/main el {:cities data/cities})))
```

(`app/render`, which runs after every code reload, already does nothing when
no app was started.)

## Loading the map

To turn the placeholder into a map, we'll use an old and trusty technique:
[progressive enhancement](https://developer.mozilla.org/en-US/docs/Glossary/Progressive_Enhancement).
The page works without JavaScript (you can read it, and the links work), and
JavaScript makes it better when it's available.

The client looks for elements with a `data-client-feature` attribute, and
decides what to do based on its value:

```clojure
;; src/atlas/progressive_enhancement.cljs
(ns atlas.progressive-enhancement)

(defn revive-map [el]
  ,,,)

(defn main []
  (doseq [el (js/document.querySelectorAll "[data-client-feature]")]
    (case (.getAttribute el "data-client-feature")
      "marker-map"
      (revive-map el))))
```

To revive the map, we first read the EDN data from the script tag, with the
ClojureScript reader:

```clojure
(ns atlas.progressive-enhancement
  (:require [cljs.reader :as reader]))

(defn revive-map [el]
  (let [data (->> (.querySelector el "script")
                  .-textContent
                  reader/read-string)]
    ,,,))
```

That gives us exactly the map the server printed: `::map/center`,
`::map/zoom` and `::map/points`. And we already have a function that takes a
DOM node and that data, loads Mapbox and draws the map with markers:
`mount-map` from `atlas/ui/map.cljs`. It doesn't care whether the node was
created by React or by the server:

```clojure
;; src/atlas/progressive_enhancement.cljs
(ns atlas.progressive-enhancement
  (:require [atlas.ui.map :as map]
            [cljs.reader :as reader]))

(defn revive-map [el]
  (let [data (->> (.querySelector el "script")
                  .-textContent
                  reader/read-string)]
    (set! (.-innerHTML el) "")
    (map/mount-map el data)))

(defn main []
  (doseq [el (js/document.querySelectorAll "[data-client-feature]")]
    (case (.getAttribute el "data-client-feature")
      "marker-map"
      (revive-map el))))
```

Emptying the element (`(set! (.-innerHTML el) "")`) before mounting keeps
Mapbox from warning that its container isn't empty.

Call it when the bundle starts:

```clojure
;; dev/atlas/dev.cljs
(ns atlas.dev
  (:require [atlas.core :as app]
            [atlas.data :as data]
            [atlas.progressive-enhancement :as pe]
            [atlas.ui.map :as map]
            [dataspex.core :as dataspex]
            [re-frame.core :as rf]
            [re-frame.db]))

;; Put your own Mapbox access token here (https://account.mapbox.com/)
(set! map/*mapbox-api-token* "YOUR_MAPBOX_ACCESS_TOKEN")

(defonce el (js/document.getElementById "app"))

(defn ^:dev/after-load reload []
  (rf/clear-subscription-cache!)
  (app/render))

(defn main []
  ;; Add additional dev-time tooling here
  (dataspex/inspect "App state" re-frame.db/app-db)
  (when el
    (app/main el {:cities data/cities}))
  (pe/main))
```

It runs once, when the page loads, and not after code reloads: by then the
placeholders have been replaced by maps. On the client-side app page there
are no placeholders, so it does nothing.

Open http://localhost:8089/city/london: the server sends the page, the bundle
loads, and the placeholder becomes a live map of London with its Pokémon.
Click Tokyo, and the server renders a new page.

## Testing the server

Since everything on the server is pure functions and data, it's easy to
test. `hiccup/expand` shows what the server-side alias produces, and the
handler can be called with a request map, no running server needed:

```clojure
;; test/atlas/server_test.clj
(ns atlas.server-test
  (:require [atlas.html :as html]
            [atlas.server :as server]
            [atlas.ui.map :as map]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [datadriven.hiccup :as hiccup]))

(deftest marker-map-alias-test
  ,,,

  (testing "The client can read the data back"
    (let [html (html/render (server/render-city-page "london"))
          edn-str (second (re-find #"<script type=\"application/edn\">(.*?)</script>" html))]
      (is (= (-> (edn/read-string edn-str)
                 (update ::map/points #(map :point/label %)))
             {::map/center [-0.1276 51.5072]
              ::map/zoom 12
              ::map/points ["Pikachu" "Eevee" "Snorlax" "Gengar" "Lapras"]})))))

(deftest handler-test
  ,,,

  (testing "Renders city pages on the server"
    (let [{:keys [status body]} (server/handler {:uri "/city/london" :request-method :get})]
      (is (= 200 status))
      (is (not (str/includes? body "<div id=\"app\"></div>")))
      (is (str/includes? body "<h1 class=\"text-xl mb-2\">Hello <span class=\"line-through\">world</span> London!</h1>"))
      (is (str/includes? body "<div class=\"aspect-video mb-4\" data-client-feature=\"marker-map\">"))
      (is (str/includes? body "<a class=\"link\" href=\"/city/tokyo\">Tokyo</a>"))))

  ,,,)
```

Run all the tests with `clojure -M:dev -m kaocha.runner`. The full test files
are in [`code/server-alias/test`](../code/server-alias/test/atlas/).

## Conclusion

What did we get out of this?

- The exact same UI code renders in the browser (with Reagent) and on the
  server (as an HTML string).
- Server-rendered pages can still have interactive parts, thanks to a little
  progressive enhancement.
- Because the UI is data, it was easy to give one part of it (the map alias)
  separate implementations for Clojure and ClojureScript.
- The progressive enhancement code reuses the same `mount-map` as the
  client-side app.

Why does this matter? Being able to choose, page by page, whether to render
on the server or in the browser is valuable. An article, a product page or
a city guide doesn't need to be a single-page app just because it contains a
map.

Reagent is a browser library, so it can't render on the server itself. But
since our views are plain functions that return data, it doesn't have to: on
the JVM, `hiccup/expand` and a small HTML renderer do the job.

## What's different from the Replicant version

- Replicant renders to a string with `replicant.string/render`. Here, the
  server expands aliases with `datadriven.hiccup/expand`, and renders HTML
  with the [hiccup](https://github.com/weavejester/hiccup) library, after a
  small clean-up step (`atlas.html`) that drops event data, namespaced
  attributes, `:key` and `:ref`, inserts `:innerHTML` unescaped, and adapts
  `:class` and `:style` to match Reagent.
- The server-side alias is registered with `hiccup/register-alias!` in
  `atlas/ui/map.clj`. The server requires `atlas.ui.map` explicitly, since
  `atlas.ui` only uses it with `:as-alias`.
- `:event/prevent-default` is built into `datadriven.hiccup`, so we didn't
  need to add it to an action dispatcher. It runs synchronously on the DOM
  event, before anything is dispatched to re-frame.
- The server has a `-main` function, and the page template is filled in with
  a plain string replacement instead of a regular expression.
- `revive-map` reads the script's `textContent` instead of `innerText`, and
  the progressive enhancement runs once at start-up, not after every code
  reload.
