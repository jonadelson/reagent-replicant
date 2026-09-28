(ns toil.user
  (:require [toil.query :as query]))

(defn get-query [location]
  {:query/kind :query/user
   :query/data {:user-id (-> location :location/params :user/id)}})

(defn render [state]
  (let [user (query/get-result state (get-query (:location state)))]
    [:main.p-8.max-w-screen-lg
     [:h1.text-2xl.mb-4
      (if user
        (str (:user/given-name user) " " (:user/family-name user))
        (str "User " (-> state :location :location/params :user/id)))]
     (when user
       [:p.mb-2 (:user/email user)])
     [:p
      [:ui/a.link {:ui/location {:location/page-id :pages/frontpage}}
       "Back"]]]))

(def page
  {:page-id :pages/user
   :route [["users" :user/id]]
   :on-load (fn [location]
              [[:data/query (get-query location)]])
   :render #'render})
