(ns datadriven.hiccup
  "Lets us write Reagent views as pure data. Event handlers are written as
  data, like `{:on {:click [:tic 0 1]}}`, and `prepare` turns them into
  functions that dispatch the data to re-frame."
  (:require [re-frame.core :as rf]))

(defn- event-handler [handler]
  (if (fn? handler)
    handler
    (fn [_] (rf/dispatch handler))))

(defn- prepare-attrs [attrs]
  (let [on (:on attrs)]
    (cond-> (dissoc attrs :on)
      on (into (for [[event handler] on
                     :when handler]
                 [(keyword (str "on-" (name event)))
                  (event-handler handler)])))))

(defn- flatten-children [children]
  (mapcat #(if (seq? %) (flatten-children %) [%]) children))

(defn prepare [hiccup]
  (cond
    (and (vector? hiccup) (keyword? (first hiccup)))
    (let [[tag & [attrs & more :as children]] hiccup
          [attrs children] (if (map? attrs) [attrs more] [{} children])]
      (into [tag (prepare-attrs attrs)]
            (map prepare)
            (flatten-children children)))

    (seq? hiccup)
    (map prepare hiccup)

    :else
    hiccup))
