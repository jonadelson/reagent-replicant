(ns reagent-i18n.core
  (:require [datadriven.hiccup :as hiccup]
            [m1p.core :as m1p]
            [re-frame.core :as rf]
            [reagent-i18n.i18n :as i18n]
            [reagent-i18n.i18n.en :as en]
            [reagent-i18n.i18n.nb :as nb]
            [reagent.dom.client :as rdc]))

(def dictionaries
  (-> {:nb nb/dictionary
       :en en/dictionary}
      (update-vals m1p/prepare-dictionary)))

(def other-locale
  {:en :nb
   :nb :en})

(rf/reg-event-db :app/init
  (fn [_ _]
    {:locale :en}))

(rf/reg-event-db :switch-locale
  (fn [db _]
    (update db :locale other-locale)))

(rf/reg-sub :locale
  (fn [db _]
    (:locale db)))

(defn render-ui [user]
  [:div
   [:h1 [::i18n/k :page/title]]
   [:p [::i18n/k :user/greeting user]]
   [:button {:on {:click [:switch-locale]}}
    [::i18n/k :locale/switch]]])

(defn app []
  (hiccup/prepare
   (render-ui {:user/given-name "Christian"})
   {:alias-data {:dictionaries dictionaries
                 :locale @(rf/subscribe [:locale])}}))

(defonce root
  (delay (rdc/create-root (js/document.getElementById "app"))))

(defn render []
  (rdc/render @root [app]))

(defn main []
  (rf/dispatch-sync [:app/init])
  (render))
