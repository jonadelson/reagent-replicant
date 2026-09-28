(ns toil.frontpage
  (:require [toil.query :as query]))

(def items-query
  {:query/kind :query/todo-items})

(defn render [state]
  [:main.p-8.max-w-screen-lg
   [:h1.text-2xl.mb-4 "Toil and trouble: Todos over the network"]
   (when-let [todos (query/get-result state items-query)]
     [:ul.mb-4
      (for [item todos]
        [:li.my-2
         [:span.pr-2
          (if (:todo/done? item)
            "✓"
            "▢")]
         (:todo/title item)
         " ("
         [:ui/a.link
          {:ui/location
           {:location/page-id :pages/user
            :location/params {:user/id (:todo/created-by item)}}}
          (:todo/created-by item)]
         ")"])])
   (if (query/loading? state items-query)
     [:button.btn.btn-primary {:disabled true}
      [:span.loading.loading-spinner]
      "Fetching todos"]
     [:button.btn.btn-primary
      {:on {:click [[:data/query items-query]]}}
      "Fetch todos"])])

(def page
  {:page-id :pages/frontpage
   :route [[]]
   :on-load (fn [_location]
              [[:data/query items-query]])
   :render #'render})
