(ns parens.router-test
  (:require [clojure.test :refer [deftest is testing]]
            [parens.router :as router]))

(deftest url->location-test
  (testing "Recognizes the frontpage"
    (is (= (router/url->location router/routes "/")
           {:location/page-id :pages/frontpage
            :location/params {}})))

  (testing "Extracts route parameters"
    (is (= (router/url->location router/routes "/episodes/s2e1")
           {:location/page-id :pages/episode
            :location/params {:episode/id "s2e1"}})))

  (testing "Extracts query and hash parameters"
    (is (= (router/url->location router/routes "/episodes/s2e1?view=related#description=1")
           {:location/page-id :pages/episode
            :location/params {:episode/id "s2e1"}
            :location/query-params {:view "related"}
            :location/hash-params {:description "1"}})))

  (testing "Handles full URLs"
    (is (= (:location/params
            (router/url->location router/routes "http://localhost:8080/episodes/s2e2"))
           {:episode/id "s2e2"})))

  (testing "Returns nil for unknown URLs"
    (is (nil? (router/url->location router/routes "/nope/nope/nope")))))

(deftest location->url-test
  (testing "Generates URLs from locations"
    (is (= (router/location->url router/routes
             {:location/page-id :pages/episode
              :location/params {:episode/id "s2e3"}
              :location/hash-params {:description "1"}})
           "/episodes/s2e3#description=1")))

  (testing "Round-trips"
    (let [url "/episodes/s2e1?view=related#description=1"]
      (is (= (->> url
                  (router/url->location router/routes)
                  (router/location->url router/routes))
             url)))))

(deftest essentially-same?-test
  (testing "Ignores hash parameters"
    (is (router/essentially-same?
         {:location/page-id :pages/episode
          :location/params {:episode/id "s2e1"}}
         {:location/page-id :pages/episode
          :location/params {:episode/id "s2e1"}
          :location/hash-params {:description "1"}})))

  (testing "Different params are different locations"
    (is (not (router/essentially-same?
              {:location/page-id :pages/episode
               :location/params {:episode/id "s2e1"}}
              {:location/page-id :pages/episode
               :location/params {:episode/id "s2e2"}})))))
