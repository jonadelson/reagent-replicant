(ns state-datascript.events
  (:require [datascript.core :as ds]
            [re-frame.core :as rf]
            [state-datascript.schema :as schema]))

;; app-db is a map. The Datascript database value lives under :ds.

(defn transact
  "Returns app-db with tx-data applied to its Datascript database"
  [db tx-data]
  (update db :ds ds/db-with tx-data))

(defn get-location-entity [location]
  (into {:db/ident :ui/location
         :location/query-params {}
         :location/hash-params {}
         :location/params {}}
        location))

;; Starting the app

(rf/reg-event-db :app/start
  (fn [db [_ started-at location]]
    (assoc db :ds (ds/db-with (ds/empty-db schema/schema)
                              [{:db/ident :system/app
                                :app/started-at started-at}
                               (get-location-entity location)]))))

;; Generic, low-level events

(rf/reg-event-db :db/transact
  (fn [db [_ tx-data]]
    (transact db tx-data)))

(rf/reg-event-db :db/add
  (fn [db [_ eid attr value]]
    (transact db [[:db/add eid attr value]])))

(rf/reg-event-db :db/retract
  (fn [db [_ eid attr & [value]]]
    (transact db [(cond-> [:db/retract eid attr]
                    value (conj value))])))

(rf/reg-event-db :db/retractEntity
  (fn [db [_ eid]]
    (transact db [[:db/retractEntity eid]])))

;; Domain events

(rf/reg-event-fx :counter/inc
  (fn [{:keys [db]} [_ eid]]
    (let [entity (ds/entity (:ds db) eid)]
      {:db (transact db [[:db/add eid :clicks (inc (:clicks entity 0))]])})))

(rf/reg-event-fx :actions/navigate
  (fn [{:keys [db]} [_ location]]
    {:db (transact db [(get-location-entity location)])
     :fx [[:effects/update-url {:new-location location
                                :old-location (into {} (ds/entity (:ds db) :ui/location))}]]}))
