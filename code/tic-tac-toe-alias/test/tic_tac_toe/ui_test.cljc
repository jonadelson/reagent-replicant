(ns tic-tac-toe.ui-test
  (:require [clojure.test :refer [deftest is testing]]
            [datadriven.hiccup :as hiccup]
            [lookup.core :as lookup]
            [tic-tac-toe.game :as game]
            [tic-tac-toe.ui :as ui]))

(deftest render-game-test
  (testing "Renders board"
    (is (= (->> (ui/render-game
                 {:size 3
                  :tics {[0 0] :x
                         [0 1] :o}
                  :next-player :x})
                hiccup/expand-1
                (lookup/select-one :div.board))
           [:div {:class #{"board"}}
            [:div {:class #{"row"}}
             [::ui/cell ui/mark-x]
             [::ui/cell ui/mark-o]
             [::ui/cell {:on {:click [:tic 0 2]}, :class #{"clickable"}}]]
            [:div {:class #{"row"}}
             [::ui/cell {:on {:click [:tic 1 0]}, :class #{"clickable"}}]
             [::ui/cell {:on {:click [:tic 1 1]}, :class #{"clickable"}}]
             [::ui/cell {:on {:click [:tic 1 2]}, :class #{"clickable"}}]]
            [:div {:class #{"row"}}
             [::ui/cell {:on {:click [:tic 2 0]}, :class #{"clickable"}}]
             [::ui/cell {:on {:click [:tic 2 1]}, :class #{"clickable"}}]
             [::ui/cell {:on {:click [:tic 2 2]}, :class #{"clickable"}}]]])))

  (testing "Highlights winning path"
    (is (= (-> (game/create-game {:size 3})
               (game/tic 0 0) ;; x
               (game/tic 1 0) ;; o
               (game/tic 0 1) ;; x
               (game/tic 1 1) ;; o
               (game/tic 0 2) ;; x
               ui/render-game
               hiccup/expand-1
               (->> (lookup/select '.cell-highlight)))
           [[::ui/cell {:class #{"cell-highlight"}} ui/mark-x]
            [::ui/cell {:class #{"cell-highlight"}} ui/mark-x]
            [::ui/cell {:class #{"cell-highlight"}} ui/mark-x]])))

  (testing "Dims everything besides the winning path"
    (is (= (-> (game/create-game {:size 3})
               (game/tic 0 0) ;; x
               (game/tic 1 0) ;; o
               (game/tic 0 1) ;; x
               (game/tic 1 1) ;; o
               (game/tic 0 2) ;; x
               ui/render-game
               hiccup/expand-1
               (->> (lookup/select '.cell-dim))
               count)
           6)))

  (testing "Dims tied game"
    (is (= (-> (game/create-game {:size 3})
               (game/tic 0 0) ;; x
               (game/tic 0 1) ;; o
               (game/tic 0 2) ;; x
               (game/tic 1 0) ;; o
               (game/tic 1 1) ;; x
               (game/tic 2 2) ;; o
               (game/tic 2 1) ;; x
               (game/tic 2 0) ;; o
               (game/tic 1 2) ;; x
               ui/render-game
               hiccup/expand-1
               (->> (lookup/select '.cell-dim))
               count)
           9)))

  (testing "Shows the start over button when the game is over"
    (is (= (-> (game/create-game {:size 3})
               (game/tic 0 0) ;; x
               (game/tic 1 0) ;; o
               (game/tic 0 1) ;; x
               (game/tic 1 1) ;; o
               (game/tic 0 2) ;; x
               ui/render-game
               hiccup/expand-1
               (->> (lookup/select-one :button))
               (lookup/attrs)
               :on)
           {:click [:reset]}))))

(deftest cell-test
  (testing "Passes attributes on to the button"
    (is (= (hiccup/expand [::ui/cell {:class :clickable
                                      :data-cell-id "f6c"}])
           [:button.cell {:class :clickable
                          :data-cell-id "f6c"}
            nil])))

  (testing "Takes classes in the tag"
    (is (= (hiccup/expand [::ui/cell.myclass {:data-cell-id "f6c"}])
           [:button.cell {:class ["myclass"]
                          :data-cell-id "f6c"}
            nil])))

  (testing "Wraps the content"
    (is (= (hiccup/expand [::ui/cell ui/mark-x])
           [:button.cell {} [:div.cell-content {} ui/mark-x]]))))
