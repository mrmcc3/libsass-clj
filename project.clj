(defproject mrmcc3/libsass-clj "0.1.0-SNAPSHOT"
  :description "clojure wrapper for java libsass compiler (JNA)"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories [["clojars" {:sign-releases false}]]
  :jar-exclusions [#".DS_Store"]
  :dependencies [[org.clojure/clojure "1.7.0" :scope "provided"]
                 [com.github.warmuuh/libsass-maven-plugin "0.1.6-libsass_3.2.4"]])
