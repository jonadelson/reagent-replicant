(ns toil.ui-test
  (:require [clojure.test :refer [deftest is testing]]
            [toil.ui :as ui]))

(deftest render-page-test
  (testing "Renders heading, task form and tasks"
    (let [page (ui/render-page {:tasks {1 {:task/id 1 :task/name "Scales"}}})]
      (is (= (nth page 1) [:h1.text-2xl.mb-4 "Practice log"]))
      (is (= (first (nth page 2)) :form.mb-4.flex.gap-2.max-w-screen-sm))
      (is (= (first (nth page 3)) :ol.mb-4.max-w-screen-sm)))))
