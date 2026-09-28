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
