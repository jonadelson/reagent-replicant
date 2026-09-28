(ns atlas.html
  "Renders data-driven hiccup to an HTML string on the JVM. This is the
  server-side counterpart of `datadriven.hiccup/prepare`: it expands aliases,
  then hands the result to the hiccup library."
  (:require [clojure.string :as str]
            [datadriven.hiccup :as hiccup]
            [hiccup2.core :as h]))

;; CSS properties that take plain numbers. Everything else gets "px", like
;; React does: {:margin-top 20} means 20px.
(def unitless-styles
  #{:animation-iteration-count :column-count :fill-opacity :flex :flex-grow
    :flex-shrink :font-weight :grid-column :grid-row :line-height :opacity
    :order :orphans :stroke-opacity :stroke-width :tab-size :widows :z-index
    :zoom})

(defn- class-str [class]
  (if (coll? class)
    (str/join " " (keep #(some-> % name) class))
    (name class)))

(defn- html-attrs
  "Keeps only the attributes that belong in HTML: drops event handler data,
  namespaced attributes (alias parameters), keys, refs and functions."
  [attrs]
  (let [{:keys [class style]} attrs]
    (cond-> (into {}
                  (remove (fn [[k v]]
                            (or (qualified-keyword? k)
                                (#{:on :key :ref :innerHTML} k)
                                (fn? v))))
                  attrs)
      class (assoc :class (class-str class))
      (map? style) (assoc :style (into {}
                                       (for [[k v] style]
                                         [k (if (and (number? v)
                                                     (not (unitless-styles k)))
                                              (str v "px")
                                              v)]))))))

(defn- ->html-hiccup [node]
  (cond
    (and (vector? node) (keyword? (first node)))
    (let [[tag & [attrs & more :as children]] node
          [attrs children] (if (map? attrs) [attrs more] [{} children])]
      (into [tag (html-attrs attrs)]
            (if-let [html (:innerHTML attrs)]
              [(h/raw html)]
              (map ->html-hiccup children))))

    (seq? node)
    (map ->html-hiccup node)

    :else node))

(defn render
  "Renders data-driven hiccup to a string of HTML. Takes the same options as
  `datadriven.hiccup/expand`."
  ([hiccup] (render hiccup nil))
  ([hiccup opts]
   (str (h/html (->html-hiccup (hiccup/expand hiccup opts))))))
