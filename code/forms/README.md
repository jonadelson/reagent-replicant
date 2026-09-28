# Data-driven form processing (Reagent + re-frame)

The finished code for the [Data-driven form processing tutorial](../../tutorials/forms.md).
Adds tasks to a practice log with a form handled one field at a time.

```sh
npm install
npx tailwindcss -i ./src/main.css -o ./resources/public/tailwind.css --watch
npx shadow-cljs watch app   # in another terminal
```

Open http://localhost:8080/. Run the tests with `clojure -M:dev -m kaocha.runner`.
The `Makefile` has the same commands (`make tailwind`, `make shadow`, `make test`).

Every re-frame event is logged to the browser console (see `dev/toil/dev.cljs`).
