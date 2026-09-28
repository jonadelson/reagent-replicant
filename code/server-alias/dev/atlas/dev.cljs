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
