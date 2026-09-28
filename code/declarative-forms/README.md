# Declarative forms (Reagent + re-frame)

The finished code for the [Declarative forms tutorial](../../tutorials/declarative-forms.md).
Describes the edit form, its validations and its submit actions entirely as data.

```sh
npm install
npx tailwindcss -i ./src/main.css -o ./resources/public/tailwind.css --watch
npx shadow-cljs watch app   # in another terminal
```

Open http://localhost:8080/. Run the tests with `clojure -M:dev -m kaocha.runner`.
The `Makefile` has the same commands (`make tailwind`, `make shadow`, `make test`).

Every re-frame event is logged to the browser console (see `dev/toil/dev.cljs`).
