# libsass-clj

small clojure wrapper for [jsass](https://github.com/bit3/jsass)
(which uses libsass via JNA).

[![Clojars Project](https://img.shields.io/clojars/v/mrmcc3/libsass-clj.svg)](https://clojars.org/mrmcc3/libsass-clj)

#### Examples

```clojure
;; build all sass files in the scss directory, minify output
(build ["scss"] {:output-dir   "public/compiled-css"
                 :output-style :compressed})

;; clean build artifacts
(clean ["scss"] {:output-dir "public/compiled-css"})

;; watch files in the scss directory, build on changes, inline source maps
(watch ["scss"] {:output-dir "public/compiled-css"
                 :source-map :inline})
```

#### TODO

* write some usage docs
* support all jsass/libsass options
  * currently only inline source-maps are supported
* some tests over just repl examples?
* custom importers?


