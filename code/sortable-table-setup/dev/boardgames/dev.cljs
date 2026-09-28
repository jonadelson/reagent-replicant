(ns boardgames.dev
  (:require [boardgames.core :as app]
            [boardgames.data :as data]
            [dataspex.core :as dataspex]
            [re-frame.core :as rf]
            [re-frame.db]))

(defn ^:dev/after-load reload []
  (rf/clear-subscription-cache!)
  (app/render))

(defn main []
  (dataspex/inspect "App state" re-frame.db/app-db)
  (app/main data/data))
