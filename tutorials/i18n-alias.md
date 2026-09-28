# Alias powered i18n

> Adapted for Reagent + re-frame from [Alias powered i18n](https://replicant.fun/tutorials/i18n-alias/)
> by Christian Johansen. The code for this tutorial is in [`code/i18n-alias`](../code/i18n-alias/).

In this tutorial we add translations (i18n, short for "internationalization")
to our hiccup with an *alias* built on the [m1p](https://github.com/cjohansen/m1p)
library. The result is a new element: `[::i18n/k :page/title]` renders the
page title in the user's current language.

A little background first, in case this is the first tutorial you read. The
app uses [Reagent](https://reagent-project.github.io/), which renders
[hiccup](../guides/data-driven-reagent.md#hiccup) (HTML written as Clojure
vectors, like `[:h1 "Hi"]`) using React, and
[re-frame](https://day8.github.io/re-frame/), which keeps all application
state in one map called *app-db* and changes it only through *events*. Views
are plain functions from data to hiccup, and their event handlers are data:
`{:on {:click [:switch-locale]}}` dispatches the re-frame event
`[:switch-locale]`. A small function, `datadriven.hiccup/prepare`, turns that
data into real functions right before Reagent renders.

`prepare` also expands *aliases*. An alias is a hiccup tag you define
yourself: a namespaced keyword with a function registered for it. When
`prepare` meets `[:my.app/thing attrs & children]`, it calls the function
with the attribute map and the children, and uses the hiccup it returns
instead. The [guide](../guides/data-driven-reagent.md#aliases) explains
aliases in detail, and [Tic-Tac-Toe with aliases](./tic-tac-toe-alias.md)
introduces them step by step.

## m1p at a glance

[m1p](https://github.com/cjohansen/m1p) is a small library for looking up
values in dictionaries and filling in parameters. That's most of what an i18n
library does, so you can use it to build your own. A dictionary is a map
from keywords to any Clojure data. Values can refer to parameters that you
supply when you look them up:

```clojure
(require '[m1p.core :as m1p])

(def dictionary
  (m1p/prepare-dictionary
   {:header/title [:fn/str "Hello, {{:greetee}}!"]}))

(m1p/lookup {} dictionary :header/title {:greetee "Internet"})
;;=> "Hello, Internet!"
```

- `prepare-dictionary` turns the plain map into a form that is quick to look
  things up in. Values that use functions such as `:fn/str` become functions
  of the parameters.
- The last argument to `lookup` holds the parameters. `{{:greetee}}` in the
  string is replaced by the value of `:greetee`.

Dictionary values can be any data, so they can be hiccup too.

## The goal

We want to write lookups directly in hiccup, like this:

```clojure
(defn render-header [{:user/keys [given-name]}]
  [:h1 [::i18n/k :header/greeting {:greetee given-name}]])
```

A reminder about the `::` syntax: in a namespace that requires
`[reagent-i18n.i18n :as i18n]`, `::i18n/k` is short for the keyword
`:reagent-i18n.i18n/k`. Inside `reagent-i18n.i18n` itself, `::k` means the
same.

## Implementing the alias

To follow along, copy [`code/i18n-alias-setup`](../code/i18n-alias-setup/)
to a new directory and start it:

```sh
npm install
npx shadow-cljs watch app
```

and open http://localhost:8080/. The setup is an empty Reagent + re-frame app
that shows "Hello". `deps.edn` includes m1p, and
`src/datadriven/hiccup.cljc` is the full adapter from
[`lib/`](../lib/src/datadriven/hiccup.cljc). `src/reagent_i18n/core.cljs`
has the usual wiring: an `:app/init` event that puts `{:locale :en}` in
app-db, and an `app` component that renders the UI with `prepare`. Also start
a Clojure REPL, from your editor or with `clojure -M:dev` in a terminal. The
namespaces we write are `.cljc` files, which work both in the browser and on
the JVM, so we can try them in the REPL.

Let's start with a single, hard-coded dictionary and check that we can look
things up from hiccup. Create `src/reagent_i18n/i18n.cljc`:

```clojure
;; src/reagent_i18n/i18n.cljc
(ns reagent-i18n.i18n
  (:require [datadriven.hiccup :as hiccup]
            [m1p.core :as m1p]))

(def dictionary
  (m1p/prepare-dictionary
   {:page/title "Welcome!"
    :user/greeting [:fn/str "Nice to see you, {{:user/given-name}}!"]}))

(defn translate [_attrs [k params]]
  (m1p/lookup {} dictionary k params))

(hiccup/register-alias! ::k translate)
```

An alias function receives the attributes and a list of the children. Our
alias takes no attributes, and expects the dictionary key as its first child
and the (optional) parameters as the second: `[::i18n/k :user/greeting
{:user/given-name "Christian"}]`.

Try it in the REPL. `hiccup/expand` expands aliases and nothing else, which
makes it handy for seeing what a piece of hiccup turns into:

```clojure
(require '[datadriven.hiccup :as hiccup])
(require '[reagent-i18n.i18n :as i18n])

(hiccup/expand [:h1 [::i18n/k :page/title]])
;;=> [:h1 {} "Welcome!"]
```

The empty map is just `expand` filling in the missing attributes. (Replicant
can render hiccup to an HTML string on the JVM. For how to do that with this
setup, see the [server-side alias tutorial](./server-alias.md).)

### Different tongues

One language isn't much of an i18n solution. Let's add a second one, and
move the dictionaries to namespaces of their own.

```clojure
;; src/reagent_i18n/i18n/nb.cljc
(ns reagent-i18n.i18n.nb)

(def dictionary
  {:page/title "Velkommen!"
   :user/greeting [:fn/str "Hyggelig å se deg, {{:user/given-name}}!"]})
```

```clojure
;; src/reagent_i18n/i18n/en.cljc
(ns reagent-i18n.i18n.en)

(def dictionary
  {:page/title "Welcome!"
   :user/greeting [:fn/str "Nice to see you, {{:user/given-name}}!"]})
```

In `reagent-i18n.i18n`, replace `dictionary` with a map of prepared
dictionaries, one per locale:

```clojure
;; src/reagent_i18n/i18n.cljc
(ns reagent-i18n.i18n
  (:require [datadriven.hiccup :as hiccup]
            [m1p.core :as m1p]
            [reagent-i18n.i18n.en :as en]
            [reagent-i18n.i18n.nb :as nb]))

(def dictionaries
  (-> {:nb nb/dictionary
       :en en/dictionary}
      (update-vals m1p/prepare-dictionary)))
```

The alias now needs to know which locale to use. For now, let the caller say
so explicitly, with a namespaced attribute:

```clojure
;; src/reagent_i18n/i18n.cljc
(defn translate [{::keys [locale]} [k params]]
  (m1p/lookup {} (get dictionaries locale) k params))

(hiccup/register-alias! ::k translate)
```

And in the REPL:

```clojure
(hiccup/expand [:h1 [::i18n/k {::i18n/locale :nb} :page/title]])
;;=> [:h1 {} "Velkommen!"]

(hiccup/expand [:h1 [::i18n/k {::i18n/locale :en} :page/title]])
;;=> [:h1 {} "Welcome!"]
```

The locale is namespaced for a reason. By convention, namespaced attributes
are parameters for the alias, and `prepare` removes them before Reagent sees
the hiccup, so they never end up as HTML attributes.

### Loosely coupled dictionaries

The alias is tied to our dictionaries through a `require`. If you wanted to
reuse it in another app, or publish it as a library, it should work with
whatever dictionaries the app has. We don't want to pass them as arguments
through every view function either: they're the same for the whole run of the
app.

Data like that is what *alias data* is for. Whatever you pass to `prepare` (or
`expand`) as `:alias-data`, every alias finds in its attributes under
`:datadriven.hiccup/alias-data`, or `::hiccup/alias-data` for short. Let's
have the alias take its dictionaries from there:

```clojure
;; src/reagent_i18n/i18n.cljc
(ns reagent-i18n.i18n
  (:require [datadriven.hiccup :as hiccup]
            [m1p.core :as m1p]))

(defn translate [{::keys [locale] :as attrs} [k params]]
  (let [dictionary (-> attrs ::hiccup/alias-data :dictionaries (get locale))]
    (m1p/lookup {} dictionary k params)))

(hiccup/register-alias! ::k translate)
```

`reagent-i18n.i18n` no longer knows about any dictionaries. The app provides
them instead. The browser app renders in `core.cljs`, so that's where they go:

```clojure
;; src/reagent_i18n/core.cljs
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

,,,

(defn render-ui [_state]
  [:h1 [::i18n/k {::i18n/locale :nb} :page/title]])

(defn app []
  (hiccup/prepare
   (render-ui @(rf/subscribe [:app/state]))
   {:alias-data {:dictionaries dictionaries}}))
```

(`,,,` marks code left out of a listing.) The browser now says "Velkommen!".

`core.cljs` only runs in the browser, so to check the alias on the JVM we
build the same map in a test, and pass it to `expand` the same way:

```clojure
;; test/reagent_i18n/i18n_test.cljc
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
  (testing "Looks up a key in the given locale"
    (is (= (hiccup/expand [:h1 [::i18n/k {::i18n/locale :nb} :page/title]]
                          {:alias-data {:dictionaries dictionaries}})
           [:h1 {} "Velkommen!"]))))
```

Run it with `clojure -M:dev -m kaocha.runner`.

With the dictionaries in alias data, no view function has to pass them
around. In Replicant, alias data comes with a warning: when it changes,
Replicant re-renders the whole UI from scratch. That doesn't apply here.
`prepare` expands every alias each time the `app` component renders anyway,
and React then changes only the DOM nodes whose content differs. The
dictionaries are also defined once and never change while the app runs.

## Implicit locale

The locale is still explicit. That's predictable, but awkward: nearly every
view function would need the locale, just to hand it to `::i18n/k`. What
would it take to make it implicit?

We already have a way to give every alias the same data. The locale can go in
alias data, next to the dictionaries:

```clojure
;; src/reagent_i18n/i18n.cljc
(defn translate
  "Implements the `::k` alias: `[::i18n/k :user/greeting params]` looks up
  `:user/greeting` in the dictionary for the current locale. The dictionaries
  and the locale come from the alias data."
  [attrs [k params]]
  (let [{:keys [dictionaries locale]} (::hiccup/alias-data attrs)]
    (m1p/lookup {} (get dictionaries locale) k params)))

(hiccup/register-alias! ::k translate)
```

Now the hiccup doesn't mention the locale at all:

```clojure
(hiccup/expand [:h1 [::i18n/k :page/title]]
               {:alias-data {:dictionaries dictionaries
                             :locale :nb}})
;;=> [:h1 {} "Velkommen!"]

(hiccup/expand [:h1 [::i18n/k :page/title]]
               {:alias-data {:dictionaries dictionaries
                             :locale :en}})
;;=> [:h1 {} "Welcome!"]
```

This is a big improvement. Views talk about texts in the abstract ("the page
title") and don't need to know which language the user reads.

The current locale is application state, so it lives in app-db. The setup
already puts `:locale :en` there. A subscription reads it, and `app` passes it
on as alias data:

```clojure
;; src/reagent_i18n/core.cljs
(rf/reg-sub :locale
  (fn [db _]
    (:locale db)))

(defn app []
  (hiccup/prepare
   (render-ui {:user/given-name "Christian"})
   {:alias-data {:dictionaries dictionaries
                 :locale @(rf/subscribe [:locale])}}))
```

What does this cost? The locale is now an input to the UI that doesn't show
up in any view function's arguments. When it changes, the `:locale`
subscription changes, `app` renders again, and every `::i18n/k` is looked up
in the new dictionary. In Replicant, a change to alias data rebuilds the whole
UI, and state kept in the DOM, such as text typed into an input field, is
lost. The original tutorial weighs that trade-off. With Reagent it doesn't
arise: React compares the new result with the old one and replaces only the
text that changed. Anything the user typed stays put.

The trade-off that remains is a smaller one. A view's final output now
depends on something besides its arguments. If you test a view function
without expanding it, you get `[::i18n/k :page/title]`, which is often what
you want. To see actual text, pass the alias data to `expand`, as above.

## But why?

You may wonder what we gained. We could have called `m1p/lookup` directly
wherever we used `::i18n/k`. The benefits are those of aliases in general
(see the [guide](../guides/data-driven-reagent.md#why-not-just-call-the-function)):

- **Late binding.** The lookup happens when the hiccup is prepared, with the
  dictionaries and locale that are current *then*. Views don't depend on the
  dictionaries or even on m1p, and don't care which locale is active.
  (Replicant goes one step further and only runs the alias again when its key
  or parameters change. Our adapter runs it on every render of `app`, which
  is cheap for a map lookup.)
- **A higher level of abstraction.** `[::i18n/k :user/greeting user]` says
  "greet the user". "Nice to see you, Christian!" is one particular wording of
  that. A test can check that the view greets the user, and it keeps passing
  when someone improves the wording or adds a language.

## Final code listing

Here's a small UI that uses the alias, with a button to switch between the
two languages. First, add a text for the button to each dictionary:

```clojure
;; src/reagent_i18n/i18n/en.cljc
(ns reagent-i18n.i18n.en)

(def dictionary
  {:page/title "Welcome!"
   :user/greeting [:fn/str "Nice to see you, {{:user/given-name}}!"]
   :locale/switch "På norsk, er du snill"})
```

```clojure
;; src/reagent_i18n/i18n/nb.cljc
(ns reagent-i18n.i18n.nb)

(def dictionary
  {:page/title "Velkommen!"
   :user/greeting [:fn/str "Hyggelig å se deg, {{:user/given-name}}!"]
   :locale/switch "In English, please"})
```

The alias:

```clojure
;; src/reagent_i18n/i18n.cljc
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
```

And the app. Clicking the button dispatches `[:switch-locale]`, and the event
handler flips the locale in app-db:

```clojure
;; src/reagent_i18n/core.cljs
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
```

`render-ui` passes the user map as the parameters for `:user/greeting`, so
`{{:user/given-name}}` is filled in from it. `rf/dispatch-sync` runs the
`:app/init` event right away, so the locale is in app-db before the first
render.

The finished test,
[`i18n_test.cljc`](../code/i18n-alias/test/reagent_i18n/i18n_test.cljc),
checks the implicit locale in both languages, and that parameters are filled
in.

## What's different from the Replicant version

- **The alias** is registered with `hiccup/register-alias!` under the keyword
  `::k`, and written `[::i18n/k ...]`. Replicant's `defalias` would also
  define a var, allowing `[i18n/k ...]`.
- **Alias data** is found under `::hiccup/alias-data` instead of
  `:replicant/alias-data`.
- **Trying things on the JVM** uses `hiccup/expand`, which returns hiccup,
  where the original used `replicant.string/render`, which returns HTML.
- **Changing alias data doesn't rebuild the UI.** In Replicant, a change to
  `:alias-data` re-renders everything from scratch, so an implicit locale has
  a cost. Here, React updates only the text that changed.
- **Aliases aren't memoized.** Replicant only calls an alias again when its
  arguments change. `prepare` expands every alias each time `app` renders.
- **The locale lives in app-db**, and an event switches it, in place of an
  atom and a dispatch function.

For a larger example of aliases working together, see
[A sortable table alias](./sortable-table.md).
