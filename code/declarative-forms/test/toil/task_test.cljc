(ns toil.task-test
  (:require [clojure.test :refer [deftest is testing]]
            [toil.task :as task]))

(deftest add-task-test
  (testing "Adds the first task with id 1"
    (is (= (task/add-task {} {:task/name "Major scales"})
           {:tasks {1 {:task/id 1
                       :task/name "Major scales"}}})))

  (testing "Gives new tasks the next free id"
    (is (= (-> {:tasks {1 {:task/id 1 :task/name "Major scales"}
                        4 {:task/id 4 :task/name "Minor scales"}}}
               (task/add-task {:task/name "Arpeggios"})
               (get-in [:tasks 5]))
           {:task/id 5
            :task/name "Arpeggios"}))))

(deftest get-tasks-test
  (testing "Returns nil when there are no tasks"
    (is (nil? (task/get-tasks {}))))

  (testing "Returns tasks newest first"
    (is (= (->> {:tasks {1 {:task/id 1 :task/created-at #inst "2025-03-08T09:00:00Z"}
                         2 {:task/id 2 :task/created-at #inst "2025-03-08T11:00:00Z"}
                         3 {:task/id 3 :task/created-at #inst "2025-03-08T10:00:00Z"}}}
                task/get-tasks
                (map :task/id))
           [2 3 1]))))
