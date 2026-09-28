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
             (into {::points (into [] (keep second) children)})))}]]))
