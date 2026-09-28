# A sortable table alias: setup

The starting point for the [sortable table
tutorial](../../tutorials/sortable-table.md): the boardgame data, a stripped
down router that handles query and hash parameters, the `:ui/a` link alias,
and Tailwind CSS with daisyUI. The finished code is in
[`code/sortable-table`](../sortable-table/).

Run these in two terminals:

```sh
npm install
npm run tailwind          # rebuilds resources/public/tailwind.css on change
```

```sh
npx shadow-cljs watch app
```

- The app: http://localhost:8080/
- Tests: `clojure -M:dev -m kaocha.runner`
