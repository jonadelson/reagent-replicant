(ns atlas.server
  (:require [atlas.data :as data]
            [atlas.html :as html]
            [atlas.ui :as ui]
            [atlas.ui.map] ;; Registers the server-side ::map/marker-map alias
            [clojure.java.io :as io]
            [clojure.string :as str]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.util.response :as response]))

(def template (slurp (io/resource "public/index.html")))

(defn serve-page [hiccup]
  {:status 200
   :headers {"content-type" "text/html; charset=utf-8"}
   :body
   (->> (html/render hiccup)
        (str/replace template "<div id=\"app\"></div>"))})

(defn render-city-page [city-id]
  (ui/render-page
   {:city (first (filter (comp #{city-id} :id) data/cities))
    :cities data/cities}))

(defn handler [{:keys [uri]}]
  (cond
    (= "/" uri)
    (response/resource-response "/index.html" {:root "public"})

    (str/starts-with? uri "/city")
    (serve-page (render-city-page (str/replace uri #"^/city/" "")))

    :else
    {:status 404
     :headers {"content-type" "text/html"}
     :body "<h1>Page not found</h1>"}))

(defn start-server [port]
  (jetty/run-jetty
   (-> #'handler
       (wrap-resource "public"))
   {:port port :join? false}))

(defn stop-server [server]
  (.stop server))

(defn -main [& [port]]
  (let [port (parse-long (or port "8089"))]
    (start-server port)
    (println (str "Listening on http://localhost:" port))))

(comment

  (def server (start-server 8089))
  (stop-server server)

  )
