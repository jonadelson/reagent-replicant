(ns datadriven.hiccup
  "A small bridge that lets you write Reagent views as pure data, in the
  style of Replicant.

  You write hiccup where:

  - Event handlers are data: `{:on {:click [:counter/inc]}}` dispatches the
    re-frame event `[:counter/inc]` when the element is clicked.
  - Placeholders such as `:event/target.value` inside that data are filled
    in from the DOM event before anything is dispatched.
  - Aliases are namespaced keywords: `[:ui/button {...} \"Save\"]` expands to
    whatever hiccup the function registered for `:ui/button` returns.

  Call `prepare` on your hiccup right before handing it to Reagent. Call
  `expand` when you only want the aliases expanded (on the server, or in
  tests)."
  (:require [clojure.walk :as walk]
            [re-frame.core :as rf]))

;;; Placeholders
;;
;; A placeholder is a keyword that stands in for a value that is only known
;; when the event happens, like the text in an input field. The function
;; registered for it receives the DOM event and returns the value.

(defonce placeholders
  (atom
   #?(:cljs {:event/target.value (fn [^js e] (.. e -target -value))
             :event/target.checked (fn [^js e] (.. e -target -checked))
             :clock/now (fn [_] (js/Date.))}
      :clj {})))

(defn register-placeholder! [k f]
  (swap! placeholders assoc k f))

(defn interpolate
  "Replaces every placeholder keyword in `actions` with its value for
  `dom-event`."
  [dom-event actions]
  (let [ps @placeholders]
    (walk/postwalk
     (fn [x]
       (if-let [f (and (keyword? x) (get ps x))]
         (f dom-event)
         x))
     actions)))

;;; Actions
;;
;; An action is a vector like `[:task/complete 42]`. An event handler can be
;; a single action, or a vector of actions to run in order. Two actions are
;; handled right away, because they need the live DOM event. Everything else
;; is plain data and goes to re-frame.

(defn- execute-action! [dom-event [id :as action]]
  (case id
    :event/prevent-default #?(:cljs (.preventDefault dom-event) :clj nil)
    :event/stop-propagation #?(:cljs (.stopPropagation dom-event) :clj nil)
    (rf/dispatch action)))

(defn dispatch-actions
  "Runs the actions for one DOM event: fills in placeholders, then executes
  each action in order."
  [dom-event actions]
  (let [actions (if (keyword? (first actions))
                  [actions] ;; A single action, like [:tic 0 1]
                  actions)]
    (doseq [action (interpolate dom-event (remove nil? actions))]
      (execute-action! dom-event action))))

;;; Aliases

(defonce aliases (atom {}))

(defn register-alias!
  "Registers `f` as the implementation of the alias `k`. `f` is called with
  the attribute map and a (flat) seq of children, and returns hiccup."
  [k f]
  (swap! aliases assoc k f))

;;; Translating attributes for Reagent

;; DOM event names that React spells with extra capital letters.
(def ^:private react-event-props
  {:dblclick :on-double-click
   :keydown :on-key-down
   :keyup :on-key-up
   :keypress :on-key-press
   :mousedown :on-mouse-down
   :mouseup :on-mouse-up
   :mousemove :on-mouse-move
   :mouseenter :on-mouse-enter
   :mouseleave :on-mouse-leave
   :mouseover :on-mouse-over
   :mouseout :on-mouse-out
   :pointerdown :on-pointer-down
   :pointerup :on-pointer-up
   :pointermove :on-pointer-move
   :touchstart :on-touch-start
   :touchend :on-touch-end
   :touchmove :on-touch-move
   :focusin :on-focus
   :focusout :on-blur
   :contextmenu :on-context-menu
   :dragstart :on-drag-start
   :dragend :on-drag-end
   :dragover :on-drag-over
   :dragenter :on-drag-enter
   :dragleave :on-drag-leave
   :transitionend :on-transition-end
   :animationend :on-animation-end})

(defn- event-prop [event]
  (or (react-event-props event)
      (keyword (str "on-" (name event)))))

(defn- event-handler [handler]
  (if (fn? handler)
    handler
    (fn [e] (dispatch-actions e handler))))

(defn- prepare-attrs [attrs]
  (let [{:keys [on innerHTML]} attrs]
    (cond-> (into {} (remove (comp qualified-keyword? key)) (dissoc attrs :on :innerHTML))
      innerHTML (assoc :dangerouslySetInnerHTML {:__html innerHTML})
      on (into (for [[event handler] on
                     :when handler]
                 [(event-prop event) (event-handler handler)])))))

;;; Walking the hiccup

(defn- flatten-children
  "Splices seqs (like the result of `for`) into their parent, so that Reagent
  does not ask for React keys on every list. Items that do have a `:key` keep
  it, and React still uses it."
  [children]
  (mapcat #(if (seq? %) (flatten-children %) [%]) children))

(defn- parse-node [[tag & [attrs & more :as children]]]
  (if (map? attrs)
    [tag attrs more]
    [tag {} children]))

(declare walk-hiccup)

(defn- add-key [hiccup k]
  (if (and k (vector? hiccup) (keyword? (first hiccup)))
    (let [[tag attrs children] (parse-node hiccup)]
      (into [tag (assoc attrs :key k)] children))
    hiccup))

(defn- expand-alias [tag attrs children opts]
  (if-let [f (get (merge @aliases (:aliases opts)) tag)]
    (-> (f (cond-> attrs
             (:alias-data opts) (assoc ::alias-data (:alias-data opts)))
           (flatten-children children))
        (add-key (:key attrs))
        (walk-hiccup opts))
    (do
      #?(:cljs (js/console.warn "Unknown alias" (str tag))
         :clj nil)
      [:div {:data-unknown-alias (str tag)}])))

(defn- walk-hiccup [node opts]
  (cond
    (and (vector? node) (keyword? (first node)))
    (let [[tag attrs children] (parse-node node)]
      (if (qualified-keyword? tag)
        (expand-alias tag attrs children opts)
        (with-meta
          (into [tag (cond-> attrs (:reagent? opts) prepare-attrs)]
                (map #(walk-hiccup % opts))
                (flatten-children children))
          (meta node))))

    (seq? node)
    (map #(walk-hiccup % opts) node)

    :else node))

(defn expand
  "Expands all aliases in `hiccup`, and leaves everything else as it is
  (including event handler data). Useful for tests and server rendering.

  Options:
  - `:aliases`    A map of alias keyword -> function, used in addition to
                  the registered aliases
  - `:alias-data` Extra data made available to every alias under the
                  `:datadriven.hiccup/alias-data` attribute"
  ([hiccup] (expand hiccup nil))
  ([hiccup opts]
   (walk-hiccup hiccup (dissoc opts :reagent?))))

(defn prepare
  "Turns data-driven hiccup into hiccup that Reagent can render: expands
  aliases, turns `:on` event data into functions that dispatch to re-frame,
  and drops namespaced attributes (they are reserved for aliases). Takes the
  same options as `expand`."
  ([hiccup] (prepare hiccup nil))
  ([hiccup opts]
   (walk-hiccup hiccup (assoc opts :reagent? true))))
