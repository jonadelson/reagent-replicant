# Alias powered i18n (Reagent + re-frame)

The finished code for the [Alias powered i18n
tutorial](../../tutorials/i18n-alias.md): an `::i18n/k` alias that looks up
texts with [m1p](https://github.com/cjohansen/m1p), and a button that switches
between English and Norwegian.

```sh
npm install
npx shadow-cljs watch app
```

- The app: http://localhost:8080/
- Tests: `clojure -M:dev -m kaocha.runner`
