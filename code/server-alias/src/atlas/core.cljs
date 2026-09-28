(ns atlas.core
  (:require [atlas.ui :as ui]
            [atlas.ui.map] ;; Registers the ::map/marker-map alias
            [datadriven.hiccup :as hiccup]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdc]))

;; Events: the only way the state changes

(rf/reg-event-db :app/initialize
  (fn [_ [_ initial-state]]
    initial-state))

(rf/reg-event-db :store/assoc-in
  (fn [db [_ path v]]
    (assoc-in db path v)))

;; Subscriptions: what the UI reads

(rf/reg-sub :app/state
  (fn [db _]
    db))

;; Rendering

(defn app []
  (hiccup/prepare (ui/render-page @(rf/subscribe [:app/state]))))

(defonce !root (atom nil))

(defn render []
  (some-> @!root (rdc/render [app])))

(defn main [el initial-state]
  (rf/dispatch-sync [:app/initialize initial-state])
  (reset! !root (rdc/create-root el))
  (render))
