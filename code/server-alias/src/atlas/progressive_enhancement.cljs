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
