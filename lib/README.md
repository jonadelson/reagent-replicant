# datadriven.hiccup

The small bridge between data-driven hiccup and Reagent that all the tutorials
use. It turns event handler data (`{:on {:click [:some/event]}}`) into
functions that dispatch to re-frame, fills in placeholders like
`:event/target.value`, and expands aliases (`[:ui/button ...]`).

Read [the guide](../guides/data-driven-reagent.md) for what it does and why.

It's one file, [`src/datadriven/hiccup.cljc`](src/datadriven/hiccup.cljc),
meant to be copied into your own project. Each tutorial project has a copy
(`bin/check` verifies that they're identical).

Run the tests:

```sh
clojure -M:dev -m kaocha.runner
```
