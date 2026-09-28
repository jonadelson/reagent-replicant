(ns toil.dev
  (:require [dataspex.core :as dataspex]
            [re-frame.core :as rf]
            [re-frame.db]
            [toil.core :as app]))

;; Runs after every hot reload
(defn ^:dev/after-load reload []
  (rf/clear-subscription-cache!)
  (app/render))

;; Runs once, when the page loads
(defn main []
  (dataspex/inspect "App state" re-frame.db/app-db)
  (app/main))
