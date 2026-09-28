(ns parens.ui-test
  (:require [clojure.test :refer [deftest is testing]]
            [parens.data :as data]
            [parens.ui :as ui]))

(deftest render-page-test
  (testing "Lists the episode titles"
    (is (= (->> (ui/render-page data/data)
                (tree-seq coll? seq)
                (filter #(and (vector? %) (= :li (first %))))
                (map second))
           ["It Lives Again"
            "Shambling Along"
            "Stumbling out of the Graveyard"]))))
