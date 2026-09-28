(ns boardgames.ui.sortable-table
  (:require [datadriven.hiccup :as hiccup]))

(def reverse-order
  {"desc" "asc"
   "asc" "desc"})

(def comparators
  {"asc" compare
   "desc" #(compare %2 %1)})

(defn get-sort-order [location]
  (if (= "desc" (-> location :location/hash-params :sort-order))
    "desc"
    "asc"))

(defn get-sort-column [location columns]
  (let [param (-> location :location/hash-params :sort-column)]
    (or (first (filter (comp #{param} :id) columns))
        (first (filter :default? columns))
        (first columns))))

(defn update-attrs
  "Like `update`, but for the attribute map of a hiccup node. Works the same
  whether the node has an attribute map or not."
  [[tag & [attrs & more :as children]] f & args]
  (if (map? attrs)
    (into [tag (apply f attrs args)] more)
    (into [tag (apply f {} args)] children)))

(defn render-table [attrs children]
  (into
   [:table attrs]
   (mapv #(update-attrs
           % assoc
           ::location (::location attrs)
           ::columns (::columns attrs)
           ::sort-order (get-sort-order (::location attrs))
           ::sort-column (get-sort-column (::location attrs) (::columns attrs)))
         children)))

(defn render-thead [attrs children]
  [:thead
   (into
    [:tr attrs]
    (map-indexed
     (fn [idx child]
       (update-attrs
        child assoc
        ::location (::location attrs)
        ::column (nth (::columns attrs) idx)
        ::sort-order (::sort-order attrs)
        ::sort-column (::sort-column attrs)))
     children))])

(defn render-tbody [{::keys [columns sort-column sort-order data] :as attrs} children]
  (into
   [:tbody]
   (->> data
        (sort-by (:f sort-column) (comparators sort-order))
        (mapv
         (fn [row-data]
           (into [:tr (assoc attrs :key (hash row-data))]
                 (map-indexed
                  (fn [col-idx cell]
                    (update-attrs
                     cell assoc
                     ::column (nth columns col-idx)
                     ::data row-data))
                  children)))))))

(defn render-th [{::keys [column sort-column sort-order location data] :as attrs} _]
  (if data
    [:th attrs ((:f column) data)]
    [:th attrs
     [:ui/a
      {:ui/location
       (if (= (:id column) (:id sort-column))
         (assoc-in location [:location/hash-params :sort-order]
                   (reverse-order sort-order))
         (assoc-in location [:location/hash-params :sort-column]
                   (:id column)))}
      (when (= (:id column) (:id sort-column))
        (if (= "desc" sort-order) "▼ " "▲ "))
      (:label column)]]))

(defn render-td [{::keys [column data] :as attrs} _]
  [:td attrs ((:f column) data)])

(hiccup/register-alias! ::table render-table)
(hiccup/register-alias! ::thead render-thead)
(hiccup/register-alias! ::tbody render-tbody)
(hiccup/register-alias! ::th render-th)
(hiccup/register-alias! ::td render-td)
