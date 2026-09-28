(ns boardgames.ui.sortable-table-test
  (:require [boardgames.router]
            [boardgames.ui.sortable-table :as st]
            [clojure.test :refer [deftest is testing]]
            [datadriven.hiccup :as hiccup]
            [lookup.core :as lookup]))

(def columns
  [{:f :name, :id "name", :label "Name"}
   {:f :age, :id "age", :label "Age", :default? true}])

(def people
  [{:name "Bo" :age 3}
   {:name "Al" :age 1}
   {:name "Cy" :age 2}])

(defn render-table [location]
  (hiccup/expand
   [::st/table {::st/location location
                ::st/columns columns}
    [::st/thead
     [::st/th]
     [::st/th]]
    [::st/tbody {::st/data people}
     [::st/th]
     [::st/td]]]))

(deftest get-sort-column-test
  (testing "Finds the column from the hash params"
    (is (= (st/get-sort-column {:location/hash-params {:sort-column "name"}} columns)
           (first columns))))

  (testing "Defaults to the column marked as default"
    (is (= (st/get-sort-column {} columns)
           (second columns))))

  (testing "Defaults to the first column"
    (is (= (st/get-sort-column {} [{:id "a"} {:id "b"}])
           {:id "a"}))))

(deftest update-attrs-test
  (testing "Updates existing attributes"
    (is (= (st/update-attrs [:td {:class "x"} "Hi"] assoc :id "y")
           [:td {:class "x" :id "y"} "Hi"])))

  (testing "Adds attributes to a node without them"
    (is (= (st/update-attrs [:td "Hi"] assoc :id "y")
           [:td {:id "y"} "Hi"]))))

(deftest table-test
  (testing "Sorts by the default column"
    (is (= (->> (render-table {:location/path "/"})
                (lookup/select '[tbody th])
                (map lookup/text))
           ["Al" "Cy" "Bo"])))

  (testing "Sorts by the column and order in the hash params"
    (is (= (->> (render-table {:location/path "/"
                               :location/hash-params {:sort-column "name"
                                                      :sort-order "desc"}})
                (lookup/select '[tbody th])
                (map lookup/text))
           ["Cy" "Bo" "Al"])))

  (testing "Renders data cells"
    (is (= (->> (render-table {:location/path "/"})
                (lookup/select '[tbody td])
                (map lookup/text))
           ["1" "2" "3"])))

  (testing "Marks the sort column in the header"
    (is (= (->> (render-table {:location/path "/"})
                (lookup/select '[thead th])
                (map lookup/text))
           ["Name" "▲ Age"])))

  (testing "Clicking the sort column header reverses the order"
    (is (= (->> (render-table {:location/path "/"})
                (lookup/select '[thead a])
                (map #(:href (lookup/attrs %))))
           ["/#sort-column=name"
            "/#sort-order=desc"]))))
