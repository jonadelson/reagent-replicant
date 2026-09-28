(ns atlas.data-test
  (:require [atlas.data :as data]
            [clojure.test :refer [deftest is testing]]))

;; atlas.ui now requires atlas.ui.map, which is ClojureScript only, so
;; it can't be loaded on the JVM. We'll fix that in the next tutorials.
;; Until then, we check that the city data is in the shape the map
;; expects.

(deftest cities-test
  (testing "Every city has a unique id"
    (is (apply distinct? (map :id data/cities))))

  (testing "Positions are [longitude latitude] pairs"
    (is (every? (fn [{[lng lat] :position}]
                  (and (<= -180 lng 180) (<= -90 lat 90)))
                data/cities))))
