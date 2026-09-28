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

(defn update-map [^js map {::keys [center zoom points]}]
  (.setZoom map zoom)
  (.panTo map (clj->js center))
  (.setData (.getSource map "points")
            (clj->js (points->feature-collection points))))

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

(hiccup/register-alias! ::marker-map
  (fn [attrs children]
    [map-component (assoc attrs ::points (into [] (keep second) children))]))
