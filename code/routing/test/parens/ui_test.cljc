(ns parens.ui-test
  (:require [clojure.test :refer [deftest is testing]]
            [parens.data :as data]
            [parens.ui :as ui]))

(defn find-nodes
  "Returns all hiccup nodes with the given tag"
  [tag hiccup]
  (->> (tree-seq coll? seq hiccup)
       (filter #(and (vector? %) (= tag (first %))))))

(deftest render-page-test
  (testing "Frontpage links to each episode"
    (is (= (->> (ui/render-page data/data {:location/page-id :pages/frontpage})
                (find-nodes :ui/a)
                (map #(-> % second :ui/location)))
           [{:location/page-id :pages/episode
             :location/params {:episode/id "s2e1"}}
            {:location/page-id :pages/episode
             :location/params {:episode/id "s2e2"}}
            {:location/page-id :pages/episode
             :location/params {:episode/id "s2e3"}}])))

  (testing "Episode page shows the title"
    (is (= (->> (ui/render-page data/data {:location/page-id :pages/episode
                                           :location/params {:episode/id "s2e2"}})
                (find-nodes :h1)
                first)
           [:h1 "Shambling Along"])))

  (testing "Description link toggles a hash parameter"
    (let [location {:location/page-id :pages/episode
                    :location/params {:episode/id "s2e2"}}]
      (is (= (->> (ui/render-page data/data location)
                  (find-nodes :ui/a)
                  first)
             [:ui/a {:ui/location (assoc location :location/hash-params {:description "1"})}
              "Show description"]))))

  (testing "Unknown locations render not found"
    (is (= (ui/render-page data/data nil)
           [:h1 "Not found"]))))
