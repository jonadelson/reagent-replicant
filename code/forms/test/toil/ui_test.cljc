(ns toil.ui-test
  (:require [clojure.test :refer [deftest is testing]]
            [toil.ui :as ui]))

(defn attrs [hiccup]
  (second hiccup))

(deftest render-task-form-test
  (testing "Stores every keystroke in app-db"
    (is (= (-> (ui/render-task-form {}) (nth 2) attrs :on :input)
           [:store/assoc-in [:new-task :task/name] :event/target.value])))

  (testing "Disables the button until there is some text"
    (is (= (-> (ui/render-task-form {}) (nth 3) attrs :disabled)
           "disabled"))
    (is (nil? (-> (ui/render-task-form {:new-task {:task/name "Scales"}})
                  (nth 3) attrs :disabled))))

  (testing "Does nothing but prevent the default on an empty submit"
    (is (= (->> (ui/render-task-form {}) attrs :on :submit (remove nil?))
           [[:event/prevent-default]])))

  (testing "Adds the task and clears the field on submit"
    (is (= (-> (ui/render-task-form {:new-task {:task/name "Scales"}})
               attrs :on :submit)
           [[:event/prevent-default]
            [:task/add {:task/name "Scales"
                        :task/created-at :clock/now}]
            [:store/assoc-in [:new-task :task/name] ""]]))))

(deftest render-task-test
  (testing "Toggles the task's complete state on click"
    (is (= (-> (ui/render-task {:task/id 3 :task/name "Scales"})
               second attrs :on :click)
           [:store/assoc-in [:tasks 3 :task/complete?] true]))
    (is (= (-> (ui/render-task {:task/id 3 :task/name "Scales" :task/complete? true})
               second attrs :on :click)
           [:store/assoc-in [:tasks 3 :task/complete?] false]))))
