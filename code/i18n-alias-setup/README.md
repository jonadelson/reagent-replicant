# Alias powered i18n: setup

The starting point for the [Alias powered i18n
tutorial](../../tutorials/i18n-alias.md): an empty Reagent + re-frame app with
the [m1p](https://github.com/cjohansen/m1p) library and `datadriven.hiccup`.
The finished code is in [`code/i18n-alias`](../i18n-alias/).

```sh
npm install
npx shadow-cljs watch app
```

Open http://localhost:8080/. Tests (there are none yet):
`clojure -M:dev -m kaocha.runner`.
