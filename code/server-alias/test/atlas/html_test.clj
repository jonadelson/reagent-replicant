(ns atlas.html-test
  (:require [atlas.html :as html]
            [clojure.test :refer [deftest is testing]]))

(deftest render-test
  (testing "Renders plain hiccup"
    (is (= (html/render [:main.m-4 [:h1 "Hello " nil "world!"]])
           "<main class=\"m-4\"><h1>Hello world!</h1></main>")))

  (testing "Escapes text"
    (is (= (html/render [:p "Fish & chips"])
           "<p>Fish &amp; chips</p>")))

  (testing "Combines classes from the tag and a :class collection"
    (is (= (html/render [:div.a {:class ["b" nil :c]}])
           "<div class=\"a b c\"></div>")))

  (testing "Accepts a keyword or a set as :class"
    (is (= (html/render [:div.a {:class :b}])
           "<div class=\"a b\"></div>"))
    (is (= (html/render [:div {:class #{"b"}}])
           "<div class=\"b\"></div>")))

  (testing "Adds px to numbers in styles, like React does"
    (is (= (html/render [:div {:style {:margin-top 20 :opacity 0.5}}])
           "<div style=\"margin-top:20px;opacity:0.5;\"></div>")))

  (testing "Leaves out event handlers, keys and namespaced attributes"
    (is (= (html/render [:a {:href "/"
                             :key "home"
                             :on {:click [:go-home]}
                             :my.app/secret 42}
                         "Home"])
           "<a href=\"/\">Home</a>")))

  (testing "Renders :innerHTML unescaped"
    (is (= (html/render [:script {:type "application/edn" :innerHTML "{:a \"b\"}"}])
           "<script type=\"application/edn\">{:a \"b\"}</script>")))

  (testing "Expands aliases"
    (is (= (html/render [:ui/bold "Hi"]
                        {:aliases {:ui/bold (fn [_ children] (into [:strong] children))}})
           "<strong>Hi</strong>"))))
