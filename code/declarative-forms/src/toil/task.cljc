(ns toil.task
  (:require [phosphor.icons :as icons]
            [toil.forms :as forms]))

;;; Tasks in app-db

(defn add-task
  "Adds `task` to the db under the next free id."
  [db task]
  (let [id (inc (reduce max 0 (keys (:tasks db))))]
    (assoc-in db [:tasks id] (assoc task :task/id id))))

(defn get-tasks
  "Returns all tasks, newest first, or nil when there are none."
  [db]
  (->> (vals (:tasks db))
       (sort-by :task/created-at #(compare %2 %1))
       seq))

;;; The edit form, as data

(def edit-form
  {:form/type :forms/edit-task
   :form/fields
   [{:k :task/name
     :validations [{:validation/kind :required}]}
    {:k :task/duration
     :validations
     [{:validation/kind :max-num
       :validation/message "Duration can not exceed 60 minutes"
       :max 60}]}]

   :form/submit-actions
   [[:store/save [:event/form-data]]]})

(def priorities
  [{:value :task.priority/high
    :label "High"}
   {:value :task.priority/medium
    :label "Medium"}
   {:value :task.priority/low
    :label "Low"}])

;;; Rendering

(defn render-task-form [db]
  (let [text (get-in db [:new-task :task/name])]
    [:form.mb-4.flex.gap-2.max-w-screen-sm
     {:on {:submit [[:event/prevent-default]
                    (when-not (empty? text)
                      [:task/add {:task/name text
                                  :task/created-at :clock/now}])
                    (when-not (empty? text)
                      [:store/assoc-in [:new-task :task/name] ""])]}}
     [:input.input.input-bordered.w-full
      {:type "text"
       :name "name"
       :value text
       :placeholder "What do you need to practice?"
       :on {:input [:store/assoc-in [:new-task :task/name] :event/target.value]}}]
     [:button.btn.btn-primary
      (cond-> {:type "submit"}
        (empty? text) (assoc :disabled "disabled"))
      "Add"]]))

(defn render-edit-form [form task]
  [:form.my-4.flex.flex-col.gap-4
   {:on {:submit [[:event/prevent-default]
                  [:form/submit :forms/edit-task (:task/id task) :event/form-data]]}}
   (forms/text-input task :task/id {:type "hidden" :data-type "number"})
   (forms/text-input task :task/editing? {:type "hidden"
                                          :default-value "false"
                                          :data-type "boolean"})
   (forms/input-field form "Task" task :task/name forms/text-input)
   (forms/input-field form "Duration" task :task/duration forms/text-input {:type "number"})
   (forms/input-field form "Priority" task :task/priority forms/select priorities)
   (forms/input-field form "Complete?" task :task/complete? forms/checkbox)
   [:div.flex.flex-row.gap-4
    [:button.btn.btn-primary {:type "submit"}
     "Save"]]])

(defn render-task [task]
  [:div.flex.place-content-between
   [:button.cursor-pointer.flex.items-center
    {:aria-label (if (:task/complete? task)
                   "Click to un-complete"
                   "Click to complete")
     :on {:click [:store/assoc-in [:tasks (:task/id task) :task/complete?]
                  (not (:task/complete? task))]}}
    (if (:task/complete? task)
      [:span.w-8.pr-2.tilt.transition.duration-1000.flash-success
       {:key "done"}
       (icons/render (icons/icon :phosphor.regular/check-square)
                     {:focusable "false"})]
      [:span.w-8.pr-2
       {:key "todo"}
       (icons/render (icons/icon :phosphor.regular/square)
                     {:focusable "false"})])
    [:span {:class (when (:task/complete? task)
                     "line-through")}
     (:task/name task)]]
   [:button.w-6
    {:aria-label "Edit"
     :on {:click [:store/assoc-in [:tasks (:task/id task) :task/editing?] true]}}
    (icons/render
     (icons/icon :phosphor.regular/gear)
     {:focusable "false"})]])

(defn render-tasks [db]
  [:ol.mb-4.max-w-screen-sm
   (for [task (get-tasks db)]
     [:li.bg-base-200.my-2.px-4.py-3.rounded.w-full
      {:key (:task/id task)}
      (if (:task/editing? task)
        (render-edit-form
         (get-in db [:forms [:forms/edit-task (:task/id task)]])
         task)
        (render-task task))])])
