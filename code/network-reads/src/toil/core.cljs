(ns toil.core
  (:require [cljs.reader :as reader]
            [datadriven.hiccup :as hiccup]
            [re-frame.core :as rf]
            [reagent.dom.client :as rdc]
            [toil.frontpage :as frontpage]
            [toil.query :as query]
            [toil.router :as router]
            [toil.ui :as ui]
            [toil.user :as user]))

;;; Pages

(def pages
  [user/page
   frontpage/page])

(def by-page-id
  (->> pages
       (map (juxt :page-id identity))
       (into {})))

(def routes
  (router/make-routes pages))

(defn get-render-f [state]
  (or (get-in by-page-id [(-> state :location :location/page-id) :render])
      ui/render-page))

(defn get-location-load-actions [location]
  (when-let [f (get-in by-page-id [(:location/page-id location) :on-load])]
    (f location)))

;;; The routing alias: [:ui/a {:ui/location {,,,}} "Text"]

(defn routing-anchor [attrs children]
  (let [routes (-> attrs ::hiccup/alias-data :routes)]
    (into [:a (cond-> attrs
                (:ui/location attrs)
                (assoc :href (router/location->url routes (:ui/location attrs))))]
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

;; POSTs `body` as EDN to `url`, and dispatches `on-response` with the parsed
;; response (or an error) added at the end.
(rf/reg-fx :backend/request
  (fn [{:keys [url body on-response]}]
    (-> (js/fetch url #js {:method "POST"
                           :body (pr-str body)})
        (.then #(.text %))
        (.then reader/read-string)
        (.then #(rf/dispatch (conj on-response %)))
        (.catch #(rf/dispatch (conj on-response {:error (.-message %)}))))))

;;; Events

(rf/reg-event-db :store/assoc-in
  (fn [db [_ path v]]
    (assoc-in db path v)))

(rf/reg-event-fx :app/start
  [(rf/inject-cofx :now)]
  (fn [{:keys [db now]} _]
    {:db (assoc db :app/started-at now)}))

(rf/reg-event-fx :data/query
  [(rf/inject-cofx :now)]
  (fn [{:keys [db now]} [_ query]]
    {:db (query/send-request db now query)
     :fx [[:backend/request
           {:url "/query"
            :body query
            :on-response [:data/receive-query-response query]}]]}))

(rf/reg-event-fx :data/receive-query-response
  [(rf/inject-cofx :now)]
  (fn [{:keys [db now]} [_ query response]]
    {:db (query/receive-response db now query response)}))

;; The user arrived at `location`, by clicking a link, using the back button,
;; or loading the page. When the location changes, dispatch the page's
;; :on-load actions.
(rf/reg-event-fx :router/navigate
  (fn [{:keys [db]} [_ location]]
    (cond-> {:db (assoc db :location location)}
      (not= location (:location db))
      (assoc :fx (mapv (fn [action] [:dispatch action])
                       (get-location-load-actions location))))))

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
       (router/url->location routes)))

(defn route-click [e]
  (let [href (find-target-href e)]
    (when-let [location (router/url->location routes href)]
      (.preventDefault e)
      (rf/dispatch [:router/route-click location href]))))

;;; Rendering

(defn app []
  (let [state @(rf/subscribe [:app/state])
        render-page (get-render-f state)]
    (hiccup/prepare (render-page state)
                    {:alias-data {:routes routes}})))

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
