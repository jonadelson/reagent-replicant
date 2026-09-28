(ns toil.ui-test
  (:require [clojure.test :refer [deftest is testing]]
            [toil.ui :as ui]))

(defn find-button [hiccup]
  (->> (tree-seq coll? seq hiccup)
       (filter #(and (vector? %) (= :button.btn.btn-primary (first %))))
       first))

(deftest render-frontpage-test
  (testing "Renders a clickable button before fetching"
    (is (= (second (find-button (ui/render-frontpage {})))
           {:on {:click [[:backend/fetch-todo-items]]}})))

  (testing "Disables the button while loading"
    (is (= (second (find-button (ui/render-frontpage {:loading-todos? true})))
           {:disabled true})))

  (testing "Renders the todo items"
    (is (= (->> (ui/render-frontpage
                 {:todo-items [{:todo/title "Write docs" :todo/done? true}
                               {:todo/title "Fix bugs" :todo/done? false}]})
                (tree-seq coll? seq)
                (filter #(and (vector? %) (= :li.my-2 (first %))))
                (map #(nth % 2)))
           ["Write docs" "Fix bugs"]))))
