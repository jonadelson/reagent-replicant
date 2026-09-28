(ns state-datascript.events-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [datascript.core :as ds]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [state-datascript.events]))

;; Event handlers are pure functions of app-db, so we can test them on the JVM:
;; start the app, dispatch events synchronously, then look at the database.

(def started-at #inst "2026-01-01T12:00:00Z")

(use-fixtures :each (fn [run-test]
                      (reset! app-db {})
                      (rf/dispatch-sync [:app/start started-at {:location/page-id :pages/frontpage}])
                      (run-test)))

(defn app-entity []
  (ds/entity (:ds @app-db) :system/app))

(deftest start-test
  (testing "Creates the database with the app entity"
    (is (= (:app/started-at (app-entity)) started-at)))

  (testing "Stores the location"
    (is (= (:location/page-id (ds/entity (:ds @app-db) :ui/location))
           :pages/frontpage))))

(deftest transaction-events-test
  (let [eid (:db/id (app-entity))]
    (testing ":db/transact transacts"
      (rf/dispatch-sync [:db/transact [{:db/ident :system/app :app/title "Hi"}]])
      (is (= (:app/title (app-entity)) "Hi")))

    (testing ":db/add adds a fact"
      (rf/dispatch-sync [:db/add eid :clicks 7])
      (is (= (:clicks (app-entity)) 7)))

    (testing ":db/retract without a value retracts the attribute"
      (rf/dispatch-sync [:db/retract eid :clicks])
      (is (nil? (:clicks (app-entity)))))

    (testing ":db/retractEntity removes the entity"
      (rf/dispatch-sync [:db/retractEntity eid])
      (is (nil? (ds/entity (:ds @app-db) :system/app))))))

(deftest counter-inc-test
  (let [eid (:db/id (app-entity))]
    (testing "Starts counting from 0"
      (rf/dispatch-sync [:counter/inc eid])
      (is (= (:clicks (app-entity)) 1)))

    (testing "Increments the count"
      (rf/dispatch-sync [:counter/inc eid])
      (rf/dispatch-sync [:counter/inc eid])
      (is (= (:clicks (app-entity)) 3)))))

(deftest navigate-test
  (let [url-updates (atom [])]
    ;; Replace the browser effect with one that records what it was asked to do
    (rf/reg-fx :effects/update-url #(swap! url-updates conj %))
    (rf/dispatch-sync [:actions/navigate {:location/page-id :pages/episode
                                          :location/params {:episode/id "s2e1"}}])

    (testing "Transacts the new location"
      (is (= (-> (ds/entity (:ds @app-db) :ui/location)
                 (select-keys [:location/page-id :location/params :location/hash-params]))
             {:location/page-id :pages/episode
              :location/params {:episode/id "s2e1"}
              :location/hash-params {}})))

    (testing "Asks for the URL to be updated"
      (is (= (map (juxt (comp :location/page-id :new-location)
                        (comp :location/page-id :old-location))
                  @url-updates)
             [[:pages/episode :pages/frontpage]])))))
