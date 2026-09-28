(ns tic-tac-toe.dev
  (:require [dataspex.core :as dataspex]
            [re-frame.core :as rf]
            [re-frame.db]
            [tic-tac-toe.core :as tic-tac-toe]))

(defn ^:dev/after-load reload []
  (rf/clear-subscription-cache!)
  (tic-tac-toe/render))

(defn main []
  (dataspex/inspect "Game state" re-frame.db/app-db)
  (tic-tac-toe/main))
