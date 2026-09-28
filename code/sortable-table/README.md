# A sortable table alias (Reagent + re-frame)

The finished code for the [sortable table
tutorial](../../tutorials/sortable-table.md): a table of boardgames, sortable
by clicking the column headers, built from a set of table aliases in
`src/boardgames/ui/sortable_table.cljc`. The sort column and order live in the
URL's hash. The starting point is in
[`code/sortable-table-setup`](../sortable-table-setup/).

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
