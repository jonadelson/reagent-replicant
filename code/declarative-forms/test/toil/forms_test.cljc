(ns toil.forms-test
  (:require [clojure.test :refer [deftest is testing]]
            [toil.forms :as forms]))

(deftest validate-form-data-test
  (testing "Validates required field"
    (is (= (forms/validate-form-data
            {:form/id :forms/test-form
             :form/fields
             [{:k :task/name
               :validations [{:validation/kind :required}]}]}
            {:task/name nil})
           [{:validation-error/field :task/name
             :validation-error/message "Please type in some text"}])))

  (testing "Empty strings do not satisfy requiredness"
    (is (= (forms/validate-form-data
            {:form/id :forms/test-form
             :form/fields
             [{:k :task/name
               :validations [{:validation/kind :required}]}]}
            {:task/name ""})
           [{:validation-error/field :task/name
             :validation-error/message "Please type in some text"}])))

  (testing "Validates required field with custom message"
    (is (= (forms/validate-form-data
            {:form/id :forms/test-form
             :form/fields
             [{:k :task/name
               :validations [{:validation/kind :required
                              :validation/message "Oh no!"}]}]}
            {:task/name nil})
           [{:validation-error/field :task/name
             :validation-error/message "Oh no!"}])))

  (testing "Passes validation for required field"
    (is (= (forms/validate-form-data
            {:form/id :forms/test-form
             :form/fields
             [{:k :task/name
               :validations [{:validation/kind :required
                              :validation/message "Oh no!"}]}]}
            {:task/name "I'm ok!"})
           [])))

  (testing "Validates max number field"
    (is (= (forms/validate-form-data
            {:form/id :forms/test-form
             :form/fields
             [{:k :task/duration
               :validations [{:validation/kind :max-num
                              :max 60}]}]}
            {:task/duration 65})
           [{:validation-error/field :task/duration
             :validation-error/message "Should be max 60"}])))

  (testing "Validates max number with custom message"
    (is (= (forms/validate-form-data
            {:form/id :forms/test-form
             :form/fields
             [{:k :task/duration
               :validations [{:validation/kind :max-num
                              :validation/message "I don't think so"
                              :max 60}]}]}
            {:task/duration 65})
           [{:validation-error/field :task/duration
             :validation-error/message "I don't think so"}])))

  (testing "Passes max validation"
    (is (= (forms/validate-form-data
            {:form/id :forms/test-form
             :form/fields
             [{:k :task/duration
               :validations [{:validation/kind :max-num
                              :max 60}]}]}
            {:task/duration 55})
           []))))

(deftest validate-test
  (testing "Stores the validation errors for the form"
    (is (= (forms/validate
            {:form/id [:forms/test-form 1]
             :form/fields
             [{:k :task/name
               :validations [{:validation/kind :required}]}]}
            {:task/name ""})
           [[:store/assoc-in [:forms [:forms/test-form 1]]
             {:form/id [:forms/test-form 1]
              :form/validation-errors
              [{:validation-error/field :task/name
                :validation-error/message "Please type in some text"}]}]]))))

(deftest submit-test
  (testing "Validates form"
    (is (= (forms/submit
            {:form/id [:forms/test-form 1]
             :form/fields
             [{:k :task/name
               :validations [{:validation/kind :required}]}]}
            {:task/name nil}
            1)
           [[:store/assoc-in [:forms [:forms/test-form 1]]
             {:form/id [:forms/test-form 1]
              :form/validation-errors
              [{:validation-error/field :task/name
                :validation-error/message "Please type in some text"}]}]])))

  (testing "Calls form handler when form is valid, then cleans up"
    (is (= (forms/submit
            {:form/id [:forms/test-form 1]
             :form/handler (fn [data task-id]
                             [[:store/merge-in [:tasks task-id] data]])}
            {:task/name "Do it!"}
            1)
           [[:store/merge-in [:tasks 1] {:task/name "Do it!"}]
            [:store/dissoc-in [:forms [:forms/test-form 1]]]])))

  (testing "Cleans up form even when the handler has no actions"
    (is (= (forms/submit
            {:form/id [:forms/test-form 1]
             :form/handler (fn [_ _] [])}
            {:task/name "Do it!"}
            1)
           [[:store/dissoc-in [:forms [:forms/test-form 1]]]])))

  (testing "Uses submit actions, with the form data in place of :event/form-data"
    (is (= (forms/submit
            {:form/id [:forms/test-form 1]
             :form/submit-actions [[:store/save [:event/form-data]]]}
            {:task/id 1
             :task/name "Do it!"}
            1)
           [[:store/save [{:task/id 1
                           :task/name "Do it!"}]]
            [:store/dissoc-in [:forms [:forms/test-form 1]]]]))))
