(ns toil.router-test
  (:require [clojure.test :refer [deftest is testing]]
            [toil.router :as router]))

(deftest router-test
  (testing "Finds the location of a URL"
    (is (= (router/url->location router/routes "/")
           {:location/page-id :pages/frontpage
            :location/params {}})))

  (testing "Finds the URL of a location"
    (is (= (router/location->url router/routes
             {:location/page-id :pages/frontpage})
           "/")))

  (testing "Does not route unknown URLs"
    (is (nil? (router/url->location router/routes "/nope"))))

  (testing "Keeps query parameters"
    (is (= (router/url->location router/routes "/?filter=done")
           {:location/page-id :pages/frontpage
            :location/params {}
            :location/query-params {:filter "done"}})))

  (testing "Ignores the hash when comparing locations"
    (is (true? (router/essentially-same?
                {:location/page-id :pages/frontpage}
                {:location/page-id :pages/frontpage
                 :location/hash-params {:section "top"}})))))
