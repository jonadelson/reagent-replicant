(ns tic-tac-toe.core
  (:require [datadriven.hiccup :as hiccup]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdc]
            [tic-tac-toe.game :as game]
            [tic-tac-toe.ui :as ui]))

;; Events: how the game changes

(rf/reg-event-db :reset
  (fn [_ _]
    (game/create-game {:size 3})))

(rf/reg-event-db :tic
  (fn [game [_ y x]]
    (game/tic game y x)))

;; Subscriptions: what the UI needs to know

(rf/reg-sub :game
  (fn [db _]
    db))

;; Rendering

(defn app []
  (-> @(rf/subscribe [:game])
      ui/render-game
      hiccup/prepare))

(defonce root
  (delay (rdc/create-root (js/document.getElementById "app"))))

(defn render []
  (rdc/render @root [app]))

(defn main []
  (rf/dispatch-sync [:reset])
  (render))
