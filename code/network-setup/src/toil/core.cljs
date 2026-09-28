(ns toil.core
  (:require [datadriven.hiccup :as hiccup]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdc]
            [toil.router :as router]
            [toil.ui :as ui]))

;;; The routing alias: [:ui/a {:ui/location {,,,}} "Text"]

(defn routing-anchor [attrs children]
  (let [routes (-> attrs ::hiccup/alias-data :routes)]
    (into [:a (cond-> attrs
                (:ui/location attrs)
                (assoc :href (router/location->url routes
                                                   (:ui/location attrs))))]
          children)))

(hiccup/register-alias! :ui/a routing-anchor)

;;; Coeffects and effects: the impure edges

;; Event handlers that need the current time ask for it with
;; (rf/inject-cofx :now), and stay pure.
(rf/reg-cofx :now
  (fn [cofx _]
    (assoc cofx :now (js/Date.))))

(rf/reg-fx :router/update-url
  (fn [{:keys [url replace?]}]
    (if replace?
      (.replaceState js/history nil "" url)
      (.pushState js/history nil "" url))))

;;; Events

(rf/reg-event-db :store/assoc-in
  (fn [db [_ path v]]
    (assoc-in db path v)))

(rf/reg-event-fx :app/start
  [(rf/inject-cofx :now)]
  (fn [{:keys [db now]} _]
    {:db (assoc db :app/started-at now)}))

;; The user arrived at `location`, by clicking a link, using the back button,
;; or loading the page.
(rf/reg-event-db :router/navigate
  (fn [db [_ location]]
    (assoc db :location location)))

;; The user clicked a link to one of our own pages: update the browser URL
;; and navigate.
(rf/reg-event-fx :router/route-click
  (fn [{:keys [db]} [_ location url]]
    {:fx [[:router/update-url
           {:url url
            :replace? (router/essentially-same? location (:location db))}]
          [:dispatch [:router/navigate location]]]}))

;;; Subscriptions

(rf/reg-sub :app/state
  (fn [db _]
    db))

;;; Browser plumbing

(defn find-target-href [e]
  (some-> e .-target
          (.closest "a")
          (.getAttribute "href")))

(defn get-current-location []
  (->> js/location.pathname
       (router/url->location router/routes)))

(defn route-click [e]
  (let [href (find-target-href e)]
    (when-let [location (router/url->location router/routes href)]
      (.preventDefault e)
      (rf/dispatch [:router/route-click location href]))))

;;; Rendering

(defn app []
  (hiccup/prepare (ui/render-page @(rf/subscribe [:app/state]))
                  {:alias-data {:routes router/routes}}))

(defonce root
  (delay (rdc/create-root (js/document.getElementById "app"))))

(defn render []
  (rdc/render @root [app]))

(defn main []
  (rf/dispatch-sync [:app/start])

  (js/document.body.addEventListener "click" route-click)

  (js/window.addEventListener
   "popstate"
   (fn [_] (rf/dispatch [:router/navigate (get-current-location)])))

  (rf/dispatch-sync [:router/navigate (get-current-location)])
  (render))
