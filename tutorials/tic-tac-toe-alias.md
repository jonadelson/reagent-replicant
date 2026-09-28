# Tic-Tac-Toe with aliases

> Adapted for Reagent + re-frame from [Tic-Tac-Toe with aliases](https://replicant.fun/tutorials/tic-tac-toe-alias/)
> by Christian Johansen. The code for this tutorial is in [`code/tic-tac-toe-alias`](../code/tic-tac-toe-alias/).

This tutorial revisits the game from the [Tic-Tac-Toe tutorial](./tic-tac-toe.md)
and rewrites its UI with *aliases*. On the way, we'll look at what aliases are
good for, how they change the way you test a UI, and where they make it harder
to keep your code tidy.

A quick recap, in case you haven't read the first tutorial. The game is built
with [Reagent](https://reagent-project.github.io/) (which renders
[hiccup](../guides/data-driven-reagent.md#hiccup), HTML written as Clojure
vectors, using React) and [re-frame](https://day8.github.io/re-frame/) (which
keeps all application state in one map, *app-db*, and changes it only through
*events*). Views are plain functions from data to hiccup. Their event handlers
are data too: `{:on {:click [:tic 0 2]}}` means "dispatch the re-frame event
`[:tic 0 2]` when this is clicked". A small function,
`datadriven.hiccup/prepare`, turns that data into real functions right before
Reagent renders. The [guide](../guides/data-driven-reagent.md) explains the
approach in more depth.

## What's an alias?

An alias is a hiccup tag you define yourself. You pick a namespaced keyword,
like `:tic-tac-toe.ui/cell`, and register a function for it. When `prepare`
finds an element with that tag, it calls your function with the element's
attribute map and its children, and puts the hiccup your function returns in
its place:

```clojure
(hiccup/register-alias! :tic-tac-toe.ui/cell render-cell)

[:tic-tac-toe.ui/cell {:class :clickable} "X"]
;; prepare calls (render-cell {:class :clickable} ["X"])
```

Typing the full namespace gets old quickly, and Clojure has a shorthand for
it. Inside the `tic-tac-toe.ui` namespace, `::cell` (two colons) means
`:tic-tac-toe.ui/cell`. In a namespace that requires
`[tic-tac-toe.ui :as ui]`, `::ui/cell` means the same thing. So the aliases
we define will look like `[::cell ...]` where they're defined and like
`[::ui/cell ...]` everywhere else.

Replicant, the library the original tutorial uses, has aliases built in. Its
`defalias` macro registers the function *and* defines a var holding the
keyword, so you can also write `[ui/cell ...]`. We'll use keywords
throughout. The [guide](../guides/data-driven-reagent.md#aliases) has more on
aliases in general.

## Getting started

Copy the finished game from [`code/tic-tac-toe`](../code/tic-tac-toe/) to a
new directory, and start it:

```sh
npm install
npx shadow-cljs watch app portfolio
```

The game is at http://localhost:8080/, the UI elements in Portfolio (a
workshop that shows UI elements in isolation) at
http://localhost:8080/portfolio.html, and the tests run with
`clojure -M:dev -m kaocha.runner`.

### Upgrading `datadriven.hiccup`

The first tutorial wrote a minimal `datadriven.hiccup` that only turns `:on`
data into functions. It knows nothing about aliases. Replace
`src/datadriven/hiccup.cljc` with the full version from
[`lib/src/datadriven/hiccup.cljc`](../lib/src/datadriven/hiccup.cljc). The
public function, `prepare`, is used the same way, but it now also:

- **Expands aliases.** `register-alias!` registers an alias globally, and
  `prepare` also takes an `:aliases` option for passing them in directly.
  `expand` expands aliases and leaves everything else alone, which is handy in
  tests. `:alias-data` hands extra data to every alias (the
  [i18n tutorial](./i18n-alias.md) uses it).
- **Drops namespaced attributes**, like `:tic-tac-toe.ui/dim?`, before
  Reagent sees them. We'll see why that matters in a moment.
- **Runs several actions per event**, as in
  `{:on {:click [[:event/prevent-default] [:save]]}}`, and fills in
  *placeholders* such as `:event/target.value` from the DOM event.
- **Uses React's names for multi-word events**, so `{:on {:dblclick ...}}`
  becomes `:on-double-click`.

The [guide](../guides/data-driven-reagent.md#aliases) walks through all of
it. Nothing in the game uses these features yet, so it works exactly as
before.

## Reusable UI elements with aliases

We'll start by turning the UI elements into aliases, beginning with the cell.
In `src/tic_tac_toe/ui.cljc` it looks like this:

```clojure
;; src/tic_tac_toe/ui.cljc
(defn render-cell [{:keys [content on-click dim? highlight? clickable?]}]
  [:button.cell
   {:on {:click on-click}
    :class (cond-> []
             dim? (conj "cell-dim")
             highlight? (conj "cell-highlight")
             clickable? (conj "clickable"))}
   (when content
     [:div.cell-content
      content])])
```

The quickest way to use it as an alias is to hand it to `prepare`:

```clojure
(hiccup/prepare hiccup {:aliases {:ui/cell render-cell}})
```

An alias function gets the attribute map as its first argument, which is
exactly the map `render-cell` expects. It also always gets the children as a
second argument, so `render-cell` would need a second parameter it ignores.
With that, `[:ui/cell {:clickable? true :on-click [:tic 0 0]}]` would work.

But we can make a better building block with a couple of changes. The mark in
the cell can be the alias's *children*, just like the text in a `[:button
"Save"]`. And if the alias's own options get namespaced keys, the alias can
hand the whole attribute map on to the `:button`:

```clojure
;; src/tic_tac_toe/ui.cljc
(ns tic-tac-toe.ui
  (:require [datadriven.hiccup :as hiccup]))

,,,

(defn render-cell [{::keys [on-click dim? highlight? clickable?] :as attrs} content]
  [:button.cell
   (cond-> attrs
     on-click (assoc-in [:on :click] on-click)
     dim? (update :class conj "cell-dim")
     highlight? (update :class conj "cell-highlight")
     clickable? (update :class conj "clickable"))
   (when (seq content)
     (into [:div.cell-content] content))])

(hiccup/register-alias! ::cell render-cell)
```

(`,,,` stands for code left out of a listing. Clojure treats commas as
whitespace, so it's a common way to write "and so on".)

`{::keys [dim?]}` destructures the namespaced key `:tic-tac-toe.ui/dim?`,
which callers write as `::ui/dim?`. Passing `attrs` straight to the button
would be a bad idea if those keys ended up in the DOM. They don't: `prepare`
removes every namespaced attribute before Reagent sees it, just like Replicant
ignores them. That gives us a clean rule. Namespaced attributes are
parameters for the alias. Everything else is an HTML attribute the alias
passes along.

This is what makes aliases compose well. The cell doesn't need to know about
every attribute a caller might want: a caller can add an id, a data attribute,
an `aria-label` or another class, and it ends up on the button:

```clojure
[::ui/cell {::ui/clickable? true
            :class ["my-class"]
            :data-cell-id "f6c"}
 ui/mark-x]
```

Two differences from Replicant show up here. Replicant lets you put classes
in an alias's tag, as in `[::ui/cell.my-class ...]`. Our adapter looks up the
tag as it is written, so pass classes in `:class` instead. And Replicant
always gives an alias its `:class` as a collection. Our adapter passes
attributes on unchanged, so `(update :class conj ...)` above only works if
the caller gives `:class` as a collection, or leaves it out.

Now look at what the options do. Each one maps to exactly one attribute:
`::dim?` means "add the class `cell-dim`", `::on-click` means "set
`[:on :click]`". Nothing is gained by the translation, so let's drop it and
let callers set the class and the click handler themselves:

```clojure
;; src/tic_tac_toe/ui.cljc
(defn render-cell
  "Renders a cell on the board. The children are the cell's content (a mark).
  Any attribute is passed on to the button. Suggested classes:

   - `cell-dim`
   - `cell-highlight`
   - `clickable`"
  [attrs content]
  [:button.cell attrs
   (when (seq content)
     (into [:div.cell-content] content))])

(hiccup/register-alias! ::cell render-cell)
```

The implementation is now tiny and more flexible than before. The price is
that it's harder to tell how the cell is supposed to be used. The docstring
lists the classes, and Portfolio scenes can show each state. Here are the
updated scenes:

```clojure
;; portfolio/tic_tac_toe/scenes.cljs
(ns tic-tac-toe.scenes
  (:require [datadriven.hiccup :as hiccup]
            [portfolio.reagent-18 :as pr :refer-macros [defscene]]
            [portfolio.ui :as portfolio]
            [tic-tac-toe.ui :as ui]))

;; Run every scene through hiccup/prepare before Reagent renders it. This
;; also expands the aliases.
(pr/set-decorator! hiccup/prepare)

(defscene empty-cell
  [::ui/cell {:class :clickable}])

(defscene cell-with-x
  [::ui/cell ui/mark-x])

(defscene cell-with-o
  [::ui/cell ui/mark-o])

(defscene interactive-cell
  "Click the cell to toggle the tic on/off"
  :params (atom nil)
  [store]
  [::ui/cell
   {:class :clickable
    :on {:click (fn [_]
                  (swap! store #(if % nil ui/mark-x)))}}
   @store])

(defscene dimmed-cell
  [::ui/cell {:class :cell-dim}
   ui/mark-o])

(defscene highlighted-cell
  [::ui/cell {:class :cell-highlight}
   ui/mark-o])
```

Scenes are now hiccup, not function calls. Reagent accepts keywords as class
names, so `{:class :clickable}` works as well as `{:class "clickable"}`.

### The board

The board is currently a function that loops over rows of cell data:

```clojure
;; src/tic_tac_toe/ui.cljc
(defn render-board [{:keys [rows]}]
  [:div.board
   (for [row rows]
     [:div.row
      (for [cell row]
        (render-cell cell))])])
```

This doesn't carry over directly. The cell now wants attributes and content
separately, so the cell maps in `rows` no longer fit. More importantly, the
board function barely does anything: it's two loops and two class names.
There's little to gain from making it an alias as it stands, so let's leave
it for now. The board scenes in Portfolio can be written as plain hiccup:

```clojure
;; portfolio/tic_tac_toe/scenes.cljs
(defscene empty-board
  [:div.board
   [:div.row [::ui/cell] [::ui/cell] [::ui/cell]]
   [:div.row [::ui/cell] [::ui/cell] [::ui/cell]]
   [:div.row [::ui/cell] [::ui/cell] [::ui/cell]]])

(defscene partial-board
  [:div.board
   [:div.row [::ui/cell ui/mark-o] [::ui/cell] [::ui/cell]]
   [:div.row [::ui/cell ui/mark-x] [::ui/cell ui/mark-o] [::ui/cell]]
   [:div.row [::ui/cell] [::ui/cell] [::ui/cell]]])

(defscene winning-board
  [:div.board
   [:div.row
    [::ui/cell {:class :cell-dim}]
    [::ui/cell {:class :cell-highlight} ui/mark-o]
    [::ui/cell {:class :cell-dim}]]
   [:div.row
    [::ui/cell {:class :cell-dim} ui/mark-x]
    [::ui/cell {:class :cell-highlight} ui/mark-o]
    [::ui/cell {:class :cell-dim}]]
   [:div.row
    [::ui/cell {:class :cell-dim}]
    [::ui/cell {:class :cell-highlight} ui/mark-o]
    [::ui/cell {:class :cell-dim} ui/mark-x]]])
```

## Converting business domain data to UI data

In the first tutorial, `game->ui-data` translated the game (the business
domain) into generic UI data, and `render-game` drew that UI data:

```clojure
;; src/tic_tac_toe/ui.cljc
(defn game->ui-data [{:keys [size tics victory over?]}]
  (let [highlight? (set (:path victory))]
    {:button (when over?
               {:text "Start over"
                :on-click [:reset]})
     :board
     {:rows
      (for [y (range size)]
        (for [x (range size)]
          (if-let [player (get tics [y x])]
            (let [victorious? (highlight? [y x])]
              (cond-> {:content (player->mark player)}
                victorious? (assoc :highlight? true)
                (and over? (not victorious?)) (assoc :dim? true)))
            (if over?
              {:dim? true}
              {:clickable? true
               :on-click [:tic y x]}))))}}))

(defn render-game [{:keys [board button]}]
  [:div
   (render-board board)
   (when button
     [:button {:on {:click (:on-click button)}
               :style {:margin-top 20
                       :font-size 20}}
      (:text button)])])
```

Now that the cell alias takes classes directly, there's no need to produce
`{:dim? true}` and turn it into a class later. `game->ui-data` can produce
the hiccup right away, and the board alias we skipped earlier suddenly has a
job: it turns a game into a board. The conversion step and the rendering
step collapse into one. Replace `render-board`, `game->ui-data` and
`render-game` with:

```clojure
;; src/tic_tac_toe/ui.cljc
(def player->mark
  {:x mark-x
   :o mark-o})

(defn render-board [{:keys [size tics victory over?]} _children]
  (let [highlight? (set (:path victory))]
    [:div.board
     (for [y (range size)]
       [:div.row
        (for [x (range size)]
          (if-let [player (get tics [y x])]
            (let [victorious? (highlight? [y x])]
              [::cell {:class (cond-> []
                                victorious? (conj :cell-highlight)
                                (and over? (not victorious?)) (conj :cell-dim))}
               (player->mark player)])
            (if over?
              [::cell {:class :cell-dim}]
              [::cell {:class :clickable
                       :on {:click [:tic y x]}}])))])]))

(hiccup/register-alias! ::board render-board)

(defn render-button [attrs children]
  (into [:button
         (assoc attrs :style {:margin-top 20
                              :font-size 20})]
        children))

(hiccup/register-alias! ::button render-button)

(defn render-game [game]
  [:div
   [::board game]
   (when (:over? game)
     [::button {:on {:click [:reset]}}
      "Start over"])])
```

The board alias takes the whole game as its "attributes". Its keys aren't
namespaced, but that's fine: the board never passes its attributes on to an
element, so they can't end up in the DOM. The alias doesn't use its
children, so the second parameter is named `_children` to say so. (Replicant's
`defalias` allows leaving it out; here, alias functions are always called with
two arguments.)

Finally, `core.cljs` no longer needs the `:game-ui` subscription, which ran
`game->ui-data`. The `app` component subscribes to the game and renders it:

```clojure
;; src/tic_tac_toe/core.cljs
(rf/reg-sub :game
  (fn [db _]
    db))

(defn app []
  (-> @(rf/subscribe [:game])
      ui/render-game
      hiccup/prepare))
```

The game looks and plays exactly as before.

## What's the point?

Hold on. The first tutorial made a point of keeping the game and the generic
UI elements apart, with `game->ui-data` in between, so we could test the UI
logic without caring about markup. We just threw that function away.

What makes this acceptable is that the cell alias raised the level of the
hiccup. `[::cell {:class :cell-dim}]` says "a dimmed cell", not "a button
with these classes containing a div with an SVG". Hiccup at that level is
fine to test against. Let's look at the tests.

### Testing with aliases

The old tests checked that `game->ui-data` produced the right data for the
board. Now we'll check the rendered board instead. Here's what `render-game`
returns for a game in progress:

```clojure
(ui/render-game
 {:size 3
  :tics {[0 0] :x
         [0 1] :o}
  :next-player :x})

;;=>
[:div
 [:tic-tac-toe.ui/board
  {:size 3, :tics {[0 0] :x, [0 1] :o}, :next-player :x}]
 nil]
```

Not much to test there: a board, and no button. To see the cells, the board
alias has to be expanded. `hiccup/expand` expands aliases without doing
anything else (the `:on` data stays data). But it expands *all* of them,
including the cells, and then we're back to buttons, divs and SVGs:

```clojure
(hiccup/expand (ui/render-game {,,,}))

;;=>
[:div {}
 [:div.board {}
  [:div.row {}
   [:button.cell {:class []} [:div.cell-content {} ui/mark-x]]
   ,,,]]]
```

Replicant has `expand-1` for this, which expands only one level of aliases.
`datadriven.hiccup` doesn't, but `expand` takes the same `:aliases` option as
`prepare`, and aliases passed that way win over registered ones. So in the
test we can swap the cell alias for a stand-in that keeps the cell's
attributes and content, and none of its markup:

```clojure
;; test/tic_tac_toe/ui_test.cljc
(ns tic-tac-toe.ui-test
  (:require [clojure.test :refer [deftest is testing]]
            [datadriven.hiccup :as hiccup]
            [lookup.core :as lookup]
            [tic-tac-toe.game :as game]
            [tic-tac-toe.ui :as ui]))

(defn stub-cell
  "Stands in for the cell alias in tests: keeps the cell's attributes and
  content, but none of its markup."
  [attrs content]
  (into [:cell attrs] content))

(defn expand-game [game]
  (hiccup/expand (ui/render-game game)
                 {:aliases {::ui/cell stub-cell}}))
```

The stand-in uses a plain `:cell` tag. It must not be namespaced, or
`expand` would try to expand it as an alias too. Here's what we get now:

```clojure
(expand-game
 {:size 3
  :tics {[0 0] :x
         [0 1] :o}
  :next-player :x})

;;=>
[:div {}
 [:div.board {}
  [:div.row {}
   [:cell {:class []} ui/mark-x]
   [:cell {:class []} ui/mark-o]
   [:cell {:class :clickable, :on {:click [:tic 0 2]}}]]
  [:div.row {}
   [:cell {:class :clickable, :on {:click [:tic 1 0]}}]
   [:cell {:class :clickable, :on {:click [:tic 1 1]}}]
   [:cell {:class :clickable, :on {:click [:tic 1 2]}}]]
  [:div.row {}
   [:cell {:class :clickable, :on {:click [:tic 2 0]}}]
   [:cell {:class :clickable, :on {:click [:tic 2 1]}}]
   [:cell {:class :clickable, :on {:click [:tic 2 2]}}]]]
 nil]
```

That's the level of detail we want: which cells hold which marks, which are
clickable, and what clicking them does. It would be even better if the test
could zoom in on the board.

[lookup](https://github.com/cjohansen/lookup) finds elements in hiccup with
CSS selectors. Add it to `deps.edn`:

```clojure
;; deps.edn
{:paths ["src" "test" "portfolio" "resources"]
 :deps {,,,
        no.cjohansen/lookup {:mvn/version "2026.07.1"}
        ,,,}}
```

`lookup/select-one` returns the first element matching a selector, and
`lookup/select` returns all of them. Both *normalize* the hiccup they return:
classes in the tag and in `:class` are combined into one set of strings, and
empty attributes are dropped. That makes the expected values in tests
predictable, however the markup happened to write its classes.

Here's the first test:

```clojure
;; test/tic_tac_toe/ui_test.cljc
(deftest render-game-test
  (testing "Renders board"
    (is (= (->> (expand-game
                 {:size 3
                  :tics {[0 0] :x
                         [0 1] :o}
                  :next-player :x})
                (lookup/select-one :div.board))
           [:div {:class #{"board"}}
            [:div {:class #{"row"}}
             [:cell ui/mark-x]
             [:cell ui/mark-o]
             [:cell {:on {:click [:tic 0 2]}, :class #{"clickable"}}]]
            [:div {:class #{"row"}}
             [:cell {:on {:click [:tic 1 0]}, :class #{"clickable"}}]
             [:cell {:on {:click [:tic 1 1]}, :class #{"clickable"}}]
             [:cell {:on {:click [:tic 1 2]}, :class #{"clickable"}}]]
            [:div {:class #{"row"}}
             [:cell {:on {:click [:tic 2 0]}, :class #{"clickable"}}]
             [:cell {:on {:click [:tic 2 1]}, :class #{"clickable"}}]
             [:cell {:on {:click [:tic 2 2]}, :class #{"clickable"}}]]])))
```

This test covers the whole board at once. You don't want many tests like it,
since any change to the board's structure breaks them, but one is useful.

The next test can be more targeted. It plays a game until X wins, and checks
that the winning cells are highlighted:

```clojure
;; test/tic_tac_toe/ui_test.cljc
  (testing "Highlights winning path"
    (is (= (-> (game/create-game {:size 3})
               (game/tic 0 0) ;; x
               (game/tic 1 0) ;; o
               (game/tic 0 1) ;; x
               (game/tic 1 1) ;; o
               (game/tic 0 2) ;; x
               expand-game
               (->> (lookup/select '.cell-highlight)))
           [[:cell {:class #{"cell-highlight"}} ui/mark-x]
            [:cell {:class #{"cell-highlight"}} ui/mark-x]
            [:cell {:class #{"cell-highlight"}} ui/mark-x]])))
```

The expected value says nothing about *where* the three cells are. It doesn't
need to: the previous test already showed that marks end up in the right
places. Three highlighted cells, each with an X, is what this test is about.

The old test also checked that everything else was dimmed. That's clearer as
a test of its own, and counting is enough:

```clojure
;; test/tic_tac_toe/ui_test.cljc
  (testing "Dims everything besides the winning path"
    (is (= (-> (game/create-game {:size 3})
               (game/tic 0 0) ;; x
               (game/tic 1 0) ;; o
               (game/tic 0 1) ;; x
               (game/tic 1 1) ;; o
               (game/tic 0 2) ;; x
               expand-game
               (->> (lookup/select '.cell-dim))
               count)
           6)))
```

The last of the old tests checks that a tied game dims the whole board. It's
the same test with a different game and a different count:

```clojure
;; test/tic_tac_toe/ui_test.cljc
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
               expand-game
               (->> (lookup/select '.cell-dim))
               count)
           9)))
```

The finished [`ui_test.cljc`](../code/tic-tac-toe-alias/test/tic_tac_toe/ui_test.cljc)
also checks the "Start over" button, and tests the cell alias itself with
plain `hiccup/expand`: that it passes its attributes on to the button, and
wraps its content. Run the tests with `clojure -M:dev -m kaocha.runner`.

## Conclusion

The main change is that `game->ui-data` and `render-board` became one
function, the board alias. That merge shows both what aliases are good at and
where they need care: the code got better, and a line that used to be sharp
got blurry.

### The benefits

The code is more direct. Where there were two steps, converting the game to
UI data and rendering that data, there is now one.

With aliases, the hiccup that the game produces is free of passing details
like exact class lists, wrapper divs and inline styles. That let us test the
rendering itself, not a separate data format, without giving up the generic
UI elements. There's one less representation of the UI to keep in sync.

In Replicant, the merge is also a performance win: Replicant only calls an
alias again when its arguments change, so the conversion now happens lazily,
during rendering, and only when needed. Our adapter doesn't do that: `prepare`
expands every alias each time the `app` component renders. Here that's once
per change to the game, which is exactly when the old `:game-ui`
subscription recomputed, so nothing is lost. In a bigger app where other
state changes often, remember that a re-frame subscription caches its result
and an alias doesn't. An expensive conversion may still be better off as a
subscription.

### The drawbacks

We now use aliases for two different jobs: generic UI elements (the cell, the
button), and turning the business domain into UI (the board, which knows
about games and players). Both use the same mechanism and sit side by side in
the same namespace, so nothing marks the difference.

Without a clear boundary, domain logic can creep into the UI elements, and
visual details can pile up in the conversion code. There's no hard rule for
how much is too much. A couple of layout classes like `.board` and `.row` in
the board alias are probably fine. A board full of styling details is not.

Keeping the two apart takes some discipline, and that's harder the more people
work on the code. Some things that help: put the two kinds of aliases in
separate namespaces, and name them so the difference is obvious. Then you can
agree on simple rules, such as "UI elements never mention domain concepts",
and "conversion code never has enough visual detail that you'd want to show it
in Portfolio".

This tension isn't special to aliases. Component-based libraries have the same
problem, and many of them encourage mixing the two. You can decide for
yourself, but keeping them apart tends to give you more control.

## What's different from the Replicant version

- **Defining aliases.** Replicant's `defalias` registers the function and
  defines a var holding the keyword, so you can write `[ui/cell ...]`. Here,
  `hiccup/register-alias!` registers a function under a namespaced keyword,
  and hiccup uses the keyword: `[::ui/cell ...]`.
- **Alias functions always get two arguments**, attributes and children.
  Replicant's `defalias` lets you leave the second one out.
- **No classes in alias tags.** Replicant accepts `[::ui/cell.cell-dim ...]`.
  Here, write `[::ui/cell {:class :cell-dim} ...]`.
- **`:class` isn't normalized.** Replicant always hands an alias its `:class`
  as a collection. Here the alias gets whatever the caller wrote.
- **No `expand-1`.** The tests use `hiccup/expand` with a stand-in for the
  cell alias, passed in `:aliases`, to keep the cells unexpanded.
- **No memoized aliases.** Replicant skips aliases whose arguments haven't
  changed. `prepare` expands all aliases every time the `app` component
  renders, and React still only touches the DOM where something changed.
- **The mark's fade-in** uses CSS `@starting-style` instead of
  `:replicant/mounting`, as in the first tutorial. The original also faded the
  mark out when the game was reset. That's not ported, since exit transitions
  need extra work in React.

Next, [Alias powered i18n](./i18n-alias.md) uses alias data to build a
translation alias, and [A sortable table alias](./sortable-table.md) builds a
set of aliases that work together.
