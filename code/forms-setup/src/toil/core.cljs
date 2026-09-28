(ns toil.core
  (:require [datadriven.hiccup :as hiccup]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdc]
            [toil.ui :as ui]))

;; Events: the only way app-db changes

(rf/reg-event-db :app/start
  (fn [db [_ now]]
    (assoc db :app/started-at now)))

(rf/reg-event-db :store/assoc-in
  (fn [db [_ path v]]
    (assoc-in db path v)))

;; Subscriptions: what the UI reads

(rf/reg-sub :app/db
  (fn [db _]
    db))

;; Rendering

(defn app []
  (hiccup/prepare (ui/render-page @(rf/subscribe [:app/db]))))

(defonce root
  (delay (rdc/create-root (js/document.getElementById "app"))))

(defn render []
  (rdc/render @root [app]))

(defn main []
  (rf/dispatch-sync [:app/start (js/Date.)])
  (render))
