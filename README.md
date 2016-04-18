# libsass-clj

small clojure wrapper for [jsass](https://github.com/bit3/jsass)
(which uses libsass via JNI).

[![Clojars Project](https://img.shields.io/clojars/v/mrmcc3/libsass-clj.svg)](https://clojars.org/mrmcc3/libsass-clj)

#### Examples

```clojure
;; build all sass files in a directory with embedded source maps 
;; return a map of results
(build "test/sass-files" {:source-map {:embed true}})

;; build all sass files in a directory, minify output and write to :output-dir
(build ["test/sass-files"] {:output-dir   "public/compiled-css"
                            :output-style :compressed})

;; clean build artifacts
(clean ["test/sass-files"] {:output-dir "public/compiled-css"})
```

#### TODO

* write better usage docs/examples
* support all jsass/libsass options
  * currently only inline source-maps are supported
* tests?
* custom importers? webjars
