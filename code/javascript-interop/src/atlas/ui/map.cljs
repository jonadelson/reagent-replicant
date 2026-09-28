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
