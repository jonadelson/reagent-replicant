# Using a JavaScript library

> Adapted for Reagent + re-frame from [Using a JavaScript library](https://replicant.fun/tutorials/javascript-interop/)
> by Christian Johansen. The code for this tutorial is in [`code/javascript-interop`](../code/javascript-interop/).

Sooner or later, a UI needs something you don't want to build yourself: a map,
a chart, a rich text editor. These come as JavaScript libraries, and they
don't care about your pure, data-driven views. They want a real DOM node to
take over, and they keep their own state inside it.

This is the first of three tutorials about bridging that gap. We'll render a
[Mapbox](https://www.mapbox.com/) map in a Reagent + re-frame app. This part
gets a map on screen and keeps it in sync with our data.
[The next part](./interop-alias.md) wraps the map in an
[alias](../guides/data-driven-reagent.md#aliases), so views can describe maps
with plain data. [The last part](./server-alias.md) makes the same alias work
when rendering HTML on the server.

## Setup

The starting point is a small app in the style of the
[state management tutorial](./state-atom.md). Copy
[`code/javascript-interop-setup`](../code/javascript-interop-setup/) to a new
directory and run:

```sh
npm install
npx tailwindcss -i ./src/main.css -o ./resources/public/tailwind.css --watch
```

and, in another terminal:

```sh
npx shadow-cljs watch app
```

Then open http://localhost:8080. You should see "Hello world!". Tests run on
the JVM with `clojure -M:dev -m kaocha.runner`.

If you're new to the stack, here is what's in the box:

- [shadow-cljs](https://shadow-cljs.github.io/docs/UsersGuide.html) compiles
  ClojureScript, serves `resources/public` on port 8080 and reloads code when
  you save.
- [Tailwind CSS](https://v3.tailwindcss.com/) (version 3, with the
  [daisyUI](https://daisyui.com/) plugin) generates `tailwind.css` from the
  class names it finds in our source files, like `:h1.text-xl`.
- [Reagent](https://reagent-project.github.io/) renders
  [hiccup](../guides/data-driven-reagent.md#hiccup), HTML written as Clojure
  data, with React.
- [re-frame](https://day8.github.io/re-frame/) keeps all application state in
  one map, *app-db*. The state changes only through *events*: handlers
  registered with `reg-event-db` that take the current state and return the
  next one. Views read the state through *subscriptions* (`reg-sub`).
- `src/datadriven/hiccup.cljc` is a small adapter that lets views be pure
  data, in the style of Replicant. Its `prepare` function turns event handlers
  written as data, like `{:on {:click [:store/assoc-in [:city] city]}}`, into
  functions that dispatch those vectors to re-frame. The
  [guide](../guides/data-driven-reagent.md#event-handlers-as-data) explains it
  in detail.

The wiring lives in `src/atlas/core.cljs`:

```clojure
;; src/atlas/core.cljs
(ns atlas.core
  (:require [atlas.ui :as ui]
            [datadriven.hiccup :as hiccup]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdc]))

;; Events: the only way the state changes

(rf/reg-event-db :app/initialize
  (fn [_ [_ initial-state]]
    initial-state))

(rf/reg-event-db :store/assoc-in
  (fn [db [_ path v]]
    (assoc-in db path v)))

;; Subscriptions: what the UI reads

(rf/reg-sub :app/state
  (fn [db _]
    db))

;; Rendering

(defn app []
  (hiccup/prepare (ui/render-page @(rf/subscribe [:app/state]))))

(defonce !root (atom nil))

(defn render []
  (some-> @!root (rdc/render [app])))

(defn main [el initial-state]
  (rf/dispatch-sync [:app/initialize initial-state])
  (reset! !root (rdc/create-root el))
  (render))
```

`app` is the only Reagent component in the app. It subscribes to the whole
state, calls the pure function `ui/render-page`, and prepares the result for
Reagent. Whenever app-db changes, it runs again from the top. (re-frame's own
docs recommend smaller, more targeted subscriptions. Subscribing to
everything is the simplest way to render "the whole UI as a function of the
whole state", which is what these tutorials are about. The
[guide](../guides/data-driven-reagent.md#top-down-rendering-with-re-frame)
discusses the trade-off.)

`:store/assoc-in` is a generic event that puts a value somewhere in app-db.
It's all the state management this app needs.

The dev namespace starts the app, and re-renders after every code reload.
It also hands app-db to [Dataspex](https://github.com/cjohansen/dataspex), so
you can inspect the state with the Dataspex browser extension:

```clojure
;; dev/atlas/dev.cljs
(ns atlas.dev
  (:require [atlas.core :as app]
            [dataspex.core :as dataspex]
            [re-frame.core :as rf]
            [re-frame.db]))

(defonce el (js/document.getElementById "app"))

(defn ^:dev/after-load reload []
  (rf/clear-subscription-cache!)
  (app/render))

(defn main []
  ;; Add additional dev-time tooling here
  (dataspex/inspect "App state" re-frame.db/app-db)
  (app/main el {}))
```

## Reconnaissance

When a third-party library is part of the plan, it pays to collect the pieces
one at a time before putting them together:

1. Get the smallest possible example working in plain HTML and JavaScript.
2. Translate that example to ClojureScript.
3. Move the ClojureScript version into the app.

Each step adds only a few unknowns, so when something breaks, you know where
to look. Debugging your Reagent integration is no fun if the real problem is
that you misunderstood how the library works.

## Minimal JavaScript example

Mapbox needs an access token. Create a free account at
https://account.mapbox.com/ and copy the default public token (it starts with
`pk.`). With it, a page can load Mapbox's stylesheet and script from their CDN
and set the token:

```html
<!-- resources/public/mapbox.html -->
<!DOCTYPE html>
<html>
  <head>
    <title>Mapbox JavaScript example</title>
    <link rel="stylesheet" type="text/css" href="https://api.mapbox.com/mapbox-gl-js/v2.14.1/mapbox-gl.css">
  </head>
  <body>
    <div id="app"></div>
    <script src="https://api.mapbox.com/mapbox-gl-js/v2.14.1/mapbox-gl.js"></script>
    <script type="text/javascript">
      mapboxgl.accessToken = "YOUR_MAPBOX_ACCESS_TOKEN";

      // The experiment goes here
    </script>
  </body>
</html>
```

Now let's ask for a map of San Francisco, zoomed in to show the city:

```js
var el = document.getElementById("app");

var map = new mapboxgl.Map({
  container: el,
  style: "mapbox://styles/mapbox/streets-v12",
  center: [-122.475238, 37.807962],
  zoom: 11
});
```

The page stays empty, and the console has no errors. Inspecting the page
explains why: the `div` is there, but it's 0 pixels tall. Mapbox fills its
container, so the container needs a size:

```js
var el = document.getElementById("app");
el.style.width = "800px";
el.style.height = "450px";

var map = new mapboxgl.Map({
  container: el,
  style: "mapbox://styles/mapbox/streets-v12",
  center: [-122.475238, 37.807962],
  zoom: 11
});
```

Now we have a map of San Francisco. The finished example is in
`resources/public/mapbox.html` (put your token in it and open
http://localhost:8080/mapbox.html).

Note that `center` is `[longitude, latitude]`, in that order. Mapbox uses
this order everywhere.

## Minimal ClojureScript example

Next, the same thing in ClojureScript. The app's `index.html` (from the setup)
already loads Mapbox and sets the token, but draws no map. Put your token in
it:

```html
<!-- resources/public/index.html -->
<!DOCTYPE html>
<html>
  <head>
    <title>Reagent Maps</title>
    <link rel="stylesheet" type="text/css" href="/tailwind.css">
    <link rel="stylesheet" type="text/css" href="https://api.mapbox.com/mapbox-gl-js/v2.14.1/mapbox-gl.css">
  </head>
  <body>
    <div id="app"></div>
    <script src="https://api.mapbox.com/mapbox-gl-js/v2.14.1/mapbox-gl.js"></script>
    <!-- Put your own Mapbox access token here (https://account.mapbox.com/) -->
    <script type="text/javascript">mapboxgl.accessToken = "YOUR_MAPBOX_ACCESS_TOKEN";</script>
    <script src="/app-js/main.js"></script>
  </body>
</html>
```

For the experiment, temporarily replace the dev namespace with a direct
translation of the JavaScript:

```clojure
;; dev/atlas/dev.cljs (temporary)
(ns atlas.dev)

(defonce el (js/document.getElementById "app"))

(defn main []
  (set! (.. el -style -width) "800px")
  (set! (.. el -style -height) "450px")

  (js/mapboxgl.Map.
   (clj->js
    {:container el
     :style "mapbox://styles/mapbox/streets-v12"
     :center [-122.475238 37.807962]
     :zoom 11})))
```

`js/mapboxgl.Map.` (note the trailing dot) is ClojureScript for
`new mapboxgl.Map(...)`, and `clj->js` turns the Clojure map into the
JavaScript object Mapbox expects.

These two small steps take minutes, and they pay off. If we had gone straight
to the app and seen an empty page, we would probably have blamed our interop
code, not a `div` without a height.

When it works, put the original dev namespace back.

## Integrating with Reagent

Now let's have the app render the map. First, a container with a size:

```clojure
;; src/atlas/ui.cljc
(ns atlas.ui
  (:require [atlas.ui.map :as map]))

(defn render-page [state]
  [:main.m-4
   [:h1.text-xl.mb-2
    "Hello "
    [:span.line-through
     "world"]
    " San Francisco!"]
   (map/render-map)])
```

```clojure
;; src/atlas/ui/map.cljs
(ns atlas.ui.map)

(defn render-map []
  [:div.aspect-video])
```

`atlas.ui` is a `.cljc` file: code that compiles both as Clojure (on the JVM)
and as ClojureScript (in the browser). Views written as pure functions of data
fit well in `.cljc` files. They can be tested on the JVM, and even render HTML
on a server. `atlas.ui.map`, on the other hand, is a `.cljs` file: it is going
to be full of browser-specific code that talks to Mapbox, and there's no point
pretending otherwise. Keep such exceptions few and small.

Instead of a fixed width and height, the Tailwind class `aspect-video` gives
the element a 16:9 aspect ratio. It takes the full width available, and you
can still set an explicit size where needed.

### The one place where data meets React

Up to now, everything in our views has been data: vectors, maps, keywords.
Mapbox can't be expressed like that. It needs the actual DOM node, it needs
to be told when our data changes, and it should be cleaned up when the node
goes away. In other words, it needs a *life cycle*.

Replicant solves this with special attributes, `:replicant/on-mount`,
`:replicant/on-render` and `:replicant/on-unmount`, which take functions
that receive the DOM node. React has the same concept built into its
component model, and Reagent gives us two ways to use it:

- a `:ref` callback, which React calls with the DOM node when the element is
  created (and with `nil` when it's removed), and
- a class component made with `r/create-class`, which has
  `:component-did-mount`, `:component-did-update` and
  `:component-will-unmount` methods.

This is the one place in these tutorials where views reach for React's
component model. That's fine: the view is still a function of data, and the
stateful, imperative part is fenced into one small namespace whose whole
job is to translate data into Mapbox calls. The library owns its DOM node and
keeps state inside it whether we like it or not; the component is simply the
honest boundary between our world and its world.

For mounting the map, a `:ref` callback is enough:

```clojure
;; src/atlas/ui/map.cljs
(ns atlas.ui.map)

(defn mount-map [node]
  (js/mapboxgl.Map.
   (clj->js
    {:container node
     :style "mapbox://styles/mapbox/streets-v12"
     :center [-122.475238 37.807962]
     :zoom 11})))

(defn mount-map-ref [node]
  (when node
    (mount-map node)))

(defn render-map []
  [:div.aspect-video
   {:ref mount-map-ref}])
```

`hiccup/prepare` passes `:ref` through to Reagent untouched. React calls
`mount-map-ref` with the `div` once it's in the DOM, and the map appears. The
page re-renders all the time (every change to app-db runs `app` again), but
since `mount-map-ref` is the same function every time, React leaves the ref
alone and the map is only mounted once. Keep that in mind; it matters in a
minute.

## Parameterizing the map

A map that always shows San Francisco isn't very useful. Let's pass the
center and zoom level in:

```clojure
(defn mount-map [node {:keys [center zoom]}]
  (js/mapboxgl.Map.
   (clj->js
    {:container node
     :style "mapbox://styles/mapbox/streets-v12"
     :center center
     :zoom zoom})))
```

The obvious next step would be `{:ref (fn [node] (when node (mount-map node
data)))}`. Don't. That creates a *new* function every time the page renders.
When React sees a different ref function, it calls the old one with `nil` and
the new one with the node, so we would create a new Mapbox instance, in the
same `div`, on every render.

We need something that lives exactly as long as the DOM element, and can see
the latest data. That's a component:

```clojure
;; src/atlas/ui/map.cljs
(ns atlas.ui.map
  (:require [reagent.core :as r]))

(defn mount-map [^js node {:keys [center zoom]}]
  ,,,)

(defn map-component [_data]
  (let [!node (atom nil)]
    (r/create-class
     {:display-name "atlas.ui.map/map-component"

      :component-did-mount
      (fn [this]
        (mount-map @!node (r/props this)))

      :reagent-render
      (fn [_data]
        [:div.aspect-video.mb-4 {:ref #(reset! !node %)}])})))

(defn render-map [data]
  [map-component data])
```

(`,,,` marks code left out because it hasn't changed. Clojure treats commas
as whitespace.)

A few Reagent details:

- `[map-component data]`, with square brackets instead of parentheses, tells
  Reagent to create a component. React calls `map-component` when the element
  first appears and keeps the result alive until it's removed. `prepare`
  leaves vectors that start with a function alone, so this works inside our
  otherwise data-only hiccup.
- The outer function runs once per map on the page. That gives each map its
  own `!node` atom, which the `:ref` callback fills with the DOM node. (Older
  Reagent code uses `reagent.dom/dom-node` for this; React 19 removed the API
  it was built on.) The atom is a plain Clojure atom, not a Reagent atom: it
  isn't state the UI renders, just a place to keep a reference.
- `:reagent-render` returns the component's hiccup. Here it's plain Reagent
  hiccup, since it has no event handlers or aliases for `prepare` to handle.
- `:component-did-mount` runs once the element is in the DOM. `(r/props this)`
  is the map passed to the component: `data`.
- The `^js` hint on `node` tells the ClojureScript compiler that this is a
  JavaScript object, so it doesn't rename property access on it when
  compiling with advanced optimizations.

Notice what didn't change: `atlas.ui` still calls a function and gets a
vector back. Views don't know or care that there's a component inside.

To try it, give the app some cities. The data goes in a `.cljc` file:

```clojure
;; src/atlas/data.cljc
(ns atlas.data)

(def cities
  [{:id "san-francisco"
    :name "San Francisco"
    :position [-122.475238, 37.807962]
    :zoom 11}])
```

and into app-db when the app starts:

```clojure
;; dev/atlas/dev.cljs
(ns atlas.dev
  (:require [atlas.core :as app]
            [atlas.data :as data]
            [dataspex.core :as dataspex]
            [re-frame.core :as rf]
            [re-frame.db]))

(defonce el (js/document.getElementById "app"))

(defn ^:dev/after-load reload []
  (rf/clear-subscription-cache!)
  (app/render))

(defn main []
  ;; Add additional dev-time tooling here
  (dataspex/inspect "App state" re-frame.db/app-db)
  (app/main el {:cities data/cities}))
```

The page now shows the map of the selected city, and a list of cities to
choose from:

```clojure
;; src/atlas/ui.cljc
(ns atlas.ui
  (:require [atlas.ui.map :as map]))

(defn render-title [city-name]
  [:h1.text-xl.mb-2
   "Hello "
   [:span {:class (when city-name "line-through")}
    "world"]
   (when city-name
     (str " " city-name))
   "!"])

(defn render-page [{:keys [city cities]}]
  (let [{:keys [name position zoom]} city]
    [:main.m-4
     (render-title name)
     (when position
       (map/render-map
        {:center position
         :zoom (or zoom 11)}))
     [:h2.text-lg.mb-2 "Choose city"]
     [:ul
      (for [city cities]
        (if (= name (:name city))
          [:li (:name city)]
          [:li
           [:button.link
            {:on {:click [[:store/assoc-in [:city] city]]}}
            (:name city)]]))]]))
```

The button's click handler is data: a list of actions (here just one) that
`prepare` dispatches to re-frame when the button is clicked.
`[:store/assoc-in [:city] city]` puts the whole city map in app-db under
`:city`.

At first there's no map, only the title and a (short) list of cities. Click
"San Francisco": the title changes, the map element is created, React calls
`:component-did-mount`, and the map appears.

## Updating the rendered map

Let's add more cities:

```clojure
;; src/atlas/data.cljc
(ns atlas.data)

(def cities
  [{:id "san-francisco"
    :name "San Francisco"
    :position [-122.475238, 37.807962]
    :zoom 11}
   {:id "london"
    :name "London"
    :position [-0.1276, 51.5072]
    :zoom 12}
   {:id "tokyo"
    :name "Tokyo"
    :position [139.6917, 35.6895]
    :zoom 12}
   {:id "cape-town"
    :name "Cape Town"
    :position [18.4241, -33.9249]
    :zoom 11}])
```

Pick one city, then another. The title changes, but the map doesn't move. The
map component was already mounted, React reused it, and we only ever look at
the data on mount.

We'll look at two ways to fix this. Both are worth knowing.

### The heavy-handed way: keys

A `:key` tells React which element is which. If the key changes, React treats
it as a different element: it removes the old component and mounts a new one.
So if we key the map on its data, every change gives us a fresh map at the
new position:

```clojure
(defn render-map [data]
  ^{:key (pr-str data)} [map-component data])
```

(With Reagent components, the key goes in metadata. React wants a string or a
number, so we print the data to a string.)

Since maps now come and go, we should also clean up after the old ones.
Mapbox holds on to WebGL resources and event listeners until you call its
`remove` method. That needs a reference to the Mapbox instance, and an
unmount method:

```clojure
(defn map-component [_data]
  (let [!node (atom nil)
        !map (atom nil)]
    (r/create-class
     {:display-name "atlas.ui.map/map-component"

      :component-did-mount
      (fn [this]
        (reset! !map (mount-map @!node (r/props this))))

      :component-will-unmount
      (fn [_this]
        (.remove ^js @!map))

      :reagent-render
      (fn [_data]
        [:div.aspect-video.mb-4 {:ref #(reset! !node %)}])})))
```

This works, but it's a blunt instrument. Throwing away the map and building
a new one means reloading the style and the map tiles, and the user sees the
map flash.

### The lighter touch: updating the map

A better way is to keep the map and tell it about the new data. Mapbox can
change zoom and pan to a new center:

```clojure
(defn update-map [^js map {:keys [center zoom]}]
  (.setZoom map zoom)
  (.panTo map (clj->js center)))
```

Replicant gives life-cycle hooks a small "memory" (`:replicant/remember` and
`:replicant/memory`) to carry the Mapbox instance from the mount hook to the
update hook. In Reagent, the closure already does that job: `!map` lives as
long as the component. We add a `:component-did-update` method, which React
calls after every render where the component's data changed:

```clojure
;; src/atlas/ui/map.cljs
(ns atlas.ui.map
  (:require [reagent.core :as r]))

(defn mount-map [^js node {:keys [center zoom]}]
  (js/mapboxgl.Map.
   (clj->js
    {:container node
     :style "mapbox://styles/mapbox/streets-v12"
     :center center
     :zoom zoom})))

(defn update-map [^js map {:keys [center zoom]}]
  (.setZoom map zoom)
  (.panTo map (clj->js center)))

(defn map-component [_data]
  (let [!node (atom nil)
        !map (atom nil)]
    (r/create-class
     {:display-name "atlas.ui.map/map-component"

      :component-did-mount
      (fn [this]
        (reset! !map (mount-map @!node (r/props this))))

      :component-did-update
      (fn [this _old-argv]
        (update-map @!map (r/props this)))

      :component-will-unmount
      (fn [_this]
        (.remove ^js @!map))

      :reagent-render
      (fn [_data]
        [:div.aspect-video.mb-4 {:ref #(reset! !node %)}])})))

(defn render-map [data]
  [map-component data])
```

The key is gone again: we want React to keep the component. Reagent only
re-renders a component when its arguments change (compared with `=`), so
`update-map` runs when the selected city changes, and not on every render of
the page. The unmount method stays: it costs nothing and it keeps us from
leaking maps if the map is ever removed from the page.

Now the map glides from city to city. If you'd rather have a more dramatic
trip, try `flyTo`:

```clojure
(defn update-map [^js map {:keys [center zoom]}]
  (.flyTo map (clj->js {:zoom zoom
                        :center center})))
```

Replicant can also look up a node's remembered value from outside the hooks,
with `replicant.dom/recall`. React has no equivalent, so if other code needs
the Mapbox instance, you have to put it somewhere that code can find it
yourself (for example on the DOM node).

For reference, here is how the Replicant hooks map to Reagent:

| Replicant | Reagent |
|---|---|
| `:replicant/on-mount` | `:component-did-mount`, or a `:ref` callback called with the node |
| `:replicant/on-update` | `:component-did-update` |
| `:replicant/on-unmount` | `:component-will-unmount`, or a `:ref` callback called with `nil` |
| `:replicant/on-render` | the three above |
| `:replicant/node` | the node, captured with a `:ref` callback |
| `:replicant/remember` / `:replicant/memory` | an atom in the component's closure |
| `:replicant/key` | `:key` (in metadata for components) |

## Tests

`atlas.ui` now requires `atlas.ui.map`, which only exists as ClojureScript, so
the views can no longer be loaded on the JVM. The project's only JVM test for
now checks that the city data is in the shape the map expects
(`test/atlas/data_test.cljc`). The next two tutorials fix this: an alias makes
the page pure data again, and a server-side version of the alias makes it
render on the JVM.

## What's different from the Replicant version

- Replicant's life-cycle hooks are attributes on an element. Here they are a
  Reagent class component (`r/create-class`) or a `:ref` callback. This is the
  only place the tutorials use a real component, and views still just call
  `map/render-map` and get a vector back.
- The DOM node comes from a `:ref` callback, and the Mapbox instance is kept in
  an atom in the component's closure instead of Replicant's
  `remember`/`memory`.
- A `:ref` callback must be the same function on every render, or React calls
  it again. Replicant hooks don't have this pitfall.
- We added `:component-will-unmount` to remove the Mapbox instance. The
  original doesn't clean up; with React creating and discarding components,
  it's a good habit.
- State lives in re-frame's app-db and changes through the
  `:store/assoc-in` event, instead of a Clojure atom with `swap!`.
- There is no `replicant.dom/recall` equivalent.

## Up next

We now have a map that follows our data. [In the next
tutorial](./interop-alias.md) we add markers, load Mapbox only when it's
needed, and hide the component behind an alias, so views can describe a map
with nothing but data.
