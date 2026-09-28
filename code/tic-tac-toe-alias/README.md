# Tic-Tac-Toe with aliases (Reagent + re-frame)

The finished code for the [Tic-Tac-Toe with aliases
tutorial](../../tutorials/tic-tac-toe-alias.md). It starts from
[`code/tic-tac-toe`](../tic-tac-toe/) and turns the UI functions into aliases.

```sh
npm install
npx shadow-cljs watch app portfolio
```

- The game: http://localhost:8080/
- UI elements in Portfolio: http://localhost:8080/portfolio.html
- Tests: `clojure -M:dev -m kaocha.runner` (add `--watch` to re-run on save)

Unlike `code/tic-tac-toe`, this project uses the full version of
`datadriven.hiccup` from [`lib/`](../../lib/), which supports aliases.
