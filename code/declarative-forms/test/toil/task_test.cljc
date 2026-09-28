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

(defn attrs [hiccup]
  (second hiccup))

(deftest render-task-form-test
  (testing "Stores every keystroke in app-db"
    (is (= (-> (task/render-task-form {}) (nth 2) attrs :on :input)
           [:store/assoc-in [:new-task :task/name] :event/target.value])))

  (testing "Disables the button until there is some text"
    (is (= (-> (task/render-task-form {}) (nth 3) attrs :disabled)
           "disabled"))
    (is (nil? (-> (task/render-task-form {:new-task {:task/name "Scales"}})
                  (nth 3) attrs :disabled))))

  (testing "Does nothing but prevent the default on an empty submit"
    (is (= (->> (task/render-task-form {}) attrs :on :submit (remove nil?))
           [[:event/prevent-default]])))

  (testing "Adds the task and clears the field on submit"
    (is (= (-> (task/render-task-form {:new-task {:task/name "Scales"}})
               attrs :on :submit)
           [[:event/prevent-default]
            [:task/add {:task/name "Scales"
                        :task/created-at :clock/now}]
            [:store/assoc-in [:new-task :task/name] ""]]))))

(deftest render-task-test
  (testing "Toggles the task's complete state on click"
    (is (= (-> (task/render-task {:task/id 3 :task/name "Scales"})
               second attrs :on :click)
           [:store/assoc-in [:tasks 3 :task/complete?] true]))
    (is (= (-> (task/render-task {:task/id 3 :task/name "Scales" :task/complete? true})
               second attrs :on :click)
           [:store/assoc-in [:tasks 3 :task/complete?] false]))))

(deftest edit-form-test
  (testing "The edit form carries the task id and closes itself on save"
    (let [form (task/render-edit-form nil {:task/id 3
                                           :task/name "Scales"
                                           :task/editing? true})]
      (is (= (nth form 2)
             [:input.grow.input.input-bordered
              {:type "hidden"
               :name "task/id"
               :id "task/id"
               :default-value 3
               :data-type "number"}]))
      (is (= (nth form 3)
             [:input.grow.input.input-bordered
              {:type "hidden"
               :name "task/editing?"
               :id "task/editing?"
               :default-value "false"
               :data-type "boolean"}])))))

(deftest edit-task-test
  (testing "The edit button opens the edit form"
    (is (= (-> (task/render-task {:task/id 3 :task/name "Scales"})
               (nth 2) attrs :on :click)
           [:store/assoc-in [:tasks 3 :task/editing?] true])))

  (testing "Renders the edit form in place of an edited task"
    (is (= (-> (task/render-tasks {:tasks {3 {:task/id 3
                                             :task/name "Scales"
                                             :task/editing? true}}})
               second first (nth 2) first)
           :form.my-4.flex.flex-col.gap-4)))

  (testing "Submits the whole form with its data"
    (is (= (-> (task/render-edit-form nil {:task/id 3 :task/name "Scales"})
               attrs :on :submit)
           [[:event/prevent-default]
            [:form/submit :forms/edit-task 3 :event/form-data]]))))
