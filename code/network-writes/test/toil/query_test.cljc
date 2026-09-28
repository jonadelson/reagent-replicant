(ns toil.query-test
  (:require [clojure.test :refer [deftest is testing]]
            [toil.query :as query]))

(def query {:query/kind :query/todo-items})

(def todo-items
  {:todo/items [{:todo/id "74e67"
                 :todo/title "Write project documentation"
                 :todo/done? false}]})

(deftest decisions-test
  (testing "Sends request"
    (is (true? (-> (query/send-request {} #inst "2025-01-02T06:44:13" query)
                   (query/loading? query)))))

  (testing "Knows when request was made"
    (is (= (-> (query/send-request {} #inst "2025-01-02T06:42:17" query)
               (query/requested-at query))
           #inst "2025-01-02T06:42:17.000-00:00")))

  (testing "Received successful response"
    (is (false? (-> (query/send-request {} #inst "2025-01-02T06:44:13" query)
                    (query/receive-response #inst "2025-01-02T06:44:14" query
                      {:success? true
                       :result todo-items})
                    (query/loading? query)))))

  (testing "Successful response is available"
    (is (true? (-> (query/send-request {} #inst "2025-01-02T06:44:13" query)
                   (query/receive-response #inst "2025-01-02T06:44:14" query
                     {:success? true
                      :result todo-items})
                   (query/available? query)))))

  (testing "Successful response is still available when refreshing"
    (is (true? (-> (query/send-request {} #inst "2025-01-02T06:44:13" query)
                   (query/receive-response #inst "2025-01-02T06:44:14" query
                     {:success? true
                      :result todo-items})
                   (query/send-request #inst "2025-01-02T06:44:15" query)
                   (query/available? query)))))

  (testing "Is also loading when refreshing"
    (is (true? (-> (query/send-request {} #inst "2025-01-02T06:44:13" query)
                   (query/receive-response #inst "2025-01-02T06:44:14" query
                     {:success? true
                      :result todo-items})
                   (query/send-request #inst "2025-01-02T06:44:15" query)
                   (query/loading? query)))))

  (testing "Gets available data"
    (is (= (-> (query/send-request {} #inst "2025-01-02T06:44:13" query)
               (query/receive-response #inst "2025-01-02T06:44:14" query
                 {:success? true
                  :result todo-items})
               (query/get-result query))
           todo-items)))

  (testing "Knows about errors"
    (is (true? (-> (query/send-request {} #inst "2025-01-02T06:44:13" query)
                   (query/receive-response #inst "2025-01-02T06:44:14" query
                     {:error "Failed"})
                   (query/error? query)))))

  (testing "Treats empty response as error"
    (is (true? (-> (query/send-request {} #inst "2025-01-02T06:44:13" query)
                   (query/receive-response #inst "2025-01-02T06:44:14" query nil)
                   (query/error? query)))))

  (testing "Truncates log to include the start of the last successful query"
    (is (= (-> (query/send-request {} #inst "2025-01-02T06:42:17" query)
               (query/receive-response #inst "2025-01-02T06:43:05" query
                 {:success? true
                  :result todo-items})
               (query/send-request #inst "2025-01-02T06:44:12" query)
               (query/receive-response #inst "2025-01-02T06:44:13" query
                 {:success? true
                  :result todo-items})
               (query/get-log query))
           [{:query/status :query.status/success
             :query/user-time #inst "2025-01-02T06:44:13.000-00:00"
             :query/result {:todo/items
                            [{:todo/id "74e67"
                              :todo/title "Write project documentation"
                              :todo/done? false}]}}
            {:query/status :query.status/loading
             :query/user-time #inst "2025-01-02T06:44:12.000-00:00"}]))))
