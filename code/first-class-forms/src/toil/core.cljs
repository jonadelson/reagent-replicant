(ns toil.core
  (:require [clojure.walk :as walk]
            [datadriven.hiccup :as hiccup]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.dom.client :as rdc]
            [toil.forms :as forms]
            [toil.task :as task]
            [toil.ui :as ui]))

;; Reading form data from the DOM

(defn get-input-value [^js element]
  (cond
    (= "number" (.-type element))
    (when (not-empty (.-value element))
      (.-valueAsNumber element))

    (= "checkbox" (.-type element))
    (if (.hasAttribute element "value")
      (when (.-checked element)
        (.-value element))
      (.-checked element))

    (= "keyword" (aget (.-dataset element) "type"))
    (keyword (.-value element))

    (= "number" (aget (.-dataset element) "type"))
    (when (not-empty (.-value element))
      (parse-long (.-value element)))

    :else
    (.-value element)))

(defn get-input-key [^js element]
  (when-let [k (some-> element .-name not-empty keyword)]
    (when (or (not= "checkbox" (.-type element))
              (.-checked element)
              (not (.hasAttribute element "value")))
      k)))

(defn gather-form-data [^js form-el]
  (some-> (.-elements form-el)
          into-array
          (.reduce
           (fn [res ^js el]
             (let [k (get-input-key el)]
               (cond-> res
                 k (assoc k (get-input-value el)))))
           {})))

;; :event/form-data is replaced with the data in the form that the event
;; happened in: the form itself on submit, or the form around an input field.
(hiccup/register-placeholder! :event/form-data
  (fn [^js event]
    (some-> event .-target (.closest "form") gather-form-data)))

;; Events: the only way app-db changes

(rf/reg-event-db :app/start
  (fn [db [_ now]]
    (assoc db :app/started-at now)))

(rf/reg-event-db :store/assoc-in
  (fn [db [_ path v]]
    (assoc-in db path v)))

(rf/reg-event-db :store/merge-in
  (fn [db [_ path m]]
    (update-in db path merge m)))

(rf/reg-event-db :store/dissoc-in
  (fn [db [_ path]]
    (update-in db (butlast path) dissoc (last path))))

(rf/reg-event-db :task/add
  (fn [db [_ task]]
    (task/add-task db task)))

(defn actions->fx
  "Turns a list of actions into re-frame effects that dispatch them in order."
  [actions]
  (mapv (fn [action] [:dispatch action]) (remove nil? actions)))

(rf/reg-event-fx :form/submit
  (fn [_ [_ form-type id data]]
    {:fx (actions->fx
          (case form-type
            :forms/edit-task
            (forms/submit-edit-task form-type id data)))}))

(rf/reg-event-fx :form/validate
  (fn [_ [_ form-id data]]
    {:fx (actions->fx
          (case (first form-id)
            :forms/edit-task
            (forms/validate-edit-task-form form-id data)))}))

;; Subscriptions: what the UI reads

(rf/reg-sub :app/db
  (fn [db _]
    db))

;; Controlled inputs
;;
;; An input with a `:value` is "controlled": it always shows what app-db
;; says. `rf/dispatch` is asynchronous, so if React renders before the event
;; has been handled, the input is reset to the old value and the keystroke is
;; lost. As re-frame's docs recommend for this exact case, we handle these
;; events with `rf/dispatch-sync`, and then re-render right away with
;; `r/flush`, so the view is never behind what the user typed.

(defn- controlled-input? [node]
  (and (vector? node)
       (keyword? (first node))
       (re-find #"^(input|textarea)([.#]|$)" (name (first node)))
       (map? (second node))
       (contains? (second node) :value)
       (some? (get-in (second node) [:on :input]))))

(defn- dispatch-sync-actions [e actions]
  (let [actions (if (keyword? (first actions)) [actions] actions)]
    (doseq [action (hiccup/interpolate e (remove nil? actions))]
      (rf/dispatch-sync action)))
  (r/flush))

(defn sync-controlled-inputs
  "Makes the :input handler of every controlled input synchronous. Reagent
  only protects the caret of a controlled input for :on-change, so that is
  where the handler goes (React fires onChange on every keystroke)."
  [hiccup]
  (walk/prewalk
   (fn [node]
     (if (controlled-input? node)
       (let [actions (get-in node [1 :on :input])]
         (-> node
             (update-in [1 :on] dissoc :input)
             (assoc-in [1 :on :change] #(dispatch-sync-actions % actions))))
       node))
   hiccup))

;; Rendering

(defn app []
  (-> (ui/render-page @(rf/subscribe [:app/db]))
      sync-controlled-inputs
      hiccup/prepare))

(defonce root
  (delay (rdc/create-root (js/document.getElementById "app"))))

(defn render []
  (rdc/render @root [app]))

(defn main []
  (rf/dispatch-sync [:app/start (js/Date.)])
  (render))
