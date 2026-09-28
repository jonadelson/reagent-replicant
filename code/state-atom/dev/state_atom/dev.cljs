(ns state-atom.dev
  (:require [dataspex.core :as dataspex]
            [re-frame.core :as rf]
            [re-frame.db]
            [state-atom.core :as app]))

(defn ^:dev/after-load reload []
  ;; Runs after every hot reload: re-render with the new code
  (rf/clear-subscription-cache!)
  (app/render))

(defn main []
  ;; Runs once, when the page loads. Add dev-time tooling here.
  (dataspex/inspect "App state" re-frame.db/app-db)
  (app/main))
