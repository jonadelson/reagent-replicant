(ns toil.forms
  (:require [clojure.walk :as walk]))

;;; Validation

(defn validate-field [field validation data]
  (case (:validation/kind validation)
    :required
    (when (or (nil? data) (= "" data))
      {:validation-error/field field
       :validation-error/message
       (or (:validation/message validation)
           "Please type in some text")})

    :max-num
    (when (< (:max validation) (or data 0))
      {:validation-error/field field
       :validation-error/message
       (or (:validation/message validation)
           (str "Should be max " (:max validation)))})))

(defn validate-form-data [form data]
  (->> (:form/fields form)
       (mapcat
        (fn [{:keys [k validations]}]
          (let [field-data (get data k)]
            (keep #(validate-field k % field-data) validations))))))

(defn validate [form data]
  [[:store/assoc-in [:forms (:form/id form)]
    {:form/id (:form/id form)
     :form/validation-errors (validate-form-data form data)}]])

;;; Submitting

(defn submit [form data & args]
  (if-let [errors (seq (validate-form-data form data))]
    [[:store/assoc-in [:forms (:form/id form)]
      {:form/id (:form/id form)
       :form/validation-errors errors}]]
    (-> (or (:form/submit-actions form)
            (when-let [handler (:form/handler form)]
              (apply handler data args)))
        (->> (walk/postwalk-replace {:event/form-data data}))
        vec
        (conj [:store/dissoc-in [:forms (:form/id form)]]))))

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
