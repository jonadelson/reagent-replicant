(ns toil.forms-test
  (:require [clojure.test :refer [deftest is testing]]
            [toil.forms :as forms]))

(deftest keyword->s-test
  (is (= (forms/keyword->s :task/name) "task/name"))
  (is (= (forms/keyword->s :name) "name"))
  (is (= (keyword (forms/keyword->s :task/name)) :task/name)))

(deftest update-attrs-test
  (testing "Updates existing attributes"
    (is (= (forms/update-attrs [:input {:type "text"}] assoc :id "x")
           [:input {:type "text" :id "x"}])))

  (testing "Adds attributes to elements without them"
    (is (= (forms/update-attrs [:h1 "Hi!"] assoc :class "title")
           [:h1 {:class "title"} "Hi!"]))))

(deftest text-input-test
  (is (= (forms/text-input {:task/duration 15} :task/duration {:type "number"})
         [:input.grow.input.input-bordered
          {:type "number"
           :name "task/duration"
           :id "task/duration"
           :default-value 15}])))

(deftest select-test
  (is (= (forms/select {:task/priority :task.priority/low} :task/priority
                       [{:value :task.priority/high :label "High"}
                        {:value :task.priority/low :label "Low"}])
         [:select.grow.select.select-bordered
          {:name "task/priority"
           :id "task/priority"
           :default-value "task.priority/low"
           :data-type "keyword"}
          [[:option {:value "task.priority/high"} "High"]
           [:option {:value "task.priority/low"} "Low"]]])))

(deftest input-field-test
  (testing "Renders label and field"
    (is (= (forms/input-field nil "Task" {:task/name "Scales"} :task/name forms/text-input)
           [[:div.flex.items-center
             [:label.basis-24 {:for "task/name"} "Task"]
             [:input.grow.input.input-bordered
              {:type "text"
               :name "task/name"
               :id "task/name"
               :default-value "Scales"}]]
            nil])))

  (testing "Renders validation error, and validates again on input"
    (let [form {:form/id [:forms/edit-task 1]
                :form/validation-errors
                [{:validation-error/field :task/name
                  :validation-error/message "Please type in some text"}]}
          [field error] (forms/input-field form "Task" {} :task/name forms/text-input)
          attrs (second (nth field 2))]
      (is (= (:class attrs) ["input-error"]))
      (is (= (-> attrs :on :input)
             [:form/validate [:forms/edit-task 1] :event/form-data]))
      (is (= error
             [:div.validator-hint.text-error.ml-24.-m-2.mb-2
              "Please type in some text"])))))

(deftest validate-edit-task-test
  (is (= (forms/validate-edit-task {:task/name "Scales" :task/duration 15})
         []))
  (is (= (forms/validate-edit-task {:task/name "" :task/duration 61})
         [{:validation-error/field :task/name
           :validation-error/message "Please type in some text"}
          {:validation-error/field :task/duration
           :validation-error/message "Duration can not exceed 60 minutes"}])))

(deftest validate-edit-task-form-test
  (is (= (forms/validate-edit-task-form [:forms/edit-task 1] {:task/name "Scales"})
         [[:store/assoc-in [:forms [:forms/edit-task 1]]
           {:form/id [:forms/edit-task 1]
            :form/validation-errors []}]])))

(deftest submit-edit-task-test
  (testing "Stores validation errors"
    (is (= (forms/submit-edit-task :forms/edit-task 1 {:task/name ""})
           [[:store/assoc-in [:forms [:forms/edit-task 1]]
             {:form/id [:forms/edit-task 1]
              :form/validation-errors
              [{:validation-error/field :task/name
                :validation-error/message "Please type in some text"}]}]])))

  (testing "Saves the task, closes the form and cleans up the form state"
    (is (= (forms/submit-edit-task :forms/edit-task 1 {:task/name "Scales"
                                                       :task/duration 15})
           [[:store/merge-in [:tasks 1]
             {:task/name "Scales"
              :task/duration 15
              :task/editing? false}]
            [:store/dissoc-in [:forms [:forms/edit-task 1]]]]))))
