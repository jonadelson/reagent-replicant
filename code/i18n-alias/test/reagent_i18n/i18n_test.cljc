(ns reagent-i18n.i18n-test
  (:require [clojure.test :refer [deftest is testing]]
            [datadriven.hiccup :as hiccup]
            [m1p.core :as m1p]
            [reagent-i18n.i18n :as i18n]
            [reagent-i18n.i18n.en :as en]
            [reagent-i18n.i18n.nb :as nb]))

(def dictionaries
  (-> {:nb nb/dictionary
       :en en/dictionary}
      (update-vals m1p/prepare-dictionary)))

(deftest k-test
  (testing "Looks up a key in the current locale"
    (is (= (hiccup/expand [:h1 [::i18n/k :page/title]]
                          {:alias-data {:dictionaries dictionaries
                                        :locale :nb}})
           [:h1 {} "Velkommen!"]))

    (is (= (hiccup/expand [:h1 [::i18n/k :page/title]]
                          {:alias-data {:dictionaries dictionaries
                                        :locale :en}})
           [:h1 {} "Welcome!"])))

  (testing "Interpolates parameters"
    (is (= (hiccup/expand [:p [::i18n/k :user/greeting {:user/given-name "Christian"}]]
                          {:alias-data {:dictionaries dictionaries
                                        :locale :en}})
           [:p {} "Nice to see you, Christian!"]))))
