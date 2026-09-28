(ns reagent-i18n.dev
  (:require [dataspex.core :as dataspex]
            [re-frame.core :as rf]
            [re-frame.db]
            [reagent-i18n.core :as app]))

(defn ^:dev/after-load reload []
  (rf/clear-subscription-cache!)
  (app/render))

(defn main []
  (dataspex/inspect "App state" re-frame.db/app-db)
  (app/main))
