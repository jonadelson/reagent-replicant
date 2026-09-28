(ns boardgames.core
  (:require [boardgames.router :as router]
            [boardgames.ui :as ui]
            [datadriven.hiccup :as hiccup]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdc]))

;; Effects: changing the URL without loading a new page

(rf/reg-fx :history/push-state
  (fn [url]
    (.pushState js/history nil "" url)))

(rf/reg-fx :history/replace-state
  (fn [url]
    (.replaceState js/history nil "" url)))

;; Events

(rf/reg-event-db :app/init
  (fn [_ [_ data location]]
    (assoc data :location location)))

(rf/reg-event-fx :router/navigate
  (fn [{:keys [db]} [_ location]]
    {:db (assoc db :location location)
     ;; Changes to the hash params only should not add a history entry
     :fx [[(if (router/essentially-same? location (:location db))
             :history/replace-state
             :history/push-state)
           (router/location->url location)]]}))

(rf/reg-event-db :router/location-changed
  (fn [db [_ location]]
    (assoc db :location location)))

;; Subscriptions

(rf/reg-sub :app/state
  (fn [db _]
    db))

;; Rendering

(defn app []
  (let [state @(rf/subscribe [:app/state])]
    (hiccup/prepare (ui/render-page state (:location state)))))

(defonce root
  (delay (rdc/create-root (js/document.getElementById "app"))))

(defn render []
  (rdc/render @root [app]))

(defn get-current-location []
  (router/url->location
   (str js/location.pathname js/location.search js/location.hash)))

;; The back and forward buttons
(defonce popstate-listener
  (delay
    (js/window.addEventListener
     "popstate"
     (fn [_]
       (rf/dispatch [:router/location-changed (get-current-location)])))))

(defn main [data]
  (rf/dispatch-sync [:app/init data (get-current-location)])
  @popstate-listener
  (render))
