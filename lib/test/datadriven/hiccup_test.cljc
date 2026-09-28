(ns datadriven.hiccup-test
  (:require [clojure.test :refer [deftest is testing]]
            [datadriven.hiccup :as hiccup]))

(deftest prepare-test
  (testing "Leaves plain hiccup alone"
    (is (= (hiccup/prepare [:div.box {:class ["a" "b"]} "Hello"])
           [:div.box {:class ["a" "b"]} "Hello"])))

  (testing "Adds an empty attribute map when there is none"
    (is (= (hiccup/prepare [:h1 "Hi"])
           [:h1 {} "Hi"])))

  (testing "Splices lists of children into the parent"
    (is (= (hiccup/prepare [:ul (for [x [1 2]] [:li x]) [:li 3]])
           [:ul {} [:li {} 1] [:li {} 2] [:li {} 3]])))

  (testing "Turns event handler data into functions"
    (let [[_ attrs] (hiccup/prepare [:button {:on {:click [:tic 0 1]}} "Click"])]
      (is (nil? (:on attrs)))
      (is (fn? (:on-click attrs)))))

  (testing "Uses React's spelling of multi-word events"
    (let [[_ attrs] (hiccup/prepare [:input {:on {:keydown [:x]}}])]
      (is (fn? (:on-key-down attrs)))))

  (testing "Passes functions through as they are"
    (let [f (fn [_])]
      (is (= (hiccup/prepare [:button {:on {:click f}}])
             [:button {:on-click f}]))))

  (testing "Skips nil event handlers"
    (is (= (hiccup/prepare [:button {:on {:click nil}}])
           [:button {}])))

  (testing "Uses :on-change for input events on form fields, so Reagent keeps the cursor in place"
    (let [[_ attrs] (hiccup/prepare [:input.big {:value "x" :on {:input [:x]}}])]
      (is (fn? (:on-change attrs)))
      (is (nil? (:on-input attrs)))))

  (testing "Keeps :on-input on other elements"
    (let [[_ attrs] (hiccup/prepare [:div {:content-editable true :on {:input [:x]}}])]
      (is (fn? (:on-input attrs)))))

  (testing "Drops namespaced attributes"
    (is (= (hiccup/prepare [:div {:ui/size :large :id "x"}])
           [:div {:id "x"}])))

  (testing "Translates innerHTML"
    (is (= (hiccup/prepare [:div {:innerHTML "<b>Hi</b>"}])
           [:div {:dangerouslySetInnerHTML {:__html "<b>Hi</b>"}}]))))

(defn render-button [attrs children]
  (into [:button.btn (dissoc attrs :ui/size)] children))

(deftest alias-test
  (testing "Expands aliases"
    (is (= (hiccup/expand [:div [:ui/button {:ui/size :large} "Save"]]
                          {:aliases {:ui/button render-button}})
           [:div {} [:button.btn {} "Save"]])))

  (testing "Expands aliases in the output of aliases"
    (is (= (hiccup/expand [:ui/panel "Save"]
                          {:aliases {:ui/button render-button
                                     :ui/panel (fn [_ children]
                                                 [:div.panel (into [:ui/button] children)])}})
           [:div.panel {} [:button.btn {} "Save"]])))

  (testing "Passes alias data to aliases"
    (is (= (hiccup/expand [:ui/greeting]
                          {:aliases {:ui/greeting
                                     (fn [attrs _]
                                       [:h1 (-> attrs ::hiccup/alias-data :greeting)])}
                           :alias-data {:greeting "Hello"}})
           [:h1 {} "Hello"])))

  (testing "Moves the key to the element the alias returns"
    (is (= (hiccup/expand [:ui/button {:key "save"} "Save"]
                          {:aliases {:ui/button render-button}})
           [:button.btn {:key "save"} "Save"])))

  (testing "Adds classes and id from the alias tag"
    (is (= (hiccup/expand [:ui/button.primary.large#save {:class "x"} "Save"]
                          {:aliases {:ui/button (fn [attrs children]
                                                  (into [:button (select-keys attrs [:class :id])] children))}})
           [:button {:class ["primary" "large" "x"] :id "save"} "Save"])))

  (testing "Leaves nil children out"
    (is (= (hiccup/expand [:ui/button nil "Save" (when false "!")]
                          {:aliases {:ui/button render-button}})
           [:button.btn {} "Save"])))

  (testing "Puts the key on components returned by aliases"
    (let [component (fn [_])
          result (hiccup/expand [:ui/map {:key "m"}]
                                {:aliases {:ui/map (fn [attrs _] [component attrs])}})]
      (is (= "m" (:key (meta result))))))

  (testing "expand-1 only expands one level"
    (is (= (hiccup/expand-1 [:div [:ui/panel "Save"]]
                            {:aliases {:ui/button render-button
                                       :ui/panel (fn [_ children]
                                                   [:div.panel (into [:ui/button] children)])}})
           [:div {} [:div.panel [:ui/button "Save"]]])))

  (testing "expand keeps event handler data"
    (is (= (hiccup/expand [:button {:on {:click [:save]}}])
           [:button {:on {:click [:save]}}])))

  (testing "Marks unknown aliases"
    (is (= (hiccup/expand [:ui/nope "?"])
           [:div {:data-unknown-alias ":ui/nope"}]))))

(deftest interpolate-test
  (testing "Replaces registered placeholders"
    (hiccup/register-placeholder! :test/answer (fn [e] (:answer e)))
    (is (= (hiccup/interpolate {:answer 42} [[:store/assoc-in [:x] :test/answer]])
           [[:store/assoc-in [:x] 42]]))))
