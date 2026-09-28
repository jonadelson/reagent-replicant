(ns parens.core
  (:require [datadriven.hiccup :as hiccup]
            [parens.router :as router]
            [parens.ui :as ui]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdc]))

;; The routing alias: [:ui/a {:ui/location {,,,}} "Text"]

(defn routing-anchor [attrs children]
  (let [routes (-> attrs ::hiccup/alias-data :routes)]
    (into [:a (cond-> attrs
                (:ui/location attrs)
                (assoc :href (router/location->url routes
                               (:ui/location attrs))))]
          children)))

(hiccup/register-alias! :ui/a routing-anchor)

;; Events: the only way app-db changes

(rf/reg-event-db :app/initialize
  (fn [_ [_ state]]
    state))

(rf/reg-event-db :location/changed
  (fn [db [_ location]]
    (assoc db :location location)))

;; Subscriptions: what the UI reads

(rf/reg-sub :app/state
  (fn [db _]
    db))

;; Rendering

(defn app []
  (let [state @(rf/subscribe [:app/state])]
    (hiccup/prepare
     (ui/render-page state (:location state))
     {:alias-data {:routes router/routes}})))

(defonce root
  (delay (rdc/create-root (js/document.getElementById "app"))))

(defn render []
  (rdc/render @root [app]))

;; Routing

(defn find-target-href [e]
  (some-> e .-target
          (.closest "a")
          (.getAttribute "href")))

(defn get-current-location []
  (->> js/location.href
       (router/url->location router/routes)))

(defn route-click [e]
  (when-let [href (find-target-href e)]
    (when-let [location (router/url->location router/routes href)]
      (.preventDefault e)
      (if (router/essentially-same? location (get-current-location))
        (.replaceState js/history nil "" href)
        (.pushState js/history nil "" href))
      (rf/dispatch [:location/changed location]))))

(defn bootup [state]
  ;; Perform bootup steps that should only be done once here
  (rf/dispatch-sync [:app/initialize state])
  (rf/dispatch-sync [:location/changed (get-current-location)])

  (js/document.body.addEventListener "click" #(route-click %))

  (js/window.addEventListener
   "popstate"
   (fn [_]
     (rf/dispatch [:location/changed (get-current-location)])))

  (render))
