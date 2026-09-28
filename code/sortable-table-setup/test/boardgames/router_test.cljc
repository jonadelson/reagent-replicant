(ns boardgames.router-test
  (:require [boardgames.router :as router]
            [clojure.test :refer [deftest is testing]]
            [datadriven.hiccup :as hiccup]))

(deftest url->location-test
  (testing "Parses query and hash params"
    (is (= (router/url->location "/?page=2#sort-column=title&sort-order=desc")
           {:location/path "/"
            :location/query-params {:page "2"}
            :location/hash-params {:sort-column "title"
                                   :sort-order "desc"}}))))

(deftest location->url-test
  (testing "Builds a URL with query and hash params"
    (is (= (router/location->url
            {:location/path "/"
             :location/query-params {:page "2"}
             :location/hash-params {:sort-order "desc"}})
           "/?page=2#sort-order=desc"))))

(deftest essentially-same?-test
  (testing "Ignores hash params"
    (is (router/essentially-same?
         {:location/path "/"}
         {:location/path "/" :location/hash-params {:sort-order "desc"}})))

  (testing "Does not ignore query params"
    (is (not (router/essentially-same?
              {:location/path "/"}
              {:location/path "/" :location/query-params {:page "2"}})))))

(deftest routing-anchor-test
  (testing "Links to the location and navigates on click"
    (is (= (hiccup/expand
            [:ui/a {:ui/location {:location/path "/"
                                  :location/hash-params {:sort-order "desc"}}}
             "Ranking"])
           [:a {:ui/location {:location/path "/"
                              :location/hash-params {:sort-order "desc"}}
                :href "/#sort-order=desc"
                :on {:click [[:event/prevent-default]
                             [:router/navigate
                              {:location/path "/"
                               :location/hash-params {:sort-order "desc"}}]]}}
            "Ranking"]))))
