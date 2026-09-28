(ns state-atom.ui-test
  (:require [clojure.test :refer [deftest is testing]]
            [state-atom.ui :as ui]))

(defn find-nodes [tag hiccup]
  (->> (tree-seq coll? seq hiccup)
       (filter #(and (vector? %) (= tag (first %))))))

(def frontpage
  {:location {:location/page-id :pages/frontpage}})

(deftest render-page-test
  (testing "The button increments the click counter"
    (is (= (-> (find-nodes :button (ui/render-page frontpage))
               first
               second)
           {:on {:click [[:counter/inc [:clicks]]]}})))

  (testing "Shows the number of clicks"
    (is (= (->> (ui/render-page (assoc frontpage :clicks 2))
                (find-nodes :p)
                (filter #(= "Button was clicked " (second %)))
                first)
           [:p "Button was clicked " 2 " times"])))

  (testing "Renders the episode page"
    (is (= (->> (ui/render-page {:location {:location/page-id :pages/episode
                                            :location/params {:episode/id "s2e1"}}})
                (find-nodes :h1)
                first)
           [:h1 "Episode " "s2e1"])))

  (testing "Renders not found without a location"
    (is (= (ui/render-page {})
           [:h1 "Not found"]))))
