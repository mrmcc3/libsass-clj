(defproject mrmcc3/libsass-clj "0.1.4"
  :description "clojure wrapper for java libsass compiler (JNA)"
  :url "https://github.com/mrmcc3/libsass-clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories [["clojars" {:sign-releases false}]]
  :jar-exclusions [#".DS_Store"]
  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [io.bit3/jsass "4.1.0"]
                 [hawk "0.2.10"]])
