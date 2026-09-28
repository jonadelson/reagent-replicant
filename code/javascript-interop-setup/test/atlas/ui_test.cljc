(ns atlas.ui-test
  (:require [atlas.ui :as ui]
            [clojure.test :refer [deftest is testing]]))

(deftest render-page-test
  (testing "Greets the world"
    (is (= (ui/render-page {})
           [:h1 "Hello world!"]))))
