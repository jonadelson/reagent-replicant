(ns toil.dev
  (:require [dataspex.core :as dataspex]
            [re-frame.core :as rf]
            [re-frame.db]
            [toil.core :as app]))

;; Log every event to the console, so you can see what the UI dispatches
(def log-events
  (rf/->interceptor
   :id :toil.dev/log-events
   :before (fn [context]
             (js/console.log (pr-str (get-in context [:coeffects :event])))
             context)))

(defn ^:dev/after-load reload []
  (rf/clear-subscription-cache!)
  (app/render))

(defn main []
  (rf/reg-global-interceptor log-events)
  (dataspex/inspect "App db" re-frame.db/app-db)
  (app/main))
