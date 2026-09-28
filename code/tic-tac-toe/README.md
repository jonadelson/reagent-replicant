# Tic-Tac-Toe (Reagent + re-frame)

The finished code for the [Tic-Tac-Toe tutorial](../../tutorials/tic-tac-toe.md).

```sh
npm install
npx shadow-cljs watch app portfolio
```

- The game: http://localhost:8080/
- UI elements in Portfolio: http://localhost:8080/portfolio.html
- Tests: `clojure -M:dev -m kaocha.runner` (add `--watch` to re-run on save)

This project uses the minimal version of `datadriven.hiccup` that the tutorial
builds. The other tutorials use the full version from [`lib/`](../../lib/).
