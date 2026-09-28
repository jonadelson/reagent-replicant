(ns reagent-i18n.core
  (:require [datadriven.hiccup :as hiccup]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdc]))

(rf/reg-event-db :app/init
  (fn [_ _]
    {:locale :en}))

(rf/reg-sub :app/state
  (fn [db _]
    db))

(defn render-ui [_state]
  [:h1 "Hello"])

(defn app []
  (hiccup/prepare (render-ui @(rf/subscribe [:app/state]))))

(defonce root
  (delay (rdc/create-root (js/document.getElementById "app"))))

(defn render []
  (rdc/render @root [app]))

(defn main []
  (rf/dispatch-sync [:app/init])
  (render))
