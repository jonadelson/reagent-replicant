(ns state-datascript.ui-test
  (:require [clojure.test :refer [deftest is testing]]
            [datascript.core :as ds]
            [state-datascript.events :as events]
            [state-datascript.schema :as schema]
            [state-datascript.ui :as ui]))

(defn find-nodes [tag hiccup]
  (->> (tree-seq coll? seq hiccup)
       (filter #(and (vector? %) (= tag (first %))))))

(defn create-db [location & [tx-data]]
  (ds/db-with (ds/empty-db schema/schema)
              (concat [{:db/ident :system/app}
                       (events/get-location-entity location)]
                      tx-data)))

(deftest render-page-test
  (testing "The button increments the counter of the app entity"
    (let [db (create-db {:location/page-id :pages/frontpage})]
      (is (= (-> (find-nodes :button (ui/render-page db))
                 first
                 second)
             {:on {:click [[:counter/inc (:db/id (ds/entity db :system/app))]]}}))))

  (testing "Shows the number of clicks"
    (is (= (->> (ui/render-page
                 (create-db {:location/page-id :pages/frontpage}
                            [{:db/ident :system/app :clicks 1}]))
                (find-nodes :p)
                (filter #(= "Button was clicked " (second %)))
                first)
           [:p "Button was clicked " 1 " time"])))

  (testing "Renders the episode page"
    (is (= (->> (ui/render-page (create-db {:location/page-id :pages/episode
                                            :location/params {:episode/id "s2e1"}}))
                (find-nodes :h1)
                first)
           [:h1 "Episode " "s2e1"])))

  (testing "Renders not found for unknown locations"
    (is (= (ui/render-page (create-db nil))
           [:h1 "Not found"]))))
