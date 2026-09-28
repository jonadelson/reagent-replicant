(ns atlas.ui
  (:require [atlas.ui.map :as-alias map]))

(defn render-title [city-name]
  [:h1.text-xl.mb-2
   "Hello "
   [:span {:class (when city-name "line-through")}
    "world"]
   (when city-name
     (str " " city-name))
   "!"])

(defn render-page [{:keys [city cities]}]
  (let [{:keys [name position zoom points]} city]
    [:main.m-4
     (render-title name)
     (when position
       [::map/marker-map
        {:class "mb-4"
         ::map/center position
         ::map/zoom (or zoom 11)}
        (for [point points]
          [::map/marker point])])
     [:h2.text-lg.mb-2 "Choose city"]
     [:ul
      (for [city cities]
        (if (= name (:name city))
          [:li (:name city)]
          [:li
           [:button.link
            {:on {:click [[:store/assoc-in [:city] city]]}}
            (:name city)]]))]]))
