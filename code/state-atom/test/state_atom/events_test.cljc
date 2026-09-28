(ns state-atom.events-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [state-atom.events :as events]))

;; Event handlers are pure functions of app-db, so we can test them on the JVM:
;; put some state in app-db, dispatch an event synchronously, look at app-db.

(use-fixtures :each (fn [run-test]
                      (reset! app-db {})
                      (run-test)))

(deftest helpers-test
  (testing "dissoc-in removes a nested key"
    (is (= (events/dissoc-in {:a {:b 1 :c 2}} [:a :b])
           {:a {:c 2}})))

  (testing "dissoc-in removes a top-level key"
    (is (= (events/dissoc-in {:a 1 :b 2} [:a])
           {:b 2})))

  (testing "conj-in adds to a nested collection"
    (is (= (events/conj-in {:a {:b #{1}}} [:a :b] 2)
           {:a {:b #{1 2}}}))))

(deftest store-events-test
  (testing ":store/assoc-in sets a value"
    (rf/dispatch-sync [:store/assoc-in [:user :name] "Christian"])
    (is (= @app-db {:user {:name "Christian"}})))

  (testing ":store/dissoc-in removes it again"
    (rf/dispatch-sync [:store/dissoc-in [:user :name]])
    (is (= @app-db {:user {}})))

  (testing ":store/conj-in adds to a collection"
    (rf/dispatch-sync [:store/assoc-in [:tags] #{}])
    (rf/dispatch-sync [:store/conj-in [:tags] "clojure"])
    (is (= (:tags @app-db) #{"clojure"}))))

(deftest counter-inc-test
  (testing "Starts counting from 0"
    (rf/dispatch-sync [:counter/inc [:clicks]])
    (is (= (:clicks @app-db) 1)))

  (testing "Increments the number at the path"
    (rf/dispatch-sync [:counter/inc [:clicks]])
    (rf/dispatch-sync [:counter/inc [:clicks]])
    (is (= (:clicks @app-db) 3))))

(deftest navigate-test
  (let [url-updates (atom [])]
    ;; Replace the browser effect with one that records what it was asked to do
    (rf/reg-fx :effects/update-url #(swap! url-updates conj %))
    (reset! app-db {:location {:location/page-id :pages/frontpage}})
    (rf/dispatch-sync [:actions/navigate {:location/page-id :pages/episode
                                          :location/params {:episode/id "s2e1"}}])

    (testing "Stores the new location"
      (is (= (:location @app-db)
             {:location/page-id :pages/episode
              :location/params {:episode/id "s2e1"}})))

    (testing "Asks for the URL to be updated"
      (is (= @url-updates
             [{:new-location {:location/page-id :pages/episode
                              :location/params {:episode/id "s2e1"}}
               :old-location {:location/page-id :pages/frontpage}}])))))
