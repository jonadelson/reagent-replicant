# Wrapping a library in an alias

> Adapted for Reagent + re-frame from [Wrapping a library in an alias](https://replicant.fun/tutorials/interop-alias/)
> by Christian Johansen. The code for this tutorial is in [`code/interop-alias`](../code/interop-alias/).

This is the second of three tutorials about using a JavaScript library in a
data-driven Reagent + re-frame UI. In [the first one](./javascript-interop.md)
we rendered a [Mapbox](https://www.mapbox.com/) map with a small Reagent
component. Now we'll teach the map to show markers, make it load Mapbox by
itself, and put it behind an [alias](../guides/data-driven-reagent.md#aliases),
so that views can describe a map with plain data, like everything else.

## Setup

Start from the result of the first tutorial: copy
[`code/javascript-interop`](../code/javascript-interop/) to a new directory.
Run it like before:

```sh
npm install
npx tailwindcss -i ./src/main.css -o ./resources/public/tailwind.css --watch
```

and, in another terminal:

```sh
npx shadow-cljs watch app
```

Then open http://localhost:8080. Tests run on the JVM with
`clojure -M:dev -m kaocha.runner`.

A quick recap, in case you're starting here. The app keeps its state in
[re-frame](https://day8.github.io/re-frame/)'s app-db: a list of cities, and
the selected city. One Reagent component at the top (`atlas.core/app`)
subscribes to the whole state and calls the pure function `atlas.ui/render-page`,
which returns [hiccup](../guides/data-driven-reagent.md#hiccup). Event handlers
in that hiccup are data, like `{:on {:click [[:store/assoc-in [:city] city]]}}`,
and `datadriven.hiccup/prepare` turns them into functions that dispatch the
actions to re-frame (see the
[guide](../guides/data-driven-reagent.md#event-handlers-as-data)). The map
itself is `atlas.ui.map/map-component`, a Reagent class component that mounts
Mapbox when it appears, and updates it when its data changes.

## The task

We'll add markers to the map, and then wrap the component in an alias, which
gives it a data-driven interface. Before that, let's make the map easier to
use.

## Loading Mapbox on demand

Right now, `index.html` loads Mapbox's CSS and JavaScript and sets the access
token. Every page pays for Mapbox, maps or not, and the map component only
works if someone remembered to set it up elsewhere. Both problems go away if
the component loads Mapbox itself, the first time a map is mounted.

Loading a stylesheet means adding a `link` element to the document's head:

```clojure
(let [link (.createElement js/document "link")]
  (set! (.-rel link) "stylesheet")
  (set! (.-type link) "text/css")
  (set! (.-href link) "https://api.mapbox.com/mapbox-gl-js/v2.14.1/mapbox-gl.css")
  (.appendChild js/document.head link))
```

A script works the same way:

```clojure
(let [script (.createElement js/document "script")]
  (set! (.-src script) "https://api.mapbox.com/mapbox-gl-js/v2.14.1/mapbox-gl.js")
  (.appendChild js/document.head script))
```

The token can only be set once the script has loaded, which happens
asynchronously. The same goes for anything else that uses `mapboxgl`, so our
loading function returns a JavaScript promise that resolves when Mapbox is
ready. If Mapbox is already there, it resolves right away:

```clojure
;; src/atlas/ui/map.cljs
(ns atlas.ui.map
  (:require [datadriven.hiccup :as hiccup]
            [reagent.core :as r]))

(def mapbox-url "https://api.mapbox.com/mapbox-gl-js/v2.14.1")

(def ^:dynamic *mapbox-api-token* nil)

(defn load-mapbox [^js el api-token]
  (let [doc (.-ownerDocument el)
        win (.-defaultView doc)]
    (js/Promise.
     (fn [res]
       (if (.-mapboxgl win)
         (res)
         (let [link (.createElement doc "link")
               script (.createElement doc "script")]
           (.addEventListener script "load"
            (fn [_]
              (set! (.. win -mapboxgl -accessToken) api-token)
              (res))
            #js {:once true})
           (set! (.-rel link) "stylesheet")
           (set! (.-type link) "text/css")
           (set! (.-href link) (str mapbox-url "/mapbox-gl.css"))
           (set! (.-src script) (str mapbox-url "/mapbox-gl.js"))
           (.appendChild (.-head doc) link)
           (.appendChild (.-head doc) script)))))))
```

(We require `datadriven.hiccup` already; we'll need it for the alias.)

Instead of `js/document` and `js/window`, the function uses the document the
element belongs to (`ownerDocument`) and that document's window
(`defaultView`). That way the map also works inside an iframe, which is how
tools like [Portfolio](https://github.com/cjohansen/portfolio) display
components.

(If Mapbox logs a warning about missing CSS, the script finished loading
before the stylesheet did. The map still works once the CSS arrives. If it
bothers you, wait for the link's `load` event too.)

`mount-map` now waits for Mapbox before creating the map:

```clojure
(defn mount-map [^js node {::keys [center zoom]}]
  (-> (load-mapbox node *mapbox-api-token*)
      (.then
       (fn []
         (let [^js mapboxgl (.. node -ownerDocument -defaultView -mapboxgl)]
           (new (.-Map mapboxgl)
                (clj->js
                 {:container node
                  :style "mapbox://styles/mapbox/streets-v12"
                  :center center
                  :zoom zoom})))))))
```

Notice `{::keys [center zoom]}`. From now on, the map's options use
*namespaced* keys: in the `atlas.ui.map` namespace, `::center` is short for
`:atlas.ui.map/center`. Other namespaces write it as `::map/center`. You'll
see why when we build the alias. Update `update-map` the same way
(`{::keys [center zoom]}`), and the call in `atlas.ui`:

```clojure
(map/render-map
 {::map/center position
  ::map/zoom (or zoom 11)})
```

The token is a *dynamic var*, a global that can be set from outside the
namespace. Set it in the dev namespace, which is where your token now goes:

```clojure
;; dev/atlas/dev.cljs
(ns atlas.dev
  (:require [atlas.core :as app]
            [atlas.data :as data]
            [atlas.ui.map :as map]
            [dataspex.core :as dataspex]
            [re-frame.core :as rf]
            [re-frame.db]))

;; Put your own Mapbox access token here (https://account.mapbox.com/)
(set! map/*mapbox-api-token* "YOUR_MAPBOX_ACCESS_TOKEN")

,,,
```

(`,,,` marks code left out because it hasn't changed. Clojure treats commas
as whitespace.)

Finally, remove Mapbox from `index.html`:

```html
<!-- resources/public/index.html -->
<!DOCTYPE html>
<html>
  <head>
    <title>Reagent Maps</title>
    <link rel="stylesheet" type="text/css" href="/tailwind.css">
  </head>
  <body>
    <div id="app"></div>
    <script src="/app-js/main.js"></script>
  </body>
</html>
```

`mount-map` returns a promise now, not a Mapbox instance, so the component
has to wait for it. The component keeps the promise, and chains updates and
the final clean-up onto it:

```clojure
(defn map-component [_data]
  (let [!node (atom nil)
        !map (atom nil)] ;; A promise of the Mapbox instance
    (r/create-class
     {:display-name "atlas.ui.map/marker-map"

      :component-did-mount
      (fn [this]
        (reset! !map (mount-map @!node (r/props this))))

      :component-did-update
      (fn [this _old-argv]
        (let [data (r/props this)]
          (.then @!map #(update-map % data))))

      :component-will-unmount
      (fn [_this]
        (.then @!map #(.remove ^js %)))

      :reagent-render
      (fn [_data]
        [:div.aspect-video.mb-4 {:ref #(reset! !node %)}])})))
```

A nice side effect: if the user picks a new city before the map has finished
loading, the update simply waits its turn.

## Rendering markers

Now for the markers. In Mapbox, that takes three steps:

- Load an image for the marker.
- Add a *source*: the data for the markers.
- Add a *layer* that draws the source's data with the image.

Mapbox can do a lot more than this, but we're here to learn about
integration, not Mapbox, so a few markers will do.

### Loading markers

For crisp markers on high-resolution screens, the image is twice the size it
will be displayed at, and we tell Mapbox its pixel ratio is 2. Copy
[`map-marker.png`](../code/interop-alias/resources/public/map-marker.png) to
`resources/public`. `loadImage` takes a callback, so we wrap it in a promise
too:

```clojure
(defn load-marker [^js map id url]
  (js/Promise.
   (fn [resolve reject]
     (.loadImage map url
      (fn [error image]
        (if error
          (reject error)
          (do
            (.addImage map id image #js {:pixelRatio 2})
            (resolve id))))))))
```

Mapbox wants the marker data as [GeoJSON](https://geojson.org/):

```clojure
{:type "FeatureCollection"
 :features
 [{:type "Feature"
   :geometry {:type "Point"
              :coordinates [-122.00004 37.571414]}
   :properties {:id ",,,"
                ,,,}}
  ,,,]}
```

`:properties` can hold anything, and layers can use it, for example to show
a label. That's a lot of structure for a point, so our data will be more
compact, and we'll convert it:

```clojure
;; src/atlas/data.cljc
(ns atlas.data)

(def cities
  [{:id "san-francisco"
    :name "San Francisco"
    :position [-122.475238, 37.807962]
    :zoom 11
    :points
    [{:point/label "Bulbasaur"
      :point/latitude 37.807962
      :point/longitude -122.475238}
     {:point/label "Charmander"
      :point/latitude 34.062759
      :point/longitude -118.35718}
     {:point/label "Squirtle"
      :point/latitude 37.805929
      :point/longitude -122.429582}
     {:point/label "Magnemite"
      :point/latitude 37.8269775
      :point/longitude -122.425144}
     {:point/label "Magmar"
      :point/latitude 37.571414
      :point/longitude -122.00004}]}
   ,,,])
```

```clojure
(defn points->feature-collection [points]
  {:type "FeatureCollection"
   :features
   (mapv
    (fn [{:point/keys [label longitude latitude]}]
      {:type "Feature"
       :geometry {:type "Point"
                  :coordinates [longitude latitude]}
       :properties {:id label
                    :label label}})
    points)})
```

`configure-map` puts the pieces together: it loads the marker image, adds the
points as a source, and adds a layer that draws each point with the marker
and its label. It returns (a promise of) the map:

```clojure
(defn configure-map [^js map {::keys [points]}]
  (-> (load-marker map "blue-marker" "/map-marker.png")
      (.then
       (fn [_]
         (.addSource
          map "points"
          (clj->js
           {:type "geojson"
            :data (points->feature-collection points)}))

         (.addLayer
          map
          (clj->js
           {:id "points"
            :type "symbol"
            :source "points"
            :layout {:icon-image "blue-marker"
                     :icon-allow-overlap true
                     :text-field ["get" "label"]
                     :text-font ["Open Sans Semibold"]
                     :text-offset [0 0.5]
                     :text-allow-overlap true
                     :text-anchor "top"}}))

         map))))
```

`["get" "label"]` is a Mapbox *expression*: "the `label` property of each
feature".

Images and sources can only be added once the map has loaded its style, so
`mount-map` waits for Mapbox's `load` event before calling `configure-map`:

```clojure
(defn mount-map
  "Loads Mapbox, renders a map in `node` and adds the points. Returns a
  promise that resolves to the Mapbox instance when all of that is done."
  [^js node {::keys [center zoom] :as data}]
  (-> (load-mapbox node *mapbox-api-token*)
      (.then
       (fn []
         (let [^js mapboxgl (.. node -ownerDocument -defaultView -mapboxgl)
               map (new (.-Map mapboxgl)
                        (clj->js
                         {:container node
                          :style "mapbox://styles/mapbox/streets-v12"
                          :center center
                          :zoom zoom}))]
           (js/Promise.
            (fn [res]
              (.on map "load" #(res map)))))))
      (.then #(configure-map % data))))
```

The last piece is to pass the points along in `atlas.ui`:

```clojure
;; src/atlas/ui.cljc
(defn render-page [{:keys [city cities]}]
  (let [{:keys [name position zoom points]} city]
    [:main.m-4
     (render-title name)
     (when position
       (map/render-map
        {::map/center position
         ::map/zoom (or zoom 11)
         ::map/points points}))
     [:h2.text-lg.mb-2 "Choose city"]
     ,,,]))
```

Select San Francisco, and it's full of Pokémon.

### Point data updates

London deserves some Pokémon too:

```clojure
(def cities
  [{:id "san-francisco"
    ,,,}
   {:id "london"
    :name "London"
    :position [-0.1276, 51.5072]
    :zoom 12
    :points
    [{:point/label "Pikachu"
      :point/latitude 51.5081
      :point/longitude -0.1281}
     {:point/label "Eevee"
      :point/latitude 51.5074
      :point/longitude -0.1657}
     {:point/label "Snorlax"
      :point/latitude 51.5081
      :point/longitude -0.0759}
     {:point/label "Gengar"
      :point/latitude 51.5663
      :point/longitude -0.1464}
     {:point/label "Lapras"
      :point/latitude 51.5055
      :point/longitude -0.0754}]}
   ,,,])
```

Reload and pick London: Pokémon. Reload and pick San Francisco: Pokémon. But
pick one city and then the other, and the second city is empty. The points
are only added on mount, and `update-map` ignores them.

With this few points, we could hand Mapbox all of them up front and let it
sort things out. That wouldn't teach us anything about integrating a
library, though. Instead, `update-map` replaces the source's data with
`setData`:

```clojure
(defn update-map [^js map {::keys [center zoom points]}]
  (.setZoom map zoom)
  (.panTo map (clj->js center))
  (.setData (.getSource map "points")
            (clj->js (points->feature-collection points))))
```

Now every city shows its own Pokémon.

## Adding an alias

The map has the features we want. Time to give it a data-driven interface.

An alias is a custom hiccup tag: a namespaced keyword, like
`:atlas.ui.map/marker-map`, backed by a function that returns hiccup.
`hiccup/prepare` calls the function when it meets the tag. Views that use
aliases stay plain data (you can compare them with `=`, and read them in a
REPL), and the function behind the tag is looked up late, when the hiccup is
prepared. The [guide](../guides/data-driven-reagent.md#aliases) has the
details.

If the markers are the *content* of the map, they could be its children. We
won't write them as bare Clojure maps, though: a map right after the tag is
always read as the attribute map, so maps as children are a trap. Hiccup-like
nodes work well instead:

```clojure
[::map/marker-map {::map/center position
                   ::map/zoom zoom}
 [::map/marker
  {:point/label "Pikachu"
   :point/latitude 51.5081
   :point/longitude -0.1281}]
 [::map/marker
  {:point/label "Eevee"
   :point/latitude 51.5074
   :point/longitude -0.1657}]]
```

Some things to note:

- The map's parameters are namespaced keys in the attribute map. This is why
  we switched to namespaced keys earlier. `prepare` never passes namespaced
  attributes on to React, so the same map can also carry ordinary HTML
  attributes, like `:class`, and the alias can pass them all to its element
  without knowing which ones they are.
- `::map/marker` doesn't need to be an alias. The map alias receives the
  markers as its children, and just takes the attribute map out of each one.
- `prepare` hands the alias a flat list of children: lists (like the result
  of `for`) are spliced in. Unlike Replicant, it does not remove `nil`
  children, so the alias skips them itself.

Here is the alias. It registers a function for `::marker-map` that returns
our component, with the points added to its data:

```clojure
(hiccup/register-alias! ::marker-map
  (fn [attrs children]
    [map-component (assoc attrs ::points (into [] (keep second) children))]))
```

An alias usually expands to plain hiccup. This one expands to
`[map-component attrs]`, a vector that starts with a function, which is how
Reagent spells "a component". `prepare` doesn't touch such vectors: it
expands the alias, sees the component, and leaves it for Reagent. The
component's data is still just a map: everything the view said, plus the
points.

The component, in turn, renders a `div` with the attributes it was given, so
the caller can add a class. `prepare` doesn't look inside components (they
render later, when React gets to them), so the component prepares its own
hiccup. That removes the namespaced parameters (React would warn about them),
and would also handle any `:on` event data the caller added:

```clojure
(defn map-component [_attrs]
  (let [!node (atom nil)
        !map (atom nil)] ;; A promise of the Mapbox instance
    (r/create-class
     {:display-name "atlas.ui.map/marker-map"

      :component-did-mount
      (fn [this]
        (reset! !map (mount-map @!node (r/props this))))

      :component-did-update
      (fn [this _old-argv]
        (let [attrs (r/props this)]
          (.then @!map #(update-map % attrs))))

      :component-will-unmount
      (fn [_this]
        (.then @!map #(.remove ^js %)))

      :reagent-render
      (fn [attrs]
        (hiccup/prepare
         [:div.aspect-video (assoc attrs :ref #(reset! !node %))]))})))
```

`render-map` is no longer needed. The `mb-4` class that used to be
hard-coded in the component moves out to the page, which is the one that
knows how much space it wants below the map:

```clojure
;; src/atlas/ui.cljc
(ns atlas.ui
  (:require [atlas.ui.map :as-alias map]))

,,,

(defn render-page [{:keys [city cities]}]
  (let [{:keys [name position zoom points]} city]
    [:main.m-4
     (render-title name)
     (when position
       [::map/marker-map
        {:class "mb-4"
         ::map/center position
         ::map/zoom (or zoom 11)}
        (for [point points]
          [::map/marker point])])
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

Look at the `ns` form: `:as-alias` instead of `:as`. The page no longer
calls anything in `atlas.ui.map`; it only uses keywords from it. `:as-alias`
(Clojure 1.11 and newer, and ClojureScript) sets up the `map/` shorthand
without loading the namespace. Someone still has to load `atlas.ui.map` so
that the alias gets registered, and that's the app's entry point:

```clojure
;; src/atlas/core.cljs
(ns atlas.core
  (:require [atlas.ui :as ui]
            [atlas.ui.map] ;; Registers the ::map/marker-map alias
            [datadriven.hiccup :as hiccup]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdc]))
```

A bonus: `atlas.ui` no longer depends on ClojureScript-only code, so it loads
on the JVM again, and we can test the page. The test checks that the page
asks for the right map, with the right markers, without a browser or Mapbox
in sight:

```clojure
;; test/atlas/ui_test.cljc
(ns atlas.ui-test
  (:require [atlas.data :as data]
            [atlas.ui :as ui]
            [atlas.ui.map :as-alias map]
            [clojure.test :refer [deftest is testing]]))

(defn find-nodes
  "Returns every hiccup node in `hiccup` whose tag is `tag`."
  [hiccup tag]
  (->> (tree-seq coll? seq hiccup)
       (filter #(and (vector? %) (= tag (first %))))))

(def san-francisco (first data/cities))

(deftest render-page-test
  (testing "Renders no map when no city is selected"
    (is (empty? (find-nodes (ui/render-page {:cities data/cities})
                            ::map/marker-map))))

  (testing "Renders a map of the selected city"
    (is (= (-> (ui/render-page {:city san-francisco :cities data/cities})
               (find-nodes ::map/marker-map)
               first
               second)
           {:class "mb-4"
            ::map/center [-122.475238 37.807962]
            ::map/zoom 11})))

  (testing "Renders a marker for each point in the city"
    (is (= (->> (find-nodes (ui/render-page {:city san-francisco
                                              :cities data/cities})
                            ::map/marker)
                (map (comp :point/label second)))
           ["Bulbasaur" "Charmander" "Squirtle" "Magnemite" "Magmar"])))

  (testing "Selects a city on click"
    (is (= (->> (find-nodes (ui/render-page {:city san-francisco
                                              :cities data/cities})
                            :button.link)
                (map (comp :click :on second))
                first)
           [[:store/assoc-in [:city] (second data/cities)]]))))
```

Run it with `clojure -M:dev -m kaocha.runner`.

The full `src/atlas/ui/map.cljs` is in
[`code/interop-alias`](../code/interop-alias/src/atlas/ui/map.cljs).

## Closing words

We now have a data-driven interface to Mapbox. What did that buy us?

- Views draw a map with markers without any imperative code or inline
  functions. The page is data from top to bottom.
- Because the map is data, tests can check that the page has the right map
  with the right markers, without getting tangled up in Mapbox.
- The alias is a level of indirection, and that will let the map move to the
  server with no changes to the views. That's the topic of the
  [third and last part](./server-alias.md).

Also worth noting: the Reagent-specific code is small. It's the alias
registration and the class component around the Mapbox functions. Everything
else is plain ClojureScript that knows nothing about React. You don't need a
"Mapbox for Reagent" library, just a thin wrapper.

The approach has limits. Mapbox's API is huge, and wrapping all of it would be
a big job. You don't need to: several small aliases for the things your app
actually does go a long way.

We also convert the points between two representations, and then to
JavaScript, on every change. That's fine for a handful of markers, not for
thousands. For large data sets you'd let Mapbox do the heavy lifting (for
example with its own filtering), and perhaps pass the alias a URL to a
GeoJSON file instead of the points themselves. The same basic approach still
applies.

## What's different from the Replicant version

- The alias is registered with `hiccup/register-alias!` and a namespaced
  keyword, instead of `defalias`. Views write `[::map/marker-map ...]`.
- The alias expands to a Reagent component (`[map-component attrs]`) instead
  of an element with life-cycle hooks. Since `prepare` doesn't descend into
  components, the component calls `hiccup/prepare` on its own hiccup.
- The component keeps the *promise* of the Mapbox instance and chains updates
  and removal onto it, so an update that arrives while the map is still
  loading waits instead of failing.
- `atlas.ui` requires `atlas.ui.map` with `:as-alias`, and `atlas.core`
  loads the implementation. That keeps the page loadable on the JVM, so it
  has tests.
- `prepare` doesn't remove `nil` children before calling an alias, so the
  alias uses `keep`.
- Small fixes: `load-marker` only adds the image when loading succeeded, and
  the Mapbox expression is written with strings (`["get" "label"]`) instead
  of symbols.
