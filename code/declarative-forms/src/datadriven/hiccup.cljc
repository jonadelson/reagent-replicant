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

(defn- execute-action! [dom-event [id :as action] {:keys [sync?]}]
  (case id
    :event/prevent-default #?(:cljs (.preventDefault dom-event) :clj nil)
    :event/stop-propagation #?(:cljs (.stopPropagation dom-event) :clj nil)
    (if sync?
      (rf/dispatch-sync action)
      (rf/dispatch action))))

(defn dispatch-actions
  "Runs the actions for one DOM event: fills in placeholders, then executes
  each action in order. With `{:sync? true}`, events are handled right away
  (`rf/dispatch-sync`) instead of being queued."
  ([dom-event actions] (dispatch-actions dom-event actions nil))
  ([dom-event actions opts]
   (let [actions (if (keyword? (first actions))
                   [actions] ;; A single action, like [:tic 0 1]
                   actions)]
     (doseq [action (interpolate dom-event (remove nil? actions))]
       (execute-action! dom-event action opts)))))

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

(defn- tag-name
  "The element name of a hiccup tag: :input.big#name -> \"input\""
  [tag]
  (re-find #"^[^.#]+" (name tag)))

(def ^:private form-fields #{"input" "textarea" "select"})

(defn- event-prop [tag event]
  (or (react-event-props event)
      ;; React's onChange on form fields fires on every keystroke, just like
      ;; the DOM's input event. Reagent needs onChange (not onInput) to keep
      ;; the cursor in place when a field's :value comes from app-db.
      (when (and (= :input event) (form-fields (tag-name tag)))
        :on-change)
      (keyword (str "on-" (name event)))))

;; Typing must update app-db before the browser draws the next frame, or
;; fast typists lose characters. Other events can wait in re-frame's queue.
(def ^:private sync-events #{:input :change})

(defn- event-handler [event handler]
  (if (fn? handler)
    handler
    (let [opts {:sync? (contains? sync-events event)}]
      (fn [e] (dispatch-actions e handler opts)))))

(defn- prepare-attrs [tag attrs]
  (let [{:keys [on innerHTML]} attrs]
    (cond-> (into {} (remove (comp qualified-keyword? key)) (dissoc attrs :on :innerHTML))
      innerHTML (assoc :dangerouslySetInnerHTML {:__html innerHTML})
      on (into (for [[event handler] on
                     :when handler]
                 [(event-prop tag event) (event-handler event handler)])))))

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
  (cond
    (not (and k (vector? hiccup)))
    hiccup

    (keyword? (first hiccup))
    (let [[tag attrs children] (parse-node hiccup)]
      (into [tag (assoc attrs :key k)] children))

    ;; A Reagent component, like [map-component props]
    :else
    (vary-meta hiccup assoc :key k)))

(defn- class-coll [class]
  (cond
    (nil? class) []
    (coll? class) (vec class)
    :else [class]))

(defn- parse-alias-tag
  "Splits :ui/button.primary#save into the alias :ui/button and the classes
  and id to add to its attributes."
  [tag attrs]
  (let [[base & shorthand] (re-seq #"[.#]?[^.#]+" (name tag))
        classes (keep #(when (= \. (first %)) (subs % 1)) shorthand)
        id (some #(when (= \# (first %)) (subs % 1)) shorthand)]
    [(keyword (namespace tag) base)
     (cond-> attrs
       (seq classes) (assoc :class (into (vec classes) (class-coll (:class attrs))))
       (and id (not (:id attrs))) (assoc :id id))]))

(defn- expand-alias [tag attrs children opts]
  (let [[alias attrs] (parse-alias-tag tag attrs)]
    (if-let [f (get (merge @aliases (:aliases opts)) alias)]
      (cond-> (-> (f (cond-> attrs
                       (:alias-data opts) (assoc ::alias-data (:alias-data opts)))
                     (remove nil? (flatten-children children)))
                  (add-key (:key attrs)))
        (not (::once? opts)) (walk-hiccup opts))
      (do
        #?(:cljs (js/console.warn "Unknown alias" (str alias))
           :clj nil)
        [:div {:data-unknown-alias (str alias)}]))))

(defn- walk-hiccup [node opts]
  (cond
    (and (vector? node) (keyword? (first node)))
    (let [[tag attrs children] (parse-node node)]
      (if (qualified-keyword? tag)
        (expand-alias tag attrs children opts)
        (with-meta
          (into [tag (cond->> attrs (:reagent? opts) (prepare-attrs tag))]
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

(defn expand-1
  "Like `expand`, but does not expand aliases in the hiccup that aliases
  return. Useful for testing one alias, or a view that uses aliases, without
  also testing every alias underneath."
  ([hiccup] (expand-1 hiccup nil))
  ([hiccup opts]
   (walk-hiccup hiccup (-> opts (dissoc :reagent?) (assoc ::once? true)))))

(defn prepare
  "Turns data-driven hiccup into hiccup that Reagent can render: expands
  aliases, turns `:on` event data into functions that dispatch to re-frame,
  and drops namespaced attributes (they are reserved for aliases). Takes the
  same options as `expand`."
  ([hiccup] (prepare hiccup nil))
  ([hiccup opts]
   (walk-hiccup hiccup (assoc opts :reagent? true))))
