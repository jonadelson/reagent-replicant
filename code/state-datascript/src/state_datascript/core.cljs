(ns state-datascript.core
  (:require [datadriven.hiccup :as hiccup]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdc]
            [state-datascript.events :as events]
            [state-datascript.router :as router]
            [state-datascript.ui :as ui]))

;; The routing alias: [:ui/a {:ui/location {,,,}} "Text"]

(defn routing-anchor [attrs children]
  (let [routes (-> attrs ::hiccup/alias-data :routes)]
    (into [:a (cond-> attrs
                (:ui/location attrs)
                (assoc :href (router/location->url routes
                               (:ui/location attrs))))]
          children)))

(hiccup/register-alias! :ui/a routing-anchor)

;; Effects: the side effects events can ask for

(rf/reg-fx :effects/update-url
  (fn [{:keys [new-location old-location]}]
    (let [url (router/location->url router/routes new-location)]
      (if (router/essentially-same? new-location old-location)
        (.replaceState js/history nil "" url)
        (.pushState js/history nil "" url)))))

;; Subscriptions

(rf/reg-sub :ds
  (fn [db _]
    (:ds db)))

;; Rendering

(defn app []
  (hiccup/prepare
   (ui/render-page @(rf/subscribe [:ds]))
   {:alias-data {:routes router/routes}}))

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
      (rf/dispatch [:actions/navigate location]))))

;; Bootstrap

(defn main []
  (js/document.body.addEventListener "click" #(route-click %))

  (js/window.addEventListener
   "popstate"
   (fn [_]
     (rf/dispatch
      [:db/transact [(events/get-location-entity (get-current-location))]])))

  ;; Create the Datascript database in app-db, then render
  (rf/dispatch-sync [:app/start (js/Date.) (get-current-location)])
  (render))
