(ns toil.forms)

;;; Rendering helpers

(defn keyword->s [k]
  (if-let [ns (namespace k)]
    (str ns "/" (name k))
    (name k)))

(defn update-attrs
  "Like `update`, but for the attribute map of a hiccup element. Also works
  on elements without an attribute map, like `[:h1 \"Hi!\"]`."
  [[tag & [attrs & children :as more]] f & args]
  (if (map? attrs)
    (into [tag (apply f attrs args)] children)
    (into [tag (apply f {} args)] more)))

(defn text-input [m k & [attrs]]
  (let [id (keyword->s k)]
    [:input.grow.input.input-bordered
     (into
      {:type "text"
       :name id
       :id id
       :default-value (get m k)}
      attrs)]))

(defn select [m k options]
  (let [selected (get m k)
        id (keyword->s k)
        sample-value (-> options first :value)
        ->s #(cond-> % (keyword? %) keyword->s)]
    [:select.grow.select.select-bordered
     (cond-> {:name id
              :id id}
       (some? selected) (assoc :default-value (->s selected))
       (keyword? sample-value) (assoc :data-type "keyword"))
     (for [{:keys [value label]} options]
       [:option {:value (->s value)} label])]))

(defn checkbox [m k]
  (let [id (keyword->s k)]
    [:input.checkbox
     (cond-> {:type "checkbox"
              :name id
              :id id}
       (get m k) (assoc :default-checked true))]))

(defn input-field [form label m k f & args]
  (let [error (->> (:form/validation-errors form)
                   (filter (comp #{k} :validation-error/field))
                   first)]
    (list [:div.flex.items-center
           [:label.basis-24 {:for (keyword->s k)} label]
           (cond-> (apply f m k args)
             error
             (update-attrs
              #(-> %
                   (update :class conj "input-error")
                   (assoc-in [:on :input]
                             [:form/validate (:form/id form) :event/form-data]))))]
          (when error
            [:div.validator-hint.text-error.ml-24.-m-2.mb-2
             (:validation-error/message error)]))))

;;; Processing the edit task form

(defn validate-edit-task [data]
  (->> [(when (empty? (:task/name data))
          {:validation-error/field :task/name
           :validation-error/message "Please type in some text"})
        (when (< 60 (or (:task/duration data) 0))
          {:validation-error/field :task/duration
           :validation-error/message "Duration can not exceed 60 minutes"})]
       (remove nil?)))

(defn validate-edit-task-form [form-id data]
  [[:store/assoc-in [:forms form-id]
    {:form/id form-id
     :form/validation-errors (validate-edit-task data)}]])

(defn submit-edit-task [form-type task-id data]
  (let [form-id [form-type task-id]]
    (if-let [errors (seq (validate-edit-task data))]
      [[:store/assoc-in [:forms form-id]
        {:form/id form-id
         :form/validation-errors errors}]]
      [[:store/merge-in [:tasks task-id]
        (assoc data :task/editing? false)]
       [:store/dissoc-in [:forms form-id]]])))
