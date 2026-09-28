# Replicant's tutorials, for Reagent + re-frame

[Replicant](https://replicant.fun) is a ClojureScript rendering library built
on a few strong ideas: the UI is a pure function of the application state,
views are plain data (including event handlers), and all state and side
effects live outside the views. Its author, Christian Johansen, wrote an
excellent set of [tutorials](https://replicant.fun/learn/) that teach these
ideas, and notes that most of them aren't specific to Replicant.

This repository ports those tutorials to **Reagent + re-frame**. It's for
people who like these ideas but work in a Reagent/re-frame codebase they
can't (or don't want to) move away from. Every tutorial has runnable code,
checked to compile without warnings and pass its tests.

No Reagent or re-frame experience is assumed. Each tutorial explains what it
uses as it goes.

## Start here

1. [Tic-Tac-Toe](tutorials/tic-tac-toe.md) builds a game from an empty folder
   and introduces the whole workflow: UI elements in isolation, pure domain
   logic, domain data → UI data, and wiring it together with re-frame.
2. [Data-driven Reagent](guides/data-driven-reagent.md) is a guide to the ideas
   behind all the tutorials, and to the ~200 lines of code that make them work
   in Reagent (`datadriven.hiccup`). It ends with a Replicant → re-frame cheat
   sheet.

## Tutorials

| | Tutorial | Code | Original |
|---|---|---|---|
| **Getting started** | [Tic-Tac-Toe](tutorials/tic-tac-toe.md) | [code](code/tic-tac-toe) | [↗](https://replicant.fun/tutorials/tic-tac-toe/) |
| **Basics** | [Data-driven routing](tutorials/routing.md) | [code](code/routing) | [↗](https://replicant.fun/tutorials/routing/) |
| | [State management with app-db](tutorials/state-atom.md) | [code](code/state-atom) | [↗](https://replicant.fun/tutorials/state-atom/) |
| | [State management with Datascript](tutorials/state-datascript.md) | [code](code/state-datascript) | [↗](https://replicant.fun/tutorials/state-datascript/) |
| **Networking** | [Backend APIs and network](tutorials/network.md) | [code](code/network) | [↗](https://replicant.fun/tutorials/network/) |
| | [Data-driven queries](tutorials/network-reads.md) | [code](code/network-reads) | [↗](https://replicant.fun/tutorials/network-reads/) |
| | [Data-driven commands](tutorials/network-writes.md) | [code](code/network-writes) | [↗](https://replicant.fun/tutorials/network-writes/) |
| **Forms** | [Data-driven form processing](tutorials/forms.md) | [code](code/forms) | [↗](https://replicant.fun/tutorials/forms/) |
| | [Data-driven first class forms](tutorials/first-class-forms.md) | [code](code/first-class-forms) | [↗](https://replicant.fun/tutorials/first-class-forms/) |
| | [Declarative forms](tutorials/declarative-forms.md) | [code](code/declarative-forms) | [↗](https://replicant.fun/tutorials/declarative-forms/) |
| **JavaScript interop** | [Using a JavaScript library](tutorials/javascript-interop.md) | [code](code/javascript-interop) | [↗](https://replicant.fun/tutorials/javascript-interop/) |
| | [Wrapping a library in an alias](tutorials/interop-alias.md) | [code](code/interop-alias) | [↗](https://replicant.fun/tutorials/interop-alias/) |
| | [Server-side JS interop alias](tutorials/server-alias.md) | [code](code/server-alias) | [↗](https://replicant.fun/tutorials/server-alias/) |
| **Aliases** | [Tic-Tac-Toe with aliases](tutorials/tic-tac-toe-alias.md) | [code](code/tic-tac-toe-alias) | [↗](https://replicant.fun/tutorials/tic-tac-toe-alias/) |
| | [Alias powered i18n](tutorials/i18n-alias.md) | [code](code/i18n-alias) | [↗](https://replicant.fun/tutorials/i18n-alias/) |
| | [A sortable table alias](tutorials/sortable-table.md) | [code](code/sortable-table) | [↗](https://replicant.fun/tutorials/sortable-table/) |

Tutorials in the same series build on each other. Where a series starts from
an existing project, that starting point is in `code/<name>-setup`.

## The approach, in one screen

```clojure
;; A view is a pure function from data to hiccup. The click handler is data.
(defn render-counter [{:keys [count]}]
  [:button {:on {:click [:counter/inc]}}
   "Clicked " count " times"])

;; re-frame owns the state and says how events change it.
(rf/reg-event-db :counter/inc
  (fn [db _] (update db :count inc)))

(rf/reg-sub :app/state
  (fn [db _] db))

;; One component at the top renders everything from the state.
;; hiccup/prepare turns the event data into functions that dispatch to re-frame.
(defn app []
  (hiccup/prepare (render-counter @(rf/subscribe [:app/state]))))
```

`render-counter` is a pure function, so you can test it with `=`. re-frame
decides what `[:counter/inc]` means. `datadriven.hiccup` sits in between, and
is small enough to copy into your own project.
[The guide](guides/data-driven-reagent.md) covers the details: placeholders
(`:event/target.value`), multiple actions per event, aliases, life-cycle
hooks, and how to adopt this one screen at a time in an existing re-frame app.

## Repository layout

```
tutorials/   the tutorials
guides/      the concepts guide
code/        runnable code for every tutorial
lib/         datadriven.hiccup, the Reagent adapter, with tests
bin/check    compiles every project and runs every test
```

Every project uses Reagent 2 (React 19), re-frame 1.4 and shadow-cljs. To
run one, you need [Clojure](https://clojure.org/guides/install_clojure)
(which requires Java) and [Node.js](https://nodejs.org/). See the README in
each `code/` directory.

## Credits

The tutorials, their example apps and the ideas are by
[Christian Johansen](https://github.com/cjohansen), from
[replicant.fun](https://replicant.fun/learn/). The example code is adapted
from his MIT-licensed tutorial repositories. The tutorial text here is
rewritten for Reagent and re-frame. If you find these useful, read the
originals too, and consider trying [Replicant](https://github.com/cjohansen/replicant)
itself.
