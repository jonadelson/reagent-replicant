(ns toil.router-test
  (:require [clojure.test :refer [deftest is testing]]
            [toil.frontpage :as frontpage]
            [toil.router :as router]
            [toil.user :as user]))

(def routes
  (router/make-routes [user/page frontpage/page]))

(deftest router-test
  (testing "Finds the location of a URL"
    (is (= (router/url->location routes "/")
           {:location/page-id :pages/frontpage
            :location/params {}})))

  (testing "Finds a location with parameters"
    (is (= (router/url->location routes "/users/alice")
           {:location/page-id :pages/user
            :location/params {:user/id "alice"}})))

  (testing "Does not route unknown URLs"
    (is (nil? (router/url->location routes "/nope"))))

  (testing "Finds the URL of a location"
    (is (= (router/location->url routes
             {:location/page-id :pages/user
              :location/params {:user/id "alice"}})
           "/users/alice")))

  (testing "Keeps query parameters"
    (is (= (router/url->location routes "/?filter=done")
           {:location/page-id :pages/frontpage
            :location/params {}
            :location/query-params {:filter "done"}})))

  (testing "Ignores the hash when comparing locations"
    (is (true? (router/essentially-same?
                {:location/page-id :pages/frontpage}
                {:location/page-id :pages/frontpage
                 :location/hash-params {:section "top"}})))))

(deftest pages-test
  (testing "The frontpage loads the todo items"
    (is (= ((:on-load frontpage/page) {:location/page-id :pages/frontpage})
           [[:data/query {:query/kind :query/todo-items}]])))

  (testing "The user page loads the user from the URL"
    (is (= ((:on-load user/page) (router/url->location routes "/users/bob"))
           [[:data/query {:query/kind :query/user
                          :query/data {:user-id "bob"}}]]))))
