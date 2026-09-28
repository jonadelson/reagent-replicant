# Declarative forms

> Adapted for Reagent + re-frame from [Declarative forms](https://replicant.fun/tutorials/declarative-forms/)
> by Christian Johansen. The code for this tutorial is in [`code/declarative-forms`](../code/declarative-forms/).

This is the third and last tutorial on handling forms with data. In
[the second one](./first-class-forms.md) we made forms a first-class
concept: a form is read, validated and submitted as a whole. Now we'll make
the validation and submit logic declarative, so that a new form is mostly a
matter of describing it with data.

## Setup

We continue from [the previous tutorial](./first-class-forms.md). Keep
going with your own code, or copy
[`code/first-class-forms`](../code/first-class-forms/) and start it:

```sh
cp -r code/first-class-forms practice-log
cd practice-log
npm install
npx tailwindcss -i ./src/main.css -o ./resources/public/tailwind.css --watch
```

And in another terminal:

```sh
npx shadow-cljs watch app
```

Open http://localhost:8080. Tests run with `clojure -M:dev -m kaocha.runner`.

A short recap, in case you start here:

- The app is a practice log (a todo list, really) built with
  [Reagent](https://reagent-project.github.io/) and
  [re-frame](https://day8.github.io/re-frame/). All state lives in re-frame's
  **app-db** map, and changes through **events**. Views are pure functions
  that return hiccup, with event handlers written as data, like
  `{:on {:click [:store/assoc-in [:tasks 1 :task/editing?] true]}}`. See
  [Event handlers as data](../guides/data-driven-reagent.md#event-handlers-as-data).
- Tasks are stored in app-db keyed by id, under `:tasks`. The state of each
  open form, such as its validation errors, is stored under `:forms`, keyed
  by a form id like `[:forms/edit-task 3]`. The original tutorials use
  [Datascript](https://github.com/tonsky/datascript) instead; this port uses
  a plain map, like most re-frame apps.
- The `:event/form-data` placeholder is replaced with a map of everything in
  the form when the event fires.
- Many snippets leave out unchanged code with `,,,`.

## The task

We won't add any features this time. Instead, we'll rework the code so that
new forms are easier to make, without handing too much control over to
conventions and assumptions.

Here's how a form works after the last tutorial:

- The form submits with `[:form/submit form-type id :event/form-data]`.
- The submit function for that kind of form validates the data. If there are
  errors, it stores them in app-db. If not, it returns the actions that
  process the form.
- While there are validation errors, the form is validated again on every
  input.

`:form/submit` passes the data on to a function picked by the form type, so
each form has a function shaped like this:

```clojure
(defn submit-edit-task [form-type task-id data]
  (if-let [errors (seq (validate-edit-task data))]
    ;; store the validation errors
    ;; process the form
    ))
```

Every form will follow this flow, so why write it again for each one? The
machinery can take care of the flow, and of the most common validations.
Imagine describing the form like this:

```clojure
(defn edit-task [data task-id]
  [[:store/merge-in [:tasks task-id] (assoc data :task/editing? false)]])

(def edit-form
  {:form/type :forms/edit-task
   :form/fields
   [{:k :task/name
     :validations [{:validation/kind :required}]}
    {:k :task/duration
     :validations
     [{:validation/kind :max-num
       :validation/message "Duration can not exceed 60 minutes"
       :max 60}]}]

   :form/handler edit-task})
```

All the validation code is gone, replaced by a description of the rules. The
form-specific code is reduced to what happens to valid data. That's less
code to write for each new form, and fewer places for bugs to hide. By the
end of the tutorial, even the handler function will be replaced by data.

## Generalized validation

First up, functions that validate data according to a form description.
They're pure functions, so let's write the tests as we go. The first function
takes a form description and the form data, and returns a list of validation
errors:

```clojure
;; test/toil/forms_test.cljc
(ns toil.forms-test
  (:require [clojure.test :refer [deftest is testing]]
            [toil.forms :as forms]))

(deftest validate-form-data-test
  (testing "Validates required field"
    (is (= (forms/validate-form-data
            {:form/id :forms/test-form
             :form/fields
             [{:k :task/name
               :validations [{:validation/kind :required}]}]}
            {:task/name nil})
           [{:validation-error/field :task/name
             :validation-error/message "Please type in some text"}]))))
```

To pass it, we go through the fields, check each field's validations, and
collect the errors:

```clojure
;; src/toil/forms.cljc
(defn validate-field [field validation data]
  (case (:validation/kind validation)
    :required
    (when (nil? data)
      {:validation-error/field field
       :validation-error/message "Please type in some text"})))

(defn validate-form-data [form data]
  (->> (:form/fields form)
       (mapcat
        (fn [{:keys [k validations]}]
          (let [field-data (get data k)]
            (keep #(validate-field k % field-data) validations))))))
```

The form description we're aiming for has a custom `:validation/message` on
one of its rules. Let's allow that for required fields too:

```clojure
(testing "Validates required field with custom message"
  (is (= (forms/validate-form-data
          {:form/id :forms/test-form
           :form/fields
           [{:k :task/name
             :validations [{:validation/kind :required
                            :validation/message "Oh no!"}]}]}
          {:task/name nil})
         [{:validation-error/field :task/name
           :validation-error/message "Oh no!"}])))
```

An `or` in the right place does it:

```clojure
(defn validate-field [field validation data]
  (case (:validation/kind validation)
    :required
    (when (nil? data)
      {:validation-error/field field
       :validation-error/message
       (or (:validation/message validation)
           "Please type in some text")})))
```

An empty text field gives us `""`, not `nil`, so an empty string should fail
the requirement too:

```clojure
(testing "Empty strings do not satisfy requiredness"
  (is (= (forms/validate-form-data
          {:form/id :forms/test-form
           :form/fields
           [{:k :task/name
             :validations [{:validation/kind :required}]}]}
          {:task/name ""})
         [{:validation-error/field :task/name
           :validation-error/message "Please type in some text"}])))
```

```clojure
(defn validate-field [field validation data]
  (case (:validation/kind validation)
    :required
    (when (or (nil? data) (= "" data)) ;; <=
      ,,,)))
```

The maximum number validation follows the same pattern:

```clojure
(defn validate-field [field validation data]
  (case (:validation/kind validation)
    :required
    ,,,

    :max-num
    (when (< (:max validation) (or data 0))
      {:validation-error/field field
       :validation-error/message
       (or (:validation/message validation)
           (str "Should be max " (:max validation)))})))
```

The tests for all of these are in
[`test/toil/forms_test.cljc`](../code/declarative-forms/test/toil/forms_test.cljc).

Next, a function to use for the `:form/validate` event. It's much like the
one we wrote for the edit form last time, but works for any form:

```clojure
(defn validate [form data]
  [[:store/assoc-in [:forms (:form/id form)]
    {:form/id (:form/id form)
     :form/validation-errors (validate-form-data form data)}]])
```

## Generalized submits

With validation out of the way, it's time for submitting. Again, tests lead
the way. Here's how the edit form is submitted today:

```clojure
[:form/submit :forms/edit-task (:task/id task) :event/form-data]
```

The generic submit function should accept whatever arguments a form needs
(here, the task id) and pass them on to the form's handler. The first test
checks the validation:

```clojure
(deftest submit-test
  (testing "Validates form"
    (is (= (forms/submit
            {:form/id [:forms/test-form 1]
             :form/fields
             [{:k :task/name
               :validations [{:validation/kind :required}]}]}
            {:task/name nil}
            1)
           [[:store/assoc-in [:forms [:forms/test-form 1]]
             {:form/id [:forms/test-form 1]
              :form/validation-errors
              [{:validation-error/field :task/name
                :validation-error/message "Please type in some text"}]}]]))))
```

Note that `:form/id` is the id of this particular instance of the form, the
form type plus the task id. The implementation starts out a lot like
`validate`:

```clojure
(defn submit [form data & args]
  (if-let [errors (seq (validate-form-data form data))]
    [[:store/assoc-in [:forms (:form/id form)]
      {:form/id (:form/id form)
       :form/validation-errors errors}]]))
```

When the data is valid, the form's `:form/handler` should decide what
happens:

```clojure
(testing "Calls form handler when form is valid"
  (is (= (forms/submit
          {:form/id [:forms/test-form 1]
           :form/handler (fn [data task-id]
                           [[:store/merge-in [:tasks task-id] data]])}
          {:task/name "Do it!"}
          1)
         [[:store/merge-in [:tasks 1] {:task/name "Do it!"}]])))
```

```clojure
(defn submit [form data & args]
  (if-let [errors (seq (validate-form-data form data))]
    [[:store/assoc-in [:forms (:form/id form)]
      {:form/id (:form/id form)
       :form/validation-errors errors}]]
    (apply (:form/handler form) data args)))
```

Pretty good, but something from the previous version is missing: the form's
state in app-db is never cleaned up.

Should cleaning up be the job of each form, or of the machinery? If the
machinery does it, a form can't keep its state after a submit. If each form
does it, every form repeats the same action. Lingering validation errors
after a successful submit are unlikely to be what anyone wants, so we'll let
the machinery do it. It can always be made optional later.

In the original, the cleanup is a Datascript retraction, and the submit
function takes care to squeeze it into the handler's transaction, if there is
one, so that the change doesn't cause two renders. In re-frame, every action
is its own event, and there's no transaction to squeeze it into. There's no
need to either: Reagent renders at most once per animation frame, so events
that are handled in quick succession usually end up in the same render. We
simply add the cleanup at the end:

```clojure
(testing "Calls form handler when form is valid, then cleans up"
  (is (= (forms/submit
          {:form/id [:forms/test-form 1]
           :form/handler (fn [data task-id]
                           [[:store/merge-in [:tasks task-id] data]])}
          {:task/name "Do it!"}
          1)
         [[:store/merge-in [:tasks 1] {:task/name "Do it!"}]
          [:store/dissoc-in [:forms [:forms/test-form 1]]]])))
```

```clojure
(defn submit [form data & args]
  (if-let [errors (seq (validate-form-data form data))]
    [[:store/assoc-in [:forms (:form/id form)]
      {:form/id (:form/id form)
       :form/validation-errors errors}]]
    (-> (vec (apply (:form/handler form) data args))
        (conj [:store/dissoc-in [:forms (:form/id form)]]))))
```

## Connecting the dots

Let's put the declarative form to use. Most of the form code is now generic,
so it makes sense to separate it more clearly from the code about tasks. The
`toil.task` namespace, which so far holds `add-task` and `get-tasks`, gets
the edit form:

```clojure
;; src/toil/task.cljc
(ns toil.task)

,,,

(defn edit-task [data task-id]
  [[:store/merge-in [:tasks task-id] (assoc data :task/editing? false)]])

(def edit-form
  {:form/type :forms/edit-task
   :form/fields
   [{:k :task/name
     :validations [{:validation/kind :required}]}
    {:k :task/duration
     :validations
     [{:validation/kind :max-num
       :validation/message "Duration can not exceed 60 minutes"
       :max 60}]}]

   :form/handler edit-task})
```

In `core.cljs`, we keep a map of all the app's forms by type:

```clojure
;; src/toil/core.cljs
(ns toil.core
  (:require ,,,
            [toil.task :as task]
            ,,,))

;; All the forms in the app, by type

(def registered-forms
  (->> [task/edit-form]
       (map (juxt :form/type identity))
       (into {})))
```

(The original calls this map `forms`, the same name as the alias for
`toil.forms`. That works, but it's easy to trip over.)

The two form events look up the form description, add the id of the form
instance, and call the generic functions. The `case` expressions and the
edit form's own validation and submit functions are gone:

```clojure
;; src/toil/core.cljs
(rf/reg-event-fx :form/submit
  (fn [_ [_ form-type id data & args]]
    {:fx (actions->fx
          (apply forms/submit
                 (assoc (get registered-forms form-type) :form/id [form-type id])
                 data
                 id
                 args))}))

(rf/reg-event-fx :form/validate
  (fn [_ [_ form-id data]]
    {:fx (actions->fx
          (forms/validate
           (assoc (get registered-forms (first form-id)) :form/id form-id)
           data))}))
```

`actions->fx`, from the last tutorial, turns the returned actions into
re-frame `:dispatch` effects.

### Reorganizing code

`toil.forms` has become a small, general-purpose form library: rendering
helpers, validation and submit. The task-specific rendering functions can
join `edit-form` in `toil.task`: `render-task-form`, `render-edit-form`,
`render-task`, `render-tasks` and `priorities` move over unchanged (apart
from calling `get-tasks` without the `task/` prefix). That leaves very little
in the UI namespace:

```clojure
;; src/toil/ui.cljc
(ns toil.ui
  (:require [toil.task :as task]))

(defn render-page [db]
  [:main.md:p-8.p-4.max-w-screen-m
   [:h1.text-2xl.mb-4 "Practice log"]
   (task/render-task-form db)
   (task/render-tasks db)])
```

`toil.task` now requires `phosphor.icons` and `toil.forms`. The tests for the
render functions move from `ui_test.cljc` to `task_test.cljc` as well.

What's left is a form library of pure functions in
[`toil.forms`](../code/declarative-forms/src/toil/forms.cljc), and pure
functions about tasks in
[`toil.task`](../code/declarative-forms/src/toil/task.cljc). What more could
you want?

## Fully declarative forms

Well, the promise was that the form's handler function would go away too.
Here it is again:

```clojure
(defn edit-task [data task-id]
  [[:store/merge-in [:tasks task-id] (assoc data :task/editing? false)]])
```

It does two things:

- It says where the data goes: into the task with id `task-id`.
- It adds `:task/editing? false`, to close the form.

The original's handler has a third job: it turns `nil` values into
Datascript retractions. Our maps can hold `nil` just fine, but the empty
duration from the last tutorial is still lying around as
`:task/duration nil`. We'll tidy that up on the way.

### Where does the data go?

If the form data itself said which entity it belongs to, a generic event
could store it. Something like `[:store/save [data]]`, where `data` contains
`:task/id 3`, would find the task at `[:tasks 3]`. For that to work, the
store must know which attributes identify an entity, and where in app-db
those entities live. The original makes a list of Datascript's unique
attributes for the same purpose. We write it down in a new namespace:

```clojure
;; src/toil/store.cljc
(ns toil.store)

(def identity-attrs
  "Attributes that identify an entity, and where in app-db the entities with
  that attribute live."
  {:task/id [:tasks]
   :form/id [:forms]})

(defn entity-path
  "Returns the app-db path of `entity`, based on its identity attribute."
  [entity]
  (some (fn [[attr path]]
          (when-let [id (get entity attr)]
            (conj path id)))
        identity-attrs))
```

`(entity-path {:task/id 3 :task/name "Scales"})` is `[:tasks 3]`, and
`(entity-path {:form/id [:forms/edit-task 3]})` is
`[:forms [:forms/edit-task 3]]`.

The save function merges each entity into the one it identifies. And since
we're writing a generic save anyway, we'll decide what `nil` means: a key
with a `nil` value is removed from the stored entity. A cleared duration then
really disappears, which is the job the original's `:db/transact-w-nils`
action does with retractions.

```clojure
;; src/toil/store.cljc
(defn save
  "Merges each entity map into the entity it identifies. Keys with nil values
  are removed from the stored entity."
  [db entities]
  (reduce
   (fn [db entity]
     (let [path (or (entity-path entity)
                    (throw (ex-info "Entity has no identity attribute"
                                    {:entity entity})))
           nil-ks (keep (fn [[k v]] (when (nil? v) k)) entity)]
       (update-in db path #(apply dissoc (merge % entity) nil-ks))))
   db
   entities))
```

A pure function on app-db is easy to test
([`test/toil/store_test.cljc`](../code/declarative-forms/test/toil/store_test.cljc)),
and turning it into an event takes three lines in `core.cljs`:

```clojure
;; src/toil/core.cljs
(ns toil.core
  (:require ,,,
            [toil.store :as store]
            ,,,))

,,,

(rf/reg-event-db :store/save
  (fn [db [_ entities]]
    (store/save db entities)))
```

### Putting the rest in the form

The remaining job of the handler is to add `:task/id` and
`:task/editing? false` to the data. Borrowing a trick the original used in
the previous tutorial, we can put them in the form as hidden fields, so that
they come along with the rest of the form data:

```clojure
;; src/toil/task.cljc
(defn render-edit-form [form task]
  [:form.my-4.flex.flex-col.gap-4
   {:on {:submit [[:event/prevent-default]
                  [:form/submit :forms/edit-task (:task/id task) :event/form-data]]}}
   (forms/text-input task :task/id {:type "hidden" :data-type "number"})
   (forms/text-input task :task/editing? {:type "hidden"
                                          :default-value "false"
                                          :data-type "boolean"})
   ,,,])
```

The `:default-value` in the attributes replaces the one `text-input` takes
from the task. (The original sets `:value "false"`. React doesn't like an
input with both a `value` and a `defaultValue`, so we override the default
instead.)

The `data-type` hints tell `get-input-value` how to read the values. It
already knows `"number"`. We add `"boolean"`:

```clojure
;; src/toil/core.cljs
(defn get-input-value [^js element]
  (cond
    ,,,

    (= "boolean" (aget (.-dataset element) "type"))
    (= "true" (.-value element))

    :else
    (.-value element)))
```

Now we can describe the whole form as data, including what to do on submit:

```clojure
;; src/toil/task.cljc
(def edit-form
  {:form/type :forms/edit-task
   :form/fields
   [{:k :task/name
     :validations [{:validation/kind :required}]}
    {:k :task/duration
     :validations
     [{:validation/kind :max-num
       :validation/message "Duration can not exceed 60 minutes"
       :max 60}]}]

   :form/submit-actions
   [[:store/save [:event/form-data]]]})
```

`edit-task` can be deleted. For this to work, `submit` must use
`:form/submit-actions` when the form has them, and only call `:form/handler`
otherwise:

```clojure
;; src/toil/forms.cljc
(defn submit [form data & args]
  (if-let [errors (seq (validate-form-data form data))]
    ,,,
    (-> (or (:form/submit-actions form)
            (when-let [handler (:form/handler form)]
              (apply handler data args)))
        ,,,)))
```

There's one more thing. The submit actions contain the `:event/form-data`
placeholder, and they need the actual form data in its place. The original
runs the actions through its placeholder interpolation a second time, passing
in the form data it has already gathered, so it doesn't have to read the form
again.

We're in a better position here. Placeholders were filled in before the
`:form/submit` event was dispatched, and the event carries the form data, so
`submit` can do the replacement itself with
[`clojure.walk/postwalk-replace`](https://clojuredocs.org/clojure.walk/postwalk-replace).
No DOM involved, and `submit` stays a pure function we can test:

```clojure
;; src/toil/forms.cljc
(ns toil.forms
  (:require [clojure.walk :as walk]))

,,,

(defn submit [form data & args]
  (if-let [errors (seq (validate-form-data form data))]
    [[:store/assoc-in [:forms (:form/id form)]
      {:form/id (:form/id form)
       :form/validation-errors errors}]]
    (-> (or (:form/submit-actions form)
            (when-let [handler (:form/handler form)]
              (apply handler data args)))
        (->> (walk/postwalk-replace {:event/form-data data}))
        vec
        (conj [:store/dissoc-in [:forms (:form/id form)]]))))
```

```clojure
;; test/toil/forms_test.cljc
(testing "Uses submit actions, with the form data in place of :event/form-data"
  (is (= (forms/submit
          {:form/id [:forms/test-form 1]
           :form/submit-actions [[:store/save [:event/form-data]]]}
          {:task/id 1
           :task/name "Do it!"}
          1)
         [[:store/save [{:task/id 1
                         :task/name "Do it!"}]]
          [:store/dissoc-in [:forms [:forms/test-form 1]]]])))
```

And that's it: forms that are fully data-driven. Edit a task, clear its
duration and save, and the `:task/duration` key is gone from the task in
app-db.

## In conclusion

We kept improving our little form framework until forms could be processed
fully declaratively: describe the fields and their validation rules, and the
actions to perform when a valid form is submitted.

Is it worth it? That depends on how many forms you have. For a single form,
this is probably overdoing it. With a handful of forms, it starts paying for
itself.

We could have gone further and described every field in the form, including
those without validation rules, and rendered the form from that description
too. That can work, but it tends to lock you into too many assumptions and
too little flexibility. A few good helper functions for rendering go a long
way, and leave you in full control of the layout.

Hopefully these three tutorials gave you some ideas for working with forms in
a data-driven frontend, whether you use Replicant or Reagent and re-frame.
There's no need to copy any of them as is: take the parts you like, or come
up with your own solution.

## What's different from the Replicant version

- **app-db instead of Datascript.** `:db/transact-w-nils`, which turns `nil`
  values into retractions, becomes a `:store/save` event backed by the pure
  `toil.store/save`. It finds where an entity lives through a small map of
  identity attributes (the counterpart of the original's list of unique
  attributes), and removes keys whose value is `nil`.
- **Form cleanup is a separate action** at the end of the list, instead of
  being merged into the handler's transaction. Each action is its own re-frame
  event, and Reagent batches rendering.
- **The `:event/form-data` placeholder in submit actions is replaced inside
  the pure `submit` function**, with the data the `:form/submit` event
  already carries. The original interpolates the actions a second time in its
  DOM-aware action runner.
- **The hidden `:task/editing?` field** overrides `:default-value` instead of
  setting `:value`, since React doesn't allow both.
- The map of forms is called `registered-forms`, and the render functions'
  tests move along with them into `task_test.cljc`.
