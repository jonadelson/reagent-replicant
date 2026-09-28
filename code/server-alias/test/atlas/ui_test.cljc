(ns atlas.ui-test
  (:require [atlas.data :as data]
            [atlas.ui :as ui]
            [atlas.ui.map :as-alias map]
            [clojure.test :refer [deftest is testing]]))

(defn find-nodes
  "Returns every hiccup node in `hiccup` whose tag is `tag`."
  [hiccup tag]
  (->> (tree-seq coll? seq hiccup)
       (filter #(and (vector? %) (= tag (first %))))))

(def san-francisco (first data/cities))

(deftest render-page-test
  (testing "Renders no map when no city is selected"
    (is (empty? (find-nodes (ui/render-page {:cities data/cities})
                            ::map/marker-map))))

  (testing "Renders a map of the selected city"
    (is (= (-> (ui/render-page {:city san-francisco :cities data/cities})
               (find-nodes ::map/marker-map)
               first
               second)
           {:class "mb-4"
            ::map/center [-122.475238 37.807962]
            ::map/zoom 11})))

  (testing "Renders a marker for each point in the city"
    (is (= (->> (find-nodes (ui/render-page {:city san-francisco
                                              :cities data/cities})
                            ::map/marker)
                (map (comp :point/label second)))
           ["Bulbasaur" "Charmander" "Squirtle" "Magnemite" "Magmar"])))

  (testing "Links to the other cities"
    (is (= (->> (find-nodes (ui/render-page {:city san-francisco
                                              :cities data/cities})
                            :a.link)
                (map (comp :href second)))
           ["/city/london" "/city/tokyo" "/city/cape-town"])))

  (testing "Selects a city on click, without following the link"
    (is (= (->> (find-nodes (ui/render-page {:city san-francisco
                                              :cities data/cities})
                            :a.link)
                (map (comp :click :on second))
                first)
           [[:store/assoc-in [:city] (second data/cities)]
            [:event/prevent-default]]))))
