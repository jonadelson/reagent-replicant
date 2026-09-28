(ns reagent-i18n.i18n
  (:require [datadriven.hiccup :as hiccup]
            [m1p.core :as m1p]))

(defn translate
  "Implements the `::k` alias: `[::i18n/k :user/greeting params]` looks up
  `:user/greeting` in the dictionary for the current locale. The dictionaries
  and the locale come from the alias data."
  [attrs [k params]]
  (let [{:keys [dictionaries locale]} (::hiccup/alias-data attrs)]
    (m1p/lookup {} (get dictionaries locale) k params)))

(hiccup/register-alias! ::k translate)
