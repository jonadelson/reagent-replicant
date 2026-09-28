(ns atlas.server-test
  (:require [atlas.html :as html]
            [atlas.server :as server]
            [atlas.ui.map :as map]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [datadriven.hiccup :as hiccup]))

(deftest marker-map-alias-test
  (testing "Renders a placeholder with the map data in a script tag"
    (is (= (hiccup/expand
            [::map/marker-map {:class "mb-4"
                               ::map/center [-0.1276 51.5072]
                               ::map/zoom 12}
             [::map/marker {:point/label "Pikachu"
                            :point/latitude 51.5081
                            :point/longitude -0.1281}]])
           [:div.aspect-video {:class "mb-4"
                               ::map/center [-0.1276 51.5072]
                               ::map/zoom 12
                               :data-client-feature "marker-map"}
            [:script
             {:type "application/edn"
              :innerHTML (pr-str {::map/points [{:point/label "Pikachu"
                                                 :point/latitude 51.5081
                                                 :point/longitude -0.1281}]
                                  ::map/center [-0.1276 51.5072]
                                  ::map/zoom 12})}]])))

  (testing "Ignores nil children"
    (is (= (-> (hiccup/expand
                [::map/marker-map {}
                 nil
                 (list [::map/marker {:point/label "Eevee"}] nil)])
               (get-in [2 1 :innerHTML]))
           (pr-str {::map/points [{:point/label "Eevee"}]}))))

  (testing "The client can read the data back"
    (let [html (html/render (server/render-city-page "london"))
          edn-str (second (re-find #"<script type=\"application/edn\">(.*?)</script>" html))]
      (is (= (-> (edn/read-string edn-str)
                 (update ::map/points #(map :point/label %)))
             {::map/center [-0.1276 51.5072]
              ::map/zoom 12
              ::map/points ["Pikachu" "Eevee" "Snorlax" "Gengar" "Lapras"]})))))

(deftest handler-test
  (testing "Serves the client-side app on /"
    (let [res (server/handler {:uri "/" :request-method :get})]
      (is (= 200 (:status res)))
      (is (str/includes? (slurp (:body res)) "<div id=\"app\"></div>"))))

  (testing "Renders city pages on the server"
    (let [{:keys [status body]} (server/handler {:uri "/city/london" :request-method :get})]
      (is (= 200 status))
      (is (not (str/includes? body "<div id=\"app\"></div>")))
      (is (str/includes? body "<h1 class=\"text-xl mb-2\">Hello <span class=\"line-through\">world</span> London!</h1>"))
      (is (str/includes? body "<div class=\"aspect-video mb-4\" data-client-feature=\"marker-map\">"))
      (is (str/includes? body "<a class=\"link\" href=\"/city/tokyo\">Tokyo</a>"))))

  (testing "Responds with 404 to unknown URLs"
    (is (= 404 (:status (server/handler {:uri "/nope" :request-method :get}))))))
