(ns toil.ui-test
  (:require [clojure.test :refer [deftest is testing]]
            [toil.ui :as ui]))

(deftest render-page-test
  (testing "Renders the page heading"
    (is (= (-> (ui/render-page {}) (nth 1))
           [:h1.text-2xl.mb-4 "Practice log"]))))
