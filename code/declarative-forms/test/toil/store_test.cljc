(ns toil.store-test
  (:require [clojure.test :refer [deftest is testing]]
            [toil.store :as store]))

(deftest entity-path-test
  (is (= (store/entity-path {:task/id 3 :task/name "Scales"}) [:tasks 3]))
  (is (= (store/entity-path {:form/id [:forms/edit-task 3]})
         [:forms [:forms/edit-task 3]]))
  (is (nil? (store/entity-path {:task/name "Scales"}))))

(deftest save-test
  (testing "Merges entities into the ones they identify"
    (is (= (store/save {:tasks {3 {:task/id 3
                                   :task/name "Scales"
                                   :task/editing? true}}}
                       [{:task/id 3
                         :task/name "Major scales"
                         :task/editing? false}])
           {:tasks {3 {:task/id 3
                       :task/name "Major scales"
                       :task/editing? false}}})))

  (testing "Removes keys with nil values"
    (is (= (store/save {:tasks {3 {:task/id 3
                                   :task/name "Scales"
                                   :task/duration 15}}}
                       [{:task/id 3
                         :task/duration nil}])
           {:tasks {3 {:task/id 3
                       :task/name "Scales"}}})))

  (testing "Saves several entities"
    (is (= (store/save {}
                       [{:task/id 1 :task/name "Scales"}
                        {:form/id [:forms/edit-task 1]
                         :form/validation-errors []}])
           {:tasks {1 {:task/id 1 :task/name "Scales"}}
            :forms {[:forms/edit-task 1] {:form/id [:forms/edit-task 1]
                                          :form/validation-errors []}}})))

  (testing "Refuses entities without an identity"
    (is (thrown? #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo)
                 (store/save {} [{:task/name "Scales"}])))))
