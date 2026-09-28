(ns toil.command-test
  (:require [clojure.test :refer [deftest is testing]]
            [toil.command :as command]))

(def command {:command/kind :command/create-todo
              :command/data {:todo/title "Implement commands"}})

(deftest decisions-test
  (testing "Issues command"
    (is (true? (-> (command/issue-command {} #inst "2025-01-02T06:44:13" command)
                   (command/issued? command)))))

  (testing "Knows when command was issued"
    (is (= (-> (command/issue-command {} #inst "2025-01-02T06:42:17" command)
               (command/issued-at command))
           #inst "2025-01-02T06:42:17.000-00:00")))

  (testing "Received successful response"
    (is (false? (-> (command/issue-command {} #inst "2025-01-02T06:44:13" command)
                    (command/receive-response #inst "2025-01-02T06:44:14" command
                      {:success? true})
                    (command/issued? command)))))

  (testing "Is successful"
    (is (true? (-> (command/issue-command {} #inst "2025-01-02T06:44:13" command)
                   (command/receive-response #inst "2025-01-02T06:44:14" command
                     {:success? true})
                   (command/success? command)))))

  (testing "Knows about errors"
    (is (true? (-> (command/issue-command {} #inst "2025-01-02T06:44:13" command)
                   (command/receive-response #inst "2025-01-02T06:44:14" command
                     {:error "Failed"})
                   (command/error? command)))))

  (testing "Treats empty response as error"
    (is (true? (-> (command/issue-command {} #inst "2025-01-02T06:44:13" command)
                   (command/receive-response #inst "2025-01-02T06:44:14" command nil)
                   (command/error? command))))))
