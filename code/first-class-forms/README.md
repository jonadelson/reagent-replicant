# Data-driven first class forms (Reagent + re-frame)

The finished code for the [Data-driven first class forms tutorial](../../tutorials/first-class-forms.md).
Adds an edit form that is read, validated and submitted as a whole.

```sh
npm install
npx tailwindcss -i ./src/main.css -o ./resources/public/tailwind.css --watch
npx shadow-cljs watch app   # in another terminal
```

Open http://localhost:8080/. Run the tests with `clojure -M:dev -m kaocha.runner`.
The `Makefile` has the same commands (`make tailwind`, `make shadow`, `make test`).

Every re-frame event is logged to the browser console (see `dev/toil/dev.cljs`).
