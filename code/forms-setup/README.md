# Practice log: starting point (Reagent + re-frame)

The starting point for the [data-driven form processing
tutorial](../../tutorials/forms.md). It's an empty page wired up with Reagent,
re-frame, `datadriven.hiccup`, Tailwind CSS and daisyUI.

```sh
npm install
npx tailwindcss -i ./src/main.css -o ./resources/public/tailwind.css --watch
npx shadow-cljs watch app   # in another terminal
```

Open http://localhost:8080/. Run the tests with `clojure -M:dev -m kaocha.runner`.
The `Makefile` has the same commands (`make tailwind`, `make shadow`, `make test`).

Every re-frame event is logged to the browser console (see `dev/toil/dev.cljs`).
