# Data-driven Reagent

This guide explains the ideas behind all the tutorials in this repository,
and the small piece of code that makes them work in Reagent: the
`datadriven.hiccup` namespace
([`lib/src/datadriven/hiccup.cljc`](../lib/src/datadriven/hiccup.cljc)).

It covers the same ground as Replicant's guides on
[top-down rendering](https://replicant.fun/top-down/),
[hiccup](https://replicant.fun/hiccup/),
[event handlers](https://replicant.fun/event-handlers/),
[aliases](https://replicant.fun/alias/),
[life-cycle hooks](https://replicant.fun/life-cycle-hooks/) and
[keys](https://replicant.fun/keys/), from the point of view of someone using
Reagent and re-frame.

- [Hiccup](#hiccup)
- [Top-down rendering with re-frame](#top-down-rendering-with-re-frame)
- [Event handlers as data](#event-handlers-as-data)
- [Placeholders](#placeholders)
- [Aliases](#aliases)
- [Life-cycle hooks](#life-cycle-hooks)
- [Keys](#keys)
- [From Replicant to re-frame](#from-replicant-to-re-frame)

## Hiccup

Hiccup writes HTML as Clojure data. A vector is an element: the first item is
the tag, an optional map of attributes comes next, and everything after that
is children:

```clojure
[:a.button#save {:href "/save" :class ["primary" "large"]}
 "Save "
 [:strong "now"]]
;; <a id="save" class="button primary large" href="/save">Save <strong>now</strong></a>
```

- `.class` and `#id` can be written in the tag, and combined with `:class`,
  which can be a string or a collection.
- `:style` takes a map: `{:style {:margin-top 20}}`.
- `nil` children render nothing, so `(when logged-in? [:p "Hi"])` is fine.
- Lists of children, such as the result of `for`, are allowed anywhere a child
  is.

Reagent and Replicant agree on all of the above. They differ in a few places,
and `datadriven.hiccup/prepare` bridges most of them:

| | Replicant | Reagent | With `prepare` |
|---|---|---|---|
| Event handlers | `{:on {:click f-or-data}}` | `{:on-click f}` | `{:on {:click f-or-data}}` |
| Raw HTML | `{:innerHTML "<b>"}` | `{:dangerouslySetInnerHTML {:__html "<b>"}}` | either |
| Namespaced attributes | ignored (reserved for aliases) | passed to React, which warns | removed |
| Aliases | `[:ui/button ...]` | not supported | supported |
| Keys | `:replicant/key` | `:key` | `:key` |
| Components | none | `[my-fn args]`, `[:> JsComponent props]` | left alone |

Reagent's `[my-fn args]` syntax creates a Reagent *component*: a function that
React calls on its own schedule, and that can have local state. We rarely use
it in these tutorials. The [next section](#top-down-rendering-with-re-frame)
explains why.

## Top-down rendering with re-frame

React's big idea was `UI = f(state)`: collect all your state in one place, and
describe the UI as a function of it. React also gave components their own
local state, network calls and effects. That breaks the idea, because the UI is
no longer a function of one value. Now it's a tree of objects, each with its
own state, talking to each other.

Replicant keeps only the first part. The whole UI is one pure function of the
application state, and it runs again from the top whenever the state changes.
The functions return plain data (hiccup), and the library works out which DOM
changes are needed. Views never keep state or talk to the network, so all of
that has to live somewhere else. That's the point: when all state and all
side effects live outside the views, you can handle them *once*, well. You get
one place for caching, one for error handling, one representation of state you
can inspect, save and restore.

re-frame is built around the same idea. It has one place for state (app-db),
one way to change it (events) and one way to perform side effects (effects).
Where it differs from Replicant is that views can subscribe to data and
dispatch events from anywhere in the component tree. The tutorials in this
repository add one rule that brings the two closer together:

> Views are plain functions that take data and return hiccup. They don't
> subscribe, don't dispatch and don't keep local state.

In practice, an app has exactly one Reagent component, at the top:

```clojure
(rf/reg-sub :app/state
  (fn [db _] db))

(defn app []
  (hiccup/prepare (ui/render-page @(rf/subscribe [:app/state]))))

(defonce root (delay (rdc/create-root (js/document.getElementById "app"))))

(defn render []
  (rdc/render @root [app]))
```

Everything under `app` is a pure function call. When app-db changes, `app`
runs again, and React applies the difference to the DOM.

### What you gain

- **Every view is testable with `=`.** Give a view function some data and
  compare the hiccup it returns. The same goes for the function that turns
  domain data into UI data, which is where most of the interesting UI logic
  ends up.
- **Views can live in `.cljc` files.** They don't depend on React, so they run
  on the JVM too: in tests, in a REPL, or for rendering HTML on a server.
- **There is only one way data flows.** Any screen can be reproduced from a
  single value: app-db.
- **Views show everything they do.** Because event handlers are data (next
  section), a view's output shows exactly what will happen on each click.

### What about performance?

Re-rendering everything on every change sounds wasteful, but for most apps it
isn't a problem. The expensive part of a UI is touching the DOM, and React
still touches only what changed. The work that remains is building hiccup and
comparing React elements, which is fast for the size of UI a person can look
at. The author of Replicant reports needing to optimize rendering only a
handful of times in a decade of building apps this way.

re-frame helps further. A subscription is only recomputed when its inputs
change, so an expensive "domain data → UI data" function written as a
subscription runs only when it has to:

```clojure
(rf/reg-sub :game-ui
  :<- [:game]
  (fn [game _]
    (ui/game->ui-data game)))
```

If you do find a slow spot, you can keep the view pure and move the
subscription down: render a small Reagent component (`[my-list-component]`)
that subscribes to just the data it needs and calls pure functions
underneath. The re-frame documentation recommends narrow subscriptions like
this by default. These tutorials take the opposite default (one subscription
at the top) because it keeps everything else simple, and they leave narrowing
as an optimization for when you need it.

### Adopting this in an existing re-frame app

You don't need to rewrite anything. The ideas apply to one screen or one
feature at a time:

- Write the part of the UI you're working on as pure functions of data, and
  call them from a single component that subscribes.
- Move the logic of turning domain data into UI data into functions or
  subscriptions, and test those.
- Replace `#(rf/dispatch [...])` in views with event data plus `prepare`.

## Event handlers as data

In Reagent, an event handler is a function:

```clojure
[:button {:on-click #(rf/dispatch [:counter/inc])} "+1"]
```

Here, it's data:

```clojure
[:button {:on {:click [:counter/inc]}} "+1"]
```

The second version is easier to test (a function is only ever `=` to itself),
easier to read in a REPL or in Dataspex, and keeps re-frame out of the view
code entirely. The function that renders it is pure, and so is the function
that built its data.

`hiccup/prepare` walks the hiccup right before Reagent sees it, and replaces
each `:on` map with the `:on-*` attributes React expects:

```clojure
(hiccup/prepare [:button {:on {:click [:counter/inc]}} "+1"])
;; => [:button {:on-click (fn [e] ...)} "+1"]
```

What the handler does when it's called:

- A **single action**, like `[:counter/inc]`, is dispatched to re-frame.
- A **vector of actions**, like `[[:form/reset :login] [:page/goto :home]]`, is
  dispatched one action at a time, in order. `nil` actions are skipped, which
  makes `(when ...)` handy inside the vector.
- Two actions run on the spot instead: `[:event/prevent-default]` and
  `[:event/stop-propagation]`. They need the live DOM event, and a re-frame
  dispatch happens too late for that. (`dispatch` queues the event and handles
  it shortly after, by which time the browser has already done its default
  action.)
- A **function** is used as is. This is how Portfolio scenes and other
  one-off code can still pass plain functions.

Event names are the DOM's (`:click`, `:input`, `:submit`, `:keydown`,
`:mouseenter`...). `prepare` knows how React spells the multi-word ones
(`:keydown` → `:on-key-down`).

### Actions are re-frame events

Every action that isn't `:event/prevent-default` or `:event/stop-propagation`
is a re-frame event, so you give it meaning with `reg-event-db` or
`reg-event-fx`:

```clojure
(rf/reg-event-db :counter/inc
  (fn [db _]
    (update db :count inc)))
```

A handful of generic events go a long way. The
[state management tutorial](../tutorials/state-atom.md) builds this one, which
covers most everyday needs:

```clojure
(rf/reg-event-db :store/assoc-in
  (fn [db [_ path value]]
    (assoc-in db path value)))
```

```clojure
[:button {:on {:click [:store/assoc-in [:menu :open?] true]}} "Menu"]
```

Replicant's tutorials use [Nexus](https://github.com/cjohansen/nexus) to run
actions. Nexus separates *actions* (pure functions from state to more actions)
from *effects* (the functions that actually change things). re-frame has the
same split: an event handler registered with `reg-event-fx` is a pure function
from the current state to a description of effects, and `reg-fx` registers
the code that carries out an effect.

## Placeholders

Some values only exist when the event happens: the text in an input, whether
a checkbox is checked, the current time. The view can't know them in advance,
so it writes a *placeholder* keyword where the value should go:

```clojure
[:input {:value (:query state)
         :on {:input [:store/assoc-in [:query] :event/target.value]}}]
```

When the event fires, `prepare`'s handler replaces every placeholder in the
actions with its value, and dispatches the result:
`[:store/assoc-in [:query] "hello"]`. The re-frame event gets plain data
and never sees the DOM event.

Built-in placeholders:

| Placeholder | Value |
|---|---|
| `:event/target.value` | `(.. event -target -value)` |
| `:event/target.checked` | `(.. event -target -checked)` |
| `:clock/now` | `(js/Date.)` |

Register your own with `register-placeholder!`. The function receives the DOM
event:

```clojure
(hiccup/register-placeholder! :event/form-data
  (fn [e]
    (gather-form-data (.-target e))))
```

The [forms tutorials](../tutorials/forms.md) use exactly this to read a
whole form in one go.

## Aliases

An alias is a custom hiccup tag: a namespaced keyword that stands for a
function that returns hiccup.

```clojure
(defn render-button [attrs children]
  (into [:button.btn
         {:class (when (= :large (:ui/size attrs)) "btn-lg")
          :on (:on attrs)}]
        children))

(hiccup/register-alias! :ui/button render-button)
```

```clojure
[:ui/button {:ui/size :large :on {:click [:save]}} "Save"]
;; after prepare:
[:button.btn {:class "btn-lg" :on-click (fn ...)} "Save"]
```

`prepare` (and `expand`, which only expands aliases) replaces any element
whose tag is a namespaced keyword with the result of calling its function with
the attribute map and the children. The result is itself prepared, so aliases
can use other aliases. An unknown alias renders as an empty
`[:div {:data-unknown-alias ":ui/nope"}]` and logs a warning.

### Why not just call the function?

`(render-button {...} ["Save"])` gives the same result. An alias differs in
three ways:

- **It's still data.** `[:ui/button ...]` stays readable in tests and at the
  REPL. `expand` turns it into full hiccup only when you want that, for
  example to assert on the final markup.
- **It's late bound.** The function is looked up when the hiccup is prepared,
  not when it's written. Views can use `:ui/map` or `:i18n/text` without
  depending on the namespace that implements them, and that namespace can be
  different in the browser and on the server (see the
  [server-side alias tutorial](../tutorials/server-alias.md)).
- **It can get context.** Pass `:alias-data` to `prepare`, and every alias
  finds it under `::hiccup/alias-data` in its attributes. The routing
  tutorials use this to give the `:ui/a` link alias the route table, so views
  can write `[:ui/a {:ui/location {...}} "Home"]` without knowing about routes.

```clojure
(hiccup/prepare (ui/render-page state) {:alias-data {:routes router/routes}})
```

### Attributes versus parameters

By convention, namespaced attributes (`:ui/size`) are parameters for the alias,
and plain attributes (`:class`, `:on`, `:aria-label`) are HTML attributes the
alias can pass along. `prepare` removes namespaced attributes before Reagent
sees them. An alias can therefore pass all of its attributes to its root
element, and the caller can add any HTML attribute without the alias
explicitly supporting it. The [Tic-Tac-Toe with aliases
tutorial](../tutorials/tic-tac-toe-alias.md) shows this in detail.

### Aliases are not components

A Reagent component (`[my-component props]`) is created and kept alive by
React. It can hold local state and re-render on its own. An alias is only a
function call that happens during `prepare`. It has no state, no life cycle,
and nothing to keep track of. If you need state, it belongs in app-db. If you
need a life cycle, see the next section.

Replicant defines aliases with a `defalias` macro, which also creates a var
you can refer to. Here we use `register-alias!` with a namespaced keyword. The
`::` shorthand saves typing: in a namespace that requires
`[my-app.ui :as ui]`, `::ui/button` means `:my-app.ui/button`.

## Life-cycle hooks

Pure views have one limit: some things need a real DOM node. A map, a chart or
a rich text editor from a JavaScript library needs an element to mount into,
and needs to hear about updates and removal.

Replicant handles this with hook attributes (`:replicant/on-mount`,
`:replicant/on-render`, `:replicant/on-unmount`). React's version of the idea
is the component life cycle, and Reagent exposes it with `r/create-class`:

```clojure
(defn chart [_props]
  (let [!el (atom nil)] ;; will hold the DOM node
    (r/create-class
     {:component-did-mount (fn [this] (init-chart! @!el (r/props this)))
      :component-did-update (fn [this _] (update-chart! @!el (r/props this)))
      :component-will-unmount (fn [_] (destroy-chart! @!el))
      :reagent-render (fn [_props] [:div.chart {:ref #(reset! !el %)}])})))
```

The outer function runs once per chart on the page, so each chart gets its
own `!el`. The `:ref` callback receives the DOM node when React creates it.
(Older Reagent code often uses `reagent.dom/dom-node` for this. React 19
removed the API it was built on.)

This is the one place where the tutorials use a real Reagent component. Use it
as `[chart {:data ...}]` inside otherwise pure hiccup. `prepare` leaves
function-headed vectors alone, and the component's props are plain data. Put
the component behind an alias (`[:ui/chart {...}]`) and views stay data all the
way down. The [JavaScript interop tutorial](../tutorials/javascript-interop.md)
builds one of these for a Mapbox map, step by step.

For simpler cases, a `:ref` callback is enough. React calls it with the DOM
node when the element is added, and with `nil` when it's removed:

```clojure
[:input {:ref (fn [el] (when el (.focus el)))}]
```

## Keys

When the same kind of element appears several times in a row, React matches
old and new elements by position. Usually that's what you want. When items
are reordered, though, React ends up rewriting the content of every element
instead of moving them. A `:key` tells React which item is which:

```clojure
[:ul
 (for [fruit fruits]
   [:li {:key fruit} fruit])]
```

React normally asks for a key on every item in a list, and warns in the
console when one is missing, even when position is fine. `prepare` avoids the
warning by splicing lists into their parent element. `[:ul (for ...)]`
becomes `[:ul [:li ...] [:li ...]]`. Keys you do add still work.

Add keys when items in a list can move, and the elements have state the
browser keeps track of: form fields (focus, cursor position), elements with
animations, or Reagent components with life cycles. Otherwise you don't need
them. Replicant spells the attribute `:replicant/key`. Here it's `:key`.

## From Replicant to re-frame

A cheat sheet for reading the original tutorials side by side with these:

| Replicant (+ Nexus) | Reagent + re-frame (+ `datadriven.hiccup`) |
|---|---|
| A store atom | re-frame's `app-db` |
| `(add-watch store ::render (fn [...] (r/render el (render state))))` | One root component: `(defn app [] (hiccup/prepare (render @(rf/subscribe [:app/state]))))` |
| `(swap! store ...)` from event handlers | An event handler (`reg-event-db`) returns the new state |
| `r/set-dispatch!` | Built into `prepare`: actions become `rf/dispatch` |
| Nexus action: state → actions | `reg-event-fx`: state → effects (`{:db ... :fx [[:dispatch ...]]}`) |
| Nexus effect | `reg-fx` |
| Nexus placeholder | `hiccup/register-placeholder!` |
| Batched `swap!`s (`^:nexus/batch`) | Built in: each event updates app-db once, and Reagent batches rendering |
| `defalias` / `replicant.alias/register!` | `hiccup/register-alias!` |
| `:replicant/alias-data` | `::hiccup/alias-data` (pass `:alias-data` to `prepare`) |
| `:replicant/key` | `:key` |
| `:replicant/on-mount` and friends | `r/create-class` or `:ref` |
| `:replicant/mounting` | CSS `@starting-style` |
| `replicant.string/render` | `hiccup/expand` + an HTML renderer (see the server-side alias tutorial) |
| `portfolio.replicant` | `portfolio.reagent-18` with `(pr/set-decorator! hiccup/prepare)` |

## The full source

The whole of `datadriven.hiccup` is about 200 lines, with tests in
[`lib/test`](../lib/test/datadriven/hiccup_test.cljc). It's meant to be copied
into your project and changed as you see fit. Every tutorial project has its
own copy.
