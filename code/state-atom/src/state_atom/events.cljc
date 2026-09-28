(ns state-atom.events
  (:require [re-frame.core :as rf]))

;; Helpers that work on nested data, like assoc-in

(defn dissoc-in [m path]
  (if (= 1 (count path))
    (dissoc m (first path))
    (update-in m (butlast path) dissoc (last path))))

(defn conj-in [m path v]
  (update-in m path conj v))

;; Starting the app

(rf/reg-event-db :app/start
  (fn [db [_ started-at location]]
    (assoc db
           :app/started-at started-at
           :location location)))

;; Generic, low-level events

(rf/reg-event-db :store/assoc-in
  (fn [db [_ path value]]
    (assoc-in db path value)))

(rf/reg-event-db :store/dissoc-in
  (fn [db [_ path]]
    (dissoc-in db path)))

(rf/reg-event-db :store/conj-in
  (fn [db [_ path value]]
    (conj-in db path value)))

;; Domain events

(rf/reg-event-fx :counter/inc
  (fn [{:keys [db]} [_ path]]
    {:db (assoc-in db path (inc (get-in db path 0)))}))

(rf/reg-event-fx :actions/navigate
  (fn [{:keys [db]} [_ location]]
    {:db (assoc db :location location)
     :fx [[:effects/update-url {:new-location location
                                :old-location (:location db)}]]}))
