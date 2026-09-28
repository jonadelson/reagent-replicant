(ns parens.core
  (:require [datadriven.hiccup :as hiccup]
            [parens.ui :as ui]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdc]))

;; Events: the only way app-db changes

(rf/reg-event-db :app/initialize
  (fn [_ [_ state]]
    state))

;; Subscriptions: what the UI reads

(rf/reg-sub :app/state
  (fn [db _]
    db))

;; Rendering

(defn app []
  (hiccup/prepare (ui/render-page @(rf/subscribe [:app/state]))))

(defonce root
  (delay (rdc/create-root (js/document.getElementById "app"))))

(defn render []
  (rdc/render @root [app]))

(defn bootup [state]
  ;; Perform bootup steps that should only be done once here
  (rf/dispatch-sync [:app/initialize state])
  (render))
